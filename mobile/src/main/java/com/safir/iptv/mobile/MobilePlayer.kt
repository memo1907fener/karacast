package com.safir.iptv.mobile

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.safir.iptv.IptvApp
import com.safir.iptv.domain.model.AspectMode
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.player.OptionRow
import com.safir.iptv.ui.player.OptionsPage
import com.safir.iptv.ui.player.PlaybackWatchdog
import com.safir.iptv.ui.player.PlayerUiState
import com.safir.iptv.ui.player.PlayerViewModel
import com.safir.iptv.ui.player.aspectRows
import com.safir.iptv.ui.player.friendlyPlaybackMessage
import com.safir.iptv.ui.player.selectedTrackName
import com.safir.iptv.ui.player.sleepRows
import com.safir.iptv.ui.player.subtitleRows
import com.safir.iptv.ui.player.trackRows
import com.safir.iptv.ui.vod.VodPlayback
import com.safir.iptv.util.PictureInPicture
import com.safir.iptv.util.applyLanguagePreferences
import com.safir.iptv.util.buildPlayer
import com.safir.iptv.util.repairSilentAudio
import kotlinx.coroutines.delay

private const val CONTROLS_TIMEOUT_MS = 4_000L

/** Danach gibt der Film-Player auf und zeigt den Fehler statt eines schwarzen Bildes. */
private const val MAX_VOD_RETRIES = 6

private val TopScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.8f),
    1f to Color.Transparent
)
private val BottomScrimDark = Brush.verticalGradient(
    0f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.85f)
)

@Composable
private fun VideoSurface(player: ExoPlayer, aspect: AspectMode) {
    val resize = when (aspect) {
        AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                this.player = player
            }
        },
        update = { it.resizeMode = resize },
        // Hochkant ↔ Vollbild baut das Bild neu; die alte Fläche gibt den Player frei.
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun OverlayIcon(icon: ImageVector, label: String, size: Int = 44, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape((size / 2).dp),
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
        modifier = Modifier.size(size.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size((size * 0.5f).dp))
        }
    }
}

/** Die Zeitzähler im Spieler, die ohne Bildschirm-Antippen verblassen. */
private class ControlsClock {
    var visible by mutableStateOf(true)
    var shownAt by mutableLongStateOf(System.currentTimeMillis())

    fun poke() {
        visible = true
        shownAt = System.currentTimeMillis()
    }

    fun toggle() {
        if (visible) visible = false else poke()
    }
}

// ============================================================ Live-Player

/**
 * Das Vollbild für Live TV und Aufzeichnungen.
 *
 * Dieselbe Logik wie am Fernseher ([PlayerViewModel]: Umschalten, Reparieren,
 * Bildformat pro Kanal) — nur bedient mit dem Finger: Antippen blendet die
 * Bedienelemente ein und aus, Pfeile schalten um, die Kanalliste kommt von rechts.
 *
 * Lebt nicht als eigene Seite, sondern über allen Seiten ([PlayerLayerHost]):
 * nach unten gewischt wird es zum kleinen Fenster über der Leiste und spielt
 * weiter, ohne neu zu verbinden — wie bei YouTube. [collapsed] schaltet nur das
 * Aussehen um; Player, Stream und Zustand bleiben dieselben.
 */
