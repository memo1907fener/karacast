package com.safir.iptv.data.repository

import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.data.remote.xtream.XtreamClient
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.Episode
import com.safir.iptv.domain.model.Movie
import com.safir.iptv.domain.model.MovieDetail
import com.safir.iptv.domain.model.Series
import com.safir.iptv.domain.model.SeriesDetail
import com.safir.iptv.domain.model.SourceType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The film and series catalogue.
 *
 * Unlike the live list this is *not* written into the database. A live playlist is
 * a few thousand rows that have to be searchable, sortable and grouped, and that
 * must be there the second the app opens; a film catalogue is tens of thousands of
 * rows that change every week and that nobody browses until they ask for them. So
 * it is fetched per category, on demand, and kept in memory for as long as the app
 * is running — the first open of a category costs a moment, every later one is
 * instant, and nothing has to be migrated when a provider reorganises its shelves.
 *
 * Only Xtream accounts have a catalogue at all. An M3U playlist is a flat list of
 * streams with no notion of a film, a season or a plot, so the screens say so
 * rather than inventing one.
 */
class VodRepository(
    private val playlists: PlaylistRepository,
    private val settings: SettingsStore
) {

    private val lock = Mutex()
    private var client: XtreamClient? = null
    private var clientKey: String? = null

    private val movieCategoryCache = mutableListOf<Category>()
    private val seriesCategoryCache = mutableListOf<Category>()
    private val moviesByCategory = mutableMapOf<String, List<Movie>>()
    private val seriesByCategory = mutableMapOf<String, List<Series>>()
    private val movieDetails = mutableMapOf<Int, MovieDetail>()
    private val seriesDetails = mutableMapOf<Int, SeriesDetail>()

    /**
     * The whole catalogue, fetched once the first time somebody searches.
     *
     * `player_api.php` has no search of its own, so the only honest way to answer
     * "where is that film" is to hold the list and filter it here. Asking for every
     * category one by one would be fifty requests; asking without a category is one,
     * and after it the search is instant for the rest of the evening.
     */
    private var allMovies: List<Movie>? = null
    private var allSeries: List<Series>? = null

    /** False for M3U sources and when nothing is set up at all. */
    suspend fun isSupported(): Boolean = playlists.currentSource()?.type == SourceType.XTREAM

    // ---------------------------------------------------------------- films

    suspend fun movieCategories(): List<Category> {
        if (movieCategoryCache.isNotEmpty()) return movieCategoryCache.toList()
        val fetched = requireClient().vodCategories()
            .filterNot { settings.isBlocked(it.id, it.name) }
            .map { Category(it.id, it.name) }
            .distinctBy { it.id }
        movieCategoryCache.clear()
        movieCategoryCache.addAll(fetched)
        return fetched
    }

    /*
     * Everything that leaves this class is deduplicated by id.
     *
     * Providers repeat titles — the same film filed under "Action" and under "Neu",
     * the same stream id listed twice in one category. A list drawn with ids as its
     * keys throws the moment two of them match, which on a television is not a
     * warning in a log but the app disappearing mid-scroll.
     */
    suspend fun movies(categoryId: String): List<Movie> {
        moviesByCategory[categoryId]?.let { return it }
        val api = requireClient()
        val fetched = api.vodStreams(categoryId).distinctBy { it.streamId }.map { vod ->
            Movie(
                id = vod.streamId,
                name = vod.name,
                posterUrl = vod.cover,
                categoryId = vod.categoryId,
                rating = vod.rating,
                streamUrl = api.vodUrl(vod.streamId, vod.extension)
            )
        }
        moviesByCategory[categoryId] = fetched
        return fetched
    }

    /**
     * Films whose title contains [query].
     *
     * The first call pays for the whole catalogue; every one after it is a filter
     * over memory. Locked categories never enter the list, so the child lock holds
     * here exactly as it does in the sidebar.
     */
    suspend fun searchMovies(query: String): List<Movie> {
        val needle = query.trim().lowercase()
        if (needle.length < 2) return emptyList()
        val all = allMovies ?: loadAllMovies().also { allMovies = it }
        return all.filter { it.name.lowercase().contains(needle) }.take(SEARCH_LIMIT)
    }

    suspend fun searchSeries(query: String): List<Series> {
        val needle = query.trim().lowercase()
        if (needle.length < 2) return emptyList()
        val all = allSeries ?: loadAllSeries().also { allSeries = it }
        return all.filter { it.name.lowercase().contains(needle) }.take(SEARCH_LIMIT)
    }

    private suspend fun loadAllMovies(): List<Movie> {
        val api = requireClient()
        val blocked = blockedMovieCategoryIds()
        return api.vodStreams(null)
            .filterNot { it.categoryId in blocked }
            .distinctBy { it.streamId }
            .map { vod ->
                Movie(
                    id = vod.streamId,
                    name = vod.name,
                    posterUrl = vod.cover,
                    categoryId = vod.categoryId,
                    rating = vod.rating,
                    streamUrl = api.vodUrl(vod.streamId, vod.extension)
                )
            }
    }

    private suspend fun loadAllSeries(): List<Series> {
        val blocked = blockedSeriesCategoryIds()
        return requireClient().series(null)
            .filterNot { it.categoryId in blocked }
            .distinctBy { it.seriesId }
            .map { it.toDomain() }
    }

    /** Category ids the lock takes out, resolved once against the provider's names. */
    private suspend fun blockedMovieCategoryIds(): Set<String> =
        runCatching {
            requireClient().vodCategories()
                .filter { settings.isBlocked(it.id, it.name) }
                .map { it.id }
                .toSet()
        }.getOrDefault(emptySet())

    private suspend fun blockedSeriesCategoryIds(): Set<String> =
        runCatching {
            requireClient().seriesCategories()
                .filter { settings.isBlocked(it.id, it.name) }
                .map { it.id }
                .toSet()
        }.getOrDefault(emptySet())

    /**
     * The plot, the cast, the running time. Asked for only when a film is actually
     * opened — a category of four hundred films would otherwise be four hundred
     * requests for text nobody reads.
     */
    suspend fun movieDetail(movieId: Int): MovieDetail {
        movieDetails[movieId]?.let { return it }
        val info = requireClient().vodInfo(movieId)
        val detail = MovieDetail(
            plot = info?.plot,
            cast = info?.cast,
            director = info?.director,
            genre = info?.genre,
            released = info?.releaseDate,
            durationSecs = info?.durationSecs,
            rating = info?.rating,
            posterUrl = info?.cover
        )
        movieDetails[movieId] = detail
        return detail
    }

    // --------------------------------------------------------------- series

    suspend fun seriesCategories(): List<Category> {
        if (seriesCategoryCache.isNotEmpty()) return seriesCategoryCache.toList()
        val fetched = requireClient().seriesCategories()
            .filterNot { settings.isBlocked(it.id, it.name) }
            .map { Category(it.id, it.name) }
            .distinctBy { it.id }
        seriesCategoryCache.clear()
        seriesCategoryCache.addAll(fetched)
        return fetched
    }

    suspend fun series(categoryId: String): List<Series> {
        seriesByCategory[categoryId]?.let { return it }
        val fetched = requireClient().series(categoryId)
            .distinctBy { it.seriesId }
            .map { it.toDomain() }
        seriesByCategory[categoryId] = fetched
        return fetched
    }

    suspend fun seriesDetail(seriesId: Int): SeriesDetail {
        seriesDetails[seriesId]?.let { return it }
        val api = requireClient()
        val (info, rawEpisodes) = api.seriesInfo(seriesId)
        val known = seriesByCategory.values.asSequence().flatten().firstOrNull { it.id == seriesId }
        val series = info?.toDomain()?.let { fetched ->
            // The listing usually carries a better cover and name than the detail
            // call, so what is already on screen wins where it has something.
            fetched.copy(
                name = known?.name?.takeIf { it.isNotBlank() } ?: fetched.name,
                posterUrl = known?.posterUrl ?: fetched.posterUrl,
                categoryId = known?.categoryId ?: fetched.categoryId
            )
        } ?: known ?: Series(seriesId, "", null, "")

        val episodes = rawEpisodes.map { episode ->
            Episode(
                id = episode.id,
                title = episode.title,
                season = episode.season,
                number = episode.number,
                plot = episode.plot,
                durationSecs = episode.durationSecs,
                imageUrl = episode.image ?: series.posterUrl,
                streamUrl = api.episodeUrl(episode.id, episode.extension)
            )
        }
        val detail = SeriesDetail(series, episodes)
        seriesDetails[seriesId] = detail
        return detail
    }

    // --------------------------------------------------------------- shared

    /** Called after a re-sync or a change of credentials: the shelves may have moved. */
    suspend fun clearCache() = lock.withLock {
        client = null
        clientKey = null
        movieCategoryCache.clear()
        seriesCategoryCache.clear()
        moviesByCategory.clear()
        seriesByCategory.clear()
        movieDetails.clear()
        seriesDetails.clear()
        allMovies = null
        allSeries = null
    }

    private companion object {
        /** More than this on a television is a wall of posters, not an answer. */
        const val SEARCH_LIMIT = 200
    }

    private suspend fun requireClient(): XtreamClient = lock.withLock {
        val source = playlists.currentSource()
            ?: throw IllegalStateException("No source configured")
        require(source.type == SourceType.XTREAM) { "Catalogue needs an Xtream account" }
        val key = "${source.url}|${source.username}"
        val existing = client
        if (existing != null && clientKey == key) return@withLock existing
        XtreamClient(source.url, source.username, source.password).also {
            client = it
            clientKey = key
        }
    }
}

private fun com.safir.iptv.data.remote.xtream.XtreamSeries.toDomain() = Series(
    id = seriesId,
    name = name,
    posterUrl = cover,
    categoryId = categoryId,
    plot = plot,
    genre = genre,
    rating = rating,
    released = releaseDate,
    cast = cast,
    director = director
)
