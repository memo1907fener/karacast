package com.safir.iptv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.domain.model.Movie
import com.safir.iptv.domain.model.MovieDetail
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.EmptyState
import com.safir.iptv.ui.components.TopBarSearch
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.asTimeLabel
import kotlinx.coroutines.delay

/**
 * The film catalogue. Categories on the left, posters on the right, and a sheet
 * over the top when one is opened — the plot has to live somewhere, and a viewer
 * coming back to a half-watched film deserves the choice between carrying on and
 * starting again.
 */
@Composable
fun MoviesScreen(
    onPlay: (Movie, startMs: Long) -> Unit,
    onResume: (ContinueItem) -> Unit,
    onBack: () -> Unit
) {
    val t = LocalStrings.current
    val viewModel: MoviesViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val continueItems by viewModel.continueWatching.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()

    if (!state.supported) {
        UnsupportedSource(title = t.movies, message = t.vod.onlyXtream, onBack = onBack)
        return
    }

    // „Suche“ in der Kopfleiste.
    LaunchedEffect(Unit) {
        TopBarSearch.requests.collect { viewModel.selectCategory(SEARCH_CATEGORY_ID) }
    }

    // Was rechts groß steht: das angewählte Cover — oder, bevor eins angewählt
    // ist, das erste der Kategorie, damit die Fläche nie leer ist.
    var focusedMovie by remember { mutableStateOf<Movie?>(null) }
    var focusedContinue by remember { mutableStateOf<ContinueItem?>(null) }
    val shown = when (state.selectedCategoryId) {
        SEARCH_CATEGORY_ID -> state.results
        VOD_FAVORITES_CATEGORY_ID -> favorites
        VOD_CONTINUE_CATEGORY_ID -> emptyList()
        else -> state.movies
    }
    LaunchedEffect(state.selectedCategoryId) {
        focusedMovie = null
        focusedContinue = null
    }
    val previewMovie = focusedMovie ?: shown.firstOrNull()
    LaunchedEffect(previewMovie?.id) { previewMovie?.let(viewModel::preview) }

    // Die Suche und die eigenen Regale vor den Kategorien des Anbieters: wer
    // schon weiß, was er will, soll nicht an hundert Kategorien vorbei müssen.
    val categories = remember(state.categories, continueItems.isNotEmpty(), t) {
        buildList {
            add(Category(SEARCH_CATEGORY_ID, t.vod.search))
            if (continueItems.isNotEmpty()) add(Category(VOD_CONTINUE_CATEGORY_ID, t.vod.continueWatching))
            add(Category(VOD_FAVORITES_CATEGORY_ID, t.vod.favorites))
            addAll(state.categories)
        }
    }
    val selectedName = categories.firstOrNull { it.id == state.selectedCategoryId }?.name.orEmpty()

    Box(Modifier.fillMaxSize()) {
        CatalogFrame(
            title = t.movies,
            categories = categories,
            selectedCategoryId = state.selectedCategoryId,
            loadingCategories = state.loadingCategories,
            onSelectCategory = viewModel::selectCategory,
            preview = {
                val resumed = focusedContinue ?: continueItems.firstOrNull()
                if (state.selectedCategoryId == VOD_CONTINUE_CATEGORY_ID && resumed != null) {
                    CatalogPreview(
                        posterUrl = resumed.posterUrl,
                        title = resumed.title,
                        facts = t.vod.continueAt(resumed.positionMs.asTimeLabel()),
                        plot = null,
                        footer = t.vod.gridHint,
                        progress = resumed.progress
                    )
                } else if (previewMovie != null) {
                    val detail = preview?.takeIf { it.first == previewMovie.id }?.second
                    CatalogPreview(
                        posterUrl = detail?.posterUrl?.takeIf { it.isNotBlank() } ?: previewMovie.posterUrl,
                        title = previewMovie.name,
                        facts = listOfNotNull(
                            detail?.released?.takeIf { it.isNotBlank() }?.take(4),
                            detail?.genre?.takeIf { it.isNotBlank() },
                            detail?.durationSecs?.takeIf { it > 0 }?.let { t.nMinutes(it / 60) },
                            (detail?.rating ?: previewMovie.rating)
                                ?.takeIf { it.isNotBlank() && it != "0" }
                                ?.let { "★ $it" }
                        ).joinToString("  ·  "),
                        plot = detail?.plot,
                        footer = t.vod.gridHint
                    )
                } else {
                    CatalogPreview(posterUrl = null, title = null, facts = null, plot = null, footer = t.vod.gridHint)
                }
            }
        ) {
            val onFocusMovie: (Movie) -> Unit = { focusedMovie = it }
            when (state.selectedCategoryId) {
                SEARCH_CATEGORY_ID -> SearchPane(
                    query = state.query,
                    onQuery = viewModel::setQuery,
                    searching = state.searching,
                    error = state.error,
                    resultCount = state.results.size
                ) {
                    MovieGrid(state.results, viewModel::open, onFocusMovie)
                }

                VOD_CONTINUE_CATEGORY_ID -> {
                    CatalogHeader(t.vod.continueWatching, null) {
                        TvButton(onClick = viewModel::clearContinueWatching) { Text(t.vod.clearList) }
                    }
                    ContinueGrid(continueItems, onPlay = onResume, onFocus = { focusedContinue = it })
                }

                VOD_FAVORITES_CATEGORY_ID -> {
                    CatalogHeader(t.vod.favorites, favorites.size)
                    if (favorites.isEmpty()) {
                        EmptyState(title = t.vod.noFavorites, message = t.vod.favoritesHint)
                    } else {
                        MovieGrid(favorites, viewModel::open, onFocusMovie)
                    }
                }

                else -> {
                    CatalogHeader(selectedName, state.movies.size)
                    if (state.movies.isEmpty()) {
                        CatalogPlaceholder(
                            loading = state.loadingItems,
                            error = state.error,
                            emptyTitle = t.vod.noMovies,
                            emptyMessage = t.vod.emptyCategory
                        )
                    } else {
                        MovieGrid(state.movies, viewModel::open, onFocusMovie)
                    }
                }
            }
        }

        state.opened?.let { movie ->
            MovieSheet(
                movie = movie,
                detail = state.openedDetail,
                resumeMs = state.openedResumeMs,
                isFavorite = state.openedIsFavorite,
                onPlay = { startMs ->
                    viewModel.close()
                    onPlay(movie, startMs)
                },
                onToggleFavorite = { viewModel.toggleFavorite(movie) },
                onDismiss = viewModel::close
            )
        }
    }
}

