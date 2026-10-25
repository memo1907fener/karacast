package com.safir.iptv.mobile

import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.itemsIndexed
import com.safir.iptv.ui.vod.MoviesViewModel
import com.safir.iptv.ui.vod.SeriesViewModel
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.safir.iptv.IptvApp
import com.safir.iptv.domain.model.ALL_CATEGORY_ID
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.domain.model.FAVORITES_CATEGORY_ID
import com.safir.iptv.domain.model.NowNext
import com.safir.iptv.domain.model.RECENT_CATEGORY_ID
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.channels.ChannelsViewModel
import com.safir.iptv.ui.i18n.LocalStrings

import com.safir.iptv.ui.vod.VodPlayback
import com.safir.iptv.util.buildPlayer
import com.safir.iptv.util.repairSilentAudio
import kotlinx.coroutines.delay

/**
 * Jetzt und Danach für eine Liste von Kanälen, jede Minute frisch.
 * Für die Startseite, die keinen eigenen ViewModel hat.
 */
@Composable
fun rememberNowNext(channels: List<Channel>): Map<String, NowNext> {
    val epg = (LocalContext.current.applicationContext as IptvApp).container.epgRepository
    val result by produceState(emptyMap<String, NowNext>(), channels) {
        while (true) {
            val ids = channels.mapNotNull { it.epgId }.filter { it.isNotBlank() }
            value = if (ids.isEmpty()) emptyMap() else runCatching { epg.nowNext(ids) }.getOrDefault(emptyMap())
            delay(60_000L)
        }
    }
    return result
}

fun NowNext?.progressNow(): Float = this?.now?.progressAt(System.currentTimeMillis()) ?: 0f

// ================================================================ Startseite

@Composable
fun MobileStartScreen(
    playingId: String?,
    onTapChannel: (Channel, String) -> Unit,
    onPlayVod: () -> Unit,
    onOpenTab: (MainTab) -> Unit,
    onOpenMovie: (com.safir.iptv.domain.model.Movie) -> Unit,
    onOpenSeries: (Int) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val container = (LocalContext.current.applicationContext as IptvApp).container

    val resume by container.settings.continueWatching.collectAsStateWithLifecycle()
    val recentFlow = remember { container.playlistRepository.observeChannels(RECENT_CATEGORY_ID) }
    val favoriteFlow = remember { container.playlistRepository.observeChannels(FAVORITES_CATEGORY_ID) }
    val recent by recentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by favoriteFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val nowNext = rememberNowNext(remember(recent, favorites) { (recent + favorites).distinctBy { it.id } })

    // Dieselben ViewModels wie in den Reitern Filme und Serien: was hier geladen
    // wird, steht dort schon bereit, und ein Tipp auf ein Plakat öffnet es dort.
    val movies: MoviesViewModel = viewModel(factory = AppViewModelFactory)
    val series: SeriesViewModel = viewModel(factory = AppViewModelFactory)
    val movieState by movies.state.collectAsStateWithLifecycle()
    val seriesState by series.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandTitle()
            Spacer(Modifier.weight(1f))
            RoundIconButton(Icons.Filled.Search, t.search, onSearch)
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Filled.Settings, t.settings, onOpenSettings)
        }

        // Ein Gruß nach Tageszeit — macht die Startseite freundlicher als eine Leiste.
        val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
        val greeting = m.greetings[
            when (hour) {
                in 5..10 -> 0
                in 11..17 -> 1
                in 18..22 -> 2
                else -> 3
            }
        ]
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(greeting, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(m.greetingHint, fontSize = 15.sp, color = Kc.TextSecondary)
        }

        // Vier Wege hinein, groß und farbig — wer die App zum ersten Mal öffnet,
        // sieht sofort, was es gibt.
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(t.liveTv, Icons.Filled.LiveTv, TileLive, Modifier.weight(1f)) { onOpenTab(MainTab.LIVE) }
                QuickTile(t.movies, Icons.Filled.Movie, TileMovies, Modifier.weight(1f)) { onOpenTab(MainTab.MOVIES) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(t.series, Icons.Filled.VideoLibrary, TileSeries, Modifier.weight(1f)) { onOpenTab(MainTab.SERIES) }
                QuickTile(t.guide, Icons.Filled.DateRange, TileGuide, Modifier.weight(1f)) { onOpenTab(MainTab.GUIDE) }
            }
        }

        if (resume.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(t.vod.continueWatching)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(resume, key = { it.key }) { item ->
                        ResumeCard(item) {
                            VodPlayback.queue = emptyList()
                            VodPlayback.pending = item.toRequest()
                            onPlayVod()
                        }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(t.recentlyWatched, m.seeAll) { onOpenTab(MainTab.LIVE) }
                ChannelCardRow(recent.take(20), nowNext, playingId) { onTapChannel(it, RECENT_CATEGORY_ID) }
            }
        }

        if (favorites.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(t.favorites, m.seeAll) { onOpenTab(MainTab.LIVE) }
                ChannelCardRow(favorites, nowNext, playingId) { onTapChannel(it, FAVORITES_CATEGORY_ID) }
            }
        }

        if (movieState.movies.isNotEmpty()) {
            val name = movieState.categories.firstOrNull { it.id == movieState.selectedCategoryId }?.name
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(cleanTitle(name ?: t.movies), m.seeAll) { onOpenTab(MainTab.MOVIES) }
                PosterRow(movieState.movies.take(20).map { Triple("m${it.id}", it.name, it.posterUrl) }) { index ->
                    onOpenMovie(movieState.movies[index])
                }
            }
        }

        if (seriesState.series.isNotEmpty()) {
            val name = seriesState.categories.firstOrNull { it.id == seriesState.selectedCategoryId }?.name
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(cleanTitle(name ?: t.series), m.seeAll) { onOpenTab(MainTab.SERIES) }
                PosterRow(seriesState.series.take(20).map { Triple("s${it.id}", it.name, it.posterUrl) }) { index ->
                    onOpenSeries(seriesState.series[index].id)
                }
            }
        }

        if (resume.isEmpty() && recent.isEmpty() && favorites.isEmpty()) {
            EmptyHint(m.emptyStartTitle, m.emptyStart)
        }
    }
}

