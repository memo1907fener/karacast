package com.safir.iptv.ui.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.data.repository.VodRepository
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.domain.model.Episode
import com.safir.iptv.domain.model.FavoriteItem
import com.safir.iptv.domain.model.Movie
import com.safir.iptv.domain.model.MovieDetail
import com.safir.iptv.domain.model.Series
import com.safir.iptv.domain.model.SeriesDetail
import com.safir.iptv.ui.login.friendlyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Two shelves the app puts in front of the provider's own categories: the search
 * and whatever the viewer has marked to come back to. They are ids rather than a
 * separate pane so the sidebar stays one list and the remote keeps walking down
 * it the way it always has.
 */
const val SEARCH_CATEGORY_ID = "__vod_search__"
const val VOD_FAVORITES_CATEGORY_ID = "__vod_favorites__"

/** How long a letter is allowed to be the last one before the search goes out. */
private const val TYPING_PAUSE_MS = 350L

/**
 * Films: categories on the left, posters on the right, one film opened at a time.
 *
 * A category's contents are fetched the first time it is focused and then kept, so
 * walking back and forth across the sidebar costs one request per category and
 * nothing afterwards.
 */
data class MoviesUiState(
    val supported: Boolean = true,
    val loadingCategories: Boolean = true,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val loadingItems: Boolean = false,
    val movies: List<Movie> = emptyList(),
    val error: String? = null,
    /** The film whose detail sheet is open, if any. */
    val opened: Movie? = null,
    val openedDetail: MovieDetail? = null,
    /** Where the viewer stopped last time, in milliseconds. 0 = never started. */
    val openedResumeMs: Long = 0L,
    /** Whether the open film is on the favourites shelf, for the button's label. */
    val openedIsFavorite: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Movie> = emptyList()
)

