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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.safir.iptv.domain.model.LiveLayout
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
 * Wie lange man auf einer Gruppe stehen bleiben muss, bis die Kanäle ihr folgen.
 * Kurz genug, dass es sich sofort anfühlt — lang genug, dass schnelles Durchblättern
 * nicht jede überflogene Gruppe aus der Datenbank lädt.
 */
private const val GROUP_FOLLOW_MS = 220L

/** Nach dem Öffnen: das Loslassen von OK aus dem vorigen Bildschirm verwerfen. */
private const val SELECT_GUARD_MS = 600L

/** Welcher Bereich gerade den Fokus hat — entscheidet, wohin Zurück führt. */
private enum class Pane { TABS, GROUPS, CHANNELS, DETAIL }

private val KinoGround = Color(0xFF06080E)
private val Glass = Color(0xCC12161F)
private val RailGround = Color(0xFF10131D)
private val RowIdle = Color(0xFF12151F)
private val TextSoft = Color(0xFFC9D1E0)
private val TextDim = Color(0xFFA9B1C4)

/**
 * Alles, was eine der drei Ansichten zum Zeichnen braucht. Die Ansichten selbst
 * halten keinen Zustand — der liegt in [ThemedChannelsScreen], damit alle drei
 * sich gleich verhalten: gleiche Tasten, gleicher Fokus, gleiche Vorschau.
 */
private class LiveUi(
    val categories: List<Category>,
    /** Null während der Suche: dann ist keine Gruppe gewählt. */
    val selectedCategoryId: String?,
    val groupName: String,
    val channels: List<Channel>,
    val nowNext: Map<String, NowNext>,
    /** Der Kanal, der gerade in der Vorschau läuft. */
    val playing: Channel?,
    val playingNowNext: NowNext?,
    val searchMode: Boolean,
    val query: String,
    val focusTargetId: String?,
    val showNumbers: Boolean,
    val previewPlayer: ExoPlayer?,
    val previewFailed: Boolean,
    val groupsState: LazyListState,
    val groupsRequester: FocusRequester,
    val onFocusCategory: (String) -> Unit,
    val onOpenCategory: (String) -> Unit,
    val onChannel: (Channel) -> Unit,
    val onToggleFavorite: (Channel) -> Unit,
    val onFocusConsumed: () -> Unit,
    val onQuery: (String) -> Unit,
    val onSearch: () -> Unit,
    val onFullscreen: () -> Unit,
    val onFavoritePlaying: () -> Unit,
    val onGuide: () -> Unit,
    val onHome: () -> Unit,
    val onMovies: () -> Unit,
    val onSeries: () -> Unit,
    val onSettings: () -> Unit,
    /** Meldet, welcher Bereich den Fokus hat; an jeden Bereich gehängt. */
    val paneModifier: (Pane) -> Modifier
)

/**
 * Live TV in einer der drei Ansichten Kino, Mosaik oder Receiver.
 *
 * Gemeinsam ist allen der Weg: Gruppen und Kanäle stehen nebeneinander, die
 * Kanalliste folgt der Gruppe unter dem Fokus, und OK auf einem Kanal spielt ihn
 * in der Vorschau ab. Erst ein zweites OK auf denselben Kanal öffnet das Vollbild —
 * so kann man durchprobieren, ohne jedes Mal den Bildschirm zu wechseln.
 */
