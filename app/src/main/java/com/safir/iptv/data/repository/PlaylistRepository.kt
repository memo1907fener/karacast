package com.safir.iptv.data.repository

import androidx.room.withTransaction
import com.safir.iptv.data.local.AppDatabase
import com.safir.iptv.data.local.CategoryEntity
import com.safir.iptv.data.local.ChannelEntity
import com.safir.iptv.data.local.ChannelDao
import com.safir.iptv.data.local.ChannelRow
import com.safir.iptv.data.local.FavoriteDao
import com.safir.iptv.data.local.FavoriteEntity
import com.safir.iptv.data.local.RecentDao
import com.safir.iptv.data.local.RecentEntity
import com.safir.iptv.data.local.SourceDao
import com.safir.iptv.data.local.SourceEntity
import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.data.remote.Http
import com.safir.iptv.data.remote.m3u.M3uParser
import com.safir.iptv.data.remote.xtream.XtreamClient
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.domain.model.ALL_CATEGORY_ID
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.CategoryGroup
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.ChannelSort
import com.safir.iptv.domain.model.FAVORITES_CATEGORY_ID
import com.safir.iptv.domain.model.GROUP_PREFIX
import com.safir.iptv.domain.model.PlaylistSource
import com.safir.iptv.domain.model.RECENT_CATEGORY_ID
import com.safir.iptv.domain.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class PlaylistRepository(
    private val database: AppDatabase,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    private val recentDao: RecentDao,
    private val settings: SettingsStore
) {

    /** The access everything on screen belongs to. */
    private val activeId: Long get() = settings.activeSourceId.value

    /** A query that follows the active access, so switching redraws every list. */
    private fun <T> perSource(query: (Long) -> Flow<T>): Flow<T> =
        settings.activeSourceId.flatMapLatest(query)

    fun observeSource(): Flow<PlaylistSource?> =
        perSource { sourceDao.observe(it) }.map { it?.toDomain() }

    /** Every saved access, oldest first. */
    fun observeSources(): Flow<List<PlaylistSource>> =
        sourceDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun allSources(): List<PlaylistSource> = sourceDao.all().map { it.toDomain() }

    suspend fun currentSource(): PlaylistSource? = sourceDao.get(activeId)?.toDomain()

    /** An id no saved access uses — for one that is about to be added. */
    suspend fun newSourceId(): Long = sourceDao.maxId() + 1

    suspend fun saveSource(source: PlaylistSource) {
        sourceDao.upsert(source.toEntity())
    }

    /** Makes [sourceId] the access on screen; see [SettingsStore.switchSource]. */
    fun activate(sourceId: Long) {
        if (sourceId != activeId) settings.switchSource(sourceId)
    }

    /**
     * The active access, or — should it have gone missing — the first one left,
     * which then becomes the active one. Null only when nothing is saved at all.
     */
    suspend fun ensureActive(): PlaylistSource? {
        currentSource()?.let { return it }
        val first = allSources().firstOrNull() ?: return null
        settings.switchSource(first.id)
        return first
    }

    /**
     * Forgets one access completely: its channels, its favourites and its own
     * settings. Its guide is the guide's business, see [EpgRepository.clear].
     */
    suspend fun removeSource(sourceId: Long) {
        val prefix = "$sourceId:"
        database.withTransaction {
            channelDao.deleteChannels(sourceId)
            channelDao.deleteCategories(sourceId)
            favoriteDao.deleteWithPrefix(prefix)
            recentDao.deleteWithPrefix(prefix)
            sourceDao.delete(sourceId)
        }
        settings.forgetSource(sourceId)
    }

    suspend fun channelCount(): Int = channelDao.count(activeId)

    /** Live favourites of every access, for a backup. */
    suspend fun allFavorites(): List<FavoriteEntity> = favoriteDao.all()

    suspend fun restoreFavorites(items: List<FavoriteEntity>) =
        database.withTransaction { items.forEach { favoriteDao.add(it) } }

    // ---------------------------------------------------------------- observing

    /**
     * Provider categories in the user's arrangement, with their own groups in front.
     * Categories the user never sorted keep the provider's order behind the sorted ones.
     */
    fun observeCategories(): Flow<List<Category>> = combine(
        perSource { channelDao.observeCategories(it) },
        settings.groups,
        settings.categoryOrder,
        settings.parentalRevision
    ) { rows, groups, order, _ ->
        // Locked categories are filtered out here rather than hidden in the UI, so
        // that nothing downstream — search, "all channels", a group — can leak one.
        val provided = rows
            .filterNot { settings.isLiveBlocked(it.id, it.name) }
            .map { Category(it.id, it.name, it.channelCount) }
        val countById = provided.associate { it.id to it.channelCount }

        val groupCategories = groups.map { group ->
            Category(
                id = GROUP_PREFIX + group.id,
                name = group.name,
                channelCount = group.categoryIds.sumOf { countById[it] ?: 0 }
            )
        }

        val rank = order.withIndex().associate { (index, id) -> id to index }
        // sortedBy is stable, so anything the user never moved keeps provider order.
        val ordered = provided.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
        groupCategories + ordered
    }

    /**
     * Every provider category, lock or no lock.
     *
     * Exactly one screen wants this: the child lock itself, which has to be able to
     * show a locked category in order to offer to unlock it again. Everywhere else
     * uses [observeCategories], which cannot show one at all.
     */
    fun observeAllCategories(): Flow<List<Category>> =
        perSource { channelDao.observeCategories(it) }.map { rows ->
            rows.map { Category(it.id, it.name, it.channelCount) }
        }

    fun observeChannels(categoryId: String): Flow<List<Channel>> {
        // Re-reads the group on every edit, so membership, hidden channels and the
        // user's order all reach the list that is currently on screen.
        if (categoryId.startsWith(GROUP_PREFIX)) {
            return combine(settings.activeSourceId, settings.groups, ::Pair).flatMapLatest { (sourceId, groups) ->
                val group = groups.firstOrNull { GROUP_PREFIX + it.id == categoryId }
                if (group == null || group.categoryIds.isEmpty()) {
                    flowOf(emptyList<Channel>())
                } else {
                    channelDao.observeByCategories(sourceId, group.categoryIds).map { rows ->
                        val hidden = group.hiddenChannelIds.toSet()
                        // A group draws from several provider categories, so its numbers
                        // would jump around (802, 1140, 47 …). Inside a group they are
                        // handed out fresh from 1, which is also what remote-control
                        // number entry then matches against.
                        arrange(rows, group.channelOrder)
                            .filterNot { it.id in hidden }
                            .mapIndexed { index, channel -> channel.copy(number = index + 1) }
                    }
                }
            }
        }

        val source: Flow<List<ChannelRow>> = when (categoryId) {
            FAVORITES_CATEGORY_ID -> perSource { channelDao.observeFavorites(it) }
            RECENT_CATEGORY_ID -> perSource { channelDao.observeRecent(it) }
            ALL_CATEGORY_ID -> perSource { channelDao.observeAll(it) }
            else -> perSource { channelDao.observeByCategory(it, categoryId) }
        }

        // "Zuletzt gesehen" is already in the only order that means anything there —
        // most recent first — so alphabetical sorting is not applied to it.
        val sortable = categoryId != RECENT_CATEGORY_ID
        return combine(source, settings.channelSort) { rows, sort ->
            // The lock again, at the level nothing can slip past: "all channels",
            // favourites and recently-watched all come through here.
            val channels = rows
                .filterNot { settings.isLiveBlocked(it.categoryId, it.categoryName) }
                .map { it.toDomain() }
            if (sortable && sort == ChannelSort.NAME) {
                channels.sortedBy { it.name.lowercase() }
            } else {
                channels
            }
        }
    }

    /**
     * Every channel a group draws from, hidden ones included, in the user's order —
     * what the group's channel editor works on. The live list filters the hidden
     * ones out; here they stay visible so they can be switched back on.
     */
    fun observeGroupChannels(group: CategoryGroup): Flow<List<Channel>> {
        if (group.categoryIds.isEmpty()) return flowOf(emptyList<Channel>())
        return perSource { channelDao.observeByCategories(it, group.categoryIds) }
            .map { rows -> arrange(rows, group.channelOrder) }
    }

    /** Applies a saved order; anything not listed keeps provider order behind it. */
    private fun arrange(rows: List<ChannelRow>, order: List<String>): List<Channel> {
        val channels = rows.map { it.toDomain() }
        if (order.isEmpty()) return channels
        val rank = order.withIndex().associate { (index, id) -> id to index }
        // sortedBy is stable, so untouched channels keep provider order among themselves.
        return channels.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }

    fun search(query: String): Flow<List<Channel>> =
        perSource { channelDao.search(it, query) }.map { rows ->
            rows.filterNot { settings.isLiveBlocked(it.categoryId, it.categoryName) }
                .map { it.toDomain() }
        }

    suspend fun channelById(id: String): Channel? = channelDao.byId(id)?.toDomain()

    suspend fun knownEpgIds(sourceId: Long = activeId): Set<String> =
        channelDao.knownEpgIds(sourceId).toSet()

    /** @return the new favourite state. */
    suspend fun toggleFavorite(channelId: String): Boolean = database.withTransaction {
        if (favoriteDao.isFavorite(channelId)) {
            favoriteDao.remove(channelId)
            false
        } else {
            favoriteDao.add(FavoriteEntity(channelId, System.currentTimeMillis()))
            true
        }
    }

    /**
     * Playback URL for a programme that already aired. Null when the source is not
     * Xtream or the channel id carries no stream id — an M3U playlist has no archive.
     */
    suspend fun archiveUrl(channelId: String, startMs: Long, durationMinutes: Int): String? {
        // The channel's own access, which is not necessarily the one on screen.
        val sourceId = sourceIdOf(channelId) ?: return null
        val source = sourceDao.get(sourceId)?.toDomain() ?: return null
        if (source.type != SourceType.XTREAM) return null
        val streamId = channelId.substringAfterLast(':').toIntOrNull() ?: return null
        return XtreamClient(source.url, source.username, source.password)
            .timeshiftUrl(streamId, startMs, durationMinutes)
    }

    suspend fun markWatched(channelId: String) {
        // The setting first: the channel list reads it the moment Back leaves the
        // player, and a quick Back right after zapping must not beat the database.
        settings.lastChannelId = channelId
        recentDao.mark(RecentEntity(channelId, System.currentTimeMillis()))
    }

    // -------------------------------------------------------------------- sync

    /**
     * Downloads the catalogue and replaces the local copy.
     * @return how many channels the source now has.
     */
    suspend fun sync(
        source: PlaylistSource,
        onProgress: (step: String, fraction: Float?) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val (categories, channels) = when (source.type) {
            SourceType.XTREAM -> syncXtream(source, onProgress)
            SourceType.M3U -> syncM3u(source, onProgress)
        }

        if (channels.isEmpty()) {
            throw IllegalStateException(AppLocale.strings.errNoPlayableChannels)
        }

        onProgress(AppLocale.strings.syncSaving(channels.size), null)
        // One transaction, so the UI never observes a half-empty catalogue mid-sync.
        database.withTransaction {
            channelDao.deleteChannels(source.id)
            channelDao.deleteCategories(source.id)
            channelDao.insertCategories(categories)
            channels.chunked(500).forEach { channelDao.insertChannels(it) }
        }
        sourceDao.markSynced(System.currentTimeMillis(), source.id)
        channels.size
    }

    private suspend fun syncXtream(
        source: PlaylistSource,
        onProgress: (String, Float?) -> Unit
    ): Pair<List<CategoryEntity>, List<ChannelEntity>> {
        val client = XtreamClient(source.url, source.username, source.password)
        val sourceId = source.id

        onProgress(AppLocale.strings.syncSigningIn, null)
        val account = client.authenticate()
        if (!account.isActive) {
            throw IllegalStateException(AppLocale.strings.errAccountInactive(account.status))
        }

        onProgress(AppLocale.strings.syncCategories, null)
        val remoteCategories = client.liveCategories()
        val categoryNames = remoteCategories.associate { it.id to it.name }

        onProgress(AppLocale.strings.syncChannels, null)
        // One unfiltered call returns every live stream — far faster than walking
        // categories one by one, which some panels rate-limit.
        val streams = client.liveStreams()

        val extension = if (settings.preferHls) "m3u8" else "ts"
        val channels = streams.mapIndexed { index, stream ->
            val categoryName = categoryNames[stream.categoryId] ?: "Sonstige"
            ChannelEntity(
                id = "$sourceId:${stream.streamId}",
                sourceId = sourceId,
                name = stream.name,
                logoUrl = stream.icon,
                categoryId = categoryKey(sourceId, stream.categoryId),
                categoryName = categoryName,
                streamUrl = stream.directSource?.takeIf { it.startsWith("http", true) }
                    ?: client.liveStreamUrl(stream.streamId, extension),
                epgId = stream.epgChannelId,
                number = if (stream.number > 0) stream.number else index + 1,
                hasArchive = stream.hasArchive
            )
        }

        val usedCategoryIds = channels.mapTo(HashSet()) { it.categoryId }
        val categories = remoteCategories
            .filter { categoryKey(sourceId, it.id) in usedCategoryIds }
            .mapIndexed { index, category ->
                CategoryEntity(
                    id = categoryKey(sourceId, category.id),
                    sourceId = sourceId,
                    name = category.name,
                    sortOrder = index
                )
            }
            .toMutableList()

        // Streams can point at a category the panel did not list.
        usedCategoryIds
            .filter { id -> categories.none { it.id == id } }
            .forEachIndexed { index, id ->
                categories += CategoryEntity(id, sourceId, "Sonstige", categories.size + index)
            }

        return categories to channels
    }

    private fun syncM3u(
        source: PlaylistSource,
        onProgress: (String, Float?) -> Unit
    ): Pair<List<CategoryEntity>, List<ChannelEntity>> {
        val sourceId = source.id
        onProgress(AppLocale.strings.syncPlaylist, null)
        val playlist = Http.getStream(source.url) { stream -> M3uParser.parse(stream) }

        onProgress(AppLocale.strings.syncEntries(playlist.entries.size), null)
        val categoryOrder = LinkedHashMap<String, String>() // id -> name
        val channels = playlist.entries.map { entry ->
            val categoryId = categoryKey(sourceId, "g:" + shortHash(entry.group))
            if (!categoryOrder.containsKey(categoryId)) categoryOrder[categoryId] = entry.group
            ChannelEntity(
                id = "$sourceId:" + shortHash(entry.url),
                sourceId = sourceId,
                name = entry.name,
                logoUrl = entry.logo,
                categoryId = categoryId,
                categoryName = entry.group,
                streamUrl = entry.url,
                epgId = entry.tvgId ?: entry.tvgName,
                number = entry.number,
                hasArchive = false
            )
        }

        val categories = categoryOrder.entries.mapIndexed { index, (id, name) ->
            CategoryEntity(id = id, sourceId = sourceId, name = name, sortOrder = index)
        }
        return categories to channels
    }

    companion object {
        /** Channel ids carry their access in front: "3:1234" belongs to access 3. */
        fun sourceIdOf(channelId: String): Long? = channelId.substringBefore(':').toLongOrNull()

        /**
         * Category ids are the provider's own numbers, so two accesses both have a
         * "5". The first access keeps the bare ids it always had — groups, order
         * and locks saved before there were several accesses point at those —
         * every later one gets its own prefix.
         */
        fun categoryKey(sourceId: Long, providerId: String): String =
            if (sourceId == 1L) providerId else "s$sourceId:$providerId"

        /** Stable 12-char id so favourites survive a re-sync. */
        fun shortHash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            return digest.take(6).joinToString("") { "%02x".format(it) }
        }
    }
}

// ------------------------------------------------------------------ mapping

private fun SourceEntity.toDomain() = PlaylistSource(
    id = id,
    name = name,
    type = runCatching { SourceType.valueOf(type) }.getOrDefault(SourceType.M3U),
    url = url,
    username = username,
    password = password,
    epgUrl = epgUrl,
    lastSyncAt = lastSyncAt
)

private fun PlaylistSource.toEntity() = SourceEntity(
    id = id,
    name = name,
    type = type.name,
    url = url,
    username = username,
    password = password,
    epgUrl = epgUrl,
    lastSyncAt = lastSyncAt
)

private fun ChannelRow.toDomain() = Channel(
    id = id,
    name = name,
    logoUrl = logoUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    streamUrl = streamUrl,
    epgId = epgId,
    number = number,
    isFavorite = isFavorite,
    hasArchive = hasArchive
)
