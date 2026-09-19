package com.safir.iptv.data.remote.xtream

import com.safir.iptv.data.remote.Http
import com.safir.iptv.util.LenientJson
import com.safir.iptv.util.asArrayOrEmpty
import com.safir.iptv.util.flag
import com.safir.iptv.util.int
import com.safir.iptv.util.long
import com.safir.iptv.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.URLEncoder

data class XtreamAccount(
    val username: String,
    val status: String,
    val isActive: Boolean,
    val expiresAtMs: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?
)

data class XtreamCategory(
    val id: String,
    val name: String
)

data class XtreamLiveStream(
    val streamId: Int,
    val name: String,
    val icon: String?,
    val epgChannelId: String?,
    val categoryId: String,
    val number: Int,
    val hasArchive: Boolean,
    /** Some panels hand back a ready-made URL; when present it wins. */
    val directSource: String?
)

data class XtreamEpgEntry(
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long
)

/** One film in a catalogue listing. The plot and the cast need a second call. */
data class XtreamVod(
    val streamId: Int,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val rating: String?,
    val addedMs: Long?,
    /** mp4, mkv, avi … Panels serve a film only under its own extension. */
    val extension: String?
)

/** What `get_vod_info` adds on top of the listing. */
data class XtreamVodInfo(
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val durationSecs: Int?,
    val rating: String?,
    val cover: String?
)

data class XtreamSeries(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val plot: String?,
    val genre: String?,
    val rating: String?,
    val releaseDate: String?,
    val cast: String?,
    val director: String?
)

data class XtreamEpisode(
    /** The panel's episode id — what the stream URL is built from. */
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val extension: String?,
    val plot: String?,
    val durationSecs: Int?,
    val image: String?
)

/**
 * Talks to the classic `player_api.php` interface used by Xtream Codes and its
 * many forks (XUI.one, and friends).
 */
