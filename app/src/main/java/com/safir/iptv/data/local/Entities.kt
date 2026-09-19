package com.safir.iptv.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: Long = 1L,
    val name: String,
    /** "XTREAM" or "M3U" — stored as a string so adding a type never needs a migration. */
    val type: String,
    val url: String,
    val username: String,
    val password: String,
    val epgUrl: String,
    val lastSyncAt: Long
)

@Entity(
    tableName = "categories",
    indices = [Index("sourceId")]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val sourceId: Long,
    val name: String,
    val sortOrder: Int
)

@Entity(
    tableName = "channels",
    indices = [
        Index("sourceId"),
        Index("categoryId"),
        Index("epgId"),
        Index("name")
    ]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val sourceId: Long,
    val name: String,
    val logoUrl: String?,
    val categoryId: String,
    val categoryName: String,
    val streamUrl: String,
    val epgId: String?,
    val number: Int,
    val hasArchive: Boolean
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val channelId: String,
    val addedAt: Long
)

@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey val channelId: String,
    val watchedAt: Long
)

@Entity(
    tableName = "programs",
    indices = [Index(value = ["epgId", "startMs"]), Index("endMs")]
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val epgId: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long
)

/** Channel joined with its favourite flag — what the lists actually render. */
data class ChannelRow(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val categoryId: String,
    val categoryName: String,
    val streamUrl: String,
    val epgId: String?,
    val number: Int,
    val hasArchive: Boolean,
    val isFavorite: Boolean
)

/** Category joined with how many channels it holds. */
data class CategoryRow(
    val id: String,
    val name: String,
    val channelCount: Int
)
