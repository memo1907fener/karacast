package com.safir.iptv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.ui.components.EmptyState
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.TvTextField
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.asTimeLabel

/**
 * The shell both catalogues share: the provider's categories down the left, the
 * posters in the space that is left. The same two-pane shape as the live list, so
 * films are somewhere already familiar rather than somewhere new.
 */
@Composable
fun CatalogFrame(
    title: String,
    hint: String,
    categories: List<Category>,
    selectedCategoryId: String?,
    loadingCategories: Boolean,
    onSelectCategory: (String) -> Unit,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val t = LocalStrings.current

    Row(Modifier.fillMaxSize().background(Brand.Background)) {

        Column(
            Modifier
                .width(320.dp)
                .fillMaxHeight()
                .background(Brand.Surface)
                .padding(vertical = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = Brand.TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))

            when {
                loadingCategories -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand.Accent)
                }

                categories.isEmpty() -> Box(
                    Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t.noCategories,
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.TextSecondary
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(categories) { category ->
                        CategoryLine(
                            name = category.name,
                            selected = category.id == selectedCategoryId,
                            onSelect = { onSelectCategory(category.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.padding(horizontal = 24.dp)) {
                TvButton(onClick = onBack) { Text(t.back) }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            content = content
        )
    }
}

@Composable
private fun CategoryLine(name: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Brand.SurfaceHigh else Color.Transparent,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = if (selected) Brand.TextPrimary else Brand.TextSecondary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().alsoTappable(onSelect)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
        )
    }
}

/**
 * One poster. A film with no artwork still has to be findable, so the fallback is
 * the title itself on a plain card rather than a broken-image icon.
 */
@Composable
fun PosterTile(
    title: String,
    posterUrl: String?,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Wahr, wenn die Kachel ihre Höhe von außen bekommt (siehe [PosterGrid]).
     * Dann füllt das Bild aus, was nach dem Titel übrig bleibt, statt seine
     * Höhe aus der Breite zu bestimmen — nur so ist die Reihenhöhe genau die
     * gerechnete, und nur dann passt beim Rollen immer eine ganze Reihe ins Bild.
     */
    fixedHeight: Boolean = false
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = Brand.TextPrimary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier.alsoTappable(onClick)
    ) {
        Column(
            if (fixedHeight) Modifier.fillMaxHeight().padding(6.dp) else Modifier.padding(6.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (fixedHeight) Modifier.weight(1f) else Modifier.aspectRatio(2f / 3f))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brand.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                if (posterUrl.isNullOrBlank()) {
                    PosterFallback(title)
                } else {
                    SubcomposeAsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { PosterFallback(title) },
                        error = { PosterFallback(title) }
                    )
                }
                if (!badge.isNullOrBlank()) {
                    Text(
                        text = badge,
                        fontSize = 11.sp,
                        color = Brand.TextPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Das Posterraster für Filme und Serien.
 *
 * Der Punkt: die Kachelgröße wird nicht vorgegeben, sondern ausgerechnet — und
 * zwar aus der Höhe, die tatsächlich übrig ist. Vorher standen dort feste 150dp
 * Mindestbreite; auf dem Fernseher war eine Kachel damit höher als die halbe
 * freie Fläche, und beim Rollen stand immer eine Reihe halb im Bild, deren Titel
 * niemand mehr lesen konnte.
 *
 * Jetzt wird zweierlei getan. Erstens wird unter den möglichen Spaltenzahlen die
 * genommen, bei der die meisten *vollständigen* Reihen hineinpassen (bei
 * Gleichstand die mit den größeren Covern). Zweitens — und das ist der
 * eigentliche Trick — bekommt das Raster genau die Höhe dieser ganzen Reihen.
 * Was darunter bleibt, bleibt leer. Dadurch kann gar keine angeschnittene Reihe
 * mehr zu sehen sein: entweder eine Reihe ist ganz da, oder sie ist es nicht.
 */
@Composable
fun <T> PosterGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    tile: @Composable (T, Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val metrics = posterMetrics(maxWidth, maxHeight)

        LazyVerticalGrid(
            columns = GridCells.Fixed(metrics.columns),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP),
            // Der Rand ringsum ist kein Zierrat: eine Kachel wächst beim
            // Anwählen leicht an, und ohne diesen Platz schneidet das Raster
            // genau das an, was gerade ausgewählt ist.
            contentPadding = PaddingValues(FOCUS_ROOM),
            modifier = Modifier.fillMaxWidth().height(metrics.gridHeight)
        ) {
            // Bewusst ohne Schlüssel: ein Anbieter darf dieselbe Kennung zweimal
            // liefern, und doppelte Schlüssel lassen die Liste abstürzen.
            items(items) { item ->
                tile(item, Modifier.height(metrics.tileHeight))
            }
        }
    }
}

private class GridMetrics(val columns: Int, val tileHeight: Dp, val gridHeight: Dp)

/** Abstand zwischen den Kacheln, waagerecht wie senkrecht. */
private val GRID_GAP = 12.dp

/** Luft am Rand des Rasters für die Kachel, die beim Anwählen größer wird. */
private val FOCUS_ROOM = 8.dp

/**
 * Was eine Kachel außer dem Bild noch braucht: 6dp Rand ringsum und eine Zeile
 * Titel darunter. Bewusst *eine* Zeile: der Titel darf zwei haben, und weil das
 * Bild den Rest ausfüllt (siehe [PosterTile]), wird bei einem langen Titel
 * einfach das Cover ein paar Pixel niedriger. Die Reihenhöhe bleibt dieselbe.
 */
private const val TILE_CHROME = 12f + 6f + 17f

/** Unter dieser Breite ist ein Cover kein Cover mehr, sondern eine Briefmarke. */
private const val MIN_POSTER_WIDTH = 96f

/** Und darüber füllt ein einziger Film den halben Bildschirm. */
private const val MAX_POSTER_WIDTH = 190f

private fun posterMetrics(width: Dp, height: Dp): GridMetrics {
    val gap = GRID_GAP.value
    val room = FOCUS_ROOM.value * 2
    val w = (width.value - room).coerceAtLeast(1f)
    val h = (height.value - room).coerceAtLeast(1f)

    var best: GridMetrics? = null
    var bestRows = 0
    var bestLeftover = Float.MAX_VALUE

    // Bis zwölf Spalten: auf einem sehr breiten Bild ist das der einzige Weg zu
    // einer zweiten ganzen Reihe.
    for (columns in 2..12) {
        val posterWidth = (w - gap * (columns - 1)) / columns - 12f
        if (posterWidth < MIN_POSTER_WIDTH || posterWidth > MAX_POSTER_WIDTH) continue

        val tileHeight = posterWidth * 1.5f + TILE_CHROME
        if (tileHeight > h) continue

        val rows = (((h + gap) / (tileHeight + gap)).toInt()).coerceAtLeast(1)
        val used = rows * tileHeight + gap * (rows - 1)
        val leftover = h - used

        // Mehr ganze Reihen schlägt alles; bei gleicher Reihenzahl gewinnt, was
        // die Fläche besser ausnutzt — und das sind die größeren Cover.
        if (rows > bestRows || (rows == bestRows && leftover < bestLeftover)) {
            bestRows = rows
            bestLeftover = leftover
            best = GridMetrics(columns, tileHeight.dp, (used + room).dp)
        }
    }

    // Fällt alles durch — sehr schmale oder sehr niedrige Fläche —, dann lieber
    // eine Reihe, die die Höhe ausfüllt, als gar nichts.
    return best ?: GridMetrics(
        columns = ((w + gap) / (120f + gap)).toInt().coerceAtLeast(2),
        tileHeight = h.dp,
        gridHeight = (h + room).dp
    )
}

@Composable
private fun PosterFallback(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = Brand.TextSecondary,
        textAlign = TextAlign.Center,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(10.dp)
    )
}

/**
 * The half-watched titles, across the top of the catalogue.
 *
 * Small on purpose: it is a shortcut, not the main event, and the grid underneath
 * is what somebody browsing actually came for. Each card carries the cover, the
 * bar showing how far in they got, and the time itself — a bar alone says "some
 * of the way", and what a viewer wants to know is whether they stopped before or
 * after the interesting part.
 */
@Composable
fun ContinueRow(
    items: List<ContinueItem>,
    onPlay: (ContinueItem) -> Unit,
    onClear: () -> Unit
) {
    if (items.isEmpty()) return
    val t = LocalStrings.current

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = t.vod.continueWatching,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = t.vod.continueHint,
                style = MaterialTheme.typography.labelSmall,
                color = Brand.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            TvButton(onClick = onClear) { Text(t.vod.clearList) }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(CARD_HEIGHT)
        ) {
            items(items) { item ->
                ContinueCard(item = item, onClick = { onPlay(item) })
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ContinueCard(item: ContinueItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = Brand.TextPrimary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        // A fixed size, and deliberately so: without it the weight inside stretches
        // the card over the whole pane and the poster grid it sits above never gets
        // a pixel. A shortcut has no business crowding out what it is a shortcut to.
        modifier = Modifier
            .width(250.dp)
            .height(CARD_HEIGHT)
            .alsoTappable(onClick)
    ) {
        Row(Modifier.padding(8.dp)) {
            Box(
                Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brand.SurfaceHigh)
            ) {
                item.posterUrl?.takeIf { it.isNotBlank() }?.let { poster ->
                    SubcomposeAsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    // An episode spends its second line on the season and number,
                    // which say more than the rest of a long series title would.
                    maxLines = if (item.subtitle.isBlank()) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = item.positionMs.asTimeLabel(),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brand.SurfaceHigh)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.progress)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brand.Accent)
                    )
                }
            }
        }
    }
}

