package com.safir.iptv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.safir.iptv.IptvApp
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.domain.model.Episode
import com.safir.iptv.domain.model.Movie
import com.safir.iptv.domain.model.Series
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.i18n.LocalStrings

import com.safir.iptv.ui.vod.MoviesViewModel
import com.safir.iptv.ui.vod.SeriesDetailViewModel
import com.safir.iptv.ui.vod.SeriesViewModel
import com.safir.iptv.ui.vod.VOD_FAVORITES_CATEGORY_ID
import com.safir.iptv.ui.vod.VodPlayback

/** Kopf eines Katalog-Reiters: Titel, Anzahl, Suchfeld. */
@Composable
private fun CatalogHeader(
    title: String,
    count: String?,
    query: String,
    placeholder: String,
    onQuery: (String) -> Unit
) {
    var searching by remember { mutableStateOf(query.isNotEmpty()) }
    if (searching) {
        SearchBar(
            query = query,
            placeholder = placeholder,
            onQuery = onQuery,
            onClose = {
                onQuery("")
                searching = false
            }
        )
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(10.dp))
            if (count != null) Text(count, fontSize = 14.sp, color = Kc.TextSecondary)
            Spacer(Modifier.weight(1f))
            RoundIconButton(Icons.Filled.Search, placeholder) { searching = true }
        }
    }
}

/** Die Kategorien als Chips, „Favoriten“ vorneweg. */
@Composable
private fun CategoryChips(
    categories: List<Category>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    val t = LocalStrings.current
    val all = remember(categories) {
        listOf(Category(VOD_FAVORITES_CATEGORY_ID, t.vod.favorites)) + categories
    }
    ChipRow(all, key = { it.id }) { category ->
        KcChip(label = cleanTitle(category.name), selected = category.id == selected, onClick = { onSelect(category.id) })
    }
}

@Composable
private fun PosterCell(title: String, url: String?, caption: String?, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val shown = cleanTitle(title)
        Poster(url, shown, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Text(shown, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ratingOf(caption)?.let {
            Text("★ $it", fontSize = 12.sp, color = Kc.AccentSoft, maxLines = 1)
        }
    }
}

@Composable
private fun ResumeStrip(items: List<ContinueItem>, onPlay: (ContinueItem) -> Unit) {
    val t = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(t.vod.continueWatching)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.key }) { item ->
                Column(
                    Modifier.width(200.dp).clip(RoundedCornerShape(12.dp)).clickable { onPlay(item) },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(12.dp))) {
                        Poster(item.posterUrl, item.title, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(BottomScrim))
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(34.dp)
                        )
                        ThinProgress(
                            item.progress,
                            Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                            track = Color.White.copy(alpha = 0.2f)
                        )
                    }
                    Text(
                        listOf(item.title, item.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 12.sp,
                        color = Kc.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// =================================================================== Filme

@Composable
fun MobileMoviesScreen(onPlayVod: () -> Unit) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val viewModel: MoviesViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resume by viewModel.continueWatching.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    val showingSearch = state.query.trim().length >= 2
    val movies = when {
        showingSearch -> state.results
        state.selectedCategoryId == VOD_FAVORITES_CATEGORY_ID -> favorites
        else -> state.movies
    }

    fun play(movie: Movie, startMs: Long) {
        VodPlayback.queue = emptyList()
        VodPlayback.pending = VodPlayback.Request(
            title = movie.name,
            subtitle = "",
            url = movie.streamUrl,
            resumeKey = movie.resumeKey,
            startMs = startMs,
            posterUrl = movie.posterUrl
        )
        viewModel.close()
        onPlayVod()
    }

    Column(Modifier.fillMaxSize()) {
        CatalogHeader(
            title = t.movies,
            count = if (!showingSearch && movies.isNotEmpty()) t.vod.nTitles(movies.size) else null,
            query = state.query,
            placeholder = m.searchMovies,
            onQuery = viewModel::setQuery
        )
        Spacer(Modifier.height(12.dp))

        when {
            !state.supported -> EmptyHint(t.vod.noMovies, t.vod.onlyXtream)
            state.loadingCategories -> Loading()
            else -> {
                if (!showingSearch) {
                    CategoryChips(state.categories, state.selectedCategoryId, viewModel::selectCategory)
                    Spacer(Modifier.height(12.dp))
                }
                CatalogGrid(
                    loading = state.loadingItems || state.searching,
                    error = state.error,
                    empty = if (showingSearch) t.vod.noResults else t.vod.emptyCategory,
                    resume = if (showingSearch) emptyList() else resume,
                    onResume = { item ->
                        VodPlayback.queue = emptyList()
                        VodPlayback.pending = item.toRequest()
                        onPlayVod()
                    },
                    entries = movies,
                    key = { "m${it.id}" }
                ) { movie ->
                    PosterCell(movie.name, movie.posterUrl, movie.rating) { viewModel.open(movie) }
                }
            }
        }
    }

    state.opened?.let { movie ->
        MovieSheet(
            movie = movie,
            plot = state.openedDetail?.plot,
            meta = listOfNotNull(
                state.openedDetail?.released?.take(4),
                state.openedDetail?.genre,
                state.openedDetail?.durationSecs?.let { durationOf(it * 1000L) },
                ratingOf(state.openedDetail?.rating ?: movie.rating)?.let { "★ $it" }
            ).filter { it.isNotBlank() }.joinToString(" · "),
            resumeMs = state.openedResumeMs,
            favorite = state.openedIsFavorite,
            onPlay = { play(movie, it) },
            onFavorite = { viewModel.toggleFavorite(movie) },
            onDismiss = viewModel::close
        )
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun <T> CatalogGrid(
    loading: Boolean,
    error: String?,
    empty: String,
    resume: List<ContinueItem>,
    onResume: (ContinueItem) -> Unit,
    entries: List<T>,
    key: (T) -> Any,
    cell: @Composable (T) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (resume.isNotEmpty()) {
            item(key = "resume", span = { GridItemSpan(maxLineSpan) }) {
                // Das Raster hat 20 dp Rand; der Streifen soll wie überall bis an
                // den Bildschirmrand laufen und bringt seinen Rand selbst mit.
                Box(
                    Modifier
                        .padding(bottom = 4.dp)
                        .layout { measurable, constraints ->
                            val extra = 40.dp.roundToPx()
                            val width = constraints.maxWidth + extra
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = width, maxWidth = width)
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.place(-extra / 2, 0)
                            }
                        }
                ) { ResumeStrip(resume, onResume) }
            }
        }
        when {
            loading && entries.isEmpty() -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) { Loading() }
            error != null && entries.isEmpty() -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                EmptyHint(LocalStrings.current.playbackFailed, error)
            }
            entries.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyHint(empty) }
            else -> items(entries, key = key) { cell(it) }
        }
    }
}