@Composable
internal fun ThemedChannelsScreen(
    viewModel: ChannelsViewModel,
    layout: LiveLayout,
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
    // gesehener Kanal in der Liste steht, und mit Zurück aus den anderen Bereichen.
    suspend fun focusGroups() {
        // Direkt aus dem ViewModel gelesen: nach „Suche verlassen“ ist die Gruppe
        // dort schon zurückgestellt, auf dem Bildschirm aber erst ein Bild später.
        var selected = viewModel.selectedCategoryId.value
        var index = viewModel.categories.value.indexOfFirst { it.id == selected }
        if (index < 0 && viewModel.categories.value.isNotEmpty()) {
            // Die gemerkte Gruppe gibt es nicht mehr: sonst trüge keine Zeile den
            // Fokus-Anker, und jeder Versuch ginge ins Leere.
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

    // Auffangnetz: hat nach dem Aufbau kein Bereich den Fokus, bekommt ihn die
    // Gruppenliste. Ohne Fokus wirkte die Fernbedienung tot.
    LaunchedEffect(categories.isNotEmpty()) {
        if (categories.isEmpty()) return@LaunchedEffect
        delay(900)
        if (!anyPaneFocused && !viewModel.searchMode.value) focusGroups()
    }

    // Die Kanäle folgen der Gruppe unter dem Fokus — nur solange er in den Gruppen
    // steht: wer schneller ▶ drückt, als die Pause dauert, steht schon in der alten
    // Liste, und die würde ihm sonst unter dem Fokus weggezogen.
    // Ziel eines OK auf einer Gruppe (siehe unten).
    var jumpTarget by remember { mutableStateOf<String?>(null) }
    var jumpStaleList by remember { mutableStateOf<List<Channel>?>(null) }
    val inGroups = pane == Pane.GROUPS
    LaunchedEffect(hoveredCategory, inGroups) {
        if (!inGroups) return@LaunchedEffect
        val id = hoveredCategory ?: return@LaunchedEffect
        if (id == selectedCategory && !searchMode) return@LaunchedEffect
        delay(GROUP_FOLLOW_MS)
        if (id == viewModel.selectedCategoryId.value && !viewModel.searchMode.value) {
            return@LaunchedEffect
        }
        jumpTarget = null
        viewModel.consumeFocusTarget()
        viewModel.leaveSearch()
        viewModel.selectCategory(id)
    }

    // OK auf einer Gruppe: auswählen und hinüber zum ersten Kanal — aber erst, wenn
    // die Liste DIESER Gruppe da ist. Wer schnell drückt, hätte sonst den ersten
    // Kanal der vorigen Gruppe bekommen, der gleich darauf verschwindet.
    LaunchedEffect(jumpTarget, channels) {
        if (jumpTarget == null) return@LaunchedEffect
        if (jumpStaleList != null && channels === jumpStaleList) return@LaunchedEffect
        val first = channels.firstOrNull()
        // Auch bei leerer Gruppe zurücksetzen: sonst risse ein liegengebliebener
        // Sprung später, beim Blättern, den Fokus in die Kanäle.
        jumpTarget = null
        jumpStaleList = null
        if (first != null) viewModel.focusChannel(first.id)
    }

    // Die gewählte Gruppe im Bild halten, solange der Fokus woanders steht: nur
    // dann kann ◀ (bzw. ▲) auf genau ihr landen statt auf irgendeiner Gruppe auf
    // gleicher Höhe, die dann auch noch die Kanalliste austauschen würde.
    LaunchedEffect(selectedCategory, categories.size) {
        if (pane == Pane.GROUPS) return@LaunchedEffect
        val index = categories.indexOfFirst { it.id == selectedCategory }
        if (index < 0) return@LaunchedEffect
        val visible = groupsState.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (!visible) groupsState.scrollToItem(index)
    }

    // Zurück geht immer einen Bereich zurück in die Gruppen; erst von dort aus
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

    val playCategory = if (searchMode) ALL_CATEGORY_ID else selectedCategory

    // Was in der Vorschau läuft, wählt man mit OK — nicht der Fokus. Blättern kostet
    // so keine Verbindung zum Anbieter, und die Vorschau bleibt stehen, während man
    // sich durch andere Gruppen bewegt.
    var playing by remember { mutableStateOf<Channel?>(null) }
    var playingCategory by remember { mutableStateOf<String?>(null) }

    // Beim Öffnen läuft der zuletzt gesehene Kanal schon in der Vorschau.
    // Nur bei der ersten Liste — später, beim Blättern durch eine Gruppe, die ihn
    // zufällig enthält, soll er nicht plötzlich anspringen.
    val lastWatchedId = remember { viewModel.lastChannelId }
    var initialPreviewDone by remember { mutableStateOf(false) }
    LaunchedEffect(channels) {
        if (initialPreviewDone || channels.isEmpty()) return@LaunchedEffect
        initialPreviewDone = true
        if (playing == null) {
            channels.firstOrNull { it.id == lastWatchedId }?.let { last ->
                playing = last
                playingCategory = playCategory
            }
        }
    }

    // Aus der aktuellen Liste nachgeschlagen, wo es geht: der gemerkte Kanal ist
    // eine Momentaufnahme, und ohne das bliebe nach „Favorit“ der Stern leer.
    val playingNow: Channel? = playing?.let { p -> channels.firstOrNull { it.id == p.id } ?: p }

    LaunchedEffect(playing?.id) {
        val player = previewPlayer ?: return@LaunchedEffect
        player.stop()
        previewFailed = false
        val channel = playing ?: return@LaunchedEffect
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

    fun openFullscreen(channel: Channel) {
        // Stammt der Kanal aus einer anderen Gruppe als der angezeigten, schaltet
        // der Player durch die Gruppe, aus der er gestartet wurde.
        val inList = channels.any { it.id == channel.id }
        onPlay(channel, if (inList) playCategory else playingCategory ?: ALL_CATEGORY_ID)
    }

    val ui = LiveUi(
        categories = categories,
        selectedCategoryId = if (searchMode) null else selectedCategory,
        groupName = if (searchMode) {
            s.search
        } else {
            categories.firstOrNull { it.id == selectedCategory }?.name ?: s.channels
        },
        channels = channels,
        nowNext = nowNextMap,
        playing = playingNow,
        playingNowNext = playingNow?.epgId?.let { nowNextMap[it] },
        searchMode = searchMode,
        query = query,
        focusTargetId = focusTargetId,
        showNumbers = selectedCategory != RECENT_CATEGORY_ID && !searchMode,
        previewPlayer = previewPlayer,
        previewFailed = previewFailed,
        groupsState = groupsState,
        groupsRequester = groupsRequester,
        onFocusCategory = { id ->
            hoveredCategory = id
            if (id != jumpTarget) jumpTarget = null
        },
        onOpenCategory = { id ->
            val sameGroup = id == viewModel.selectedCategoryId.value && !viewModel.searchMode.value
            viewModel.consumeFocusTarget()
            viewModel.leaveSearch()
            viewModel.selectCategory(id)
            jumpStaleList = if (sameGroup) null else channels
            jumpTarget = id
        },
        onChannel = { channel ->
            // Erstes OK: in der Vorschau abspielen. Zweites OK auf denselben
            // Kanal: Vollbild.
            // Ohne Vorschau gibt es nichts „erst mal abzuspielen“ — dann gleich Vollbild.
            if (previewPlayer == null || playing?.id == channel.id) {
                openFullscreen(channel)
            } else {
                playing = channel
                playingCategory = playCategory
            }
            Unit
        },
        onToggleFavorite = { channel ->
            // In „Favoriten“ verschwindet die Zeile beim Entfernen — vorher den
            // Fokus auf den Nachbarn setzen, sonst fiele er ins Leere.
            if (selectedCategory == FAVORITES_CATEGORY_ID && !searchMode && channel.isFavorite) {
                val index = channels.indexOfFirst { it.id == channel.id }
                val neighbour = channels.getOrNull(index + 1) ?: channels.getOrNull(index - 1)
                if (neighbour != null) viewModel.focusChannel(neighbour.id)
            }
            viewModel.toggleFavorite(channel)
        },
        onFocusConsumed = viewModel::consumeFocusTarget,
        onQuery = viewModel::setQuery,
        // Ein zweites „Suche“ darf das schon Getippte nicht löschen.
        onSearch = { if (!searchMode) viewModel.enterSearch() },
        onFullscreen = { playingNow?.let { openFullscreen(it) } },
        onFavoritePlaying = { playingNow?.let(viewModel::toggleFavorite) },
        onGuide = onOpenGuide,
        onHome = onBackToDashboard,
        onMovies = onOpenMovies,
        onSeries = onOpenSeries,
        onSettings = onOpenSettings,
        paneModifier = { target ->
            Modifier.onFocusChanged {
                if (it.hasFocus) {
                    pane = target
                    anyPaneFocused = true
                }
            }
        }
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
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
        when (layout) {
            LiveLayout.MOSAIK -> MosaikLayout(ui)
            LiveLayout.RECEIVER -> ReceiverLayout(ui)
            else -> KinoLayout(ui)
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

// ===================================================================== A: Kino

/**
 * Das Live-Bild füllt den ganzen Hintergrund; Gruppen und Kanäle liegen als
 * halbdurchsichtige Spalten darüber, unten rechts groß die laufende Sendung.
 */
@Composable
private fun KinoLayout(ui: LiveUi) {
    val s = LocalStrings.current
    Box(Modifier.fillMaxSize().background(KinoGround)) {
        PreviewSurface(ui = ui, showHint = false, modifier = Modifier.fillMaxSize())
        // Abdunkeln: links fast dicht, damit die Listen lesbar bleiben; rechts
        // bleibt das Bild sichtbar; unten wieder dunkler für die Sendung.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to KinoGround.copy(alpha = 0.97f),
                    0.48f to KinoGround.copy(alpha = 0.92f),
                    0.72f to KinoGround.copy(alpha = 0.35f),
                    1f to KinoGround.copy(alpha = 0.5f)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.5f to Color.Transparent,
                    1f to KinoGround.copy(alpha = 0.96f)
                )
            )
        )

        Column(Modifier.fillMaxSize()) {
            LiveTopBar(ui = ui, searchPill = false, modifier = ui.paneModifier(Pane.TABS))

            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 30.dp, end = 30.dp, top = 4.dp, bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    Modifier
                        .weight(0.19f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Glass)
                        .padding(horizontal = 7.dp, vertical = 10.dp)
                        .then(ui.paneModifier(Pane.GROUPS))
                ) {
                    SectionLabel(s.layout.groups)
                    GroupList(ui = ui, modifier = Modifier.weight(1f).fillMaxWidth())
                }

                Column(
                    Modifier
                        .weight(0.33f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Glass)
                        .padding(horizontal = 7.dp, vertical = 10.dp)
                ) {
                    ListHeader(ui)
                    SearchFieldIfNeeded(ui)
                    ChannelColumnList(
                        ui = ui,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(ui.paneModifier(Pane.CHANNELS))
                    ) { channel, rowModifier ->
                        KinoChannelRow(ui = ui, channel = channel, modifier = rowModifier)
                    }
                }

                Column(Modifier.weight(0.48f).fillMaxHeight()) {
                    Text(
                        text = s.layout.hint,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.End)
                    )
                    Spacer(Modifier.weight(1f))
                    NowInfo(channel = ui.playing, nowNext = ui.playingNowNext, large = true)
                    Spacer(Modifier.height(14.dp))
                    ActionButtons(ui = ui, modifier = ui.paneModifier(Pane.DETAIL))
                }
            }
        }
    }
}

