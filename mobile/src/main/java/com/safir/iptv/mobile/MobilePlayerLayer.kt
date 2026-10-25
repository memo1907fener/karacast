package com.safir.iptv.mobile

import android.app.Application
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.Program
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Der eine Player der Handy-App — wie bei YouTube.
 *
 * Er ist keine Seite, zu der man navigiert, sondern liegt über allen Seiten.
 * Groß füllt er den Bildschirm; nach unten gewischt (oder mit Zurück) wird er
 * zum kleinen Fenster über der Leiste und spielt weiter, während man in der
 * App sucht, blättert, Filme anschaut. Ein Tipp darauf macht ihn wieder groß,
 * das X beendet ihn. Live TV, Aufzeichnungen, Filme und Folgen gehen alle hier
 * durch, immer nur eins zur Zeit — viele Anbieter erlauben nur eine Verbindung.
 *
 * Groß und klein ist derselbe Player mit demselben Stream: es wird nichts neu
 * verbunden, nur anders gezeichnet.
 */
object PlayerLayer {

    sealed interface Item {
        val id: Long
    }

    data class Live(
        override val id: Long,
        val channelId: String,
        val categoryId: String,
        val archiveStartMs: Long = 0L,
        val archiveMinutes: Int = 0
    ) : Item

    /** Der Film selbst steht in [com.safir.iptv.ui.vod.VodPlayback.pending]. */
    data class Vod(override val id: Long) : Item

    val item = MutableStateFlow<Item?>(null)
    val expanded = MutableStateFlow(false)

    /**
     * Groß und quer über den ganzen Bildschirm statt hochkant mit Liste darunter.
     * Kommt vom Vollbild-Knopf oder vom Querlegen des Handys ([RotateToFullscreen]).
     */
    val fullscreen = MutableStateFlow(false)

    /** Der Live-Kanal, der gerade läuft (auch nach dem Umschalten im Player). */
    val liveChannelId = MutableStateFlow<String?>(null)

    private var counter = 0L

    /** Ein Kanal: klein als Vorschau ([expand] = false) oder gleich im Vollbild. */
    fun playLive(channel: Channel, categoryId: String, expand: Boolean) {
        item.value = Live(++counter, channel.id, categoryId)
        liveChannelId.value = channel.id
        fullscreen.value = false
        expanded.value = expand
    }

    fun playLive(channelId: String, categoryId: String) {
        item.value = Live(++counter, channelId, categoryId)
        liveChannelId.value = channelId
        fullscreen.value = false
        expanded.value = true
    }

    fun playArchive(channel: Channel, program: Program) {
        val minutes = (program.durationMs / 60_000L).toInt().coerceAtLeast(1)
        item.value = Live(++counter, channel.id, com.safir.iptv.domain.model.ALL_CATEGORY_ID, program.startMs, minutes)
        liveChannelId.value = null
        fullscreen.value = false
        expanded.value = true
    }

    /** Spielt, was in VodPlayback.pending steht, im Vollbild. */
    fun playVod() {
        item.value = Vod(++counter)
        liveChannelId.value = null
        fullscreen.value = false
        expanded.value = true
    }

    fun expand() {
        if (item.value != null) expanded.value = true
    }

    fun collapse() {
        fullscreen.value = false
        expanded.value = false
    }

    fun close() {
        item.value = null
        liveChannelId.value = null
        fullscreen.value = false
        expanded.value = false
    }

    /** Höhe des kleinen Fensters samt Rand — so viel Platz halten die Reiter frei. */
    val MiniHeight: Dp = 80.dp
}

/**
 * Jede Wiedergabe bekommt ihren eigenen ViewModel-Speicher, der mit ihr geht:
 * so läuft nach dem Schließen nichts im Hintergrund weiter, und der nächste
 * Kanal beginnt sauber.
 */
private class SessionOwner(private val app: Application) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    override val defaultViewModelCreationExtras: CreationExtras =
        MutableCreationExtras().apply { set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, app) }
}

/**
 * Zeichnet den Player über allem.
 *
 * @param bottomInset wie weit das kleine Fenster vom unteren Rand entfernt sitzt
 *   — über der Reiterleiste, wenn sie zu sehen ist.
 */