@Composable
private fun MovieSheet(
    movie: Movie,
    plot: String?,
    meta: String,
    resumeMs: Long,
    favorite: Boolean,
    onPlay: (Long) -> Unit,
    onFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Kc.Surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Poster(movie.posterUrl, movie.name, Modifier.width(110.dp).aspectRatio(2f / 3f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cleanTitle(movie.name), style = MaterialTheme.typography.titleLarge)
                    if (meta.isNotBlank()) Text(meta, fontSize = 13.sp, color = Kc.TextSecondary)
                }
            }
            if (resumeMs > 0L) {
                Button(
                    onClick = { onPlay(resumeMs) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(t.vod.continueAt(durationOf(resumeMs)), fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (resumeMs > 0L) {
                    OutlinedButton(onClick = { onPlay(0L) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(t.vod.playFromStart)
                    }
                } else {
                    Button(
                        onClick = { onPlay(0L) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(m.play, fontWeight = FontWeight.Bold)
                    }
                }
                FavoriteButton(favorite, onFavorite)
            }
            if (!plot.isNullOrBlank()) {
                Text(plot, fontSize = 14.sp, color = Color(0xFFC9CFDC), lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun FavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Kc.SurfaceHigh,
        contentColor = if (favorite) Kc.Accent else Kc.TextPrimary,
        modifier = Modifier.size(50.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (favorite) LocalStrings.current.vod.removeFavorite else LocalStrings.current.vod.addFavorite
            )
        }
    }
}

// ================================================================== Serien

@Composable
fun MobileSeriesScreen(onOpenSeries: (Int) -> Unit, onPlayVod: () -> Unit) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val viewModel: SeriesViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resume by viewModel.continueWatching.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    val showingSearch = state.query.trim().length >= 2
    val list: List<Series> = when {
        showingSearch -> state.results
        state.selectedCategoryId == VOD_FAVORITES_CATEGORY_ID -> favorites
        else -> state.series
    }

    Column(Modifier.fillMaxSize()) {
        CatalogHeader(
            title = t.series,
            count = if (!showingSearch && list.isNotEmpty()) t.vod.nTitles(list.size) else null,
            query = state.query,
            placeholder = m.searchSeries,
            onQuery = viewModel::setQuery
        )
        Spacer(Modifier.height(12.dp))

        when {
            !state.supported -> EmptyHint(t.vod.noSeries, t.vod.onlyXtream)
            state.loadingCategories -> Loading()
            else -> {
                if (!showingSearch) {
                    CategoryChips(state.categories, state.selectedCategoryId, viewModel::selectCategory)
                    Spacer(Modifier.height(12.dp))
                }
                CatalogGrid(
                    loading = state.loadingItems || state.searching,
                    error = state.error,
                    empty = if (showingSearch) t.vod.noResults else t.vod.emptyCategory,
                    resume = if (showingSearch) emptyList() else resume,
                    onResume = { item ->
                        VodPlayback.queue = emptyList()
                        VodPlayback.pending = item.toRequest()
                        onPlayVod()
                    },
                    entries = list,
                    key = { "s${it.id}" }
                ) { series ->
                    PosterCell(series.name, series.posterUrl, series.rating) { onOpenSeries(series.id) }
                }
            }
        }
    }
}

/** Eine Serie: Bild, Infos, Staffeln als Reiter, Folgen darunter. */
@Composable
fun MobileSeriesDetail(seriesId: Int, onBack: () -> Unit, onPlay: () -> Unit) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val viewModel: SeriesDetailViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = (LocalContext.current.applicationContext as IptvApp).container.settings
    val continueList by settings.continueWatching.collectAsStateWithLifecycle()

    LaunchedEffect(seriesId) { viewModel.load(seriesId) }

    val detail = state.detail
    val series = detail?.series
    val name = series?.name.orEmpty()

    fun start(episode: Episode, startMs: Long) {
        // Die ganze Staffel als Warteschlange: am Ende einer Folge geht es weiter.
        val season = detail?.episodesOf(episode.season).orEmpty()
        VodPlayback.queue = season.map { VodPlayback.episodeRequest(name, seriesId, it, 0L) }
        VodPlayback.pending = VodPlayback.episodeRequest(name, seriesId, episode, startMs)
        onPlay()
    }

    // Wo man in dieser Serie zuletzt war — der große Knopf führt dorthin.
    val lastEpisode: Pair<Episode, Long>? = remember(detail, continueList) {
        val item = continueList.firstOrNull { it.isEpisode && it.seriesId == seriesId } ?: return@remember null
        val episode = detail?.episodes?.firstOrNull { it.resumeKey == item.key } ?: return@remember null
        episode to item.positionMs
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Kc.Background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item(key = "hero") {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                if (!series?.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = series?.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(placeholderColor(name)))
                }
                Box(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Kc.Background.copy(alpha = 0.35f),
                            0.5f to Color.Transparent,
                            1f to Kc.Background
                        )
                    )
                )
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(22.dp),
                    color = Kc.Background.copy(alpha = 0.6f),
                    modifier = Modifier.statusBarsPadding().padding(12.dp).size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.back)
                    }
                }
            }
        }

        item(key = "info") {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(top = 0.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(cleanTitle(name), fontSize = 32.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
                val meta = listOfNotNull(
                    series?.released?.take(4),
                    detail?.seasons?.size?.takeIf { it > 0 }?.let { t.vod.nSeasons(it) },
                    series?.genre,
                    ratingOf(series?.rating)?.let { "★ $it" }
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, fontSize = 14.sp, color = Kc.TextSecondary)

                if (detail != null && detail.episodes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val last = lastEpisode
                                if (last != null) start(last.first, last.second)
                                else detail.episodes.sortedWith(compareBy({ it.season }, { it.number }))
                                    .firstOrNull()?.let { start(it, 0L) }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                lastEpisode?.first?.let { m.resumeEpisode(it.season, it.number) } ?: m.startSeries,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        FavoriteButton(state.isFavorite, viewModel::toggleFavorite)
                    }
                }

                val plot = series?.plot
                if (!plot.isNullOrBlank()) {
                    var expanded by remember { mutableStateOf(false) }
                    Text(
                        plot,
                        fontSize = 14.sp,
                        color = Color(0xFFC9CFDC),
                        lineHeight = 20.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                }
            }
        }

        when {
            state.loading -> item(key = "loading") { Loading() }
            state.error != null -> item(key = "error") { EmptyHint(t.vod.noEpisodes, state.error) }
            detail == null || detail.episodes.isEmpty() -> item(key = "none") { EmptyHint(t.vod.noEpisodes) }
            else -> {
                item(key = "seasons") {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        ChipRow(detail.seasons, key = { it }) { season ->
                            KcChip(
                                label = t.vod.season(season),
                                selected = season == state.selectedSeason,
                                onClick = { viewModel.selectSeason(season) }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                items(state.episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        progress = continueList.firstOrNull { it.key == episode.resumeKey }?.progress,
                        label = episode.label(t.vod.episode),
                        onClick = { start(episode, viewModel.resumeMs(episode)) }
                    )
                }
                item(key = "autonext") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t.vod.autoNext, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.autoNext,
                            onCheckedChange = viewModel::setAutoNext,
                            colors = SwitchDefaults.colors(checkedTrackColor = Kc.Accent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, progress: Float?, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 120.dp, height = 68.dp).clip(RoundedCornerShape(10.dp))
                .background(placeholderColor(label)),
            contentAlignment = Alignment.Center
        ) {
            if (!episode.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = episode.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.9f))
            if (progress != null) {
                ThinProgress(
                    progress,
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                    track = Color.White.copy(alpha = 0.2f)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.durationSecs?.takeIf { it > 0 }?.let {
                Text(durationOf(it * 1000L), fontSize = 13.sp, color = Kc.TextSecondary)
            }
        }
        if (progress != null && progress >= 0.95f) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Kc.Live)
        }
    }
}
