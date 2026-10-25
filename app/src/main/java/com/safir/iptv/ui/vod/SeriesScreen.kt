package com.safir.iptv.ui.vod

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.domain.model.Series
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.EmptyState
import com.safir.iptv.ui.components.TopBarSearch
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.util.asTimeLabel

/**
 * The series catalogue. Same shell as the films, one level shallower: a poster
 * here opens the seasons rather than starting anything, because nobody wants a
 * random episode when they press OK on a series.
 *
 * Gebaut wie die Kino-Ansicht von Live TV: Kategorien links, Cover in der
 * Mitte, rechts groß die angewählte Serie mit Handlung.
 */
@Composable
fun SeriesScreen(
    onOpenSeries: (Int) -> Unit,
    onResume: (ContinueItem) -> Unit,
    onBack: () -> Unit
) {
    val t = LocalStrings.current
    val viewModel: SeriesViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val continueItems by viewModel.continueWatching.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    if (!state.supported) {
        UnsupportedSource(title = t.series, message = t.vod.onlyXtream, onBack = onBack)
        return
    }

    // „Suche“ in der Kopfleiste.
    LaunchedEffect(Unit) {
        TopBarSearch.requests.collect { viewModel.selectCategory(SEARCH_CATEGORY_ID) }
    }

    var focusedSeries by remember { mutableStateOf<Series?>(null) }
    var focusedContinue by remember { mutableStateOf<ContinueItem?>(null) }
    val shown = when (state.selectedCategoryId) {
        SEARCH_CATEGORY_ID -> state.results
        VOD_FAVORITES_CATEGORY_ID -> favorites
        VOD_CONTINUE_CATEGORY_ID -> emptyList()
        else -> state.series
    }
    LaunchedEffect(state.selectedCategoryId) {
        focusedSeries = null
        focusedContinue = null
    }
    val previewSeries = focusedSeries ?: shown.firstOrNull()

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
            title = t.series,
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
                        facts = listOf(resumed.subtitle, t.vod.continueAt(resumed.positionMs.asTimeLabel()))
                            .filter { it.isNotBlank() }
                            .joinToString("  ·  "),
                        plot = null,
                        footer = t.vod.gridHint,
                        progress = resumed.progress
                    )
                } else if (previewSeries != null) {
                    CatalogPreview(
                        posterUrl = previewSeries.posterUrl,
                        title = previewSeries.name,
                        facts = listOfNotNull(
                            previewSeries.released?.takeIf { it.isNotBlank() }?.take(4),
                            previewSeries.genre?.takeIf { it.isNotBlank() },
                            previewSeries.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" }
                        ).joinToString("  ·  "),
                        plot = previewSeries.plot,
                        footer = t.vod.gridHint
                    )
                } else {
                    CatalogPreview(posterUrl = null, title = null, facts = null, plot = null, footer = t.vod.gridHint)
                }
            }
        ) {
            val onFocusSeries: (Series) -> Unit = { focusedSeries = it }
            when (state.selectedCategoryId) {
                SEARCH_CATEGORY_ID -> SearchPane(
                    query = state.query,
                    onQuery = viewModel::setQuery,
                    searching = state.searching,
                    error = state.error,
                    resultCount = state.results.size
                ) {
                    SeriesGrid(state.results, onOpenSeries, onFocusSeries)
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
                        SeriesGrid(favorites, onOpenSeries, onFocusSeries)
                    }
                }

                else -> {
                    CatalogHeader(selectedName, state.series.size)
                    if (state.series.isEmpty()) {
                        CatalogPlaceholder(
                            loading = state.loadingItems,
                            error = state.error,
                            emptyTitle = t.vod.noSeries,
                            emptyMessage = t.vod.emptyCategory
                        )
                    } else {
                        SeriesGrid(state.series, onOpenSeries, onFocusSeries)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesGrid(series: List<Series>, onOpenSeries: (Int) -> Unit, onFocus: (Series) -> Unit) {
    PosterGrid(
        items = series
    ) { entry, tileModifier ->
        PosterTile(
            title = entry.name,
            posterUrl = entry.posterUrl,
            badge = entry.rating?.takeIf { it.isNotBlank() && it != "0" },
            onClick = { onOpenSeries(entry.id) },
            modifier = tileModifier,
            fixedHeight = true,
            onFocused = { onFocus(entry) }
        )
    }
}