/** Tall enough for two lines of title and the bar, short enough to stay a strip. */
private val CARD_HEIGHT = 92.dp

/**
 * The search pane, shared by both catalogues.
 *
 * `player_api.php` has no search of its own, so this is a filter over the whole
 * catalogue rather than a request per keystroke — which is why the field can stay
 * a plain field and the answer arrives while the next letter is still being typed.
 */
@Composable
fun SearchPane(
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    error: String?,
    resultCount: Int,
    grid: @Composable () -> Unit
) {
    val t = LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.width(560.dp)) {
            TvTextField(
                value = query,
                onValueChange = onQuery,
                label = t.vod.search,
                placeholder = t.vod.searchPrompt
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (resultCount > 0) t.vod.nResults(resultCount) else t.vod.searchHint,
            style = MaterialTheme.typography.labelMedium,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(14.dp))

        when {
            searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.Accent)
            }

            query.trim().length < 2 ->
                EmptyState(title = t.vod.search, message = t.vod.searchHint)

            error != null -> EmptyState(title = t.vod.noResults, message = error)

            resultCount == 0 ->
                EmptyState(title = t.vod.noResults, message = t.vod.noResultsHint)

            else -> grid()
        }
    }
}

/** Spinner, message, or nothing — the three states a half-loaded grid can be in. */
@Composable
fun CatalogPlaceholder(
    loading: Boolean,
    error: String?,
    emptyTitle: String,
    emptyMessage: String
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Brand.Accent)
        }

        error != null -> EmptyState(title = emptyTitle, message = error)

        else -> EmptyState(title = emptyTitle, message = emptyMessage)
    }
}
