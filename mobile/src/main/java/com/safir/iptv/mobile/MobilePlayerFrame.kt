package com.safir.iptv.mobile

import android.content.Context
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safir.iptv.domain.model.Channel

/**
 * Wie der große Player auf dem Bildschirm liegt — wie bei YouTube.
 *
 * Hochkant sitzt das Bild oben in 16:9, darunter steht, was läuft, die
 * Werkzeuge und die Kanalliste (oder die nächsten Folgen). Quer gedreht oder
 * mit dem Vollbild-Knopf füllt das Bild alles ([full]). Auf einem Tablet im
 * Querformat stehen Bild und Liste nebeneinander.
 *
 * Wischen nach unten auf dem Bild verkleinert ([CollapseDrag]); die Liste
 * darunter scrollt ganz normal, weil der Wisch nur auf dem Bild greift.
 */
@Composable
fun PlayerFrame(
    full: Boolean,
    drag: CollapseDrag,
    video: @Composable BoxScope.() -> Unit,
    details: @Composable ColumnScope.() -> Unit
) {
    if (full) {
        Box(
            Modifier
                .fillMaxSize()
                .then(drag.visual)
                .then(drag.gesture)
                .background(Color.Black),
            content = video
        )
        return
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(drag.visual)
            .background(Kc.Background)
    ) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Box(
                    Modifier.weight(0.62f).fillMaxHeight().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(drag.gesture), content = video)
                }
                Column(Modifier.weight(0.38f).fillMaxHeight(), content = details)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(drag.gesture), content = video)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    content = details
                )
            }
        }
    }
}

/** Ein Werkzeug unter dem Bild: Tonspur, Untertitel, Bildformat, Sleep-Timer. */
@Composable
fun ToolChip(icon: ImageVector, label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Kc.Surface,
        contentColor = if (highlighted) Kc.AccentSoft else Kc.TextPrimary,
        border = BorderStroke(1.dp, if (highlighted) Kc.Accent.copy(alpha = 0.6f) else Kc.Border),
        modifier = Modifier.height(38.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun ToolRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Eine Zeile der Kanalliste im Player — hochkant unter dem Bild, quer von rechts. */
@Composable
fun PlaylistRow(item: Channel, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) Kc.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.number.takeIf { it > 0 }?.toString().orEmpty(),
            fontSize = 12.sp,
            color = Kc.TextSecondary,
            modifier = Modifier.width(34.dp)
        )
        ChannelBadge(item.logoUrl, item.name, Modifier.size(width = 48.dp, height = 32.dp), corner = 7.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            cleanTitle(item.name),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (current) Kc.AccentSoft else Kc.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (current) LiveDot()
    }
}

/**
 * Drehen wie bei YouTube: wer das Handy quer legt, bekommt das Vollbild, wer es
 * wieder aufrichtet, die Ansicht mit Liste. Nur wenn das automatische Drehen am
 * Handy an ist — wer es gesperrt hat, nimmt den Vollbild-Knopf.
 *
 * Nach dem Knopf „Vollbild beenden“ in der Hand quer wartet es, bis das Handy
 * einmal aufrecht war; sonst ginge das Vollbild sofort wieder an.
 */
@Composable
fun RotateToFullscreen(context: Context, fullscreen: Boolean) {
    val current by rememberUpdatedState(fullscreen)
    val gate = remember { RotationGate() }
    LaunchedEffect(fullscreen) {
        if (fullscreen) gate.sawLandscape = false else gate.armed = false
    }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return
                val landscape = degrees in 65..115 || degrees in 245..295
                val portrait = degrees <= 25 || degrees >= 335
                if (!current) {
                    if (portrait) {
                        gate.armed = true
                    } else if (landscape && gate.armed && autoRotateOn(context)) {
                        gate.armed = false
                        PlayerLayer.fullscreen.value = true
                    }
                } else {
                    if (landscape) {
                        gate.sawLandscape = true
                    } else if (portrait && gate.sawLandscape && autoRotateOn(context)) {
                        gate.sawLandscape = false
                        PlayerLayer.fullscreen.value = false
                    }
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
}

private class RotationGate {
    var armed = false
    var sawLandscape = false
}

private fun autoRotateOn(context: Context): Boolean =
    runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    }.getOrDefault(false)
