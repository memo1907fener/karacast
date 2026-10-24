package com.safir.iptv.ui.channels

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.safir.iptv.domain.model.ALL_CATEGORY_ID
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.FAVORITES_CATEGORY_ID
import com.safir.iptv.domain.model.NowNext
import com.safir.iptv.domain.model.RECENT_CATEGORY_ID
import com.safir.iptv.ui.components.ChannelLogo
import com.safir.iptv.ui.components.EmptyState
import com.safir.iptv.ui.components.LiveBadge
import com.safir.iptv.ui.components.NumberEntryOverlay
import com.safir.iptv.ui.components.ProgressLine
import com.safir.iptv.ui.components.TvTextField
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.components.asDigit
import com.safir.iptv.ui.components.rememberNumberEntry
import com.safir.iptv.ui.components.submitNow
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.asClock
import com.safir.iptv.util.buildPlayer
import com.safir.iptv.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wie lange ein Kanal unter dem Fokus stehen muss, bevor die Vorschau eine
 * Verbindung aufmacht. Etwas kürzer als in der Zwei-Ebenen-Ansicht: hier ist die
 * Vorschau der Grund, warum man die Ansicht gewählt hat.
 */
private const val PREVIEW_DELAY_MS = 550L

/**
 * Wie lange man auf einer Gruppe stehen bleiben muss, bis die Kanalliste ihr folgt.
 * Kurz genug, dass es sich sofort anfühlt — lang genug, dass schnelles Durchblättern
 * nicht jede überflogene Gruppe aus der Datenbank lädt.
 */
private const val GROUP_FOLLOW_MS = 220L

/** Nach dem Öffnen: das Loslassen von OK aus dem vorigen Bildschirm verwerfen. */
private const val SELECT_GUARD_MS = 600L

/** Welche Spalte gerade den Fokus hat — entscheidet, wohin Zurück führt. */
private enum class Pane { TABS, GROUPS, CHANNELS, DETAIL }

/**
 * Live TV in drei Spalten, wie man es von vielen IPTV-Boxen kennt:
 *
 *     Gruppen │ Kanäle │ Vorschau + Sendung + Knöpfe
 *
 * Der Unterschied zur Zwei-Ebenen-Ansicht ist nicht die Optik, sondern der Weg:
 * dort geht man in eine Gruppe hinein und wieder heraus. Hier folgt die Kanalliste
 * der Gruppe, auf der der Fokus steht — ▲▼ in der linken Spalte, und daneben steht
 * schon die neue Liste. ▶ führt in die Kanäle, ◀ zurück. Die Vorschau läuft weiter,
 * während man die Gruppen durchgeht: sie zeigt, was man zuletzt angesehen hat,
 * nicht den ersten Kanal jeder Gruppe, über die man hinwegscrollt — das würde bei
 * jedem Tastendruck eine Verbindung zum Anbieter auf- und wieder abbauen.
 */
