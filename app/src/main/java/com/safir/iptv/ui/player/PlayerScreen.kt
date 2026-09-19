package com.safir.iptv.ui.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
// Modifier.focusable lives in foundation, not in ui.focus — the neighbouring
// focusRequester / onFocusChanged modifiers do come from ui.focus.
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.domain.model.AspectMode
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.ChannelLogo
import com.safir.iptv.ui.components.LiveBadge
import com.safir.iptv.ui.components.NumberEntryOverlay
import com.safir.iptv.ui.components.ProgressLine
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.components.asDigit
import com.safir.iptv.ui.components.rememberNumberEntry
import com.safir.iptv.ui.components.submitNow
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.SleepTimer
import com.safir.iptv.util.applyLanguagePreferences
import com.safir.iptv.util.buildPlayer
import com.safir.iptv.util.repairSilentAudio
import com.safir.iptv.util.asClock
import kotlinx.coroutines.delay

/** Window after opening in which a stray OK key-up from the previous screen is dropped. */
private const val SELECT_GUARD_MS = 600L

@Composable
fun PlayerScreen(
    channelId: String,
    categoryId: String,
    onExit: () -> Unit,
    /** Non-zero switches the screen into catch-up: one recorded programme, no zapping. */
    archiveStartMs: Long = 0L,
    archiveDurationMin: Int = 0
) {
    val viewModel: PlayerViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mediaToken by viewModel.mediaToken.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }
    val numbers = rememberNumberEntry { number -> viewModel.jumpToNumber(number) }
    val showClock = remember { viewModel.showClock }
    val s = LocalStrings.current

    // The OK that opened this screen still has a key-up coming; without this it
    // would immediately pop the channel list open again.
    val openedAt = remember { System.currentTimeMillis() }

    LaunchedEffect(channelId, categoryId) {
        viewModel.start(channelId, categoryId, archiveStartMs, archiveDurationMin)
    }

    val exoPlayer = remember {
        val bufferMs = viewModel.bufferSeconds * 1_000
        buildPlayer(context, minBufferMs = bufferMs, maxBufferMs = bufferMs * 6)
            .apply {
                playWhenReady = true
                applyLanguagePreferences(viewModel.audioLanguage, viewModel.subtitleLanguage)
            }
    }

    // Staying awake is handled once for the whole app by the activity window,
    // so there is nothing to do here but hand the decoder back.
    DisposableEffect(Unit) {
        onDispose { runCatching { exoPlayer.release() } }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_START -> exoPlayer.play()
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
                // Picture but no sound: switch to a track this box can decode.
                runCatching { exoPlayer.repairSilentAudio() }
            }

            override fun onPlayerError(error: PlaybackException) {
                // A decoder that refused the stream is worth one quiet second
                // attempt on a different audio track before anybody is told.
                val repaired = runCatching { exoPlayer.repairSilentAudio() }.getOrDefault(false)
                if (repaired) {
                    runCatching {
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                    return
                }
                viewModel.onError(error.friendlyPlaybackMessage())
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Re-preparing is debounced: rapid zapping cancels the previous effect before
    // it ever opens a connection, so the box does not queue up dead streams.
    val streamUrl = state.channel?.streamUrl
    LaunchedEffect(streamUrl, mediaToken) {
        val url = streamUrl ?: return@LaunchedEffect
        delay(350L)
        val item = MediaItem.Builder()
            .setUri(url)
            .apply {
                if (url.substringBefore('?').endsWith(".m3u8", true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // ExoPlayer only reports the loud failures. This catches the quiet ones — the
    // frozen frame, the buffer that never fills — and hands them to the same repair.
    PlaybackWatchdog(
        player = exoPlayer,
        restartKey = mediaToken,
        enabled = viewModel.autoRecover,
        onStalled = viewModel::onStall,
        onHealthy = viewModel::onPlaybackHealthy
    )

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // When the coloured bar closes, its focused entry goes with it. Without this the
    // focus would be left nowhere and the remote would appear dead.
    LaunchedEffect(state.colorBarVisible) {
        if (!state.colorBarVisible) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            // A tap on the picture does what OK does: open the channel list.
            .alsoTappable(onClick = { viewModel.setChannelListVisible(true) })
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false

                val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (isSelect && System.currentTimeMillis() - openedAt < SELECT_GUARD_MS) {
                    return@onPreviewKeyEvent true
                }

                // Digits come first: on a remote they mean "take me to that channel",
                // whatever else the key would otherwise do here.
                val digit = event.key.asDigit()
                if (digit != null) {
                    numbers.append(digit)
                    return@onPreviewKeyEvent true
                }
                if (numbers.isActive) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter ->
                            return@onPreviewKeyEvent numbers.submitNow { viewModel.jumpToNumber(it) }
                        Key.Back, Key.Escape -> {
                            numbers.clear()
                            return@onPreviewKeyEvent true
                        }
                        else -> Unit
                    }
                }

                when (event.key) {
                    Key.Back, Key.Escape -> {
                        when {
                            viewModel.closeOptions() -> Unit
                            state.colorBarVisible -> viewModel.leaveColorBar()
                            state.channelListVisible -> viewModel.setChannelListVisible(false)
                            state.infoVisible -> viewModel.toggleInfo()
                            else -> onExit()
                        }
                        true
                    }

                    // Up counts forwards, down counts backwards — the direction every
                    // satellite receiver has used for thirty years. With the list
                    // open the keys belong to the list instead.
                    Key.DirectionUp, Key.ChannelUp -> {
                        when {
                            // The bar owns the D-pad while it is up; up leaves it.
                            // Raus ist raus: danach schaltet dieselbe Taste
                            // wieder um, statt sofort wieder hineinzuführen.
                            state.colorBarVisible -> {
                                viewModel.leaveColorBar(); true
                            }

                            state.channelListVisible || state.optionsVisible -> false
                            else -> {
                                viewModel.next(); true
                            }
                        }
                    }

                    // Hier saß der Hinterhalt: „unten“ öffnete aus der Infoleiste
                    // heraus die Farbleiste. Gedacht war das als tastenloser Weg
                    // dorthin — nur steht die Infoleiste nach *jedem* Umschalten
                    // ein paar Sekunden, und genau in diesen Sekunden drückt man
                    // weiter. Statt zum nächsten Kanal kam man in ein Menü, das
                    // niemand gerufen hatte.
                    //
                    // Jetzt entscheidet, wer die Leiste heraufgeholt hat. Kam sie
                    // vom Umschalten (nicht „gerufen“), gehört die Taste dem
                    // nächsten Kanal. Hat jemand sie selbst geholt — mit der
                    // Info-Taste oder links/rechts —, dann führt sie hinein, so
                    // wie bisher. Und wer wieder heraus will, drückt noch einmal
                    // unten: danach schaltet dieselbe Taste wieder um.
                    Key.DirectionDown, Key.ChannelDown -> {
                        when {
                            state.colorBarVisible -> {
                                viewModel.leaveColorBar(); true
                            }

                            state.channelListVisible || state.optionsVisible -> false

                            state.infoVisible && state.infoPinned -> {
                                viewModel.setColorBarVisible(true); true
                            }

                            else -> {
                                viewModel.previous(); true
                            }
                        }
                    }

                    // OK is the channel list — the shortcut people reach for first.
                    Key.DirectionCenter, Key.Enter -> {
                        if (state.channelListVisible || state.optionsVisible ||
                            state.colorBarVisible
                        ) {
                            false // the focused row handles it
                        } else {
                            viewModel.setChannelListVisible(true); true
                        }
                    }

                    // Left and right belong to whatever is open: the bar walks its
                    // own entries with them. Swallowing them here was what left the
                    // bar on screen with the focus gone and nothing reacting.
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (state.colorBarVisible) {
                            false
                        } else {
                            when {
                                state.optionsVisible -> viewModel.closeOptions()
                                state.channelListVisible -> viewModel.setChannelListVisible(false)
                                else -> viewModel.toggleInfo()
                            }
                            true
                        }
                    }

                    Key.Info -> {
                        when {
                            state.colorBarVisible -> viewModel.leaveColorBar()
                            state.optionsVisible -> viewModel.closeOptions()
                            state.channelListVisible -> viewModel.setChannelListVisible(false)
                            else -> viewModel.toggleInfo()
                        }
                        true
                    }

                    else -> {
                        // The coloured keys, the way a receiver does it: the first
                        // press only brings up the legend, and a colour acts once
                        // the legend is on screen. Nothing is lost to a stray thumb.
                        val color = event.key.asColorKey()
                        when {
                            state.colorBarVisible && color != null -> {
                                when (color) {
                                    ColorKey.RED -> viewModel.jumpToPreviousChannel()
                                    ColorKey.GREEN -> {
                                        viewModel.setColorBarVisible(false)
                                        viewModel.showOptionsPage(OptionsPage.AUDIO)
                                    }
                                    ColorKey.YELLOW -> {
                                        viewModel.setColorBarVisible(false)
                                        viewModel.showOptionsPage(OptionsPage.SUBTITLE)
                                    }
                                    ColorKey.BLUE -> {
                                        viewModel.setColorBarVisible(false)
                                        viewModel.showOptionsPage(OptionsPage.ASPECT)
                                    }
                                }
                                true
                            }

                            event.key.opensColorBar() -> {
                                if (state.colorBarVisible) {
                                    viewModel.leaveColorBar()
                                } else {
                                    viewModel.setColorBarVisible(true)
                                }
                                true
                            }

                            else -> false
                        }
                    }
                }
            }
    ) {
        val resize = when (state.aspectMode) {
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
                    this.player = exoPlayer
                }
            },
            update = { it.resizeMode = resize },
            modifier = Modifier.fillMaxSize()
        )

        if (state.isBuffering && state.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.Accent)
            }
        }

        state.error?.let { message ->
            PlaybackErrorOverlay(
                message = message,
                // The repairs carry on behind the overlay, so the overlay says so —
                // otherwise it reads like the app has given up when it has not.
                note = if (state.recovering) s.recovery.stillTrying else null,
                onRetry = viewModel::retry,
                onExit = onExit
            )
        }

        if (state.recovering && state.error == null) {
            RecoveryNotice(
                attempt = state.recoverAttempt,
                switchedFormat = state.switchedFormat,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
            )
        }

        if (showClock) {
            PlaybackClock(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 40.dp, vertical = 24.dp)
            )
        }

        SleepCountdown(
            Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 40.dp, vertical = if (showClock) 62.dp else 24.dp)
        )

        NumberEntryOverlay(
            state = numbers,
            caption = s.switchNow,
            modifier = Modifier.align(Alignment.TopStart).padding(40.dp)
        )

        AnimatedVisibility(
            visible = state.infoVisible && !state.channelListVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            InfoBar(
                state = state,
                actions = listOf(
                    ColorAction(
                        key = ColorKey.RED,
                        label = s.colors.previousChannel,
                        detail = state.previousChannelName ?: s.colors.noPreviousChannel,
                        enabled = viewModel.hasPreviousChannel,
                        onSelect = viewModel::jumpToPreviousChannel
                    ),
                    ColorAction(
                        key = ColorKey.GREEN,
                        label = s.colors.audio,
                        onSelect = {
                            viewModel.setColorBarVisible(false)
                            viewModel.showOptionsPage(OptionsPage.AUDIO)
                        }
                    ),
                    ColorAction(
                        key = ColorKey.YELLOW,
                        label = s.colors.subtitles,
                        onSelect = {
                            viewModel.setColorBarVisible(false)
                            viewModel.showOptionsPage(OptionsPage.SUBTITLE)
                        }
                    ),
                    ColorAction(
                        key = ColorKey.BLUE,
                        label = s.colors.picture,
                        detail = state.aspectMode.shortLabel(),
                        onSelect = {
                            viewModel.setColorBarVisible(false)
                            viewModel.showOptionsPage(OptionsPage.ASPECT)
                        }
                    ),
                    ColorAction(
                        key = null,
                        label = s.options,
                        onSelect = {
                            viewModel.setColorBarVisible(false)
                            viewModel.openOptions()
                        }
                    )
                ),
                actionsFocused = state.colorBarVisible
            )
        }

        AnimatedVisibility(
            visible = state.optionsVisible,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            PlayerOptionsPanel(
                page = state.optionsPage,
                player = exoPlayer,
                aspectMode = state.aspectMode,
                aspectScope = state.channel?.name,
                isFavorite = state.channel?.isFavorite == true,
                onPage = viewModel::showOptionsPage,
                onAspect = viewModel::setAspectMode,
                onToggleFavorite = viewModel::toggleFavorite,
                onClose = { viewModel.closeOptions() }
            )
        }

        AnimatedVisibility(
            visible = state.channelListVisible,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ChannelOverlayList(
                channels = state.playlist,
                currentId = state.channel?.id,
                onSelect = viewModel::select
            )
        }
    }
}