@Composable
fun PlayerLayerHost(bottomInset: Dp) {
    val item by PlayerLayer.item.collectAsStateWithLifecycle()
    val expanded by PlayerLayer.expanded.collectAsStateWithLifecycle()
    val current = item ?: return
    val app = LocalContext.current.applicationContext as Application

    key(current.id) {
        val owner = remember { SessionOwner(app) }
        DisposableEffect(owner) {
            onDispose { owner.viewModelStore.clear() }
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    if (expanded) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomInset)
                    }
                ) {
                    when (current) {
                        is PlayerLayer.Live -> MobileLivePlayer(
                            channelId = current.channelId,
                            categoryId = current.categoryId,
                            collapsed = !expanded,
                            onCollapse = PlayerLayer::collapse,
                            onExpand = PlayerLayer::expand,
                            onClose = PlayerLayer::close,
                            onChannel = { channel ->
                                if (current.archiveStartMs == 0L) {
                                    PlayerLayer.liveChannelId.value = channel?.id
                                }
                            },
                            archiveStartMs = current.archiveStartMs,
                            archiveDurationMin = current.archiveMinutes
                        )

                        is PlayerLayer.Vod -> MobileVodPlayer(
                            collapsed = !expanded,
                            onCollapse = PlayerLayer::collapse,
                            onExpand = PlayerLayer::expand,
                            onClose = PlayerLayer::close
                        )
                    }
                }
            }
        }
    }
}

/**
 * Was ein Wisch nach unten mit dem großen Player macht: [gesture] gehört auf das
 * Bild (dort wird gewischt), [visual] auf alles, was dabei mitschrumpfen soll.
 */
class CollapseDrag(val visual: Modifier, val gesture: Modifier)

/**
 * Nach unten ziehen verkleinert: das Bild folgt dem Finger, wird dabei kleiner,
 * und wer weit genug zieht, lässt es als kleines Fenster unten los.
 */
@Composable
fun rememberCollapseDrag(onCollapse: () -> Unit): CollapseDrag {
    val collapse by rememberUpdatedState(onCollapse)
    var offset by remember { mutableFloatStateOf(0f) }
    val visual = Modifier
        .graphicsLayer {
            // Gemessen an höchstens 600 dp, damit hochkant (ganzer Bildschirm)
            // und quer gleich weit gewischt werden muss.
            val height = size.height.coerceIn(1f, 600.dp.toPx())
            val fraction = (offset / height).coerceIn(0f, 1f)
            val scale = 1f - fraction * 0.45f
            transformOrigin = TransformOrigin(0.5f, 1f)
            translationY = offset * 0.6f
            scaleX = scale
            scaleY = scale
            alpha = 1f - fraction * 0.3f
        }
    val gesture = Modifier
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (offset > 96.dp.toPx()) collapse()
                    offset = 0f
                },
                onDragCancel = { offset = 0f },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    offset = (offset + dy).coerceAtLeast(0f)
                }
            )
        }
    return CollapseDrag(visual, gesture)
}

/**
 * Das kleine Fenster über der Leiste: Bild, Titel, Pause, Schließen.
 * Antippen macht groß, nach unten wischen schließt.
 */
@Composable
fun CollapsedPlayer(
    player: ExoPlayer,
    title: String,
    subtitle: String,
    progress: Float,
    buffering: Boolean,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val m = LocalMobileStrings.current
    val close by rememberUpdatedState(onClose)
    var dragged by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Kc.SurfaceHigh,
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .height(64.dp)
            .graphicsLayer {
                translationY = dragged
                alpha = 1f - (dragged / 200f).coerceIn(0f, 0.8f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragged > 48.dp.toPx()) close()
                        dragged = 0f
                    },
                    onDragCancel = { dragged = 0f },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        dragged = (dragged + dy).coerceAtLeast(0f)
                    }
                )
            }
            .clickable(onClick = onExpand)
    ) {
        Box {
            Row(
                Modifier.fillMaxSize().padding(start = 6.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(width = 96.dp, height = 54.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.Black),
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
                        onRelease = { it.player = null },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (buffering) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kc.Accent)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, fontSize = 12.sp, color = Kc.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onTogglePause) {
                    Icon(
                        if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (paused) m.play else m.pause
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = m.close, tint = Kc.TextSecondary)
                }
            }
            ThinProgress(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), height = 2.dp)
        }
    }
}
