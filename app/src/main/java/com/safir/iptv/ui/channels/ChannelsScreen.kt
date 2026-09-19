package com.safir.iptv.ui.channels

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.safir.iptv.domain.model.GROUP_PREFIX
import com.safir.iptv.domain.model.NowNext
import com.safir.iptv.domain.model.RECENT_CATEGORY_ID
import com.safir.iptv.ui.AppViewModelFactory
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
import com.safir.iptv.util.buildPlayer
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.asClock
import com.safir.iptv.util.formatDuration
import kotlinx.coroutines.delay

/** How long a channel must stay focused before the preview opens a connection. */
private const val PREVIEW_DELAY_MS = 800L

/** Window after a screen change in which a stray OK key-up is discarded. */
private const val SELECT_GUARD_MS = 600L

/**
 * Browsing works on two levels, the way a set-top box does: a screen of categories,
 * and then the inside of one category filling the whole display. Nothing from the
 * other level stays visible — once you are in a category, the only things on screen
 * are its channels. Back (or ◀) is always one step out.
 */
@Composable
fun ChannelsScreen(
    onPlay: (channel: Channel, categoryId: String) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackToDashboard: () -> Unit = {},
    /** Opened from the dashboard's search tile — go straight to the search field. */
    startInSearch: Boolean = false
) {
    val s = LocalStrings.current
    val viewModel: ChannelsViewModel = viewModel(factory = AppViewModelFactory)
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val nowNextMap by viewModel.nowNext.collectAsStateWithLifecycle()
    val focusTargetId by viewModel.focusTargetId.collectAsStateWithLifecycle()
    val numberMiss by viewModel.numberMiss.collectAsStateWithLifecycle()
    val inCategory by viewModel.inCategory.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()

    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    val numbers = rememberNumberEntry { number -> viewModel.jumpToNumber(number) }

    // tv-material3 acts on the key-down of OK, so the matching key-up lands on
    // whatever took focus on the new level and fires a second time. One press must
    // mean one action, so OK is ignored for a moment after a level change.
    var levelChangedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(inCategory) { levelChangedAt = System.currentTimeMillis() }

    LaunchedEffect(startInSearch) {
        if (startInSearch) viewModel.enterSearch()
    }

    // One step out, wherever that leads: search → the list, the list → the category
    // overview, and with Live TV pinned to one list (where there is no overview) →
    // the dashboard.
    val stepBack: () -> Unit = { if (!viewModel.leaveCategory()) onBackToDashboard() }

    BackHandler(enabled = inCategory) { stepBack() }

    // ------------------------------------------------------------- preview player
    val context = LocalContext.current
    val previewEnabled = remember { viewModel.previewEnabled }
    val previewMuted = remember { viewModel.previewMuted }
    var previewFailed by remember { mutableStateOf(false) }

    val previewPlayer = remember {
        if (!previewEnabled) {
            null
        } else {
            // A preview may stutter; it must above all start quickly, so it runs
            // on a much shorter buffer than the full-screen player.
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
            // Frees the provider connection slot the moment we leave this screen.
            previewPlayer?.release()
        }
    }

    // No preview on the category screen — nothing there to preview, and it would
    // hold a connection open while the user is only choosing where to go.
    val previewTarget = if (inCategory) focusedChannel ?: channels.firstOrNull() else null

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

    LaunchedEffect(numberMiss) {
        if (numberMiss != null) {
            delay(2_000)
            viewModel.clearNumberMiss()
        }
    }

    // ------------------------------------------------------------------- layout
    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
            .onPreviewKeyEvent { event ->
                // ◀ has to be caught on the key-DOWN: Compose runs its focus search
                // there, and by the time the key-up arrives the focus has already
                // moved to the back button instead of leaving the category.
                if (inCategory && !searchMode && event.key == Key.DirectionLeft) {
                    if (event.type == KeyEventType.KeyDown) stepBack()
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (isSelect &&
                    !numbers.isActive &&
                    System.currentTimeMillis() - levelChangedAt < SELECT_GUARD_MS
                ) {
                    return@onPreviewKeyEvent true
                }

                val digit = event.key.asDigit()
                when {
                    digit != null && inCategory -> {
                        numbers.append(digit)
                        true
                    }

                    numbers.isActive && (event.key == Key.DirectionCenter || event.key == Key.Enter) ->
                        numbers.submitNow { viewModel.jumpToNumber(it) }

                    numbers.isActive && event.key == Key.Back -> {
                        numbers.clear()
                        true
                    }

                    else -> false
                }
            }
    ) {
        Crossfade(targetState = inCategory, label = "level") { insideCategory ->
            if (!insideCategory) {
                CategoryPage(
                    categories = categories,
                    onOpen = viewModel::enterCategory,
                    onSearch = viewModel::enterSearch,
                    onOpenGuide = onOpenGuide,
                    onOpenSettings = onOpenSettings,
                    onHome = onBackToDashboard
                )
            } else {
                ChannelPage(
                    categoryName = categories.firstOrNull { it.id == selectedCategory }?.name
                        ?: s.channels,
                    channels = channels,
                    nowNext = nowNextMap,
                    focusTargetId = focusTargetId,
                    onFocusConsumed = viewModel::consumeFocusTarget,
                    searchMode = searchMode,
                    query = query,
                    onQueryChange = viewModel::setQuery,
                    previewPlayer = previewPlayer,
                    previewFailed = previewFailed,
                    previewTarget = previewTarget,
                    previewNowNext = previewTarget?.epgId?.let { nowNextMap[it] },
                    onPlay = { channel -> onPlay(channel, selectedCategory) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onFocusChannel = { focusedChannel = it },
                    showNumbers = selectedCategory != RECENT_CATEGORY_ID,
                    onBack = stepBack
                )
            }
        }

        NumberEntryOverlay(
            state = numbers,
            caption = s.jumpNow,
            modifier = Modifier.align(Alignment.TopStart).padding(28.dp)
        )

        numberMiss?.let { message ->
            if (!numbers.isActive) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(28.dp)
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

// ========================================================== level 1: categories

@Composable
private fun CategoryPage(
    categories: List<Category>,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onHome: () -> Unit
) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().background(Brand.Background)) {

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brand.Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Tv,
                    contentDescription = null,
                    tint = Brand.Background,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "Karacast IPTV",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Clock(large = true)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                BigRow(
                    icon = category.icon(),
                    label = category.name,
                    trailing = category.channelCount.takeIf { it > 0 }?.let { "$it" },
                    onClick = { onOpen(category.id) },
                    modifier = Modifier.widthIn(max = 900.dp)
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BigRow(
                icon = Icons.Default.Search,
                label = s.search,
                onClick = onSearch,
                modifier = Modifier.weight(1f),
                compact = true
            )
            BigRow(
                icon = Icons.Default.Tv,
                label = s.guide,
                onClick = onOpenGuide,
                modifier = Modifier.weight(1f),
                compact = true
            )
            BigRow(
                icon = Icons.Default.Settings,
                label = s.settings,
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
                compact = true
            )
            BigRow(
                icon = Icons.Default.Home,
                label = s.home,
                onClick = onHome,
                modifier = Modifier.weight(1f),
                compact = true
            )
        }
    }
}

/** An icon per category so the list is readable without reading it. */
private fun Category.icon(): ImageVector = when {
    id == FAVORITES_CATEGORY_ID -> Icons.Default.Star
    id == RECENT_CATEGORY_ID -> Icons.Default.History
    id == ALL_CATEGORY_ID -> Icons.Default.Tv
    id.startsWith(GROUP_PREFIX) -> Icons.Default.Folder
    else -> Icons.Default.Subscriptions
}

/** Deliberately oversized: this app is also for people who squint at a TV. */
@Composable
private fun BigRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    compact: Boolean = false
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth().alsoTappable(onClick)
    ) {
        Row(
            Modifier.padding(
                horizontal = if (compact) 12.dp else 22.dp,
                vertical = if (compact) 16.dp else 18.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 21.dp else 24.dp)
            )
            Spacer(Modifier.width(if (compact) 8.dp else 18.dp))
            Text(
                text = label,
                fontSize = if (compact) 15.sp else 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Text(text = trailing, fontSize = 15.sp)
            }
        }
    }
}

