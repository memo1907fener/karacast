package com.safir.iptv.ui.multiview

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.safir.iptv.IptvApp
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.buildPlayer
import com.safir.iptv.util.isSeparatorName
import com.safir.iptv.util.repairSilentAudio
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

/**
 * Mehrere Sender gleichzeitig — zwei nebeneinander oder vier im Quadrat.
 *
 * Jedes Feld hat seinen eigenen Player. Zu hören ist nur das ausgewählte; wer mit
 * den Pfeiltasten weiterspringt, nimmt den Ton mit. OK auf einem Feld öffnet ein
 * kleines Menü: Sender wechseln, Vollbild, Entfernen. Die Belegung bleibt
 * gespeichert, beim nächsten Mal laufen dieselben Sender wieder.
 *
 * Bei vier Feldern wird jedes Bild auf 720p begrenzt: vier volle HD-Ströme
 * dekodiert kaum ein Fernseher gleichzeitig, und auf einem Viertel des
 * Bildschirms sieht man den Unterschied nicht.
 */
@Composable
fun MultiviewScreen(onBack: () -> Unit, onFullscreen: (Channel) -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val container = (context.applicationContext as IptvApp).container
    val settings = container.settings
    val repository = container.playlistRepository

    var count by remember { mutableIntStateOf(settings.multiviewSlots) }
    val slots = remember { mutableStateListOf<Channel?>(null, null, null, null) }
    val players = remember { mutableStateListOf<ExoPlayer?>(null, null, null, null) }
    val failed = remember { mutableStateListOf(false, false, false, false) }
    var focused by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var pickFor by remember { mutableStateOf<Int?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val requesters = remember { List(SLOT_MAX) { FocusRequester() } }

    // Die zuletzt gespeicherte Belegung — oder, beim ersten Mal, der zuletzt
    // gesehene Sender im ersten Feld.
    LaunchedEffect(Unit) {
        val ids = settings.multiviewChannels
        ids.take(SLOT_MAX).forEachIndexed { index, id ->
            if (id.isNotBlank()) slots[index] = repository.channelById(id)
        }
        if (slots.all { it == null }) {
            settings.lastChannelId?.let { slots[0] = repository.channelById(it) }
        }
        loaded = true
        delay(150)
        runCatching { requesters[0].requestFocus() }
    }

    fun save() {
        settings.multiviewChannels = slots.map { it?.id.orEmpty() }
    }

    // Ein Player je sichtbarem, belegtem Feld. Fällt ein Feld weg (4 → 2 oder
    // „Entfernen“), wird sein Player sofort freigegeben — das gibt auch die
    // Verbindung beim Anbieter wieder frei.
    for (index in 0 until SLOT_MAX) {
        key(index) {
            val channel = slots[index]
            val visible = index < count
            LaunchedEffect(visible, channel?.id, loaded) {
                if (!loaded) return@LaunchedEffect
                failed[index] = false
                if (!visible || channel == null) {
                    players[index]?.release()
                    players[index] = null
                    return@LaunchedEffect
                }
                val player = players[index] ?: buildPlayer(
                    context,
                    minBufferMs = 2_000,
                    maxBufferMs = 12_000,
                    bufferForPlaybackMs = 1_000,
                    bufferAfterRebufferMs = 2_000
                ).also { created ->
                    created.playWhenReady = true
                    created.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            failed[index] = true
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) failed[index] = false
                        }

                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            created.repairSilentAudio()
                        }
                    })
                    players[index] = created
                }
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(if (count == 4) 1280 else 1920, if (count == 4) 720 else 1080)
                    .build()
                player.stop()
                val url = channel.streamUrl
                player.setMediaItem(
                    MediaItem.Builder()
                        .setUri(url)
                        .apply {
                            if (url.substringBefore('?').endsWith(".m3u8", true)) {
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                            }
                        }
                        .build()
                )
                player.prepare()
                player.play()
            }
        }
    }

    // Nur das ausgewählte Feld ist zu hören.
    SideEffect {
        players.forEachIndexed { index, player -> player?.volume = if (index == focused) 1f else 0f }
    }

    DisposableEffect(Unit) {
        onDispose {
            players.forEach { it?.release() }
        }
    }

    BackHandler(enabled = menuFor != null || pickFor != null) {
        if (pickFor != null) pickFor = null else menuFor = null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            // ---------------------------------------------------------- Kopf
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(t.multiview.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Brand.TextPrimary)
                Text(
                    t.multiview.hint,
                    fontSize = 11.sp,
                    color = Brand.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TvButton(onClick = {
                    count = if (count == 4) 2 else 4
                    settings.multiviewSlots = count
                    if (focused >= count) focused = 0
                }) {
                    Text(if (count == 4) t.multiview.two else t.multiview.four, fontSize = 12.sp)
                }
                TvButton(onClick = onBack) { Text(t.multiview.back, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(8.dp))

            // ---------------------------------------------------------- Felder
            val rows = if (count == 4) listOf(listOf(0, 1), listOf(2, 3)) else listOf(listOf(0, 1))
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { row ->
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { index ->
                            Slot(
                                channel = slots[index],
                                player = players[index],
                                failed = failed[index],
                                selected = focused == index,
                                emptyLabel = t.multiview.pick,
                                cannotPlay = t.multiview.cannotPlay,
                                onFocused = { focused = index },
                                onClick = {
                                    if (slots[index] == null) pickFor = index else menuFor = index
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .focusRequester(requesters[index])
                            )
                        }
                    }
                }
            }
        }

        menuFor?.let { index ->
            val channel = slots[index]
            SlotMenu(
                title = channel?.name.orEmpty(),
                onChange = {
                    menuFor = null
                    pickFor = index
                },
                onFullscreen = {
                    menuFor = null
                    channel?.let(onFullscreen)
                },
                onRemove = {
                    menuFor = null
                    slots[index] = null
                    save()
                },
                onCancel = { menuFor = null }
            )
        }

        pickFor?.let { index ->
            ChannelPicker(
                onPick = { channel ->
                    slots[index] = channel
                    save()
                    pickFor = null
                    runCatching { requesters[index].requestFocus() }
                },
                onCancel = { pickFor = null }
            )
        }
    }
}

