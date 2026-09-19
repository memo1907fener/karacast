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

    fun observeSource(): Flow<PlaylistSource?> =
        sourceDao.observe(SOURCE_ID).map { it?.toDomain() }

    suspend fun currentSource(): PlaylistSource? = sourceDao.get(SOURCE_ID)?.toDomain()

    suspend fun saveSource(source: PlaylistSource) {
        sourceDao.upsert(source.toEntity())
    }

    suspend fun clearEverything() {
        channelDao.deleteChannels(SOURCE_ID)
        channelDao.deleteCategories(SOURCE_ID)
        recentDao.clear()
        sourceDao.clear()
    }

    suspend fun channelCount(): Int = channelDao.count(SOURCE_ID)

    // ---------------------------------------------------------------- observing

    /**
     * Provider categories in the user's arrangement, with their own groups in front.
     * Categories the user never sorted keep the provider's order behind the sorted ones.
     */
    fun observeCategories(): Flow<List<Category>> = combine(
        channelDao.observeCategories(SOURCE_ID),
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
        channelDao.observeCategories(SOURCE_ID).map { rows ->
            rows.map { Category(it.id, it.name, it.channelCount) }
        }

    fun observeChannels(categoryId: String): Flow<List<Channel>> {
        // Re-reads the group on every edit, so membership, hidden channels and the
        // user's order all reach the list that is currently on screen.
        if (categoryId.startsWith(GROUP_PREFIX)) {
            return settings.groups.flatMapLatest { groups ->
                val group = groups.firstOrNull { GROUP_PREFIX + it.id == categoryId }
                if (group == null || group.categoryIds.isEmpty()) {
                    flowOf(emptyList<Channel>())
                } else {
                    channelDao.observeByCategories(SOURCE_ID, group.categoryIds).map { rows ->
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
            FAVORITES_CATEGORY_ID -> channelDao.observeFavorites(SOURCE_ID)
            RECENT_CATEGORY_ID -> channelDao.observeRecent(SOURCE_ID)
            ALL_CATEGORY_ID -> channelDao.observeAll(SOURCE_ID)
            else -> channelDao.observeByCategory(SOURCE_ID, categoryId)
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
        return channelDao.observeByCategories(SOURCE_ID, group.categoryIds)
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
        channelDao.search(SOURCE_ID, query).map { rows ->
            rows.filterNot { settings.isLiveBlocked(it.categoryId, it.categoryName) }
                .map { it.toDomain() }
        }

    suspend fun channelById(id: String): Channel? = channelDao.byId(id)?.toDomain()

    suspend fun knownEpgIds(): Set<String> = channelDao.knownEpgIds(SOURCE_ID).toSet()

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
        val source = currentSource() ?: return null
        if (source.type != SourceType.XTREAM) return null
        val streamId = channelId.substringAfterLast(':').toIntOrNull() ?: return null
        return XtreamClient(source.url, source.username, source.password)
            .timeshiftUrl(streamId, startMs, durationMinutes)
    }

    suspend fun markWatched(channelId: String) {
        recentDao.mark(RecentEntity(channelId, System.currentTimeMillis()))
        settings.lastChannelId = channelId
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
            channelDao.deleteChannels(SOURCE_ID)
            channelDao.deleteCategories(SOURCE_ID)
            channelDao.insertCategories(categories)
            channels.chunked(500).forEach { channelDao.insertChannels(it) }
        }
        sourceDao.markSynced(System.currentTimeMillis(), SOURCE_ID)
        channels.size
    }

    private suspend fun syncXtream(
        source: PlaylistSource,
        onProgress: (String, Float?) -> Unit
    ): Pair<List<CategoryEntity>, List<ChannelEntity>> {
        val client = XtreamClient(source.url, source.username, source.password)

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
                id = "$SOURCE_ID:${stream.streamId}",
                sourceId = SOURCE_ID,
                name = stream.name,
                logoUrl = stream.icon,
                categoryId = stream.categoryId,
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
            .filter { it.id in usedCategoryIds }
            .mapIndexed { index, category ->
                CategoryEntity(
                    id = category.id,
                    sourceId = SOURCE_ID,
                    name = category.name,
                    sortOrder = index
                )
            }
            .toMutableList()

        // Streams can point at a category the panel did not list.
        usedCategoryIds
            .filter { id -> categories.none { it.id == id } }
            .forEachIndexed { index, id ->
                categories += CategoryEntity(id, SOURCE_ID, "Sonstige", categories.size + index)
            }

        return categories to channels
    }

    private fun syncM3u(
        source: PlaylistSource,
        onProgress: (String, Float?) -> Unit
    ): Pair<List<CategoryEntity>, List<ChannelEntity>> {
        onProgress(AppLocale.strings.syncPlaylist, null)
        val playlist = Http.getStream(source.url) { stream -> M3uParser.parse(stream) }

        onProgress(AppLocale.strings.syncEntries(playlist.entries.size), null)
        val categoryOrder = LinkedHashMap<String, String>() // id -> name
        val channels = playlist.entries.map { entry ->
            val categoryId = "g:" + shortHash(entry.group)
            if (!categoryOrder.containsKey(categoryId)) categoryOrder[categoryId] = entry.group
            ChannelEntity(
                id = "$SOURCE_ID:" + shortHash(entry.url),
                sourceId = SOURCE_ID,
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
            CategoryEntity(id = id, sourceId = SOURCE_ID, name = name, sortOrder = index)
        }
        return categories to channels
    }

    companion object {
        const val SOURCE_ID = 1L

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
