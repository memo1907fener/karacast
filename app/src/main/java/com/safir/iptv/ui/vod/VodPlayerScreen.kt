package com.safir.iptv.ui.vod

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.IptvApp
import com.safir.iptv.domain.model.AspectMode
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.player.ColorAction
import com.safir.iptv.ui.player.ColorBar
import com.safir.iptv.ui.player.ColorKey
import com.safir.iptv.ui.player.OptionsPage
import com.safir.iptv.ui.player.asColorKey
import com.safir.iptv.ui.player.opensColorBar
import com.safir.iptv.ui.player.shortLabel
import com.safir.iptv.ui.player.PlaybackWatchdog
import com.safir.iptv.ui.player.PlayerOptionsPanel
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.applyLanguagePreferences
import com.safir.iptv.util.buildPlayer
import com.safir.iptv.util.repairSilentAudio
import com.safir.iptv.util.asTimeLabel
import kotlinx.coroutines.delay

/**
 * The film player. A different animal from the live one: there is nothing to zap
 * to, the timeline is finite and seekable, and leaving in the middle has to be
 * survivable — so the position is written down as it goes, not only on the way out.
 */
@Composable
fun VodPlayerScreen(onExit: () -> Unit) {
    val opened = remember { VodPlayback.pending }
    if (opened == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    /**
     * What is playing *now* — not necessarily what was opened.
     *
     * At the end of an episode the player moves on to the next one in the season
     * rather than closing, so everything downstream reads from here: the title on
     * the bar, the bookmark, the picture format, the stream itself.
     */
    var request by remember { mutableStateOf(opened) }

    val t = LocalStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = remember { (context.applicationContext as IptvApp).container.settings }

    val focusRequester = remember { FocusRequester() }
    var barVisible by remember { mutableStateOf(true) }
    var barShownAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var optionsPage by remember { mutableStateOf(OptionsPage.NONE) }
    var aspectMode by remember { mutableStateOf(settings.aspectFor(request.resumeKey)) }
    /** Bumped when an episode ends and the next one is to take over. */
    var advanceToken by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }
    /**
     * True once the D-pad has moved down into the icon row under the seek bar.
     * While it is false, left and right still scrub — the way every streaming app
     * behaves, and the reason the icons can sit there without stealing the keys.
     */
    var actionsFocused by remember { mutableStateOf(false) }

    var positionMs by remember { mutableLongStateOf(request.startMs) }
    var durationMs by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember {
        val bufferMs = settings.bufferSeconds * 1_000
        buildPlayer(context, minBufferMs = bufferMs, maxBufferMs = bufferMs * 6)
            .apply {
                playWhenReady = true
                applyLanguagePreferences(settings.audioLanguage, settings.subtitleLanguage)
            }
    }

    /**
     * Writes down where the viewer is, with everything the "keep watching" row will
     * need to draw this title later. Called on the way out and every quarter minute,
     * so a power cut costs fifteen seconds of film rather than the whole evening.
     */
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

    DisposableEffect(Unit) {
        onDispose {
            runCatching { rememberPosition() }
            runCatching { exoPlayer.release() }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    rememberPosition()
                    exoPlayer.pause()
                }

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
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    // Watched to the end: drop the bookmark rather than offering to
                    // resume thirty seconds before the credits next time.
                    settings.forgetProgress(request.resumeKey)
                    // And then carry on into the next episode, which is what
                    // somebody who just watched one to the end wanted anyway.
                    if (settings.autoNextEpisode && VodPlayback.after(request) != null) {
                        advanceToken++
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
                        exoPlayer.play()
                    }
                    return
                }
                if (settings.autoRecover) {
                    retryToken++
                } else {
                    failure = error.errorCodeName
                    buffering = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Re-prepared on every repair and on every change of episode, always back to
    // where the viewer actually was.
    LaunchedEffect(request, retryToken) {
        if (retryToken > 0) delay(2_000L)
        val resumeAt = if (retryToken == 0) request.startMs else positionMs
        val item = MediaItem.Builder()
            .setUri(request.url)
            .apply {
                if (request.url.substringBefore('?').endsWith(".m3u8", true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        exoPlayer.setMediaItem(item, resumeAt.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.play()
        failure = null
    }

    PlaybackWatchdog(
        player = exoPlayer,
        restartKey = retryToken,
        enabled = settings.autoRecover,
        onStalled = { retryToken++ },
        onHealthy = { failure = null }
    )

    // One tick drives everything time-shaped: the bar's numbers, its auto-hide,
    // and the bookmark that makes a power cut survivable.
    LaunchedEffect(exoPlayer) {
        var sinceSave = 0
        while (true) {
            delay(1_000L)
            positionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            // Never fade the bar out from under a focused icon: that is exactly how
            // a screen ends up with the focus nowhere and the remote doing nothing.
            if (barVisible &&
                !actionsFocused &&
                optionsPage == OptionsPage.NONE &&
                System.currentTimeMillis() - barShownAt > BAR_TIMEOUT_MS
            ) {
                barVisible = false
            }
            if (++sinceSave >= 15) {
                sinceSave = 0
                rememberPosition()
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Whenever the icons let go, the screen itself takes the focus back, so the
    // next key press always lands somewhere.
    LaunchedEffect(actionsFocused, barVisible) {
        if (!actionsFocused) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun showBar() {
        barShownAt = System.currentTimeMillis()
        barVisible = true
    }

    /**
     * Hands the player the next episode without leaving the screen.
     *
     * Deliberately does not write a bookmark for the episode being left: at the
     * end of one the position is the credits, and filing that would put a finished
     * episode back on the "keep watching" row every single time.
     */
    fun playNext() {
        val next = VodPlayback.after(request) ?: return
        positionMs = 0L
        durationMs = 0L
        retryToken = 0
        request = next
        aspectMode = settings.aspectFor(next.resumeKey)
        barShownAt = System.currentTimeMillis()
        barVisible = true
    }

    LaunchedEffect(advanceToken) {
        if (advanceToken > 0) playNext()
    }

    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
        val total = exoPlayer.duration
        exoPlayer.seekTo(if (total > 0L) target.coerceAtMost(total) else target)
        positionMs = exoPlayer.currentPosition
        showBar()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .alsoTappable(onClick = { showBar() })
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        when {
                            optionsPage != OptionsPage.NONE -> {
                                optionsPage = if (optionsPage == OptionsPage.ROOT) {
                                    OptionsPage.NONE
                                } else {
                                    OptionsPage.ROOT
                                }
                            }

                            actionsFocused -> actionsFocused = false
                            barVisible -> barVisible = false
                            else -> {
                                rememberPosition()
                                onExit()
                            }
                        }
                        true
                    }

                    // Left and right scrub — unless the focus has gone down into
                    // the icons, which then walk under the same two keys.
                    Key.DirectionLeft, Key.MediaRewind -> {
                        if (optionsPage != OptionsPage.NONE || actionsFocused) false else {
                            seekBy(-SEEK_STEP_MS); true
                        }
                    }

                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (optionsPage != OptionsPage.NONE || actionsFocused) false else {
                            seekBy(+SEEK_STEP_MS); true
                        }
                    }

                    Key.DirectionDown -> {
                        if (optionsPage != OptionsPage.NONE) false else {
                            showBar()
                            actionsFocused = true
                            true
                        }
                    }

                    Key.DirectionUp -> {
                        if (optionsPage != OptionsPage.NONE) false else {
                            actionsFocused = false
                            showBar()
                            true
                        }
                    }

                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        when {
                            optionsPage != OptionsPage.NONE -> false
                            // An icon has the focus: let it answer its own press.
                            actionsFocused -> false
                            barVisible -> {
                                barVisible = false; true
                            }

                            else -> {
                                showBar(); true
                            }
                        }
                    }

                    else -> {
                        // Same two-step as the live player: the row has to be on
                        // screen before a colour does anything. A film has nothing
                        // to zap back to, so red jumps to the beginning instead.
                        val color = event.key.asColorKey()
                        when {
                            barVisible && color != null -> {
                                when (color) {
                                    ColorKey.RED -> {
                                        exoPlayer.seekTo(0L)
                                        positionMs = 0L
                                        showBar()
                                    }
                                    ColorKey.GREEN -> optionsPage = OptionsPage.AUDIO
                                    ColorKey.YELLOW -> optionsPage = OptionsPage.SUBTITLE
                                    ColorKey.BLUE -> optionsPage = OptionsPage.ASPECT
                                }
                                true
                            }

                            event.key.opensColorBar() -> {
                                showBar()
                                actionsFocused = true
                                true
                            }

                            else -> false
                        }
                    }
                }
            }
    ) {
        val resize = when (aspectMode) {
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
                    player = exoPlayer
                }
            },
            update = { it.resizeMode = resize },
            modifier = Modifier.fillMaxSize()
        )

        if (buffering && failure == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.Accent)
            }
        }

        failure?.let { message ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(28.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = t.playbackFailed,
                        style = MaterialTheme.typography.titleMedium,
                        color = Brand.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.TextSecondary
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = barVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ControlBar(
                title = request.title,
                subtitle = request.subtitle,
                positionMs = positionMs,
                durationMs = durationMs,
                actionsFocused = actionsFocused,
                actions = listOf(
                    ColorAction(
                        key = ColorKey.RED,
                        label = t.colors.fromStart,
                        onSelect = {
                            exoPlayer.seekTo(0L)
                            positionMs = 0L
                            showBar()
                        }
                    ),
                    ColorAction(
                        key = ColorKey.GREEN,
                        label = t.colors.audio,
                        onSelect = { optionsPage = OptionsPage.AUDIO }
                    ),
                    ColorAction(
                        key = ColorKey.YELLOW,
                        label = t.colors.subtitles,
                        onSelect = { optionsPage = OptionsPage.SUBTITLE }
                    ),
                    ColorAction(
                        key = ColorKey.BLUE,
                        label = t.colors.picture,
                        detail = aspectMode.shortLabel(),
                        onSelect = { optionsPage = OptionsPage.ASPECT }
                    ),
                    ColorAction(
                        key = null,
                        label = t.vod.nextEpisode,
                        enabled = VodPlayback.after(request) != null,
                        onSelect = {
                            // Skipped by hand, mid-episode: that position is worth
                            // keeping, unlike one reached by watching to the end.
                            rememberPosition()
                            playNext()
                        }
                    ),
                    ColorAction(
                        key = null,
                        label = t.options,
                        onSelect = { optionsPage = OptionsPage.ROOT }
                    )
                )
            )
        }

        AnimatedVisibility(
            visible = optionsPage != OptionsPage.NONE,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            PlayerOptionsPanel(
                page = optionsPage,
                player = exoPlayer,
                aspectMode = aspectMode,
                aspectScope = request.title,
                isFavorite = false,
                onPage = { optionsPage = it },
                onAspect = {
                    aspectMode = it
                    settings.saveAspectFor(request.resumeKey, it)
                },
                onToggleFavorite = null,
                onClose = { optionsPage = OptionsPage.NONE }
            )
        }
    }
}