@Composable
internal fun ColumnsChannelsScreen(
    viewModel: ChannelsViewModel,
    onPlay: (channel: Channel, categoryId: String) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackToDashboard: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    startInSearch: Boolean
) {
    val s = LocalStrings.current
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val nowNextMap by viewModel.nowNext.collectAsStateWithLifecycle()
    val focusTargetId by viewModel.focusTargetId.collectAsStateWithLifecycle()
    val numberMiss by viewModel.numberMiss.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val numbers = rememberNumberEntry { number -> viewModel.jumpToNumber(number) }
    val openedAt = remember { System.currentTimeMillis() }

    var pane by remember { mutableStateOf(Pane.GROUPS) }
    var anyPaneFocused by remember { mutableStateOf(false) }
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var hoveredCategory by remember { mutableStateOf<String?>(null) }

    val groupsState = rememberLazyListState()
    val groupsRequester = remember { FocusRequester() }

    // ------------------------------------------------------------ first focus
    var searchOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startInSearch && !searchOpened) {
            searchOpened = true
            viewModel.enterSearch()
        } else {
            viewModel.enterColumns()
        }
    }

    // Holt den Fokus auf die gewählte Gruppe — beim Öffnen, wenn kein zuletzt
    // gesehener Kanal in der Liste steht, und mit Zurück aus den anderen Spalten.
    suspend fun focusGroups() {
        // Direkt aus dem ViewModel gelesen, nicht aus dem Compose-Zustand: nach
        // „Suche verlassen“ ist die Gruppe dort schon zurückgestellt, auf dem
        // Bildschirm aber erst ein Bild später.
        var selected = viewModel.selectedCategoryId.value
        var index = viewModel.categories.value.indexOfFirst { it.id == selected }
        if (index < 0 && viewModel.categories.value.isNotEmpty()) {
            // Die gemerkte Gruppe gibt es nicht mehr (anderer Anbieter, Gruppe
            // gelöscht): dann trägt keine Zeile den Fokus-Anker, und alle Versuche
            // gingen ins Leere. Also auf „Alle Kanäle“ ausweichen.
            viewModel.selectCategory(ALL_CATEGORY_ID)
            selected = ALL_CATEGORY_ID
            index = viewModel.categories.value.indexOfFirst { it.id == selected }
        }
        if (index >= 0) groupsState.scrollToItem(index)
        for (attempt in 0 until 10) {
            delay(50)
            if (runCatching { groupsRequester.requestFocus() }.isSuccess) break
        }
    }

    // Auffangnetz: hat nach dem Aufbau keine Spalte den Fokus — der zuletzt
    // gesehene Kanal steht nicht in dieser Gruppe, oder es gab keinen —, dann
    // bekommt ihn die Gruppenliste. Ohne Fokus wirkte die Fernbedienung tot.
    LaunchedEffect(categories.isNotEmpty()) {
        if (categories.isEmpty()) return@LaunchedEffect
        delay(900)
        if (!anyPaneFocused && !viewModel.searchMode.value) focusGroups()
    }

    // Die Kanalliste folgt der Gruppe unter dem Fokus — mit einer kleinen Pause,
    // damit schnelles Durchblättern nicht jede Gruppe einzeln lädt.
    //
    // Nur solange der Fokus in den Gruppen steht: wer schneller als die Pause ▶
    // drückt, steht schon in der alten Liste — sie dann noch auszutauschen, risse
    // ihm die Zeile unter dem Fokus weg. Und weil die Spalte mit im Schlüssel steht,
    // greift die Regel auch, wenn man aus der Suche auf dieselbe Gruppe zurückkehrt.
    val inGroups = pane == Pane.GROUPS
    LaunchedEffect(hoveredCategory, inGroups) {
        if (!inGroups) return@LaunchedEffect
        val id = hoveredCategory ?: return@LaunchedEffect
        if (id == selectedCategory && !searchMode) return@LaunchedEffect
        delay(GROUP_FOLLOW_MS)
        // Inzwischen schon gewählt (OK war schneller als die Pause)? Dann nichts
        // anfassen — sonst löschte das hier das Fokusziel, das OK gerade gesetzt hat.
        if (id == viewModel.selectedCategoryId.value && !viewModel.searchMode.value) {
            return@LaunchedEffect
        }
        // Ein liegengebliebenes Fokusziel (der zuletzt gesehene Kanal, der in der
        // vorigen Gruppe nicht vorkam) würde sonst später den Fokus aus den
        // Gruppen in die Kanalliste reißen.
        viewModel.consumeFocusTarget()
        viewModel.leaveSearch()
        viewModel.selectCategory(id)
    }

    // OK auf einer Gruppe: auswählen und hinüber zum ersten Kanal, sobald die Liste da ist.
    var jumpToChannels by remember { mutableStateOf(false) }
    LaunchedEffect(jumpToChannels, channels.firstOrNull()?.id) {
        if (!jumpToChannels) return@LaunchedEffect
        val first = channels.firstOrNull() ?: return@LaunchedEffect
        jumpToChannels = false
        viewModel.focusChannel(first.id)
    }

    // Zurück geht immer eine Spalte nach links; erst aus den Gruppen heraus
    // verlässt es den Bildschirm.
    BackHandler {
        when {
            numbers.isActive -> numbers.clear()
            searchMode -> {
                viewModel.leaveSearch()
                scope.launch { focusGroups() }
            }
            pane != Pane.GROUPS -> scope.launch { focusGroups() }
            else -> onBackToDashboard()
        }
    }

    LaunchedEffect(numberMiss) {
        if (numberMiss != null) {
            delay(2_000)
            viewModel.clearNumberMiss()
        }
    }

    // ---------------------------------------------------------- preview player
    val context = LocalContext.current
    val previewEnabled = remember { viewModel.previewEnabled }
    val previewMuted = remember { viewModel.previewMuted }
    var previewFailed by remember { mutableStateOf(false) }

    val previewPlayer = remember {
        if (!previewEnabled) {
            null
        } else {
            buildPlayer(
                context,
                minBufferMs = 2_000,
                maxBufferMs = 10_000,
                bufferForPlaybackMs = 800,
                bufferAfterRebufferMs = 1_500
            ).apply {
                playWhenReady = true
                volume = if (previewMuted) 0f else 1f
            }
        }
    }

    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                previewFailed = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) previewFailed = false
            }
        }
        previewPlayer?.addListener(listener)
        onDispose {
            previewPlayer?.removeListener(listener)
            // Gibt den Verbindungsplatz beim Anbieter sofort wieder frei.
            previewPlayer?.release()
        }
    }

    val lastWatchedId = remember { viewModel.lastChannelId }
    // Der zuletzt gesehene Kanal, einmal gefunden, bleibt gemerkt: blättert man
    // durch Gruppen, in denen er nicht vorkommt, läuft die Vorschau trotzdem weiter,
    // statt bei jeder Gruppe ab- und wieder aufzubauen.
    var lastWatched by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(channels) {
        channels.firstOrNull { it.id == lastWatchedId }?.let { lastWatched = it }
    }
    // Aus der aktuellen Liste nachgeschlagen, wo es geht: der gemerkte Kanal ist
    // eine Momentaufnahme, und ohne das bliebe nach „Favorit“ der Stern leer.
    val previewTarget: Channel? =
        focusedChannel?.let { picked -> channels.firstOrNull { it.id == picked.id } ?: picked }
            ?: channels.firstOrNull { it.id == lastWatchedId }
            ?: lastWatched

    LaunchedEffect(previewTarget?.id) {
        val player = previewPlayer ?: return@LaunchedEffect
        player.stop()
        previewFailed = false
        val channel = previewTarget ?: return@LaunchedEffect
        delay(PREVIEW_DELAY_MS)
        val url = channel.streamUrl
        val item = MediaItem.Builder()
            .setUri(url)
            .apply {
                if (url.substringBefore('?').endsWith(".m3u8", true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    val playCategory = if (searchMode) ALL_CATEGORY_ID else selectedCategory

    // ------------------------------------------------------------------ layout
    Box(
        Modifier
            .fillMaxSize()
            .background(Look.Page)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (isSelect &&
                    !numbers.isActive &&
                    System.currentTimeMillis() - openedAt < SELECT_GUARD_MS
                ) {
                    return@onPreviewKeyEvent true
                }
                val digit = event.key.asDigit()
                when {
                    digit != null && !searchMode -> {
                        numbers.append(digit)
                        true
                    }

                    numbers.isActive && isSelect ->
                        numbers.submitNow { viewModel.jumpToNumber(it) }

                    else -> false
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {

            TopBar(
                query = query,
                searchMode = searchMode,
                onHome = onBackToDashboard,
                onMovies = onOpenMovies,
                onSeries = onOpenSeries,
                onGuide = onOpenGuide,
                // Ein zweites „Suche“ darf das schon Getippte nicht löschen.
                onSearch = { if (!searchMode) viewModel.enterSearch() },
                onSettings = onOpenSettings,
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) {
                        pane = Pane.TABS
                        anyPaneFocused = true
                    }
                }
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(Look.Separator))

            Row(Modifier.weight(1f).fillMaxWidth()) {
                // -------------------------------------------------- 1: Gruppen
                GroupColumn(
                    categories = categories,
                    selectedId = if (searchMode) null else selectedCategory,
                    state = groupsState,
                    selectedRequester = groupsRequester,
                    onFocusCategory = { hoveredCategory = it },
                    onOpenCategory = { id ->
                        // OK auf einer Gruppe: sofort folgen und hinüber in die Kanäle.
                        viewModel.consumeFocusTarget()
                        viewModel.leaveSearch()
                        viewModel.selectCategory(id)
                        jumpToChannels = true
                    },
                    modifier = Modifier
                        .weight(0.225f)
                        .fillMaxHeight()
                        .onFocusChanged {
                            if (it.hasFocus) {
                                pane = Pane.GROUPS
                                anyPaneFocused = true
                            }
                        }
                )

                ColumnSeparator()

                // -------------------------------------------------- 2: Kanäle
                Column(
                    Modifier
                        .weight(0.275f)
                        .fillMaxHeight()
                ) {
                    if (searchMode) {
                        TvTextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            label = s.searchChannel,
                            placeholder = s.typeName,
                            autoFocus = true,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)
                        )
                    }
                    if (channels.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            EmptyState(title = s.noChannels, message = s.emptyList)
                        }
                    } else {
                        ColumnChannelList(
                            channels = channels,
                            focusTargetId = focusTargetId,
                            onFocusConsumed = viewModel::consumeFocusTarget,
                            previewingId = previewTarget?.id,
                            showNumbers = selectedCategory != RECENT_CATEGORY_ID && !searchMode,
                            onPlay = { channel -> onPlay(channel, playCategory) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onFocusChannel = { focusedChannel = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onFocusChanged {
                                    if (it.hasFocus) {
                                        pane = Pane.CHANNELS
                                        anyPaneFocused = true
                                    }
                                }
                        )
                    }
                }

                ColumnSeparator()

                // -------------------------------------- 3: Vorschau und Knöpfe
                DetailColumn(
                    channel = previewTarget,
                    nowNext = previewTarget?.epgId?.let { nowNextMap[it] },
                    previewPlayer = previewPlayer,
                    previewFailed = previewFailed,
                    onGuide = onOpenGuide,
                    onToggleFavorite = { previewTarget?.let(viewModel::toggleFavorite) },
                    onSearch = { if (!searchMode) viewModel.enterSearch() },
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                        .onFocusChanged {
                            if (it.hasFocus) {
                                pane = Pane.DETAIL
                                anyPaneFocused = true
                            }
                        }
                )
            }
        }

        NumberEntryOverlay(
            state = numbers,
            caption = s.jumpNow,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 72.dp, start = 28.dp)
        )

        numberMiss?.let { message ->
            if (!numbers.isActive) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 72.dp, start = 28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message,
                        color = Brand.Danger,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// =================================================================== Farben

/**
 * Das zweite Design hat seine eigenen Farben: schwarze Kopfzeile, dunkle Kacheln
 * mit einem violetten Schimmer, Gelb für das, was gewählt ist, und ein violett-
 * magentafarbener Verlauf unter der Vorschau. Absichtlich nur hier — der Rest der
 * App bleibt in Karacast-Orange.
 */
private object Look {
    val TopBar = Color(0xFF000000)
    val Page = Color(0xFF14121B)
    val Separator = Color(0xFF4A4856)
    val TileTop = Color(0xFF2C2B32)
    val TileBottom = Color(0xFF3B2F52)
    val TileFocusTop = Color(0xFF45434F)
    val TileFocusBottom = Color(0xFF5E4888)
    val TileEdge = Color(0xFF1C1B22)
    val Highlight = Color(0xFFFFD84D)
    val Text = Color(0xFFF2F2F5)
    val TextDim = Color(0xFFC9C5D8)
    val PanelStart = Color(0xFF14163A)
    val PanelMid = Color(0xFF4E1764)
    val PanelEnd = Color(0xFF26104C)
    val Button = Color(0xFF6E62A6)
    val Logo = Color(0xFF9B59D0)
}

// ================================================================== Kopfzeile

/**
 * Oben: Start | Live TV | Filme | Serien | Programm, das Suchfeld, rechts Uhr und
 * Schriftzug. Live TV ist der Reiter, auf dem man steht — gelb und nicht anwählbar.
 */
@Composable
private fun TopBar(
    query: String,
    searchMode: Boolean,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onGuide: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    Row(
        modifier
            .fillMaxWidth()
            .background(Look.TopBar)
            .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopTab(label = s.home, onClick = onHome)
        TabSeparator()
        Text(
            text = s.liveTv,
            color = Look.Highlight,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        TabSeparator()
        TopTab(label = s.movies, onClick = onMovies)
        TabSeparator()
        TopTab(label = s.series, onClick = onSeries)
        TabSeparator()
        TopTab(label = s.guide, onClick = onGuide)
        Spacer(Modifier.width(18.dp))
        Box(Modifier.weight(1f)) {
            SearchPill(
                text = if (searchMode) query else "",
                onClick = onSearch,
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()
            )
        }
        Spacer(Modifier.width(12.dp))
        TopIcon(icon = Icons.Default.Settings, description = s.settings, onClick = onSettings)
        Spacer(Modifier.width(12.dp))
        Clock()
        Spacer(Modifier.width(14.dp))
        Text(
            text = "KARACAST",
            color = Look.Logo,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun TopTab(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Look.Highlight,
            pressedContainerColor = Look.Highlight,
            contentColor = Look.Text,
            focusedContentColor = Color.Black,
            pressedContentColor = Color.Black
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.alsoTappable(onClick)
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TabSeparator() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(2.dp)
            .height(26.dp)
            .background(Look.Separator)
    )
}

@Composable
private fun TopIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Look.Highlight,
            pressedContainerColor = Look.Highlight,
            contentColor = Look.TextDim,
            focusedContentColor = Color.Black,
            pressedContentColor = Color.Black
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.alsoTappable(onClick)
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.padding(6.dp).size(20.dp))
    }
}

/** Das Suchfeld oben. Ein Druck darauf öffnet die Suche über der Kanalliste. */
@Composable
private fun SearchPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Look.TileFocusBottom,
            pressedContainerColor = Look.TileFocusBottom,
            contentColor = Look.Text,
            focusedContentColor = Look.Text,
            pressedContentColor = Look.Text
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(2.dp, Look.TextDim), shape = shape),
            focusedBorder = Border(border = BorderStroke(2.dp, Look.Highlight), shape = shape)
        ),
        modifier = modifier.alsoTappable(onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ColumnSeparator() {
    Box(
        Modifier
            .width(3.dp)
            .fillMaxHeight()
            .background(Look.Separator)
    )
}

// ==================================================================== Kachel

/**
 * Eine Zeile als Kachel: dunkel mit violettem Schimmer nach unten rechts. Gewählt
 * heißt gelbe Schrift; der Fokus macht die Kachel heller und gibt ihr einen gelben
 * Rand — so bleiben „gewählt“ und „hier steht die Fernbedienung“ unterscheidbar.
 */
@Composable
private fun Tile(
    onClick: () -> Unit,
    highlighted: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.(Color) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(3.dp)
    val textColor = if (focused || highlighted) Look.Highlight else Look.Text
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
            contentColor = textColor,
            focusedContentColor = textColor,
            pressedContentColor = textColor
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, Look.TileEdge), shape = shape),
            focusedBorder = Border(border = BorderStroke(2.dp, Look.Highlight), shape = shape)
        ),
        modifier = modifier
            .fillMaxWidth()
            .alsoTappable(onClick = onClick, onLongClick = onLongClick)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        if (focused) {
                            listOf(Look.TileFocusTop, Look.TileFocusBottom)
                        } else {
                            listOf(Look.TileTop, Look.TileBottom)
                        }
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(textColor)
        }
    }
}