private val TileLive = Brush.linearGradient(listOf(Color(0xFFFF9A2E), Color(0xFFFF4D1A)))
private val TileMovies = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF5B21B6)))
private val TileSeries = Brush.linearGradient(listOf(Color(0xFF14B8A6), Color(0xFF0E7490)))
private val TileGuide = Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1E3A8A)))

@Composable
private fun QuickTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    brush: Brush,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.95f),
            modifier = Modifier.size(26.dp).align(Alignment.TopStart)
        )
        Text(
            label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

/** Eine Reihe Plakate (Schlüssel, Titel, Bild); gemeldet wird die Stelle. */
@Composable
private fun PosterRow(items: List<Triple<String, String, String?>>, onTap: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.first }) { index, item ->
            Column(
                Modifier.width(112.dp).clip(RoundedCornerShape(12.dp)).clickable { onTap(index) },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Poster(item.third, cleanTitle(item.second), Modifier.fillMaxWidth().height(168.dp))
                Text(
                    cleanTitle(item.second),
                    fontSize = 12.sp,
                    color = Kc.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = Kc.Surface,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ResumeCard(item: ContinueItem, onClick: () -> Unit) {
    Column(
        Modifier.width(262.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(147.dp).clip(RoundedCornerShape(14.dp))) {
            Poster(item.posterUrl, item.title, Modifier.fillMaxSize(), corner = 14.dp, titleSize = 20)
            Box(Modifier.fillMaxSize().background(BottomScrim))
            Box(
                Modifier.align(Alignment.Center).size(46.dp).clip(RoundedCornerShape(23.dp))
                    .background(Kc.Background.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            }
            ThinProgress(
                item.progress,
                Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                track = Color.White.copy(alpha = 0.18f),
                height = 4.dp
            )
        }
        Text(
            text = listOf(cleanTitle(item.title), item.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
            fontSize = 13.sp,
            color = Kc.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChannelCardRow(
    channels: List<Channel>,
    nowNext: Map<String, NowNext>,
    playingId: String?,
    onTap: (Channel) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(channels, key = { it.id }) { channel ->
            val info = channel.epgId?.let { nowNext[it] }
            val playing = channel.id == playingId
            Column(
                Modifier
                    .width(150.dp)
                    .height(112.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (playing) Kc.SurfaceHigh else Kc.Surface)
                    .clickable { onTap(channel) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelBadge(channel.logoUrl, channel.name, Modifier.size(width = 44.dp, height = 28.dp), corner = 7.dp)
                    Spacer(Modifier.weight(1f))
                    if (playing) {
                        LiveDot()
                        Spacer(Modifier.width(5.dp))
                        Text(LocalStrings.current.live, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Kc.Live)
                    }
                }
                Text(
                    cleanTitle(channel.name),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (playing) Kc.AccentSoft else Kc.TextPrimary
                )
                Text(
                    info?.now?.title.orEmpty(),
                    fontSize = 12.sp,
                    color = Kc.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ThinProgress(info.progressNow(), Modifier.fillMaxWidth())
            }
        }
    }
}

// ================================================================== Live TV

@Composable
fun MobileLiveScreen(
    playingId: String?,
    startInSearch: Boolean,
    onSearchHandled: () -> Unit,
    onTapChannel: (Channel, String) -> Unit
) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val viewModel: ChannelsViewModel = viewModel(factory = AppViewModelFactory)
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val selected by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val nowNext by viewModel.nowNext.collectAsStateWithLifecycle()

    var searching by remember { mutableStateOf(startInSearch) }
    LaunchedEffect(startInSearch) {
        if (startInSearch) {
            searching = true
            onSearchHandled()
        }
    }

    val chipState = rememberLazyListState()
    // Die gewählte Gruppe ins Bild holen, wenn der Reiter (wieder) aufgeht.
    LaunchedEffect(categories.isNotEmpty()) {
        val index = categories.indexOfFirst { it.id == selected }
        if (index > 0) chipState.scrollToItem((index - 1).coerceAtLeast(0))
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.scrollToItem(0) }

    val total = categories.firstOrNull { it.id == ALL_CATEGORY_ID }?.channelCount ?: 0
    val playCategory = if (query.isNotBlank()) ALL_CATEGORY_ID else selected

    Column(Modifier.fillMaxSize()) {
        if (searching) {
            SearchBar(
                query = query,
                placeholder = t.searchChannel,
                onQuery = viewModel::setQuery,
                onClose = {
                    viewModel.setQuery("")
                    searching = false
                }
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t.liveTv, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(10.dp))
                if (total > 0) Text(m.nChannels(total), fontSize = 14.sp, color = Kc.TextSecondary)
                Spacer(Modifier.weight(1f))
                RoundIconButton(Icons.Filled.Search, t.searchChannel) { searching = true }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (!searching || query.isBlank()) {
            ChipRow(categories, key = { it.id }, state = chipState) { category ->
                KcChip(
                    label = cleanTitle(category.name),
                    selected = category.id == selected,
                    count = category.channelCount.takeIf { it > 0 },
                    onClick = { viewModel.selectCategory(category.id) }
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        // Der Hinweis nur, solange noch nichts läuft — danach weiß man es.
        if (playingId == null) {
            Text(
                m.tapHint,
                fontSize = 12.sp,
                color = Kc.AccentSoft,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))

        if (channels.isEmpty()) {
            EmptyHint(t.noChannels)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(channels, key = { it.id }) { channel ->
                    // Viele Anbieter setzen Trennzeilen wie „##### ULUSAL #####“
                    // als Kanal in die Liste. Das ist eine Überschrift, kein Sender.
                    if (isSeparator(channel.name)) {
                        Text(
                            channel.name.trim(' ', '#', '=', '*', '~', '-', '_', '|').trim(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Kc.AccentSoft,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp)
                        )
                        return@items
                    }
                    ChannelRow(
                        channel = channel,
                        info = channel.epgId?.let { nowNext[it] },
                        playing = channel.id == playingId,
                        onTap = { onTapChannel(channel, playCategory) },
                        onToggleFavorite = { viewModel.toggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    placeholder: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = LocalStrings.current.back)
        }
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(placeholder) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = LocalMobileStrings.current.close)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = RoundedCornerShape(23.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Kc.Surface,
                unfocusedContainerColor = Kc.Surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f).focusRequester(focus)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: Channel,
    info: NowNext?,
    playing: Boolean,
    onTap: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (playing) Kc.SurfaceHigh else Kc.Surface.copy(alpha = 0.7f))
            .then(
                if (playing) Modifier.border(1.5.dp, Kc.Accent, RoundedCornerShape(14.dp)) else Modifier
            )
            .combinedClickable(onClick = onTap, onLongClick = onToggleFavorite)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            channel.number.takeIf { it > 0 }?.toString().orEmpty(),
            fontSize = 13.sp,
            color = Kc.TextSecondary,
            modifier = Modifier.width(30.dp),
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        ChannelBadge(channel.logoUrl, channel.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    LiveDot()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    cleanTitle(channel.name),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (playing) Kc.AccentSoft else Kc.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val now = info?.now
            if (now != null) {
                Text(
                    "${clockOf(now.startMs)} · ${now.title}",
                    fontSize = 13.sp,
                    color = Kc.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ThinProgress(info.progressNow(), Modifier.fillMaxWidth())
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (channel.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = LocalStrings.current.favorite,
                tint = if (channel.isFavorite) Kc.Accent else Kc.TextSecondary.copy(alpha = 0.6f)
            )
        }
    }
}

/** Eine Trennzeile des Anbieters („##### SPORT #####“) statt eines Senders. */
fun isSeparator(name: String): Boolean = SeparatorPattern.matches(name.trim())

private val SeparatorPattern = Regex("""^[#=*~\-_|]{3,}.*[#=*~\-_|]{3,}$|^[#=*~]{3,}.*""")

// =============================================================== Mini-Player

/**
 * Der kleine Player über der Leiste. Spielt mit Ton — er ist die Vorschau, die
 * am Fernseher rechts neben der Liste läuft. Ein Tipp darauf öffnet das Vollbild.
 */
@Composable
fun MiniPlayer(
    item: MiniPlayback.Item,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val m = LocalMobileStrings.current
    val context = LocalContext.current
    val container = remember { (context.applicationContext as IptvApp).container }
    val lifecycleOwner = LocalLifecycleOwner.current
    val channel = item.channel

    val player = remember {
        // Kurzer Puffer wie bei der Vorschau am Fernseher. Achtung: Media3 verlangt
        // bufferAfterRebufferMs ≤ minBufferMs, sonst stürzt der Aufbau ab.
        buildPlayer(
            context,
            minBufferMs = 2_000,
            maxBufferMs = 10_000,
            bufferForPlaybackMs = 800,
            bufferAfterRebufferMs = 1_500
        ).apply { playWhenReady = true }
    }
    var paused by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                if (runCatching { player.repairSilentAudio() }.getOrDefault(false)) {
                    runCatching { player.prepare(); player.play() }
                } else {
                    failed = true
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                runCatching { player.repairSilentAudio() }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            runCatching { player.release() }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> if (!paused) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(channel.streamUrl, retry) {
        failed = false
        paused = false
        if (retry > 0) delay(1_500)
        val url = channel.streamUrl
        player.setMediaItem(
            MediaItem.Builder().setUri(url).apply {
                if (url.substringBefore('?').endsWith(".m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8)
            }.build()
        )
        player.prepare()
        player.play()
        // Ab jetzt ist das der zuletzt gesehene Kanal — wie am Fernseher.
        runCatching { container.playlistRepository.markWatched(channel.id) }
    }

    val info by produceState<NowNext?>(null, channel.id) {
        val id = channel.epgId
        value = if (id.isNullOrBlank()) null else runCatching {
            container.epgRepository.nowNext(listOf(id))[id]
        }.getOrNull()
    }

    Surface(
        onClick = onExpand,
        shape = RoundedCornerShape(14.dp),
        color = Kc.SurfaceHigh,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).height(64.dp)
    ) {
        Box {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(width = 86.dp, height = 48.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                this.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (buffering && !failed) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kc.Accent)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(cleanTitle(channel.name), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        info?.now?.title.orEmpty(),
                        fontSize = 12.sp,
                        color = Kc.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (failed) {
                    IconButton(onClick = { retry++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = LocalStrings.current.retry)
                    }
                } else {
                    IconButton(onClick = {
                        paused = !paused
                        if (paused) player.pause() else player.play()
                    }) {
                        Icon(
                            if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (paused) m.play else m.pause
                        )
                    }
                }
                Surface(
                    onClick = onExpand,
                    shape = RoundedCornerShape(20.dp),
                    color = Kc.Accent,
                    contentColor = Color(0xFF101010),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = m.fullscreen)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = m.close, tint = Kc.TextSecondary)
                }
            }
            ThinProgress(
                info.progressNow(),
                Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                height = 2.dp
            )
        }
    }
}