@Composable
fun MobileLivePlayer(
    channelId: String,
    categoryId: String,
    collapsed: Boolean,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onChannel: (Channel?) -> Unit,
    archiveStartMs: Long = 0L,
    archiveDurationMin: Int = 0
) {
    val forced by PlayerLayer.fullscreen.collectAsStateWithLifecycle()
    if (!collapsed) PlayerChrome(forced)
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val viewModel: PlayerViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mediaToken by viewModel.mediaToken.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controls = remember { ControlsClock() }
    var sheet by remember { mutableStateOf<OptionsPage?>(null) }
    var paused by remember { mutableStateOf(false) }

    LaunchedEffect(channelId, categoryId) {
        viewModel.start(channelId, categoryId, archiveStartMs, archiveDurationMin)
    }

    val exoPlayer = remember {
        val bufferMs = viewModel.bufferSeconds * 1_000
        buildPlayer(context, minBufferMs = bufferMs, maxBufferMs = bufferMs * 6).apply {
            playWhenReady = true
            applyLanguagePreferences(viewModel.audioLanguage, viewModel.subtitleLanguage)
        }
    }

    // Bild-in-Bild nur aus dem Vollbild: als kleines Fenster in der App läuft
    // es schon „nebenher“, beim Verlassen der App hält es an.
    DisposableEffect(collapsed) {
        PictureInPicture.setPlaying(!collapsed)
        onDispose { PictureInPicture.setPlaying(false) }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { exoPlayer.release() } }
    }
    val inPip by PictureInPicture.active.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Bild-in-Bild pausiert die Activity nur, es stoppt sie nicht —
                // ON_STOP heißt also wirklich: weg. Dann auch kein Ton mehr.
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_START -> if (!paused) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                viewModel.onBuffering(playbackState == Player.STATE_BUFFERING)
            }

            override fun onTracksChanged(tracks: Tracks) {
                runCatching { exoPlayer.repairSilentAudio() }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (runCatching { exoPlayer.repairSilentAudio() }.getOrDefault(false)) {
                    runCatching { exoPlayer.prepare(); exoPlayer.play() }
                    return
                }
                viewModel.onError(error.friendlyPlaybackMessage())
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    val streamUrl = state.channel?.streamUrl
    LaunchedEffect(streamUrl, mediaToken) {
        val url = streamUrl ?: return@LaunchedEffect
        delay(250L)
        exoPlayer.setMediaItem(
            MediaItem.Builder().setUri(url).apply {
                if (url.substringBefore('?').endsWith(".m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8)
            }.build()
        )
        exoPlayer.prepare()
        paused = false
        exoPlayer.play()
    }

    PlaybackWatchdog(
        player = exoPlayer,
        restartKey = mediaToken,
        enabled = viewModel.autoRecover,
        onStalled = viewModel::onStall,
        onHealthy = viewModel::onPlaybackHealthy
    )

    // Bedienelemente verblassen nach ein paar Sekunden — außer etwas ist offen.
    LaunchedEffect(controls.shownAt, controls.visible, sheet, state.channelListVisible, paused) {
        if (!controls.visible || sheet != null || state.channelListVisible || paused) return@LaunchedEffect
        delay(CONTROLS_TIMEOUT_MS)
        controls.visible = false
    }
    // Nach dem Umschalten kurz zeigen, wohin man gekommen ist.
    LaunchedEffect(state.channel?.id) {
        controls.poke()
        onChannel(state.channel)
    }

    // Zurück verkleinert, statt zu beenden — wie bei YouTube. Beendet wird über
    // das X am kleinen Fenster.
    BackHandler(enabled = !collapsed) {
        when {
            sheet != null -> sheet = null
            state.channelListVisible -> viewModel.setChannelListVisible(false)
            forced -> PlayerLayer.fullscreen.value = false
            else -> onCollapse()
        }
    }

    // Klein gibt es kein Blatt für Tonspur & Co.; wer es offen ließ, findet es zu.
    LaunchedEffect(collapsed) { if (collapsed) sheet = null }

    if (collapsed) {
        val now = state.nowNext?.now
        CollapsedPlayer(
            player = exoPlayer,
            title = cleanTitle(state.channel?.name.orEmpty()),
            subtitle = state.archiveLabel?.let { "$it · ${now?.title.orEmpty()}" } ?: now?.title.orEmpty(),
            progress = now?.progressAt(System.currentTimeMillis()) ?: 0f,
            buffering = state.isBuffering && state.error == null,
            paused = paused,
            onTogglePause = {
                paused = !paused
                if (paused) exoPlayer.pause() else exoPlayer.play()
            },
            onExpand = onExpand,
            onClose = onClose
        )
        return
    }

    val full = forced || inPip
    val drag = rememberCollapseDrag(if (full) ({ PlayerLayer.fullscreen.value = false }) else onCollapse)
    val channel = state.channel
    val isArchive = state.archiveLabel != null
    val togglePause = {
        paused = !paused
        if (paused) exoPlayer.pause() else exoPlayer.play()
        controls.poke()
    }
    val toggleFullscreen = {
        PlayerLayer.fullscreen.value = !forced
        controls.poke()
    }
    // Die Kanalliste von rechts gibt es nur im Vollbild; hochkant steht sie unter dem Bild.
    LaunchedEffect(full) { if (!full) viewModel.setChannelListVisible(false) }

    PlayerFrame(
        full = full,
        drag = drag,
        video = {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { controls.toggle() }) }
            ) {
                VideoSurface(exoPlayer, state.aspectMode)
                if (inPip) return@Box

                if (state.isBuffering && state.error == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Kc.Accent)
                }

                if (state.recovering && state.error == null) {
                    Text(
                        if (state.switchedFormat) t.recovery.tryingOtherFormat else t.recovery.reconnecting,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (full) 96.dp else 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                AnimatedVisibility(
                    visible = controls.visible || state.error != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (full) {
                        LiveFullOverlay(
                            state = state,
                            paused = paused,
                            onCollapse = onCollapse,
                            onSheet = { sheet = it },
                            onFavorite = viewModel::toggleFavorite,
                            onPrevious = { viewModel.previous(); controls.poke() },
                            onNext = { viewModel.next(); controls.poke() },
                            onTogglePause = togglePause,
                            onChannelList = { viewModel.setChannelListVisible(true) },
                            onExitFullscreen = toggleFullscreen
                        )
                    } else {
                        LiveCompactOverlay(
                            state = state,
                            paused = paused,
                            onCollapse = onCollapse,
                            onPrevious = { viewModel.previous(); controls.poke() },
                            onNext = { viewModel.next(); controls.poke() },
                            onTogglePause = togglePause,
                            onFullscreen = toggleFullscreen
                        )
                    }
                }

                // ---- Fehler
                state.error?.let { message ->
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(if (full) 32.dp else 12.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(if (full) 24.dp else 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (full) 12.dp else 6.dp)
                    ) {
                        Text(t.playbackFailed, fontWeight = FontWeight.Bold, fontSize = if (full) 18.sp else 15.sp, color = Color.White)
                        Text(message, color = Color(0xFFC9CFDC), fontSize = 13.sp, maxLines = if (full) 4 else 2, overflow = TextOverflow.Ellipsis)
                        if (state.recovering && full) Text(t.recovery.stillTrying, color = Kc.TextSecondary, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onClose) { Text(m.close) }
                            Button(onClick = viewModel::retry) { Text(t.retry) }
                        }
                    }
                }

                // ---- Kanalliste von rechts (nur im Vollbild)
                AnimatedVisibility(
                    visible = full && state.channelListVisible,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(Unit) {
                        val index = state.index
                        if (index > 2) listState.scrollToItem(index - 2)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                            .background(Kc.Background.copy(alpha = 0.94f))
                            .windowInsetsPadding(WindowInsets.displayCutout)
                            .pointerInput(Unit) { detectTapGestures { } },
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(state.playlist, key = { it.id }) { item ->
                            PlaylistRow(item, current = item.id == state.channel?.id) { viewModel.select(item) }
                        }
                    }
                }
            }
        },
        details = {
            // ---- hochkant: was läuft, Werkzeuge, die Kanäle dieser Liste
            val now = state.nowNext?.now
            val next = state.nowNext?.next
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (channel != null) {
                    ChannelBadge(channel.logoUrl, channel.name, Modifier.size(width = 56.dp, height = 38.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(
                            channel?.number?.takeIf { it > 0 }?.toString(),
                            cleanTitle(channel?.name.orEmpty())
                        ).joinToString(" · "),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val line = state.archiveLabel?.let { "$it · ${now?.title.orEmpty()}" } ?: now?.title.orEmpty()
                    if (line.isNotBlank()) {
                        Text(line, fontSize = 14.sp, color = Kc.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!isArchive && channel != null) {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (channel.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = t.favorite,
                            tint = if (channel.isFavorite) Kc.Accent else Kc.TextSecondary
                        )
                    }
                }
            }
            if (now != null && !isArchive) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(clockOf(now.startMs), color = Kc.TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    ThinProgress(now.progressAt(System.currentTimeMillis()), Modifier.weight(1f), height = 4.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(clockOf(now.endMs), color = Kc.TextSecondary, fontSize = 12.sp)
                }
            }
            if (next != null && !isArchive) {
                Text(
                    "${t.nextUp}: ${clockOf(next.startMs)} ${next.title}",
                    fontSize = 13.sp,
                    color = Kc.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            ToolRow {
                ToolChip(Icons.Filled.Audiotrack, t.audioTrack) { sheet = OptionsPage.AUDIO }
                ToolChip(Icons.Filled.Subtitles, t.subtitles) { sheet = OptionsPage.SUBTITLE }
                ToolChip(Icons.Filled.AspectRatio, t.aspect) { sheet = OptionsPage.ASPECT }
                ToolChip(Icons.Filled.Bedtime, t.sleepTimer) { sheet = OptionsPage.SLEEP }
            }
            if (!isArchive && state.playlist.isNotEmpty()) {
                HorizontalDivider(color = Kc.Border.copy(alpha = 0.6f))
                Text(
                    m.channelList,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                )
                val listState = rememberLazyListState()
                // Den laufenden Kanal in Sicht halten — auch nach dem Umschalten mit den Pfeilen.
                LaunchedEffect(state.index) {
                    val index = state.index
                    if (index < 0) return@LaunchedEffect
                    val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
                    if (!visible) listState.scrollToItem((index - 2).coerceAtLeast(0))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(state.playlist, key = { it.id }) { item ->
                        PlaylistRow(item, current = item.id == state.channel?.id) { viewModel.select(item) }
                    }
                }
            }
        }
    )

    sheet?.let { page ->
        TrackSheet(
            page = page,
            player = exoPlayer,
            aspectMode = state.aspectMode,
            onAspect = viewModel::setAspectMode,
            onPage = { sheet = it },
            onDismiss = { sheet = null }
        )
    }
}

/** Die Bedienung über dem Bild im Vollbild: oben Sender und Werkzeuge, Mitte umschalten. */
@Composable
private fun LiveFullOverlay(
    state: PlayerUiState,
    paused: Boolean,
    onCollapse: () -> Unit,
    onSheet: (OptionsPage) -> Unit,
    onFavorite: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePause: () -> Unit,
    onChannelList: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val channel = state.channel
    val isArchive = state.archiveLabel != null
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        // ---- oben: verkleinern, Sender, Werkzeuge
        Row(
            Modifier
                .fillMaxWidth()
                .background(TopScrim)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = m.minimize, tint = Color.White)
            }
            if (channel != null) {
                ChannelBadge(channel.logoUrl, channel.name, Modifier.size(width = 48.dp, height = 32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(
                            channel.number.takeIf { it > 0 }?.toString(),
                            cleanTitle(channel.name)
                        ).joinToString(" · "),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val now = state.nowNext?.now
                    Text(
                        state.archiveLabel?.let { "$it · ${now?.title.orEmpty()}" } ?: now?.title.orEmpty(),
                        color = Color(0xFFC9CFDC),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayIcon(Icons.Filled.Audiotrack, t.audioTrack) { onSheet(OptionsPage.AUDIO) }
                OverlayIcon(Icons.Filled.Subtitles, t.subtitles) { onSheet(OptionsPage.SUBTITLE) }
                OverlayIcon(Icons.Filled.AspectRatio, t.aspect) { onSheet(OptionsPage.ASPECT) }
                OverlayIcon(Icons.Filled.Bedtime, t.sleepTimer) { onSheet(OptionsPage.SLEEP) }
                if (!isArchive) {
                    OverlayIcon(
                        if (channel?.isFavorite == true) Icons.Filled.Star else Icons.Filled.StarBorder,
                        t.favorite,
                        onClick = onFavorite
                    )
                }
            }
        }

        // ---- Mitte: umschalten und anhalten
        CenterControls(
            paused = paused,
            compact = false,
            left = if (isArchive) null else Icons.Filled.SkipPrevious to m.previousChannel,
            right = if (isArchive) null else Icons.Filled.SkipNext to m.nextChannel,
            onLeft = onPrevious,
            onRight = onNext,
            onTogglePause = onTogglePause,
            modifier = Modifier.align(Alignment.Center)
        )

        // ---- unten: Sendung, Fortschritt, Kanalliste, Vollbild beenden
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(BottomScrimDark)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val now = state.nowNext?.now
            if (now != null && !isArchive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(clockOf(now.startMs), color = Color(0xFFC9CFDC), fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    ThinProgress(
                        now.progressAt(System.currentTimeMillis()),
                        Modifier.weight(1f),
                        track = Color.White.copy(alpha = 0.25f),
                        height = 4.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(clockOf(now.endMs), color = Color(0xFFC9CFDC), fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val next = state.nowNext?.next
                Text(
                    if (next != null) "${t.nextUp}: ${clockOf(next.startMs)} ${next.title}" else "",
                    color = Color(0xFFC9CFDC),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!isArchive) {
                    Surface(
                        onClick = onChannelList,
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(m.channelList, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onExitFullscreen) {
                    Icon(Icons.Filled.FullscreenExit, contentDescription = m.exitFullscreen, tint = Color.White)
                }
            }
        }
    }
}

/** Die Bedienung über dem Bild hochkant: knapp, der Rest steht unter dem Bild. */
@Composable
private fun LiveCompactOverlay(
    state: PlayerUiState,
    paused: Boolean,
    onCollapse: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePause: () -> Unit,
    onFullscreen: () -> Unit
) {
    val m = LocalMobileStrings.current
    val isArchive = state.archiveLabel != null
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = m.minimize, tint = Color.White)
        }
        CenterControls(
            paused = paused,
            compact = true,
            left = if (isArchive) null else Icons.Filled.SkipPrevious to m.previousChannel,
            right = if (isArchive) null else Icons.Filled.SkipNext to m.nextChannel,
            onLeft = onPrevious,
            onRight = onNext,
            onTogglePause = onTogglePause,
            modifier = Modifier.align(Alignment.Center)
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val now = state.nowNext?.now
            if (now != null && !isArchive) {
                Text(clockOf(now.startMs), color = Color(0xFFE0E4EE), fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                ThinProgress(
                    now.progressAt(System.currentTimeMillis()),
                    Modifier.weight(1f),
                    track = Color.White.copy(alpha = 0.3f),
                    height = 3.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(clockOf(now.endMs), color = Color(0xFFE0E4EE), fontSize = 11.sp)
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onFullscreen) {
                Icon(Icons.Filled.Fullscreen, contentDescription = m.fullscreen, tint = Color.White)
            }
        }
    }
}

/** Zurück, Pause, Vor — groß im Vollbild, kleiner hochkant. */
@Composable
private fun CenterControls(
    paused: Boolean,
    compact: Boolean,
    left: Pair<ImageVector, String>?,
    right: Pair<ImageVector, String>?,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMobileStrings.current
    val side = if (compact) 42 else 52
    val main = if (compact) 58 else 72
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 36.dp else 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (left != null) OverlayIcon(left.first, left.second, side, onLeft)
        Surface(
            onClick = onTogglePause,
            shape = RoundedCornerShape((main / 2).dp),
            color = Kc.Accent,
            contentColor = Color(0xFF101010),
            modifier = Modifier.size(main.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (paused) m.play else m.pause,
                    modifier = Modifier.size((main * 0.47f).dp)
                )
            }
        }
        if (right != null) OverlayIcon(right.first, right.second, side, onRight)
    }
}

// =========================================================== Spuren-Blatt

/**
 * Tonspur, Untertitel, Bildformat und Sleep-Timer als Blatt von unten. Die
 * Zeilen selbst kommen aus dem gemeinsamen Code ([trackRows] und Co.), damit die
 * Spuren hier genau so heißen und gezählt werden wie am Fernseher.
 */
@Composable
fun TrackSheet(
    page: OptionsPage,
    player: ExoPlayer,
    aspectMode: AspectMode,
    onAspect: (AspectMode) -> Unit,
    onPage: (OptionsPage?) -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Der Player ist kein Compose-Zustand — zuhören, sonst bleibt der Punkt stehen.
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var params by remember(player) { mutableStateOf(player.trackSelectionParameters) }
    var sleepTick by remember { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                params = parameters
            }
        }
        player.addListener(listener)
        tracks = player.currentTracks
        params = player.trackSelectionParameters
        onDispose { player.removeListener(listener) }
    }

    val (title, rows) = when (page) {
        OptionsPage.AUDIO -> t.audioTrack to trackRows(player, tracks, C.TRACK_TYPE_AUDIO)
        OptionsPage.SUBTITLE -> t.subtitles to subtitleRows(player, tracks, params)
        OptionsPage.ASPECT -> t.aspect to aspectRows(aspectMode) { onAspect(it); onDismiss() }
        OptionsPage.SLEEP -> t.sleepTimer to sleepRows { sleepTick++; onDismiss() }
        else -> t.options to emptyList<OptionRow>()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Kc.Surface
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (page == OptionsPage.AUDIO || page == OptionsPage.SUBTITLE) {
                val now = if (page == OptionsPage.AUDIO) selectedTrackName(tracks, C.TRACK_TYPE_AUDIO) else null
                if (now != null) {
                    Text(now, color = Kc.AccentSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            if (rows.isEmpty()) {
                Text(
                    t.nothingToChoose,
                    color = Kc.TextSecondary,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(rows, key = { it.id }) { row -> SheetRow(row) }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(row: OptionRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = row.enabled, onClick = row.onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (row.selected) Kc.Accent else Kc.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            if (row.selected) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF101010)))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            row.title,
            fontSize = 16.sp,
            color = when {
                !row.enabled -> Kc.TextSecondary.copy(alpha = 0.5f)
                row.selected -> Kc.TextPrimary
                else -> Kc.TextPrimary.copy(alpha = 0.85f)
            },
            fontWeight = if (row.selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (row.detail.isNotEmpty()) {
            Text(row.detail, fontSize = 13.sp, color = Kc.TextSecondary, maxLines = 1)
        }
    }
}

// ============================================================= Film-Player

/**
 * Filme und Folgen. Merkt sich die Stelle laufend (alle 15 Sekunden und beim
 * Verlassen) und spielt am Ende einer Folge die nächste, wenn gewünscht —
 * dieselben Regeln wie am Fernseher.
 */
@Composable
fun MobileVodPlayer(
    collapsed: Boolean,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val opened = remember { VodPlayback.pending }
    if (opened == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val forced by PlayerLayer.fullscreen.collectAsStateWithLifecycle()
    if (!collapsed) PlayerChrome(forced)
    val onExit = onClose

    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = remember { (context.applicationContext as IptvApp).container.settings }

    var request by remember { mutableStateOf(opened) }
    val controls = remember { ControlsClock() }
    var sheet by remember { mutableStateOf<OptionsPage?>(null) }
    var aspectMode by remember { mutableStateOf(settings.aspectFor(request.resumeKey)) }
    var buffering by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(request.startMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember {
        val bufferMs = settings.bufferSeconds * 1_000
        buildPlayer(context, minBufferMs = bufferMs, maxBufferMs = bufferMs * 6).apply {
            playWhenReady = true
            applyLanguagePreferences(settings.audioLanguage, settings.subtitleLanguage)
        }
    }

    /** Wie oft hintereinander neu verbunden wurde; danach wird der Fehler gezeigt. */
    var attempts by remember { mutableIntStateOf(0) }

    fun rememberPosition() {
        val at = exoPlayer.currentPosition
        val total = exoPlayer.duration.takeIf { it > 0L } ?: 0L
        if (at <= 0L) return
        settings.saveProgress(
            ContinueItem(
                key = request.resumeKey,
                isEpisode = request.isEpisode,
                title = request.title,
                subtitle = request.subtitle,
                posterUrl = request.posterUrl,
                streamUrl = request.url,
                positionMs = at,
                durationMs = total,
                updatedAt = System.currentTimeMillis(),
                seriesId = request.seriesId
            )
        )
    }

    /** Die nächste Folge — mit allem zurückgesetzt, was zur vorigen gehörte. */
    fun playNext(next: VodPlayback.Request) {
        paused = false
        exoPlayer.playWhenReady = true
        positionMs = 0L
        durationMs = 0L
        retryToken = 0
        attempts = 0
        failure = null
        request = next.copy(startMs = settings.playbackPosition(next.resumeKey))
        aspectMode = settings.aspectFor(next.resumeKey)
        controls.poke()
    }

    DisposableEffect(collapsed) {
        PictureInPicture.setPlaying(!collapsed)
        onDispose { PictureInPicture.setPlaying(false) }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { rememberPosition() }
            runCatching { exoPlayer.release() }
        }
    }
    val inPip by PictureInPicture.active.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    rememberPosition()
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_START -> if (!paused) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    settings.forgetProgress(request.resumeKey)
                    val next = VodPlayback.after(request)
                    if (settings.autoNextEpisode && next != null) {
                        playNext(next)
                    } else {
                        onExit()
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                runCatching { exoPlayer.repairSilentAudio() }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (runCatching { exoPlayer.repairSilentAudio() }.getOrDefault(false)) {
                    runCatching {
                        exoPlayer.prepare()
                        if (!paused) exoPlayer.play()
                    }
                    return
                }
                if (settings.autoRecover && attempts < MAX_VOD_RETRIES) {
                    attempts++
                    retryToken++
                } else {
                    failure = error.friendlyPlaybackMessage()
                    buffering = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(request, retryToken) {
        if (retryToken > 0) delay(2_000L)
        val resumeAt = if (retryToken == 0) request.startMs else positionMs
        exoPlayer.setMediaItem(
            MediaItem.Builder().setUri(request.url).apply {
                if (request.url.substringBefore('?').endsWith(".m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8)
            }.build(),
            resumeAt.coerceAtLeast(0L)
        )
        exoPlayer.prepare()
        if (!paused) exoPlayer.play()
        failure = null
    }

    PlaybackWatchdog(
        player = exoPlayer,
        restartKey = retryToken,
        enabled = settings.autoRecover,
        onStalled = {
            if (attempts < MAX_VOD_RETRIES) {
                attempts++
                retryToken++
            }
        },
        onHealthy = {
            attempts = 0
            failure = null
        }
    )

    LaunchedEffect(exoPlayer) {
        var sinceSave = 0
        while (true) {
            delay(1_000L)
            if (!scrubbing) positionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            if (++sinceSave >= 15) {
                sinceSave = 0
                rememberPosition()
            }
        }
    }

    LaunchedEffect(controls.shownAt, controls.visible, sheet, paused, scrubbing) {
        if (!controls.visible || sheet != null || paused || scrubbing) return@LaunchedEffect
        delay(CONTROLS_TIMEOUT_MS)
        controls.visible = false
    }

    BackHandler(enabled = !collapsed) {
        when {
            sheet != null -> sheet = null
            forced -> PlayerLayer.fullscreen.value = false
            else -> onCollapse()
        }
    }

    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        exoPlayer.seekTo(target)
        positionMs = target
        controls.poke()
    }

    // Klein gibt es kein Blatt für Tonspur & Co.; wer es offen ließ, findet es zu.
    LaunchedEffect(collapsed) { if (collapsed) sheet = null }

    if (collapsed) {
        CollapsedPlayer(
            player = exoPlayer,
            title = cleanTitle(request.title),
            subtitle = request.subtitle,
            progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            buffering = buffering && failure == null,
            paused = paused,
            onTogglePause = {
                paused = !paused
                if (paused) {
                    exoPlayer.pause()
                    rememberPosition()
                } else {
                    exoPlayer.play()
                }
            },
            onExpand = onExpand,
            onClose = onClose
        )
        return
    }

    val full = forced || inPip
    val drag = rememberCollapseDrag(if (full) ({ PlayerLayer.fullscreen.value = false }) else onCollapse)
    val togglePause = {
        paused = !paused
        if (paused) {
            exoPlayer.pause()
            rememberPosition()
        } else {
            exoPlayer.play()
        }
        controls.poke()
    }
    val toggleFullscreen = {
        PlayerLayer.fullscreen.value = !forced
        controls.poke()
    }
    val next = VodPlayback.after(request)

    PlayerFrame(
        full = full,
        drag = drag,
        video = {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controls.toggle() },
                            // Doppeltippen links/rechts springt — wie man es von jedem
                            // Videoplayer am Handy kennt.
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2) seekBy(-10_000L) else seekBy(10_000L)
                            }
                        )
                    }
            ) {
                VideoSurface(exoPlayer, aspectMode)
                if (inPip) return@Box

                if (buffering && failure == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Kc.Accent)
                }

                AnimatedVisibility(
                    visible = controls.visible || failure != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (full) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier)
                            .then(if (full) Modifier else Modifier.background(Color.Black.copy(alpha = 0.35f)))
                    ) {
                        if (full) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(TopScrim)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onCollapse) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = m.minimize, tint = Color.White)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(cleanTitle(request.title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (request.subtitle.isNotBlank()) {
                                        Text(request.subtitle, color = Color(0xFFC9CFDC), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OverlayIcon(Icons.Filled.Audiotrack, t.audioTrack) { sheet = OptionsPage.AUDIO }
                                    OverlayIcon(Icons.Filled.Subtitles, t.subtitles) { sheet = OptionsPage.SUBTITLE }
                                    OverlayIcon(Icons.Filled.AspectRatio, t.aspect) { sheet = OptionsPage.ASPECT }
                                    OverlayIcon(Icons.Filled.Bedtime, t.sleepTimer) { sheet = OptionsPage.SLEEP }
                                }
                            }
                        } else {
                            IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.TopStart)) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = m.minimize, tint = Color.White)
                            }
                        }

                        CenterControls(
                            paused = paused,
                            compact = !full,
                            left = Icons.Filled.Replay10 to m.rewind,
                            right = Icons.Filled.Forward10 to m.forward,
                            onLeft = { seekBy(-10_000L) },
                            onRight = { seekBy(10_000L) },
                            onTogglePause = togglePause,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .then(if (full) Modifier.background(BottomScrimDark) else Modifier)
                                .padding(horizontal = if (full) 24.dp else 8.dp, vertical = if (full) 8.dp else 0.dp)
                        ) {
                            val fraction = if (scrubbing) scrubFraction else if (durationMs > 0) {
                                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            } else 0f
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!full) {
                                    val shownPos = if (scrubbing) (scrubFraction * durationMs).toLong() else positionMs
                                    Text(
                                        "${durationOf(shownPos)} / ${durationOf(durationMs)}",
                                        color = Color(0xFFE0E4EE),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(start = 6.dp).weight(1f)
                                    )
                                    IconButton(onClick = toggleFullscreen) {
                                        Icon(Icons.Filled.Fullscreen, contentDescription = m.fullscreen, tint = Color.White)
                                    }
                                }
                            }
                            Slider(
                                value = fraction,
                                onValueChange = {
                                    scrubbing = true
                                    scrubFraction = it
                                    controls.poke()
                                },
                                onValueChangeFinished = {
                                    if (durationMs > 0) {
                                        val target = (scrubFraction * durationMs).toLong()
                                        exoPlayer.seekTo(target)
                                        positionMs = target
                                    }
                                    scrubbing = false
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Kc.Accent,
                                    activeTrackColor = Kc.Accent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.fillMaxWidth().height(if (full) 32.dp else 24.dp)
                            )
                            if (full) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val shownPos = if (scrubbing) (scrubFraction * durationMs).toLong() else positionMs
                                    Text(
                                        "${durationOf(shownPos)} / ${durationOf(durationMs)}",
                                        color = Color(0xFFC9CFDC),
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (next != null) {
                                        Surface(
                                            onClick = {
                                                rememberPosition()
                                                playNext(next)
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color.Black.copy(alpha = 0.6f),
                                            contentColor = Color.White
                                        ) {
                                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(t.vod.nextEpisode, fontSize = 13.sp)
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    IconButton(onClick = toggleFullscreen) {
                                        Icon(Icons.Filled.FullscreenExit, contentDescription = m.exitFullscreen, tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                failure?.let { message ->
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(if (full) 32.dp else 12.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(if (full) 24.dp else 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (full) 12.dp else 6.dp)
                    ) {
                        Text(t.playbackFailed, fontWeight = FontWeight.Bold, fontSize = if (full) 18.sp else 15.sp, color = Color.White)
                        Text(message, color = Color(0xFFC9CFDC), fontSize = 13.sp, maxLines = if (full) 4 else 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onClose) { Text(m.close) }
                            Button(onClick = {
                                attempts = 0
                                failure = null
                                retryToken++
                            }) { Text(t.retry) }
                        }
                    }
                }
            }
        },
        details = {
            // ---- hochkant: Titel, Werkzeuge, die nächsten Folgen
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                Text(
                    cleanTitle(request.title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (request.subtitle.isNotBlank()) {
                    Text(request.subtitle, fontSize = 14.sp, color = Kc.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (durationMs > 0) {
                    Text(
                        "${durationOf(positionMs)} / ${durationOf(durationMs)}",
                        fontSize = 12.sp,
                        color = Kc.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            ToolRow {
                ToolChip(Icons.Filled.Audiotrack, t.audioTrack) { sheet = OptionsPage.AUDIO }
                ToolChip(Icons.Filled.Subtitles, t.subtitles) { sheet = OptionsPage.SUBTITLE }
                ToolChip(Icons.Filled.AspectRatio, t.aspect) { sheet = OptionsPage.ASPECT }
                ToolChip(Icons.Filled.Bedtime, t.sleepTimer) { sheet = OptionsPage.SLEEP }
            }
            val upcoming = remember(request) {
                VodPlayback.queue.dropWhile { it.resumeKey != request.resumeKey }.drop(1)
            }
            if (upcoming.isNotEmpty()) {
                HorizontalDivider(color = Kc.Border.copy(alpha = 0.6f))
                Text(
                    m.moreEpisodes,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(upcoming, key = { it.resumeKey }) { episode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    rememberPosition()
                                    playNext(episode)
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Poster(
                                url = episode.posterUrl,
                                title = episode.subtitle,
                                modifier = Modifier.size(width = 112.dp, height = 63.dp),
                                corner = 8.dp,
                                titleSize = 11
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                episode.subtitle,
                                fontSize = 14.sp,
                                fontWeight = if (episode == next) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    )

    sheet?.let { page ->
        TrackSheet(
            page = page,
            player = exoPlayer,
            aspectMode = aspectMode,
            onAspect = { mode ->
                aspectMode = mode
                settings.saveAspectFor(request.resumeKey, mode)
            },
            onPage = { sheet = it },
            onDismiss = { sheet = null }
        )
    }
}