// ============================================================ level 2: channels

@Composable
private fun ChannelPage(
    categoryName: String,
    channels: List<Channel>,
    nowNext: Map<String, NowNext>,
    focusTargetId: String?,
    onFocusConsumed: () -> Unit,
    searchMode: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    previewPlayer: ExoPlayer?,
    previewFailed: Boolean,
    previewTarget: Channel?,
    previewNowNext: NowNext?,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onFocusChannel: (Channel) -> Unit,
    showNumbers: Boolean,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize()) {

        CategoryHeader(
            title = if (searchMode) s.search else categoryName,
            count = channels.size,
            onBack = onBack
        )

        if (searchMode) {
            TvTextField(
                value = query,
                onValueChange = onQueryChange,
                label = s.searchChannel,
                placeholder = s.typeName,
                autoFocus = true,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }

        // The list still leads, but the preview earns real estate: at 55/45 the
        // picture is big enough to judge a channel by, not just to identify it.
        Row(Modifier.weight(1f).fillMaxWidth()) {

            Box(Modifier.weight(0.55f).fillMaxHeight()) {
                if (channels.isEmpty()) {
                    EmptyState(
                        title = s.noChannels,
                        message = s.emptyList
                    )
                } else {
                    ChannelList(
                        channels = channels,
                        nowNext = nowNext,
                        focusTargetId = focusTargetId,
                        onFocusConsumed = onFocusConsumed,
                        onPlay = onPlay,
                        onToggleFavorite = onToggleFavorite,
                        onFocusChannel = onFocusChannel,
                        showNumbers = showNumbers,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            PreviewPanel(
                channel = previewTarget,
                nowNext = previewNowNext,
                previewPlayer = previewPlayer,
                previewFailed = previewFailed,
                modifier = Modifier.weight(0.45f).fillMaxHeight()
            )
        }
    }
}

/** Says where you are and that Back leads out — the only orientation that is needed. */
@Composable
private fun CategoryHeader(title: String, count: Int, onBack: () -> Unit) {
    val s = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brand.SurfaceHigh)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
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
            modifier = Modifier.alsoTappable(onBack)
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = s.back,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(s.back, style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = s.nChannels(count),
            style = MaterialTheme.typography.labelMedium,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.width(18.dp))
        Clock()
    }
}

// -------------------------------------------------------------- preview panel

/**
 * The right-hand third: a small picture at the top and, underneath it, what is
 * running on that channel right now.
 */
@Composable
private fun PreviewPanel(
    channel: Channel?,
    nowNext: NowNext?,
    // Named distinctly from PlayerView.player: inside `apply` the member would win
    // over a parameter called `player`, and the view would be assigned to itself.
    previewPlayer: ExoPlayer?,
    previewFailed: Boolean,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    Column(
        modifier = modifier
            .background(Brand.Surface)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (previewPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
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
        }

        Spacer(Modifier.height(14.dp))

        if (channel == null) {
            Text(
                s.noChannelSelected,
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.TextSecondary
            )
            return@Column
        }

        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        val now = nowNext?.now
        if (now != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveBadge()
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${now.startMs.asClock()} – ${now.endMs.asClock()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.TextSecondary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = now.title,
                style = MaterialTheme.typography.titleSmall,
                color = Brand.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            ProgressLine(
                progress = now.progressAt(System.currentTimeMillis()),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = s.stillRunning(formatDuration(now.endMs - System.currentTimeMillis())),
                style = MaterialTheme.typography.labelSmall,
                color = Brand.TextSecondary
            )
        }

        nowNext?.next?.let { next ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${s.nextUp}  ${next.startMs.asClock()}",
                style = MaterialTheme.typography.labelSmall,
                color = Brand.TextSecondary
            )
            Text(
                text = next.title,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------- channel list

@Composable
private fun ChannelList(
    channels: List<Channel>,
    nowNext: Map<String, NowNext>,
    focusTargetId: String?,
    onFocusConsumed: () -> Unit,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onFocusChannel: (Channel) -> Unit,
    showNumbers: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val jumpRequester = remember { FocusRequester() }

    LaunchedEffect(channels.firstOrNull()?.id) {
        listState.scrollToItem(0)
    }

    // Number entry lands here: scroll the row into view, then hand it the focus so
    // the D-pad carries on from that channel.
    LaunchedEffect(focusTargetId) {
        val id = focusTargetId ?: return@LaunchedEffect
        val index = channels.indexOfFirst { it.id == id }
        if (index < 0) return@LaunchedEffect
        listState.scrollToItem(index)
        delay(80)
        runCatching { jumpRequester.requestFocus() }
        delay(500)
        onFocusConsumed()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().focusRestorer(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(channels, key = { it.id }) { channel ->
            ChannelRow(
                channel = channel,
                nowNext = channel.epgId?.let { nowNext[it] },
                showNumber = showNumbers,
                onPlay = { onPlay(channel) },
                onToggleFavorite = { onToggleFavorite(channel) },
                onFocused = { onFocusChannel(channel) },
                modifier = if (channel.id == focusTargetId) {
                    Modifier.focusRequester(jumpRequester)
                } else {
                    Modifier
                }
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    nowNext: NowNext?,
    showNumber: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    val now = nowNext?.now
    val rowShape = RoundedCornerShape(10.dp)

    Surface(
        onClick = onPlay,
        onLongClick = onToggleFavorite,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            focusedContainerColor = Brand.SurfaceHigh,
            pressedContainerColor = Brand.SurfaceHigh,
            contentColor = Brand.TextPrimary,
            focusedContentColor = Brand.TextPrimary,
            pressedContentColor = Brand.TextPrimary
        ),
        shape = ClickableSurfaceDefaults.shape(rowShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Brand.Accent), shape = rowShape)
        ),
        modifier = modifier
            .fillMaxWidth()
            .alsoTappable(onClick = onPlay, onLongClick = onToggleFavorite)
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showNumber) {
                Text(
                    text = channel.number.toString().padStart(3, '0'),
                    fontSize = 15.sp,
                    color = Brand.TextSecondary,
                    modifier = Modifier.width(46.dp)
                )
            }

            ChannelLogo(
                url = channel.logoUrl,
                name = channel.name,
                modifier = Modifier.size(56.dp, 40.dp)
            )
            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (now != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${now.startMs.asClock()}  ${now.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    ProgressLine(
                        progress = now.progressAt(System.currentTimeMillis()),
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                }
            }

            if (channel.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = s.favorite,
                    tint = Brand.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------------- pieces

@Composable
private fun Clock(large: Boolean = false) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000L)
        }
    }
    Text(
        text = now.asClock(),
        fontSize = if (large) 20.sp else 15.sp,
        fontWeight = FontWeight.Bold,
        color = Brand.TextSecondary
    )
}