class XtreamClient(
    rawBaseUrl: String,
    private val username: String,
    private val password: String
) {

    /** Normalised to "scheme://host[:port]" with no trailing slash and no path. */
    val baseUrl: String = normaliseBaseUrl(rawBaseUrl)

    private fun api(action: String?, extra: Map<String, String> = emptyMap()): String {
        val params = buildString {
            append("username=").append(enc(username))
            append("&password=").append(enc(password))
            if (action != null) append("&action=").append(enc(action))
            extra.forEach { (k, v) -> append('&').append(k).append('=').append(enc(v)) }
        }
        return "$baseUrl/player_api.php?$params"
    }

    suspend fun authenticate(): XtreamAccount = withContext(Dispatchers.IO) {
        val root = LenientJson.parseToJsonElement(Http.getString(api(null))) as? JsonObject
            ?: throw IOException("Unexpected answer from the server")
        val info = root["user_info"] as? JsonObject
            ?: throw IOException("Server did not return account info — check the URL")

        val auth = info.int("auth") ?: 0
        val status = info.str("status") ?: "Unknown"
        if (auth != 1) throw IOException("Login rejected (username or password wrong)")

        XtreamAccount(
            username = info.str("username") ?: username,
            status = status,
            isActive = status.equals("Active", ignoreCase = true),
            expiresAtMs = info.long("exp_date")?.let { it * 1000L },
            maxConnections = info.int("max_connections"),
            activeConnections = info.int("active_cons")
        )
    }

    suspend fun liveCategories(): List<XtreamCategory> = withContext(Dispatchers.IO) {
        val array = LenientJson
            .parseToJsonElement(Http.getString(api("get_live_categories")))
            .asArrayOrEmpty("categories")
        array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.str("category_id") ?: return@mapNotNull null
            XtreamCategory(id = id, name = obj.str("category_name") ?: "Kategorie $id")
        }
    }

    suspend fun liveStreams(categoryId: String? = null): List<XtreamLiveStream> =
        withContext(Dispatchers.IO) {
            val extra = categoryId?.let { mapOf("category_id" to it) } ?: emptyMap()
            val array = LenientJson
                .parseToJsonElement(Http.getString(api("get_live_streams", extra)))
                .asArrayOrEmpty("streams", "live")
            array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val streamId = obj.int("stream_id") ?: return@mapNotNull null
                XtreamLiveStream(
                    streamId = streamId,
                    name = (obj.str("name") ?: "Kanal $streamId").trim(),
                    icon = obj.str("stream_icon"),
                    epgChannelId = obj.str("epg_channel_id"),
                    categoryId = obj.str("category_id") ?: "0",
                    number = obj.int("num") ?: 0,
                    hasArchive = obj.flag("tv_archive"),
                    directSource = obj.str("direct_source")
                )
            }
        }

    /** Short EPG for one channel; titles and descriptions come base64-encoded. */
    suspend fun shortEpg(streamId: Int, limit: Int = 8): List<XtreamEpgEntry> =
        withContext(Dispatchers.IO) {
            val url = api(
                "get_short_epg",
                mapOf("stream_id" to streamId.toString(), "limit" to limit.toString())
            )
            val array = LenientJson.parseToJsonElement(Http.getString(url))
                .asArrayOrEmpty("epg_listings")
            array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val start = obj.long("start_timestamp")?.times(1000L) ?: return@mapNotNull null
                val end = obj.long("stop_timestamp")?.times(1000L) ?: return@mapNotNull null
                XtreamEpgEntry(
                    title = decodeMaybeBase64(obj.str("title").orEmpty()),
                    description = decodeMaybeBase64(obj.str("description").orEmpty()),
                    startMs = start,
                    endMs = end
                )
            }
        }

    // --------------------------------------------------------------------- films

    suspend fun vodCategories(): List<XtreamCategory> = categories("get_vod_categories")

    /** @param categoryId null asks for the whole catalogue at once — what search needs. */
    suspend fun vodStreams(categoryId: String?): List<XtreamVod> = withContext(Dispatchers.IO) {
        val extra = categoryId?.let { mapOf("category_id" to it) } ?: emptyMap()
        val array = LenientJson
            .parseToJsonElement(Http.getString(api("get_vod_streams", extra)))
            .asArrayOrEmpty("streams", "movies", "vod")
        array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.int("stream_id") ?: return@mapNotNull null
            XtreamVod(
                streamId = id,
                name = (obj.str("name", "title") ?: "#$id").trim(),
                cover = obj.str("stream_icon", "cover", "movie_image"),
                categoryId = obj.str("category_id") ?: categoryId.orEmpty(),
                rating = obj.str("rating"),
                addedMs = obj.long("added")?.times(1000L),
                extension = obj.str("container_extension")
            )
        }
    }

    suspend fun vodInfo(vodId: Int): XtreamVodInfo? = withContext(Dispatchers.IO) {
        val root = LenientJson
            .parseToJsonElement(
                Http.getString(api("get_vod_info", mapOf("vod_id" to vodId.toString())))
            ) as? JsonObject ?: return@withContext null
        val info = root["info"] as? JsonObject ?: return@withContext null
        XtreamVodInfo(
            plot = info.str("plot", "description"),
            cast = info.str("cast", "actors"),
            director = info.str("director"),
            genre = info.str("genre"),
            releaseDate = info.str("releasedate", "releaseDate", "release_date"),
            durationSecs = info.int("duration_secs", "episode_run_time"),
            rating = info.str("rating"),
            cover = info.str("movie_image", "cover_big")
        )
    }

    /** mp4 is what a panel serves when it will not say; guessing anything else fails. */
    fun vodUrl(streamId: Int, extension: String?): String =
        "$baseUrl/movie/${enc(username)}/${enc(password)}/$streamId." +
            (extension?.takeIf { it.isNotBlank() } ?: "mp4")

    // -------------------------------------------------------------------- series

    suspend fun seriesCategories(): List<XtreamCategory> = categories("get_series_categories")

    suspend fun series(categoryId: String?): List<XtreamSeries> = withContext(Dispatchers.IO) {
        val extra = categoryId?.let { mapOf("category_id" to it) } ?: emptyMap()
        val array = LenientJson
            .parseToJsonElement(Http.getString(api("get_series", extra)))
            .asArrayOrEmpty("series")
        array.mapNotNull { element -> element.toSeries(categoryId.orEmpty()) }
    }

    /**
     * Seasons and episodes for one series. The episodes come back as an object keyed
     * by season number rather than as a list, which no typed serializer survives —
     * hence walking the tree by hand.
     */
    suspend fun seriesInfo(seriesId: Int): Pair<XtreamSeries?, List<XtreamEpisode>> =
        withContext(Dispatchers.IO) {
            val root = LenientJson
                .parseToJsonElement(
                    Http.getString(api("get_series_info", mapOf("series_id" to seriesId.toString())))
                ) as? JsonObject ?: return@withContext null to emptyList()

            val info = (root["info"] as? JsonObject)?.let { obj ->
                XtreamSeries(
                    seriesId = seriesId,
                    name = obj.str("name", "title").orEmpty(),
                    cover = obj.str("cover", "cover_big"),
                    categoryId = obj.str("category_id").orEmpty(),
                    plot = obj.str("plot", "description"),
                    genre = obj.str("genre"),
                    rating = obj.str("rating"),
                    releaseDate = obj.str("releaseDate", "releasedate", "release_date"),
                    cast = obj.str("cast", "actors"),
                    director = obj.str("director")
                )
            }

            val episodes = buildList {
                val bySeason = root["episodes"] as? JsonObject ?: return@buildList
                bySeason.forEach { (seasonKey, value) ->
                    val fallbackSeason = seasonKey.toIntOrNull() ?: 1
                    value.asArrayOrEmpty().forEach { element ->
                        val obj = element as? JsonObject ?: return@forEach
                        val id = obj.str("id") ?: return@forEach
                        val number = obj.int("episode_num") ?: 0
                        val detail = obj["info"] as? JsonObject
                        add(
                            XtreamEpisode(
                                id = id,
                                title = (obj.str("title") ?: "").trim(),
                                season = obj.int("season") ?: fallbackSeason,
                                number = number,
                                extension = obj.str("container_extension"),
                                plot = detail?.str("plot", "description"),
                                durationSecs = detail?.int("duration_secs"),
                                image = detail?.str("movie_image", "cover_big")
                            )
                        )
                    }
                }
            }.sortedWith(compareBy({ it.season }, { it.number }))

            info to episodes
        }

    fun episodeUrl(episodeId: String, extension: String?): String =
        "$baseUrl/series/${enc(username)}/${enc(password)}/$episodeId." +
            (extension?.takeIf { it.isNotBlank() } ?: "mp4")

    // ------------------------------------------------------------------- shared

    private suspend fun categories(action: String): List<XtreamCategory> =
        withContext(Dispatchers.IO) {
            val array = LenientJson
                .parseToJsonElement(Http.getString(api(action)))
                .asArrayOrEmpty("categories")
            array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj.str("category_id") ?: return@mapNotNull null
                XtreamCategory(id = id, name = obj.str("category_name") ?: id)
            }
        }

    private fun kotlinx.serialization.json.JsonElement.toSeries(
        fallbackCategory: String
    ): XtreamSeries? {
        val obj = this as? JsonObject ?: return null
        val id = obj.int("series_id") ?: return null
        return XtreamSeries(
            seriesId = id,
            name = (obj.str("name", "title") ?: "#$id").trim(),
            cover = obj.str("cover", "cover_big", "stream_icon"),
            categoryId = obj.str("category_id") ?: fallbackCategory,
            plot = obj.str("plot", "description"),
            genre = obj.str("genre"),
            rating = obj.str("rating"),
            releaseDate = obj.str("releaseDate", "releasedate", "release_date"),
            cast = obj.str("cast", "actors"),
            director = obj.str("director")
        )
    }

    /** The panel's own XMLTV endpoint — a full guide for every channel at once. */
    fun xmltvUrl(): String = "$baseUrl/xmltv.php?username=${enc(username)}&password=${enc(password)}"

    /**
     * `.ts` is the raw MPEG-TS output every panel supports and it starts fastest;
     * `.m3u8` exists on most panels too and is the better fallback when TS stalls.
     */
    fun liveStreamUrl(streamId: Int, extension: String = "ts"): String =
        "$baseUrl/live/${enc(username)}/${enc(password)}/$streamId.$extension"

    /**
     * Catch-up (timeshift). XUI-style panels serve past programmes from
     * `/timeshift/<user>/<pass>/<minutes>/<yyyy-MM-dd:HH-mm>/<streamId>.ts`.
     * The timestamp is read in the panel's own timezone, which for a provider a
     * customer actually watches is in practice the device's — so local time it is.
     */
    fun timeshiftUrl(streamId: Int, startMs: Long, durationMinutes: Int): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
            .format(java.util.Date(startMs))
        val minutes = durationMinutes.coerceIn(1, 600)
        return "$baseUrl/timeshift/${enc(username)}/${enc(password)}/$minutes/$stamp/$streamId.ts"
    }

    companion object {

        fun normaliseBaseUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) return url
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                url = "http://$url"
            }
            url = url.trimEnd('/')
            // Users regularly paste the full player_api / get.php link.
            listOf("/player_api.php", "/panel_api.php", "/get.php", "/xmltv.php").forEach { suffix ->
                val idx = url.indexOf(suffix, ignoreCase = true)
                if (idx >= 0) url = url.substring(0, idx)
            }
            val queryIdx = url.indexOf('?')
            if (queryIdx >= 0) url = url.substring(0, queryIdx)
            return url.trimEnd('/')
        }

        /** Xtream base64-encodes EPG text, but not always. Decode only when it round-trips. */
        fun decodeMaybeBase64(value: String): String {
            if (value.isBlank()) return ""
            return try {
                val decoded = android.util.Base64.decode(value, android.util.Base64.DEFAULT)
                val text = String(decoded, Charsets.UTF_8)
                if (text.isNotBlank() && text.none { it.code in 0..8 || it.code in 14..31 }) {
                    text
                } else {
                    value
                }
            } catch (_: IllegalArgumentException) {
                value
            }
        }

        internal fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