/** The same poster grid whichever shelf it is drawn for. */
@Composable
private fun MovieGrid(movies: List<Movie>, onOpen: (Movie) -> Unit, onFocus: (Movie) -> Unit) {
    PosterGrid(
        items = movies
    ) { movie, tileModifier ->
        PosterTile(
            title = movie.name,
            posterUrl = movie.posterUrl,
            badge = movie.rating?.takeIf { it.isNotBlank() && it != "0" },
            onClick = { onOpen(movie) },
            modifier = tileModifier,
            fixedHeight = true,
            onFocused = { onFocus(movie) }
        )
    }
}

/** What an M3U account sees where the catalogue would be. */
@Composable
fun UnsupportedSource(title: String, message: String, onBack: () -> Unit) {
    val t = LocalStrings.current
    Box(Modifier.fillMaxSize().background(Brand.Background)) {
        EmptyState(title = title, message = message)
        Box(Modifier.align(Alignment.BottomCenter).padding(48.dp)) {
            TvButton(onClick = onBack) { Text(t.back) }
        }
    }
}

/**
 * The opened film: poster on the left, everything the provider knows on the right,
 * and the buttons that matter above the text rather than below it — resume first
 * when there is something to resume, because that is what was come back for.
 */
@Composable
private fun MovieSheet(
    movie: Movie,
    detail: MovieDetail?,
    resumeMs: Long,
    isFavorite: Boolean,
    onPlay: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalStrings.current
    val firstButton = remember { FocusRequester() }

    LaunchedEffect(movie.id) {
        delay(80)
        runCatching { firstButton.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .alsoTappable(onDismiss)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                if (event.key == Key.Back || event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .widthIn(max = 1040.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brand.Surface)
                .padding(28.dp)
        ) {
            Box(
                Modifier
                    .width(220.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brand.SurfaceHigh)
            ) {
                val poster = detail?.posterUrl ?: movie.posterUrl
                if (!poster.isNullOrBlank()) {
                    AsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.width(28.dp))

            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = movie.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))

                val facts = listOfNotNull(
                    detail?.released?.takeIf { it.isNotBlank() },
                    detail?.genre?.takeIf { it.isNotBlank() },
                    detail?.durationSecs?.takeIf { it > 0 }?.let { t.nMinutes(it / 60) },
                    (detail?.rating ?: movie.rating)
                        ?.takeIf { it.isNotBlank() && it != "0" }
                        ?.let { "★ $it" }
                )
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelMedium,
                        color = Brand.AccentSoft
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (resumeMs > 0L) {
                        TvButton(
                            onClick = { onPlay(resumeMs) },
                            modifier = Modifier.focusRequester(firstButton)
                        ) {
                            Text(t.vod.continueAt(resumeMs.asTimeLabel()))
                        }
                        TvButton(onClick = { onPlay(0L) }) { Text(t.vod.playFromStart) }
                    } else {
                        TvButton(
                            onClick = { onPlay(0L) },
                            modifier = Modifier.focusRequester(firstButton)
                        ) {
                            Text(t.vod.play)
                        }
                    }
                    TvButton(onClick = onToggleFavorite) {
                        Text(if (isFavorite) t.vod.removeFavorite else t.vod.addFavorite)
                    }
                    TvButton(onClick = onDismiss) { Text(t.back) }
                }

                Spacer(Modifier.height(18.dp))

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    detail?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand.TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Fact(t.vod.director, detail?.director)
                    Fact(t.vod.cast, detail?.cast)
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Brand.TextSecondary,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = Brand.TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