/** Digital clock that stays up during playback; switched in settings. */
@Composable
private fun PlaybackClock(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    Text(
        text = now.asClock(),
        modifier = modifier,
        color = Color.White.copy(alpha = 0.92f),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun InfoBar(
    state: PlayerUiState,
    actions: List<ColorAction>,
    actionsFocused: Boolean
) {
    val s = LocalStrings.current
    val channel = state.channel ?: return
    val now = state.nowNext?.now

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                )
            )
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(
                url = channel.logoUrl,
                name = channel.name,
                modifier = Modifier.size(88.dp, 60.dp),
                cornerRadius = 10
            )
            Spacer(Modifier.width(20.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.number.toString().padStart(3, '0'),
                        style = MaterialTheme.typography.titleMedium,
                        color = Brand.Accent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.isFavorite) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Brand.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                state.archiveLabel?.let { label ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "↺ $label",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.Accent,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (now != null && state.archiveLabel == null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveBadge()
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${now.startMs.asClock()} – ${now.endMs.asClock()}   ${now.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressLine(
                        progress = now.progressAt(System.currentTimeMillis()),
                        modifier = Modifier.fillMaxWidth(0.55f)
                    )
                }

                state.nowNext?.next?.let { next ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${s.nextUp}  ${next.startMs.asClock()}  ${next.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = state.position,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = s.playerHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }
        }

        // The coloured keys are a line of this bar, not a menu of their own. They
        // were a second panel floating just above it, saying the same thing twice;
        // down from the channel line now simply walks into them.
        Spacer(Modifier.height(14.dp))
        ColorBar(
            actions = actions,
            autoFocus = actionsFocused,
            showHint = false,
            background = Color.Transparent
        )
    }
}

@Composable
private fun ChannelOverlayList(
    channels: List<Channel>,
    currentId: String?,
    onSelect: (Channel) -> Unit
) {
    val listState = rememberLazyListState()
    val currentRequester = remember { FocusRequester() }
    val currentIndex = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val openedWith = remember { currentId }

    // Once, when the list opens. Keyed on Unit rather than on the current channel:
    // picking a channel now leaves the list standing, and scrolling it back under
    // the user's thumb after every pick would make browsing impossible.
    //
    // The focus goes to the playing channel's own row, not to the list as a whole:
    // asking the column for focus lands on whatever is topmost, which after the
    // scroll is the row *above* the one you are watching.
    LaunchedEffect(Unit) {
        // A couple of rows of context above, so the current channel does not sit
        // flush against the top edge with nothing before it.
        listState.scrollToItem((currentIndex - 2).coerceAtLeast(0))
        delay(80)
        runCatching { currentRequester.requestFocus() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .width(420.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(channels, key = { it.id }) { channel ->
            val selected = channel.id == currentId
            androidx.tv.material3.Surface(
                onClick = { onSelect(channel) },
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = if (selected) Brand.SurfaceHigh else Color.Transparent,
                    focusedContainerColor = Brand.Accent,
                    pressedContainerColor = Brand.Accent,
                    contentColor = if (selected) Brand.TextPrimary else Brand.TextSecondary,
                    focusedContentColor = Brand.Background,
                    pressedContentColor = Brand.Background
                ),
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
                    RoundedCornerShape(8.dp)
                ),
                scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (channel.id == openedWith) {
                            Modifier.focusRequester(currentRequester)
                        } else {
                            Modifier
                        }
                    )
                    .alsoTappable(onClick = { onSelect(channel) })
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = channel.number.toString().padStart(3, '0'),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(40.dp)
                    )
                    ChannelLogo(
                        url = channel.logoUrl,
                        name = channel.name,
                        modifier = Modifier.size(40.dp, 28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackErrorOverlay(
    message: String,
    note: String?,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    val s = LocalStrings.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brand.Surface)
                .padding(36.dp)
        ) {
            Text(
                s.playbackFailed,
                style = MaterialTheme.typography.titleLarge,
                color = Brand.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            note?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.Accent,
                    modifier = Modifier.widthIn(max = 520.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = onRetry) { Text(s.retry) }
                TvButton(onClick = onExit) { Text(s.back) }
            }
        }
    }
}

/**
 * The quiet version of a failure: one line low on the screen while the player puts
 * itself back together. Deliberately not a dialog — most stalls are over within a
 * few seconds, and a modal that appears and vanishes again teaches people to fear
 * their television.
 */
@Composable
private fun RecoveryNotice(
    attempt: Int,
    switchedFormat: Boolean,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = Brand.Accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (switchedFormat) s.recovery.tryingOtherFormat else s.recovery.reconnecting,
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.TextPrimary
        )
        if (attempt > 1) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = s.recovery.attempt(attempt),
                style = MaterialTheme.typography.labelSmall,
                color = Brand.TextSecondary
            )
        }
    }
}

private fun PlaybackException.friendlyPlaybackMessage(): String {
    val s = AppLocale.strings
    return when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> s.errNetwork

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> s.errRejected

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> s.errNotFound

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> s.errFormat

        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> s.errDecoder

        else -> errorCodeName
    }
}

/** Quiet reminder that the box will switch itself off, and in how long. */
@Composable
private fun SleepCountdown(modifier: Modifier = Modifier) {
    var remaining by remember { mutableLongStateOf(SleepTimer.remainingMs) }
    LaunchedEffect(Unit) {
        while (true) {
            remaining = SleepTimer.remainingMs
            delay(10_000L)
        }
    }
    if (remaining <= 0L) return
    Text(
        text = "⏻ " + AppLocale.strings.nMinutesShort(((remaining / 60_000L) + 1).toInt()),
        modifier = modifier,
        color = Brand.AccentSoft,
        style = MaterialTheme.typography.labelMedium
    )
}