class MoviesViewModel(
    private val catalog: VodRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(MoviesUiState())
    val state: StateFlow<MoviesUiState> = _state.asStateFlow()

    /** Half-watched films, for the row above the grid. Episodes have their own. */
    val continueWatching: StateFlow<List<ContinueItem>> = settings.continueWatching
        .map { list -> list.filterNot { it.isEpisode } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The favourites shelf, as films.
     *
     * A favourite carries its own title, cover and stream, so it can be drawn and
     * played without the category it came from ever having been opened — which is
     * the whole point of marking it in the first place.
     */
    val favorites: StateFlow<List<Movie>> = settings.vodFavorites
        .map { list -> list.filterNot { it.isSeries }.map { it.asMovie() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearContinueWatching() = settings.clearContinueWatching(episodes = false)

    private var itemsJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            if (!catalog.isSupported()) {
                _state.update { it.copy(supported = false, loadingCategories = false) }
                return@launch
            }
            runCatching { catalog.movieCategories() }
                .onSuccess { categories ->
                    _state.update {
                        it.copy(categories = categories, loadingCategories = false)
                    }
                    categories.firstOrNull()?.let { selectCategory(it.id) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loadingCategories = false,
                            error = (error as? Exception)?.friendlyMessage() ?: error.message
                        )
                    }
                }
        }
    }

    fun selectCategory(categoryId: String) {
        if (_state.value.selectedCategoryId == categoryId) return
        _state.update {
            it.copy(selectedCategoryId = categoryId, movies = emptyList(), error = null)
        }
        itemsJob?.cancel()
        // The two shelves the app adds have nothing to fetch: one is a text field,
        // the other is already in memory.
        if (categoryId == SEARCH_CATEGORY_ID || categoryId == VOD_FAVORITES_CATEGORY_ID) {
            _state.update { it.copy(loadingItems = false) }
            return
        }
        itemsJob = viewModelScope.launch {
            _state.update { it.copy(loadingItems = true) }
            runCatching { catalog.movies(categoryId) }
                .onSuccess { movies ->
                    // The sidebar may have moved on while this was in flight.
                    if (_state.value.selectedCategoryId != categoryId) return@launch
                    _state.update { it.copy(movies = movies, loadingItems = false) }
                }
                .onFailure { error ->
                    if (_state.value.selectedCategoryId != categoryId) return@launch
                    _state.update {
                        it.copy(
                            loadingItems = false,
                            error = (error as? Exception)?.friendlyMessage() ?: error.message
                        )
                    }
                }
        }
    }

    /**
     * A letter was typed. The search itself waits a third of a second: on a remote
     * a word arrives one key at a time, and firing after every key would spend the
     * evening filtering strings nobody meant.
     */
    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        val needle = value.trim()
        if (needle.length < MIN_QUERY) {
            _state.update { it.copy(results = emptyList(), searching = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(TYPING_PAUSE_MS)
            _state.update { it.copy(searching = true, error = null) }
            val found = runCatching { catalog.searchMovies(needle) }
            if (_state.value.query.trim() != needle) return@launch
            _state.update { current ->
                current.copy(
                    searching = false,
                    results = found.getOrDefault(emptyList()),
                    error = found.exceptionOrNull()?.let { error ->
                        (error as? Exception)?.friendlyMessage() ?: error.message
                    }
                )
            }
        }
    }

    fun open(movie: Movie) {
        _state.update {
            it.copy(
                opened = movie,
                openedDetail = null,
                openedResumeMs = settings.playbackPosition(movie.resumeKey),
                openedIsFavorite = settings.isFavorite(movie.resumeKey)
            )
        }
        viewModelScope.launch {
            val detail = runCatching { catalog.movieDetail(movie.id) }.getOrNull() ?: return@launch
            if (_state.value.opened?.id == movie.id) {
                _state.update { it.copy(openedDetail = detail) }
            }
        }
    }

    fun toggleFavorite(movie: Movie) {
        val now = settings.toggleFavorite(
            FavoriteItem(
                key = movie.resumeKey,
                isSeries = false,
                title = movie.name,
                posterUrl = movie.posterUrl,
                streamUrl = movie.streamUrl
            )
        )
        _state.update { it.copy(openedIsFavorite = now) }
    }

    fun close() = _state.update { it.copy(opened = null, openedDetail = null) }

    private companion object {
        /** One letter matches half the catalogue, which is not an answer. */
        const val MIN_QUERY = 2
    }
}

/** A favourite, put back into the shape the poster grid already knows. */
private fun FavoriteItem.asMovie() = Movie(
    id = key.substringAfter(':').toIntOrNull() ?: 0,
    name = title,
    posterUrl = posterUrl,
    categoryId = VOD_FAVORITES_CATEGORY_ID,
    rating = null,
    streamUrl = streamUrl
)

private fun FavoriteItem.asSeries() = Series(
    id = seriesId.takeIf { it > 0 } ?: key.substringAfter(':').toIntOrNull() ?: 0,
    name = title,
    posterUrl = posterUrl,
    categoryId = VOD_FAVORITES_CATEGORY_ID
)

// ----------------------------------------------------------------------- series

data class SeriesUiState(
    val supported: Boolean = true,
    val loadingCategories: Boolean = true,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val loadingItems: Boolean = false,
    val series: List<Series> = emptyList(),
    val error: String? = null,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Series> = emptyList()
)

class SeriesViewModel(
    private val catalog: VodRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesUiState())
    val state: StateFlow<SeriesUiState> = _state.asStateFlow()

    /** Half-watched episodes. Shown on the series screen, not among the films. */
    val continueWatching: StateFlow<List<ContinueItem>> = settings.continueWatching
        .map { list -> list.filter { it.isEpisode } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<Series>> = settings.vodFavorites
        .map { list -> list.filter { it.isSeries }.map { it.asSeries() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearContinueWatching() = settings.clearContinueWatching(episodes = true)

    private var itemsJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            if (!catalog.isSupported()) {
                _state.update { it.copy(supported = false, loadingCategories = false) }
                return@launch
            }
            runCatching { catalog.seriesCategories() }
                .onSuccess { categories ->
                    _state.update { it.copy(categories = categories, loadingCategories = false) }
                    categories.firstOrNull()?.let { selectCategory(it.id) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loadingCategories = false,
                            error = (error as? Exception)?.friendlyMessage() ?: error.message
                        )
                    }
                }
        }
    }

    fun selectCategory(categoryId: String) {
        if (_state.value.selectedCategoryId == categoryId) return
        _state.update {
            it.copy(selectedCategoryId = categoryId, series = emptyList(), error = null)
        }
        itemsJob?.cancel()
        if (categoryId == SEARCH_CATEGORY_ID || categoryId == VOD_FAVORITES_CATEGORY_ID) {
            _state.update { it.copy(loadingItems = false) }
            return
        }
        itemsJob = viewModelScope.launch {
            _state.update { it.copy(loadingItems = true) }
            runCatching { catalog.series(categoryId) }
                .onSuccess { list ->
                    if (_state.value.selectedCategoryId != categoryId) return@launch
                    _state.update { it.copy(series = list, loadingItems = false) }
                }
                .onFailure { error ->
                    if (_state.value.selectedCategoryId != categoryId) return@launch
                    _state.update {
                        it.copy(
                            loadingItems = false,
                            error = (error as? Exception)?.friendlyMessage() ?: error.message
                        )
                    }
                }
        }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        val needle = value.trim()
        if (needle.length < MIN_QUERY) {
            _state.update { it.copy(results = emptyList(), searching = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(TYPING_PAUSE_MS)
            _state.update { it.copy(searching = true, error = null) }
            val found = runCatching { catalog.searchSeries(needle) }
            if (_state.value.query.trim() != needle) return@launch
            _state.update { current ->
                current.copy(
                    searching = false,
                    results = found.getOrDefault(emptyList()),
                    error = found.exceptionOrNull()?.let { error ->
                        (error as? Exception)?.friendlyMessage() ?: error.message
                    }
                )
            }
        }
    }

    private companion object {
        const val MIN_QUERY = 2
    }
}

// --------------------------------------------------------------- one series

data class SeriesDetailUiState(
    val loading: Boolean = true,
    val detail: SeriesDetail? = null,
    val selectedSeason: Int? = null,
    val isFavorite: Boolean = false,
    val autoNext: Boolean = true,
    val error: String? = null
) {
    val episodes: List<Episode>
        get() = selectedSeason?.let { detail?.episodesOf(it) }.orEmpty()
}

class SeriesDetailViewModel(
    private val catalog: VodRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesDetailUiState())
    val state: StateFlow<SeriesDetailUiState> = _state.asStateFlow()

    private var loadedId: Int? = null

    fun load(seriesId: Int) {
        if (loadedId == seriesId) return
        loadedId = seriesId
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    isFavorite = settings.isFavorite(favoriteKey(seriesId)),
                    autoNext = settings.autoNextEpisode
                )
            }
            runCatching { catalog.seriesDetail(seriesId) }
                .onSuccess { detail ->
                    _state.update {
                        it.copy(
                            loading = false,
                            detail = detail,
                            selectedSeason = detail.seasons.firstOrNull()
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = (error as? Exception)?.friendlyMessage() ?: error.message
                        )
                    }
                }
        }
    }

    fun selectSeason(season: Int) = _state.update { it.copy(selectedSeason = season) }

    fun resumeMs(episode: Episode): Long = settings.playbackPosition(episode.resumeKey)

    fun toggleFavorite() {
        val series = _state.value.detail?.series ?: return
        val now = settings.toggleFavorite(
            FavoriteItem(
                key = favoriteKey(series.id),
                isSeries = true,
                title = series.name,
                posterUrl = series.posterUrl,
                seriesId = series.id
            )
        )
        _state.update { it.copy(isFavorite = now) }
    }

    fun setAutoNext(value: Boolean) {
        settings.autoNextEpisode = value
        _state.update { it.copy(autoNext = value) }
    }

    private companion object {
        fun favoriteKey(seriesId: Int) = "series:$seriesId"
    }
}
