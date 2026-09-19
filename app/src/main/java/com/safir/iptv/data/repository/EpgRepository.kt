package com.safir.iptv.data.repository

import com.safir.iptv.data.local.ProgramDao
import com.safir.iptv.data.local.ProgramEntity
import com.safir.iptv.data.remote.Http
import com.safir.iptv.data.remote.epg.XmltvParser
import com.safir.iptv.data.remote.xtream.XtreamClient
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.domain.model.NowNext
import com.safir.iptv.domain.model.PlaylistSource
import com.safir.iptv.domain.model.Program
import com.safir.iptv.domain.model.SourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class EpgRepository(
    private val programDao: ProgramDao,
    private val cacheDir: File
) {

    /** SQLite caps a statement at 999 bound variables. */
    private val sqliteVariableLimit = 900

    fun epgUrlFor(source: PlaylistSource): String? {
        source.epgUrl.takeIf { it.isNotBlank() }?.let { return it }
        return if (source.type == SourceType.XTREAM) {
            XtreamClient(source.url, source.username, source.password).xmltvUrl()
        } else {
            null
        }
    }

    /**
     * Downloads and replaces the guide.
     *
     * @param keepEpgIds only programmes for these channel ids are stored; a provider
     *        guide covering thousands of channels shrinks to what this playlist needs.
     * @return number of programmes stored, or null when the source has no guide URL.
     */
    suspend fun refresh(
        source: PlaylistSource,
        keepEpgIds: Set<String>,
        onProgress: (String, Float?) -> Unit = { _, _ -> }
    ): Int? = withContext(Dispatchers.IO) {
        val url = epgUrlFor(source) ?: return@withContext null

        onProgress(AppLocale.strings.syncGuide, null)

        // Zwei Schritte statt einem: erst die Datei holen, dann auswerten.
        // Solange beides gleichzeitig lief, lag die Verbindung zwischen zwei
        // Datenbankschreibvorgängen still, und mancher Anbieter legt dann auf —
        // das war das „unexpected end of stream". Siehe Http.downloadToFile.
        val file = File(cacheDir, "epg-download.tmp")
        file.delete()

        try {
            var reportedMb = -1L
            Http.downloadToFile(url, file) { bytes ->
                val mb = bytes / 1_048_576L
                if (mb != reportedMb) {
                    reportedMb = mb
                    onProgress("${AppLocale.strings.syncGuide} · $mb MB", null)
                }
            }

            val batch = ArrayList<ProgramEntity>(BATCH_SIZE)
            var stored = 0

            // Erst jetzt den alten Programmführer wegwerfen: wenn der Download
            // scheitert, behält das Gerät lieber die Sendungen von gestern als gar
            // keine.
            programDao.clear()
            try {
                FileInputStream(file).use { raw ->
                    Http.readMaybeGzip(raw) { stream ->
                        XmltvParser.parse(stream, keepEpgIds) { program ->
                            batch += ProgramEntity(
                                epgId = program.channelId,
                                title = program.title,
                                description = program.description,
                                startMs = program.startMs,
                                endMs = program.endMs
                            )
                            if (batch.size >= BATCH_SIZE) {
                                stored += flush(batch)
                                onProgress(AppLocale.strings.syncPrograms(stored), null)
                            }
                        }
                    }
                }
            } catch (broken: CancellationException) {
                // Der Nutzer ist weitergegangen — kein Fehler, der irgendwo
                // gemeldet werden will, sondern ein Abbruch, der durchgereicht
                // gehört.
                throw broken
            } catch (broken: Exception) {
                // Abgeschnittene oder fehlerhafte Datei (XML bricht mitten im Satz
                // ab, oder der gzip-Strom endet zu früh): was schon gelesen wurde,
                // ist trotzdem brauchbar. Nur wenn gar nichts ankam, ist es ein
                // Fehler, den der Nutzer sehen soll.
                stored += flush(batch)
                if (stored == 0) throw broken
            }
            stored += flush(batch)

            // Yesterday and older is dead weight on a TV box.
            programDao.deleteEndedBefore(System.currentTimeMillis() - DAY_MS)
            stored
        } finally {
            file.delete()
        }
    }

    private fun flush(batch: MutableList<ProgramEntity>): Int {
        if (batch.isEmpty()) return 0
        val size = batch.size
        kotlinx.coroutines.runBlocking { programDao.insertAll(batch.toList()) }
        batch.clear()
        return size
    }

    suspend fun hasGuide(): Boolean = programDao.count() > 0

    /** Current and following programme for each of [epgIds]. */
    suspend fun nowNext(epgIds: Collection<String>, nowMs: Long = System.currentTimeMillis()):
        Map<String, NowNext> = withContext(Dispatchers.IO) {
        val ids = epgIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return@withContext emptyMap()

        val rows = ids.chunked(sqliteVariableLimit).flatMap { chunk ->
            programDao.inWindow(chunk, nowMs, nowMs + 12 * 60 * 60 * 1000L)
        }

        rows.groupBy { it.epgId }.mapValues { (_, programs) ->
            val sorted = programs.sortedBy { it.startMs }
            val now = sorted.firstOrNull { nowMs in it.startMs until it.endMs }
            val next = if (now != null) {
                sorted.firstOrNull { it.startMs >= now.endMs }
            } else {
                sorted.firstOrNull { it.startMs > nowMs }
            }
            NowNext(now = now?.toDomain(), next = next?.toDomain())
        }
    }

    /** Everything overlapping [fromMs]..[toMs], grouped by channel — the guide grid. */
    suspend fun window(
        epgIds: Collection<String>,
        fromMs: Long,
        toMs: Long
    ): Map<String, List<Program>> = withContext(Dispatchers.IO) {
        val ids = epgIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return@withContext emptyMap()
        ids.chunked(sqliteVariableLimit)
            .flatMap { chunk -> programDao.inWindow(chunk, fromMs, toMs) }
            .map { it.toDomain() }
            .groupBy { it.channelEpgId }
            .mapValues { (_, list) -> list.sortedBy { it.startMs } }
    }

    /** The next [limit] programmes for one channel, for the guide and the info overlay. */
    suspend fun upcoming(
        epgId: String,
        limit: Int = 24,
        fromMs: Long = System.currentTimeMillis()
    ): List<Program> = withContext(Dispatchers.IO) {
        programDao.upcoming(epgId, fromMs, limit).map { it.toDomain() }
    }

    suspend fun clear() = programDao.clear()

    private companion object {
        const val BATCH_SIZE = 400
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

private fun ProgramEntity.toDomain() = Program(
    channelEpgId = epgId,
    title = title,
    description = description,
    startMs = startMs,
    endMs = endMs
)