@Composable
private fun KinoChannelRow(ui: LiveUi, channel: Channel, modifier: Modifier) {
    val s = LocalStrings.current
    val isPlaying = ui.playing?.id == channel.id
    val now = channel.nowOf(ui.nowNext)?.now
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = { ui.onChannel(channel) },
        onLongClick = { ui.onToggleFavorite(channel) },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPlaying) Color.White.copy(alpha = 0.10f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            pressedContainerColor = Color.White.copy(alpha = 0.16f),
            contentColor = Brand.TextPrimary,
            focusedContentColor = Brand.TextPrimary,
            pressedContentColor = Brand.TextPrimary
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Brand.Accent), shape = shape)
        ),
        modifier = modifier
            .fillMaxWidth()
            .alsoTappable(
                onClick = { ui.onChannel(channel) },
                onLongClick = { ui.onToggleFavorite(channel) }
            )
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (ui.showNumbers) {
                Text(
                    text = channel.number.toString(),
                    fontSize = 13.sp,
                    color = Brand.TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.width(36.dp)
                )
            }
            ChannelLogo(
                url = channel.logoUrl,
                name = channel.name,
                modifier = Modifier.size(44.dp, 30.dp),
                cornerRadius = 7
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isPlaying) Brand.AccentSoft else Brand.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isPlaying) PlayingDot(Modifier.padding(start = 6.dp))
                }
                if (now != null) {
                    Text(
                        text = now.title,
                        fontSize = 12.sp,
                        color = TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    ProgressLine(
                        progress = now.progressAt(System.currentTimeMillis()),
                        trackColor = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (channel.isFavorite) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = s.favorite,
                    tint = Brand.Accent,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// =================================================================== B: Mosaik

/** Gruppen als Leiste oben, Kanäle als Kacheln, rechts Vorschau und Sendung. */
@Composable
private fun MosaikLayout(ui: LiveUi) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().background(Brand.Background)) {
        LiveTopBar(ui = ui, searchPill = true, modifier = ui.paneModifier(Pane.TABS))

        LazyRow(
            state = ui.groupsState,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(ui.paneModifier(Pane.GROUPS))
                .focusRestorer { ui.selectedGroupRequester() },
            contentPadding = PaddingValues(horizontal = 30.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(ui.categories, key = { it.id }) { category ->
                val selected = category.id == ui.selectedCategoryId
                GroupChip(
                    category = category,
                    selected = selected,
                    onFocused = { ui.onFocusCategory(category.id) },
                    onClick = { ui.onOpenCategory(category.id) },
                    modifier = if (selected) Modifier.focusRequester(ui.groupsRequester) else Modifier
                )
            }
        }

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp, top = 8.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(Modifier.weight(0.62f).fillMaxHeight()) {
                SearchFieldIfNeeded(ui)
                ChannelGrid(
                    ui = ui,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(ui.paneModifier(Pane.CHANNELS))
                )
            }

            Column(
                Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.Surface)
            ) {
                PreviewSurface(
                    ui = ui,
                    showHint = true,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
                Column(Modifier.weight(1f).fillMaxWidth().padding(14.dp)) {
                    NowInfo(channel = ui.playing, nowNext = ui.playingNowNext, large = false)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = s.layout.hint,
                        fontSize = 11.sp,
                        color = Brand.TextSecondary,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionButtons(ui = ui, modifier = ui.paneModifier(Pane.DETAIL))
                }
            }
        }
    }
}

@Composable
private fun GroupChip(
    category: Category,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Brand.Accent else Brand.Surface,
            focusedContainerColor = if (selected) Brand.Accent else Brand.SurfaceHigh,
            pressedContainerColor = Brand.Accent,
            contentColor = if (selected) Brand.Background else TextSoft,
            focusedContentColor = if (selected) Brand.Background else Brand.TextPrimary,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Brand.TextPrimary), shape = shape)
        ),
        modifier = modifier
            .alsoTappable(onClick = onClick)
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.name,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            if (category.channelCount > 0) {
                Spacer(Modifier.width(6.dp))
                Text(text = "${category.channelCount}", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChannelGrid(ui: LiveUi, modifier: Modifier) {
    val s = LocalStrings.current
    val gridState = rememberLazyGridState()
    val jumpRequester = remember { FocusRequester() }

    // Eine andere Gruppe fängt oben an — außer ein Kanal soll gleich den Fokus
    // bekommen, dann würde der Sprung nach oben nur dagegen arbeiten.
    LaunchedEffect(ui.channels.firstOrNull()?.id) {
        if (ui.focusTargetId == null) gridState.scrollToItem(0)
    }

    LaunchedEffect(ui.focusTargetId, ui.channels.isEmpty()) {
        val id = ui.focusTargetId ?: return@LaunchedEffect
        if (ui.channels.isEmpty()) return@LaunchedEffect
        val index = ui.channels.indexOfFirst { it.id == id }
        if (index < 0) {
            ui.onFocusConsumed()
            return@LaunchedEffect
        }
        gridState.scrollToItem(index)
        for (attempt in 0 until 10) {
            delay(50)
            if (runCatching { jumpRequester.requestFocus() }.isSuccess) break
        }
        delay(500)
        ui.onFocusConsumed()
    }

    if (ui.channels.isEmpty()) {
        Box(modifier) { EmptyState(title = s.noChannels, message = s.emptyList) }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            modifier = modifier.focusRestorer(),
            contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(ui.channels, key = { it.id }) { channel ->
                MosaikTile(
                    ui = ui,
                    channel = channel,
                    modifier = if (channel.id == ui.focusTargetId) {
                        Modifier.focusRequester(jumpRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@Composable
private fun MosaikTile(ui: LiveUi, channel: Channel, modifier: Modifier) {
    val isPlaying = ui.playing?.id == channel.id
    val now = channel.nowOf(ui.nowNext)?.now
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { ui.onChannel(channel) },
        onLongClick = { ui.onToggleFavorite(channel) },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPlaying) Brand.SurfaceHigh else Brand.Surface,
            focusedContainerColor = Brand.SurfaceHigh,
            pressedContainerColor = Brand.SurfaceHigh,
            contentColor = Brand.TextPrimary,
            focusedContentColor = Brand.TextPrimary,
            pressedContentColor = Brand.TextPrimary
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            border = if (isPlaying) {
                Border(border = BorderStroke(1.dp, Brand.Accent.copy(alpha = 0.6f)), shape = shape)
            } else {
                Border.None
            },
            focusedBorder = Border(border = BorderStroke(3.dp, Brand.Accent), shape = shape)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(118.dp)
            .alsoTappable(
                onClick = { ui.onChannel(channel) },
                onLongClick = { ui.onToggleFavorite(channel) }
            )
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(
                    url = channel.logoUrl,
                    name = channel.name,
                    modifier = Modifier.size(42.dp, 28.dp),
                    cornerRadius = 6
                )
                Spacer(Modifier.weight(1f))
                if (isPlaying) PlayingDot(Modifier.padding(end = 6.dp))
                if (channel.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Brand.Accent,
                        modifier = Modifier.size(13.dp).padding(end = 2.dp)
                    )
                }
                if (ui.showNumbers) {
                    Text(text = channel.number.toString(), fontSize = 12.sp, color = TextDim)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = channel.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPlaying) Brand.AccentSoft else Brand.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (now != null) {
                Text(
                    text = now.title,
                    fontSize = 11.sp,
                    color = TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                ProgressLine(
                    progress = now.progressAt(System.currentTimeMillis()),
                    trackColor = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ================================================================= C: Receiver

/**
 * Gruppen als schmale Spalte links, eine dichte Liste mit „Jetzt“ und „Danach“ in
 * jeder Zeile, rechts Vorschau und Sendung — wie ein guter Sat-Receiver.
 */
@Composable
private fun ReceiverLayout(ui: LiveUi) {
    val s = LocalStrings.current
    Row(Modifier.fillMaxSize().background(Brand.Background)) {
        Column(
            Modifier
                .width(176.dp)
                .fillMaxHeight()
                .background(RailGround)
                .padding(top = 16.dp, start = 8.dp, end = 8.dp, bottom = 12.dp)
                .then(ui.paneModifier(Pane.GROUPS))
        ) {
            BrandMark(Modifier.padding(start = 6.dp, bottom = 14.dp))
            GroupList(ui = ui, modifier = Modifier.weight(1f).fillMaxWidth())
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 18.dp, end = 24.dp, top = 10.dp, bottom = 8.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .then(ui.paneModifier(Pane.TABS)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = ui.groupName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = s.nChannels(ui.channels.size),
                    fontSize = 12.sp,
                    color = Brand.TextSecondary,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                NavTab(label = s.home, onClick = ui.onHome)
                NavTab(label = s.movies, onClick = ui.onMovies)
                NavTab(label = s.series, onClick = ui.onSeries)
                NavTab(label = s.guide, onClick = ui.onGuide)
                RoundIcon(icon = Icons.Default.Search, description = s.search, onClick = ui.onSearch)
                RoundIcon(icon = Icons.Default.Settings, description = s.settings, onClick = ui.onSettings)
                Spacer(Modifier.width(6.dp))
                Clock(large = true)
            }
            Spacer(Modifier.height(6.dp))

            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(0.63f).fillMaxHeight()) {
                    ReceiverHeader()
                    SearchFieldIfNeeded(ui)
                    ChannelColumnList(
                        ui = ui,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(ui.paneModifier(Pane.CHANNELS))
                    ) { channel, rowModifier ->
                        ReceiverRow(ui = ui, channel = channel, modifier = rowModifier)
                    }
                }

                Column(Modifier.weight(0.37f).fillMaxHeight()) {
                    PreviewSurface(
                        ui = ui,
                        showHint = true,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                    Spacer(Modifier.height(10.dp))
                    NowInfo(channel = ui.playing, nowNext = ui.playingNowNext, large = false)
                    Spacer(Modifier.weight(1f))
                    ActionButtons(ui = ui, modifier = ui.paneModifier(Pane.DETAIL))
                }
            }

            Text(
                text = s.layout.hint,
                fontSize = 12.sp,
                color = Brand.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

@Composable
private fun ReceiverHeader() {
    val s = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderLabel(s.layout.number, Modifier.width(32.dp))
        HeaderLabel(s.layout.station, Modifier.width(148.dp))
        HeaderLabel(s.now, Modifier.weight(1f))
        HeaderLabel(s.nextUp, Modifier.width(96.dp))
    }
}

@Composable
private fun HeaderLabel(text: String, modifier: Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Brand.TextSecondary,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
private fun ReceiverRow(ui: LiveUi, channel: Channel, modifier: Modifier) {
    val isPlaying = ui.playing?.id == channel.id
    val pair = channel.nowOf(ui.nowNext)
    val now = pair?.now
    val next = pair?.next
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = { ui.onChannel(channel) },
        onLongClick = { ui.onToggleFavorite(channel) },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPlaying) Brand.Surface else RowIdle,
            focusedContainerColor = Brand.SurfaceHigh,
            pressedContainerColor = Brand.SurfaceHigh,
            contentColor = Brand.TextPrimary,
            focusedContentColor = Brand.TextPrimary,
            pressedContentColor = Brand.TextPrimary
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Brand.Accent), shape = shape)
        ),
        modifier = modifier
            .fillMaxWidth()
            .alsoTappable(
                onClick = { ui.onChannel(channel) },
                onLongClick = { ui.onToggleFavorite(channel) }
            )
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (ui.showNumbers) channel.number.toString() else "",
                fontSize = 13.sp,
                color = TextDim,
                maxLines = 1,
                modifier = Modifier.width(32.dp)
            )
            Row(Modifier.width(148.dp), verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(
                    url = channel.logoUrl,
                    name = channel.name,
                    modifier = Modifier.size(40.dp, 26.dp),
                    cornerRadius = 6
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = channel.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) Brand.AccentSoft else Brand.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = now?.title ?: "",
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (now != null) {
                    Spacer(Modifier.height(4.dp))
                    ProgressLine(
                        progress = now.progressAt(System.currentTimeMillis()),
                        trackColor = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Column(Modifier.width(96.dp)) {
                if (next != null) {
                    Text(text = next.startMs.asClock(), fontSize = 11.sp, color = Brand.TextSecondary)
                    Text(
                        text = next.title,
                        fontSize = 12.sp,
                        color = TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ============================================================ gemeinsame Teile

@Composable
private fun LiveTopBar(ui: LiveUi, searchPill: Boolean, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BrandMark()
        Spacer(Modifier.width(14.dp))
        NavTab(label = s.home, onClick = ui.onHome)
        ActiveNavTab(label = s.liveTv)
        NavTab(label = s.movies, onClick = ui.onMovies)
        NavTab(label = s.series, onClick = ui.onSeries)
        NavTab(label = s.guide, onClick = ui.onGuide)
        Spacer(Modifier.weight(1f))
        if (searchPill) {
            SearchPill(
                text = if (ui.searchMode && ui.query.isNotBlank()) ui.query else s.searchChannel,
                onClick = ui.onSearch,
                modifier = Modifier.width(200.dp)
            )
        } else {
            RoundIcon(icon = Icons.Default.Search, description = s.search, onClick = ui.onSearch)
        }
        RoundIcon(icon = Icons.Default.Settings, description = s.settings, onClick = ui.onSettings)
        Spacer(Modifier.width(8.dp))
        Clock(large = true)
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Brand.Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Tv,
                contentDescription = null,
                tint = Brand.Background,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Karacast",
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = Brand.TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun NavTab(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = Brand.TextSecondary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.alsoTappable(onClick)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/** Der Reiter, auf dem man steht: nicht anwählbar, nur markiert. */
@Composable
private fun ActiveNavTab(label: String) {
    Column(
        Modifier.padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(24.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brand.Accent)
        )
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = TextSoft,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.alsoTappable(onClick)
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.padding(7.dp).size(18.dp))
    }
}

@Composable
private fun SearchPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            focusedContainerColor = Brand.SurfaceHigh,
            pressedContainerColor = Brand.SurfaceHigh,
            contentColor = Brand.TextSecondary,
            focusedContentColor = Brand.TextPrimary,
            pressedContentColor = Brand.TextPrimary
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Brand.Accent), shape = shape)
        ),
        modifier = modifier.alsoTappable(onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Brand.TextSecondary,
        maxLines = 1,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun ListHeader(ui: LiveUi) {
    val s = LocalStrings.current
    Row(
        Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ui.groupName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = s.nChannels(ui.channels.size), fontSize = 12.sp, color = Brand.TextSecondary)
    }
}

@Composable
private fun SearchFieldIfNeeded(ui: LiveUi) {
    if (!ui.searchMode) return
    val s = LocalStrings.current
    TvTextField(
        value = ui.query,
        onValueChange = ui.onQuery,
        label = s.searchChannel,
        placeholder = s.typeName,
        autoFocus = true,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** Gruppenliste für Kino und Receiver. Die gewählte Gruppe ist farbig hinterlegt. */
@Composable
private fun GroupList(ui: LiveUi, modifier: Modifier) {
    LazyColumn(
        state = ui.groupsState,
        modifier = modifier.focusRestorer { ui.selectedGroupRequester() },
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items(ui.categories, key = { it.id }) { category ->
            val selected = category.id == ui.selectedCategoryId
            GroupRow(
                category = category,
                selected = selected,
                onFocused = { ui.onFocusCategory(category.id) },
                onClick = { ui.onOpenCategory(category.id) },
                modifier = if (selected) Modifier.focusRequester(ui.groupsRequester) else Modifier
            )
        }
    }
}

@Composable
private fun GroupRow(
    category: Category,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(9.dp)
    val virtualIcon = when (category.id) {
        FAVORITES_CATEGORY_ID, RECENT_CATEGORY_ID, ALL_CATEGORY_ID -> category.icon()
        else -> null
    }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Brand.Accent else Color.Transparent,
            focusedContainerColor = if (selected) Brand.Accent else Color.White.copy(alpha = 0.14f),
            pressedContainerColor = Brand.Accent,
            contentColor = if (selected) Brand.Background else Color(0xFFDDE2EC),
            focusedContentColor = if (selected) Brand.Background else Brand.TextPrimary,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Brand.TextPrimary), shape = shape)
        ),
        modifier = modifier
            .fillMaxWidth()
            .alsoTappable(onClick = onClick)
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (virtualIcon != null) {
                Icon(virtualIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = category.name,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (category.channelCount > 0) {
                Spacer(Modifier.width(6.dp))
                Text(text = "${category.channelCount}", fontSize = 12.sp)
            }
        }
    }
}

/**
 * Senkrechte Kanalliste für Kino und Receiver. Kümmert sich um das, was beide
 * gleich brauchen: neue Gruppe fängt oben an, Nummerneingabe und Rückweg aus dem
 * Vollbild holen den Kanal ins Bild und geben ihm den Fokus.
 */
@Composable
private fun ChannelColumnList(
    ui: LiveUi,
    modifier: Modifier,
    row: @Composable (Channel, Modifier) -> Unit
) {
    val s = LocalStrings.current
    val listState = rememberLazyListState()
    val jumpRequester = remember { FocusRequester() }

    LaunchedEffect(ui.channels.firstOrNull()?.id) {
        if (ui.focusTargetId == null) listState.scrollToItem(0)
    }

    LaunchedEffect(ui.focusTargetId, ui.channels.isEmpty()) {
        val id = ui.focusTargetId ?: return@LaunchedEffect
        if (ui.channels.isEmpty()) return@LaunchedEffect
        val index = ui.channels.indexOfFirst { it.id == id }
        if (index < 0) {
            ui.onFocusConsumed()
            return@LaunchedEffect
        }
        listState.scrollToItem(index)
        for (attempt in 0 until 10) {
            delay(50)
            if (runCatching { jumpRequester.requestFocus() }.isSuccess) break
        }
        delay(500)
        ui.onFocusConsumed()
    }

    if (ui.channels.isEmpty()) {
        Box(modifier) { EmptyState(title = s.noChannels, message = s.emptyList) }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(ui.channels, key = { it.id }) { channel ->
                row(
                    channel,
                    if (channel.id == ui.focusTargetId) {
                        Modifier.focusRequester(jumpRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

/** Das Live-Bild der Vorschau, mit Logo, falls der Kanal nicht startet. */
@Composable
private fun PreviewSurface(ui: LiveUi, showHint: Boolean, modifier: Modifier) {
    val s = LocalStrings.current
    val channel = ui.playing
    // Anders benannt als PlayerView.player: in `apply` gewänne sonst das Mitglied
    // gegen den Parameter, und die Ansicht bekäme sich selbst zugewiesen.
    val previewPlayer = ui.previewPlayer
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
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
        if (channel != null && (previewPlayer == null || ui.previewFailed)) {
            ChannelLogo(
                url = channel.logoUrl,
                name = channel.name,
                modifier = Modifier.size(96.dp, 64.dp)
            )
        }
        if (channel == null && showHint) {
            Text(
                text = s.layout.pickChannel,
                fontSize = 13.sp,
                color = Brand.TextSecondary,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/** Was auf dem Kanal in der Vorschau gerade läuft. */
@Composable
private fun NowInfo(channel: Channel?, nowNext: NowNext?, large: Boolean) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(if (large) 7.dp else 5.dp)) {
        if (channel == null) {
            Text(text = s.layout.pickChannel, fontSize = 14.sp, color = Brand.TextSecondary)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveBadge()
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${channel.number} · ${channel.name}",
                fontSize = 13.sp,
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val now = nowNext?.now
        Text(
            text = now?.title ?: channel.name,
            fontSize = if (large) 28.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (now != null) {
            Text(
                text = "${now.startMs.asClock()} – ${now.endMs.asClock()} · " +
                    s.stillRunning(formatDuration(now.endMs - System.currentTimeMillis())),
                fontSize = 13.sp,
                color = TextDim,
                maxLines = 1
            )
            ProgressLine(
                progress = now.progressAt(System.currentTimeMillis()),
                trackColor = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth()
            )
        }
        nowNext?.next?.let { next ->
            Text(
                text = "${s.nextUp} ${next.startMs.asClock()}  ${next.title}",
                fontSize = 12.sp,
                color = TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionButtons(ui: LiveUi, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val channel = ui.playing
    Row(
        modifier.fillMaxWidth().focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionButton(
            label = s.layout.fullscreen,
            icon = Icons.Default.Fullscreen,
            primary = true,
            enabled = channel != null,
            onClick = ui.onFullscreen,
            modifier = Modifier.weight(1f)
        )
        ActionButton(
            label = s.favorite,
            icon = if (channel?.isFavorite == true) Icons.Default.Star else Icons.Default.StarBorder,
            primary = false,
            enabled = channel != null,
            onClick = ui.onFavoritePlaying,
            modifier = Modifier.weight(1f)
        )
        ActionButton(
            // Das Archiv steckt im Programmführer.
            label = if (channel?.hasArchive == true) s.archive else s.guide,
            icon = Icons.Default.DateRange,
            primary = false,
            enabled = true,
            onClick = ui.onGuide,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Brand.Accent else Color.White.copy(alpha = 0.12f),
            focusedContainerColor = if (primary) Brand.TextPrimary else Brand.Accent,
            pressedContainerColor = Brand.AccentSoft,
            contentColor = if (primary) Brand.Background else Brand.TextPrimary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background,
            disabledContainerColor = Color.White.copy(alpha = 0.06f),
            disabledContentColor = Brand.TextSecondary.copy(alpha = 0.5f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = modifier.then(if (enabled) Modifier.alsoTappable(onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Kleiner Punkt neben dem Kanal, der gerade in der Vorschau läuft. */
@Composable
private fun PlayingDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Brand.Live)
    )
}

private fun Channel.nowOf(map: Map<String, NowNext>): NowNext? = epgId?.let { map[it] }

/**
 * Wohin der Fokus geht, wenn er von außen in die Gruppen kommt und sich dort noch
 * nichts gemerkt ist (beim Öffnen stand er gleich auf einem Kanal): die gewählte
 * Gruppe — sofern sie gerade im Bild ist. Sonst die normale Suche, denn ein Anker,
 * der an keiner Zeile hängt, würde ins Leere führen.
 */
private fun LiveUi.selectedGroupRequester(): FocusRequester {
    val visible = groupsState.layoutInfo.visibleItemsInfo.any { it.key == selectedCategoryId }
    return if (visible) groupsRequester else FocusRequester.Default
}
