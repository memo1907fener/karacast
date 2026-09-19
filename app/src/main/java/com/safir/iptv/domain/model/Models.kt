package com.safir.iptv.domain.model

import kotlinx.serialization.Serializable

/** How a playlist was configured by the user. */
enum class SourceType { XTREAM, M3U }

/**
 * A configured IPTV source. Exactly one is active at a time in this version;
 * the schema already carries an id so multi-profile support is a small step.
 */
data class PlaylistSource(
    val id: Long = 1L,
    val name: String,
    val type: SourceType,
    /** Xtream: "http://host:port" (no trailing slash). M3U: the playlist URL. */
    val url: String,
    val username: String = "",
    val password: String = "",
    /** Optional XMLTV EPG URL. Xtream sources fall back to the built-in xmltv.php. */
    val epgUrl: String = "",
    val lastSyncAt: Long = 0L
)

data class Category(
    val id: String,
    val name: String,
    val channelCount: Int = 0
)

data class Channel(
    /** Stable across syncs: "<sourceId>:<streamId or url hash>". */
    val id: String,
    val name: String,
    val logoUrl: String?,
    val categoryId: String,
    val categoryName: String,
    val streamUrl: String,
    /** tvg-id / epg_channel_id used to join against the EPG. */
    val epgId: String?,
    val number: Int,
    val isFavorite: Boolean = false,
    /** Xtream tv_archive flag — catch-up availability. Not used by the player yet. */
    val hasArchive: Boolean = false
)

data class Program(
    val channelEpgId: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    fun progressAt(nowMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return ((nowMs - startMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun isLiveAt(nowMs: Long): Boolean = nowMs in startMs until endMs
}

/** Now / next pair for a single channel, used on list rows and the player overlay. */
data class NowNext(
    val now: Program? = null,
    val next: Program? = null
)

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val step: String, val progress: Float? = null) : SyncState
    data class Done(val channels: Int, val programs: Int) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * A user-defined bundle of provider categories, shown in the sidebar as one entry.
 * Lives in preferences rather than the database: it must survive a playlist re-sync,
 * which wipes and rewrites every category row.
 */
@Serializable
data class CategoryGroup(
    val id: String,
    val name: String,
    val categoryIds: List<String> = emptyList(),
    /**
     * Channels the user took out of this group. Hiding rather than deleting, so a
     * mistake costs one click to undo and a playlist re-sync cannot resurrect the
     * channel behind the user's back.
     */
    val hiddenChannelIds: List<String> = emptyList(),
    /**
     * The user's own channel order inside this group. Written out in full whenever
     * something moves, so untouched channels keep their place instead of drifting
     * to the end. Ids that are not listed follow behind in provider order.
     */
    val channelOrder: List<String> = emptyList()
)

const val GROUP_PREFIX = "grp:"

const val FAVORITES_CATEGORY_ID = "__favorites__"
const val RECENT_CATEGORY_ID = "__recent__"
const val ALL_CATEGORY_ID = "__all__"

/** How a channel list is ordered. A user's own group always keeps its own order. */
enum class ChannelSort { NUMBER, NAME }

/** How the picture is fitted into the screen during playback. */
enum class AspectMode { FIT, FILL, ZOOM }

// ------------------------------------------------------- films and series

/**
 * One film. [streamUrl] is built once when the catalogue is read, so everything
 * downstream — the player included — deals in the same shape it already knows.
 */
data class Movie(
    val id: Int,
    val name: String,
    val posterUrl: String?,
    val categoryId: String,
    val rating: String?,
    val streamUrl: String
) {
    /** Where the viewer's position is filed. Stable across catalogue refreshes. */
    val resumeKey: String get() = "movie:$id"
}

/** What the detail panel adds once the film has been asked about individually. */
data class MovieDetail(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val released: String? = null,
    val durationSecs: Int? = null,
    val rating: String? = null,
    val posterUrl: String? = null
)

data class Series(
    val id: Int,
    val name: String,
    val posterUrl: String?,
    val categoryId: String,
    val plot: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val released: String? = null,
    val cast: String? = null,
    val director: String? = null
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val plot: String?,
    val durationSecs: Int?,
    val imageUrl: String?,
    val streamUrl: String
) {
    val resumeKey: String get() = "episode:$id"

    /** "3. Der Fall" — falls back to the number alone when the panel sends no title. */
    fun label(episodeWord: (Int) -> String): String =
        if (title.isBlank()) episodeWord(number) else "$number. $title"
}

/** A series with everything the episode screen needs, in one object. */
data class SeriesDetail(
    val series: Series,
    val episodes: List<Episode>
) {
    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()

    fun episodesOf(season: Int): List<Episode> = episodes.filter { it.season == season }
}

/**
 * A film or an episode somebody stopped in the middle of.
 *
 * Everything the "keep watching" row needs travels with it — title, cover, the
 * stream, where the viewer was — because the catalogue itself is fetched fresh
 * from the provider and a half-watched film must still be findable when its
 * category has not been opened yet, or when the provider has since moved it.
 */
@Serializable
data class ContinueItem(
    /** "movie:123" / "episode:456" — the same key the position is filed under. */
    val key: String,
    val isEpisode: Boolean,
    /** The film's title, or the series' name. */
    val title: String,
    /** "S2 · E3 · Der Fall", or empty for a film. */
    val subtitle: String,
    val posterUrl: String? = null,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** Lets the row lead back into the series rather than only into the episode. */
    val seriesId: Int = 0
) {
    /** 0f..1f — what the little bar under the cover shows. */
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * A film or a series somebody marked to come back to.
 *
 * Carries its own cover and title for the same reason [ContinueItem] does: the
 * catalogue is fetched one category at a time, so the favourites row has to be
 * drawable before any of it has been loaded.
 */
@Serializable
data class FavoriteItem(
    /** "movie:123" / "series:45". */
    val key: String,
    val isSeries: Boolean,
    val title: String,
    val posterUrl: String? = null,
    /** Films play straight from here; a series opens its episode list instead. */
    val streamUrl: String = "",
    val seriesId: Int = 0,
    val addedAt: Long = 0L
)

/**
 * The language a viewer wants to hear and to read, when a stream offers a choice.
 *
 * The names are written in each language itself, so the list reads the same
 * whichever language the app is set to — somebody looking for Turkish audio looks
 * for "Türkçe", not for whatever their interface happens to call Turkish.
 */
enum class TrackLanguage(val tag: String?, val label: String) {
    /** Audio: whatever the stream leads with. Subtitles: none. */
    AUTO(null, ""),
    DE("de", "Deutsch"),
    TR("tr", "Türkçe"),
    EN("en", "English"),
    AR("ar", "العربية"),
    FR("fr", "Français"),
    ES("es", "Español"),
    IT("it", "Italiano"),
    RU("ru", "Русский");

    companion object {
        fun fromTag(tag: String?): TrackLanguage =
            entries.firstOrNull { it.tag != null && it.tag == tag } ?: AUTO
    }
}

/**
 * One version of the child lock's settings.
 *
 * Carried as a value rather than a bare counter so that a list rebuilding because
 * of it can tell whether the lock is on at all without reading preferences again.
 */
data class ParentalRevision(val enabled: Boolean, val at: Int)

/** A newer build announced by the update endpoint. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val notes: String = ""
)