@Composable
private fun Slot(
    channel: Channel?,
    player: ExoPlayer?,
    failed: Boolean,
    selected: Boolean,
    emptyLabel: String,
    cannotPlay: String,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            focusedContainerColor = Brand.Surface,
            pressedContainerColor = Brand.Surface
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Brand.Accent), shape = shape)
        ),
        modifier = modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .alsoTappable(onClick)
    ) {
        Box(Modifier.fillMaxSize().clip(shape).background(Color.Black), contentAlignment = Alignment.Center) {
            if (player != null) {
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
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (channel == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Brand.TextSecondary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(emptyLabel, fontSize = 14.sp, color = Brand.TextSecondary)
                }
            } else {
                if (failed) {
                    Text(cannotPlay, fontSize = 14.sp, color = Brand.Danger)
                }
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Brand.Accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        channel.name,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotMenu(
    title: String,
    onChange: () -> Unit,
    onFullscreen: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit
) {
    val t = LocalStrings.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { first.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brand.Surface)
                .padding(24.dp)
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Brand.TextPrimary, maxLines = 1)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(onClick = onFullscreen, modifier = Modifier.focusRequester(first)) {
                    Text(t.multiview.fullscreen)
                }
                TvButton(onClick = onChange) { Text(t.multiview.change) }
                TvButton(onClick = onRemove) { Text(t.multiview.remove) }
                TvButton(onClick = onCancel) { Text(t.multiview.back) }
            }
        }
    }
}

/** Gruppen links, Sender rechts — wie in Live TV, nur knapper. */
@Composable
private fun ChannelPicker(onPick: (Channel) -> Unit, onCancel: () -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val repository = (context.applicationContext as IptvApp).container.playlistRepository
    val categories by remember { repository.observeLiveCategories() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var category by remember { mutableStateOf<String?>(null) }
    val channels by remember(category) {
        category?.let { repository.observeChannels(it) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val firstCategory = remember { FocusRequester() }
    val firstChannel = remember { FocusRequester() }

    LaunchedEffect(categories.isNotEmpty()) {
        if (categories.isEmpty()) return@LaunchedEffect
        if (category == null) category = categories.first().id
        delay(100)
        runCatching { firstCategory.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(16.dp))
                .background(Brand.Surface)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.multiview.pick,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                TvButton(onClick = onCancel) { Text(t.multiview.back, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LazyColumn(Modifier.weight(0.4f).fillMaxHeight()) {
                    items(categories, key = { "c" + it.id }) { entry ->
                        val index = categories.indexOf(entry)
                        PickerRow(
                            label = entry.name,
                            selected = entry.id == category,
                            onFocused = { category = entry.id },
                            onClick = {
                                category = entry.id
                                runCatching { firstChannel.requestFocus() }
                            },
                            modifier = if (index == 0) Modifier.focusRequester(firstCategory) else Modifier
                        )
                    }
                }
                val playable = channels.filterNot { it.id.startsWith("hdr:") || isSeparatorName(it.name) }
                LazyColumn(Modifier.weight(0.6f).fillMaxHeight()) {
                    items(playable, key = { "k" + it.id }) { channel ->
                        PickerRow(
                            label = channel.name,
                            selected = false,
                            onFocused = {},
                            onClick = { onPick(channel) },
                            modifier = if (channel == playable.first()) Modifier.focusRequester(firstChannel) else Modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Brand.SurfaceHigh else Color.Transparent,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = if (selected) Brand.Accent else Brand.TextPrimary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .alsoTappable(onClick)
    ) {
        Text(
            label,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

private const val SLOT_MAX = 4
