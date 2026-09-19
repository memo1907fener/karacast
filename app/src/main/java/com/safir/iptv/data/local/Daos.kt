package com.safir.iptv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

private const val CHANNEL_COLUMNS =
    "c.id AS id, c.name AS name, c.logoUrl AS logoUrl, c.categoryId AS categoryId, " +
        "c.categoryName AS categoryName, c.streamUrl AS streamUrl, c.epgId AS epgId, " +
        "c.number AS number, c.hasArchive AS hasArchive"

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun get(id: Long): SourceEntity?

    @Query("SELECT * FROM sources WHERE id = :id")
    fun observe(id: Long): Flow<SourceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: SourceEntity)

    @Query("UPDATE sources SET lastSyncAt = :ts WHERE id = :id")
    suspend fun markSynced(ts: Long, id: Long)

    @Query("DELETE FROM sources")
    suspend fun clear()
}

@Dao
interface ChannelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(items: List<ChannelEntity>)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteCategories(sourceId: Long)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteChannels(sourceId: Long)

    @Query(
        """
        SELECT cat.id AS id, cat.name AS name, COUNT(ch.id) AS channelCount
        FROM categories cat
        LEFT JOIN channels ch ON ch.categoryId = cat.id AND ch.sourceId = cat.sourceId
        WHERE cat.sourceId = :sourceId
        GROUP BY cat.id, cat.name, cat.sortOrder
        HAVING COUNT(ch.id) > 0
        ORDER BY cat.sortOrder ASC
        """
    )
    fun observeCategories(sourceId: Long): Flow<List<CategoryRow>>

    @Query(
        """
        SELECT $CHANNEL_COLUMNS, (f.channelId IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.channelId = c.id
        WHERE c.sourceId = :sourceId AND c.categoryId = :categoryId
        ORDER BY c.number ASC, c.name ASC
        """
    )
    fun observeByCategory(sourceId: Long, categoryId: String): Flow<List<ChannelRow>>

    @Query(
        """
        SELECT $CHANNEL_COLUMNS, (f.channelId IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.channelId = c.id
        WHERE c.sourceId = :sourceId
        ORDER BY c.number ASC, c.name ASC
        """
    )
    fun observeAll(sourceId: Long): Flow<List<ChannelRow>>

    /** Backs a user-defined group: several provider categories shown as one list. */
    @Query(
        """
        SELECT $CHANNEL_COLUMNS, (f.channelId IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.channelId = c.id
        WHERE c.sourceId = :sourceId AND c.categoryId IN (:categoryIds)
        ORDER BY c.number ASC, c.name ASC
        """
    )
    fun observeByCategories(sourceId: Long, categoryIds: List<String>): Flow<List<ChannelRow>>

    @Query(
        """
        SELECT $CHANNEL_COLUMNS, 1 AS isFavorite
        FROM channels c
        INNER JOIN favorites f ON f.channelId = c.id
        WHERE c.sourceId = :sourceId
        ORDER BY f.addedAt DESC
        """
    )
    fun observeFavorites(sourceId: Long): Flow<List<ChannelRow>>

    @Query(
        """
        SELECT $CHANNEL_COLUMNS, (f.channelId IS NOT NULL) AS isFavorite
        FROM channels c
        INNER JOIN recents r ON r.channelId = c.id
        LEFT JOIN favorites f ON f.channelId = c.id
        WHERE c.sourceId = :sourceId
        ORDER BY r.watchedAt DESC
        LIMIT 50
        """
    )
    fun observeRecent(sourceId: Long): Flow<List<ChannelRow>>

    @Query(
        """
        SELECT $CHANNEL_COLUMNS, (f.channelId IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.channelId = c.id
        WHERE c.sourceId = :sourceId AND c.name LIKE '%' || :query || '%'
        ORDER BY c.number ASC, c.name ASC
        LIMIT 300
        """
    )
    fun search(sourceId: Long, query: String): Flow<List<ChannelRow>>

    @Query(
        """
        SELECT $CHANNEL_COLUMNS, (f.channelId IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.channelId = c.id
        WHERE c.id = :channelId
        """
    )
    suspend fun byId(channelId: String): ChannelRow?

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun count(sourceId: Long): Int

    @Query("SELECT DISTINCT epgId FROM channels WHERE sourceId = :sourceId AND epgId IS NOT NULL AND epgId != ''")
    suspend fun knownEpgIds(sourceId: Long): List<String>
}

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE channelId = :channelId")
    suspend fun remove(channelId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE channelId = :channelId)")
    suspend fun isFavorite(channelId: String): Boolean
}

@Dao
interface RecentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mark(item: RecentEntity)

    @Query("DELETE FROM recents")
    suspend fun clear()
}

@Dao
interface ProgramDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProgramEntity>)

    @Query("DELETE FROM programs")
    suspend fun clear()

    @Query("DELETE FROM programs WHERE endMs < :beforeMs")
    suspend fun deleteEndedBefore(beforeMs: Long)

    @Query("SELECT COUNT(*) FROM programs")
    suspend fun count(): Int

    /** Programme overlapping a time window for a batch of channels — used by the guide. */
    @Query(
        """
        SELECT * FROM programs
        WHERE epgId IN (:epgIds) AND endMs > :fromMs AND startMs < :toMs
        ORDER BY epgId ASC, startMs ASC
        """
    )
    suspend fun inWindow(epgIds: List<String>, fromMs: Long, toMs: Long): List<ProgramEntity>

    @Query(
        """
        SELECT * FROM programs
        WHERE epgId = :epgId AND endMs > :fromMs
        ORDER BY startMs ASC
        LIMIT :limit
        """
    )
    fun observeUpcoming(epgId: String, fromMs: Long, limit: Int): Flow<List<ProgramEntity>>

    @Query(
        """
        SELECT * FROM programs
        WHERE epgId = :epgId AND endMs > :fromMs
        ORDER BY startMs ASC
        LIMIT :limit
        """
    )
    suspend fun upcoming(epgId: String, fromMs: Long, limit: Int): List<ProgramEntity>
}
