package com.safir.iptv.data.backup

import com.safir.iptv.data.local.FavoriteEntity
import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.data.repository.EpgRepository
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.data.repository.SourceSwitcher
import com.safir.iptv.domain.model.PlaylistSource
import com.safir.iptv.domain.model.SourceType
import com.safir.iptv.ui.i18n.AppLanguage
import com.safir.iptv.ui.i18n.AppLocale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

@Serializable
data class BackupSource(
    val id: Long,
    val name: String,
    val type: String,
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = ""
)

@Serializable
data class BackupFavorite(val channelId: String, val addedAt: Long = 0L)

/**
 * Everything one box needs to become a copy of another: the accesses, the live
 * favourites, and the settings file. The channel lists themselves are not in it —
 * they are fetched fresh from the providers, which is quicker than carrying
 * megabytes of somebody's catalogue around and never out of date.
 *
 * The licence is deliberately left out: it belongs to the box it was bought for.
 */
@Serializable
data class Backup(
    /** No default on purpose: a file without it is somebody else's JSON. */
    val app: String,
    val format: Int = 1,
    val createdAt: Long = 0L,
    val device: String = "",
    val sources: List<BackupSource> = emptyList(),
    val favorites: List<BackupFavorite> = emptyList(),
    /** Preference key → {"t": type, "v": value}; typed, so a number stays a number. */
    val settings: JsonObject = JsonObject(emptyMap())
) {
    companion object {
        const val APP_TAG = "karacast"
    }
}

class NotABackupException : Exception("not a Karacast backup")

class BackupManager(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    private val settings: SettingsStore,
    private val sources: SourceSwitcher
) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    suspend fun create(device: String): Backup = Backup(
        app = Backup.APP_TAG,
        createdAt = System.currentTimeMillis(),
        device = device,
        sources = playlists.allSources().map {
            BackupSource(it.id, it.name, it.type.name, it.url, it.username, it.password, it.epgUrl)
        },
        favorites = playlists.allFavorites().map { BackupFavorite(it.channelId, it.addedAt) },
        settings = encodeValues(settings.exportValues())
    )

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /** @throws NotABackupException for anything that is not one of ours. */
    fun decode(text: String): Backup {
        val backup = runCatching { json.decodeFromString(Backup.serializer(), text) }
            .getOrElse { throw NotABackupException() }
        if (backup.app != Backup.APP_TAG || backup.sources.isEmpty()) throw NotABackupException()
        return backup
    }

    /**
     * Replaces this box's accesses, favourites and settings with the backup's, then
     * loads every playlist again.
     *
     * @return the names of accesses whose playlist could not be loaded — they are
     *         kept all the same and can be reloaded later under Source.
     */
    suspend fun restore(backup: Backup, onProgress: (String) -> Unit): List<String> {
        // Everything that was here goes first, the same way removing it by hand
        // would: channels, favourites, guide and per-access settings.
        playlists.allSources().forEach { sources.remove(it.id) }

        settings.importValues(decodeValues(backup.settings))

        val restored = backup.sources.map {
            PlaylistSource(
                id = it.id,
                name = it.name,
                type = runCatching { SourceType.valueOf(it.type) }.getOrDefault(SourceType.M3U),
                url = it.url,
                username = it.username,
                password = it.password,
                epgUrl = it.epgUrl
            )
        }
        restored.forEach { playlists.saveSource(it) }
        playlists.restoreFavorites(backup.favorites.map { FavoriteEntity(it.channelId, it.addedAt) })

        val failed = mutableListOf<String>()
        restored.forEach { source ->
            onProgress(AppLocale.strings.backup.restoring(source.name))
            try {
                playlists.sync(source)
            } catch (error: Exception) {
                failed += source.name
            }
        }

        val active = playlists.ensureActive()
        if (active != null) {
            sources.switchTo(active.id)
            // The guide is a bonus, as it is at setup: a provider without one, or
            // one that is slow tonight, must not fail the whole restore.
            runCatching {
                epg.refresh(active, playlists.knownEpgIds(active.id)) { step, _ -> onProgress(step) }
            }
        }
        AppLocale.set(AppLanguage.fromTag(settings.language))
        return failed
    }

    // ----------------------------------------------------- preference values

    private fun encodeValues(values: Map<String, Any?>): JsonObject = buildJsonObject {
        values.forEach { (key, value) ->
            val (type, element) = when (value) {
                is String -> "s" to JsonPrimitive(value)
                is Boolean -> "b" to JsonPrimitive(value)
                is Int -> "i" to JsonPrimitive(value)
                is Long -> "l" to JsonPrimitive(value)
                is Float -> "f" to JsonPrimitive(value)
                is Set<*> -> "ss" to JsonArray(value.map { JsonPrimitive(it.toString()) })
                else -> return@forEach
            }
            put(key, JsonObject(mapOf("t" to JsonPrimitive(type), "v" to element)))
        }
    }

    private fun decodeValues(values: JsonObject): Map<String, Any> = buildMap {
        values.forEach { (key, entry) ->
            runCatching {
                val obj = entry.jsonObject
                val type = obj["t"]?.jsonPrimitive?.content
                val value: JsonElement = obj["v"] ?: return@runCatching
                val decoded: Any = when (type) {
                    "s" -> value.jsonPrimitive.content
                    "b" -> value.jsonPrimitive.boolean
                    "i" -> value.jsonPrimitive.int
                    "l" -> value.jsonPrimitive.long
                    "f" -> value.jsonPrimitive.float
                    "ss" -> value.jsonArray.map { it.jsonPrimitive.content }.toSet()
                    else -> return@runCatching
                }
                put(key, decoded)
            }
        }
    }
}