@Composable
private fun ControlBar(
    title: String,
    subtitle: String,
    positionMs: Long,
    durationMs: Long,
    actions: List<ColorAction>,
    actionsFocused: Boolean
) {
    val t = LocalStrings.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                )
            )
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.AccentSoft
            )
        }

        Spacer(Modifier.height(14.dp))

        val fraction = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brand.SurfaceHigh)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brand.Accent)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = positionMs.asTimeLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = Brand.TextPrimary
            )
            Text(
                text = t.vod.playerHint,
                style = MaterialTheme.typography.labelSmall,
                color = Brand.TextSecondary
            )
            Text(
                text = if (durationMs > 0L) durationMs.asTimeLabel() else "",
                style = MaterialTheme.typography.labelMedium,
                color = Brand.TextPrimary
            )
        }

        // The icons sit under the bar rather than in a panel of their own: the
        // shape every streaming app uses, and the one that survives having no
        // coloured keys — ▼ walks into them, ▲ goes back to scrubbing.
        Spacer(Modifier.height(14.dp))
        ColorBar(
            actions = actions,
            autoFocus = actionsFocused,
            showHint = false,
            background = Color.Transparent
        )
    }
}

/** Thirty seconds: an advert break in two presses, a missed line in one. */
private const val SEEK_STEP_MS = 30_000L

private const val BAR_TIMEOUT_MS = 5_000L
