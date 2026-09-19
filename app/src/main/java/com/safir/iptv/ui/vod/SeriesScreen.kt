package com.safir.iptv.ui.vod

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.domain.model.Series
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.EmptyState
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand

/**
 * The series catalogue. Same shell as the films, one level shallower: a poster
 * here opens the seasons rather than starting anything, because nobody wants a
 * random episode when they press OK on a series.
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

    CatalogFrame(
        title = t.series,
        hint = t.vod.gridHint,
        categories = remember(state.categories, t) {
            listOf(
                Category(SEARCH_CATEGORY_ID, t.vod.search),
                Category(VOD_FAVORITES_CATEGORY_ID, t.vod.favorites)
            ) + state.categories
        },
        selectedCategoryId = state.selectedCategoryId,
        loadingCategories = state.loadingCategories,
        onSelectCategory = viewModel::selectCategory,
        onBack = onBack
    ) {
        when (state.selectedCategoryId) {
            SEARCH_CATEGORY_ID -> SearchPane(
                query = state.query,
                onQuery = viewModel::setQuery,
                searching = state.searching,
                error = state.error,
                resultCount = state.results.size
            ) {
                SeriesGrid(state.results, onOpenSeries)
            }

            VOD_FAVORITES_CATEGORY_ID -> if (favorites.isEmpty()) {
                EmptyState(title = t.vod.noFavorites, message = t.vod.favoritesHint)
            } else {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        text = t.vod.nTitles(favorites.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = Brand.TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    SeriesGrid(favorites, onOpenSeries)
                }
            }

            else -> Column(Modifier.fillMaxSize()) {
                ContinueRow(
                    items = continueItems,
                    onPlay = onResume,
                    onClear = viewModel::clearContinueWatching
                )
                if (state.series.isEmpty()) {
                    CatalogPlaceholder(
                        loading = state.loadingItems,
                        error = state.error,
                        emptyTitle = t.vod.noSeries,
                        emptyMessage = t.vod.emptyCategory
                    )
                } else {
                    Text(
                        text = t.vod.nTitles(state.series.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = Brand.TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    SeriesGrid(state.series, onOpenSeries)
                }
            }
        }
    }
}

@Composable
private fun SeriesGrid(series: List<Series>, onOpenSeries: (Int) -> Unit) {
    PosterGrid(
        items = series
    ) { entry, tileModifier ->
        PosterTile(
            title = entry.name,
            posterUrl = entry.posterUrl,
            badge = entry.rating?.takeIf { it.isNotBlank() && it != "0" },
            onClick = { onOpenSeries(entry.id) },
            modifier = tileModifier,
            fixedHeight = true
        )
    }
}