// ================================================================ 1: Gruppen

@Composable
private fun GroupColumn(
    categories: List<Category>,
    selectedId: String?,
    state: LazyListState,
    selectedRequester: FocusRequester,
    onFocusCategory: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = state,
        modifier = modifier.focusRestorer(),
        contentPadding = PaddingValues(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            val selected = category.id == selectedId
            val virtualIcon = when (category.id) {
                FAVORITES_CATEGORY_ID, RECENT_CATEGORY_ID, ALL_CATEGORY_ID -> category.icon()
                else -> null
            }
            Tile(
                onClick = { onOpenCategory(category.id) },
                highlighted = selected,
                onFocused = { onFocusCategory(category.id) },
                modifier = if (selected) Modifier.focusRequester(selectedRequester) else Modifier
            ) { color ->
                if (virtualIcon != null) {
                    Icon(
                        virtualIcon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = category.name,
                    color = color,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (category.channelCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(text = "${category.channelCount}", color = color, fontSize = 16.sp)
                }
            }
        }
    }
}

// ================================================================= 2: Kanäle

@Composable
private fun ColumnChannelList(
    channels: List<Channel>,
    focusTargetId: String?,
    onFocusConsumed: () -> Unit,
    previewingId: String?,
    showNumbers: Boolean,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onFocusChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val jumpRequester = remember { FocusRequester() }

    // Eine andere Gruppe fängt oben an — außer ein Kanal soll gleich den Fokus
    // bekommen, dann würde der Sprung nach oben nur dagegen arbeiten.
    LaunchedEffect(channels.firstOrNull()?.id) {
        if (focusTargetId == null) listState.scrollToItem(0)
    }

    // Nummerneingabe und der Rückweg aus dem Vollbild landen hier: Zeile ins Bild
    // holen, dann den Fokus darauf — wie in der Zwei-Ebenen-Ansicht.
    LaunchedEffect(focusTargetId, channels.isEmpty()) {
        val id = focusTargetId ?: return@LaunchedEffect
        if (channels.isEmpty()) return@LaunchedEffect
        val index = channels.indexOfFirst { it.id == id }
        if (index < 0) {
            onFocusConsumed()
            return@LaunchedEffect
        }
        listState.scrollToItem(index)
        for (attempt in 0 until 10) {
            delay(50)
            if (runCatching { jumpRequester.requestFocus() }.isSuccess) break
        }
        delay(500)
        onFocusConsumed()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.focusRestorer(),
        contentPadding = PaddingValues(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(channels, key = { it.id }) { channel ->
            val s = LocalStrings.current
            Tile(
                onClick = { onPlay(channel) },
                onLongClick = { onToggleFavorite(channel) },
                highlighted = channel.id == previewingId,
                onFocused = { onFocusChannel(channel) },
                modifier = if (channel.id == focusTargetId) {
                    Modifier.focusRequester(jumpRequester)
                } else {
                    Modifier
                }
            ) { color ->
                if (showNumbers) {
                    Text(
                        text = channel.number.toString(),
                        color = color,
                        fontSize = 16.sp,
                        maxLines = 1,
                        modifier = Modifier.width(52.dp)
                    )
                }
                ChannelLogo(
                    url = channel.logoUrl,
                    name = channel.name,
                    modifier = Modifier.size(34.dp, 24.dp),
                    cornerRadius = 4
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = channel.name,
                    color = color,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (channel.isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = s.favorite,
                        tint = Look.Highlight,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ======================================================= 3: Vorschau, Knöpfe

@Composable
private fun DetailColumn(
    channel: Channel?,
    nowNext: NowNext?,
    // Anders benannt als PlayerView.player: in `apply` gewänne sonst das Mitglied
    // gegen den Parameter, und die Ansicht bekäme sich selbst zugewiesen.
    previewPlayer: ExoPlayer?,
    previewFailed: Boolean,
    onGuide: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    Column(
        modifier.background(
            Brush.linearGradient(listOf(Look.PanelStart, Look.PanelMid, Look.PanelEnd))
        )
    ) {
        // Das Bild randlos oben, wie auf einer Box.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (previewPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            isFocusable = false
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = previewPlayer
                        }
                    },
                    update = { it.player = previewPlayer },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (channel != null && (previewPlayer == null || previewFailed)) {
                ChannelLogo(
                    url = channel.logoUrl,
                    name = channel.name,
                    modifier = Modifier.size(96.dp, 64.dp)
                )
            }
            if (channel == null) {
                Text(
                    text = s.layout.pickChannel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Look.TextDim,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (channel != null) {
                Text(
                    text = channel.name,
                    color = Look.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val now = nowNext?.now
                if (now != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${now.startMs.asClock()} – ${now.endMs.asClock()}   " +
                            s.stillRunning(formatDuration(now.endMs - System.currentTimeMillis())),
                        color = Look.TextDim,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = now.title,
                        color = Look.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(
                        progress = now.progressAt(System.currentTimeMillis()),
                        trackColor = Color.White.copy(alpha = 0.18f),
                        barColor = Look.Highlight,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                nowNext?.next?.let { next ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${s.nextUp} ${next.startMs.asClock()}  ${next.title}",
                        color = Look.TextDim,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth().focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PanelButton(
                    // „Catch-up“: das Archiv steckt im Programmführer.
                    label = if (channel?.hasArchive == true) s.archive else s.guide,
                    onClick = onGuide,
                    modifier = Modifier.weight(1f)
                )
                PanelButton(
                    label = s.favorite,
                    icon = if (channel?.isFavorite == true) Icons.Default.Star else Icons.Default.StarBorder,
                    enabled = channel != null,
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f)
                )
                PanelButton(
                    label = s.search,
                    onClick = onSearch,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PanelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Look.Button,
            focusedContainerColor = Look.Highlight,
            pressedContainerColor = Look.Highlight,
            contentColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContentColor = Color.Black,
            disabledContainerColor = Look.Button.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(3.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.then(if (enabled) Modifier.alsoTappable(onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
