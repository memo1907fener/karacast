package com.safir.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.safir.iptv.ui.channels.Clock
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand

/** Die Bereiche, zwischen denen die Kopfleiste wechselt. */
enum class TopTab { HOME, LIVE, MOVIES, SERIES, GUIDE }

/**
 * Die Kopfleiste über Live TV, Filmen, Serien und dem Programm.
 *
 * Sie liegt über der Navigation, nicht in den einzelnen Bildschirmen: beim
 * Wechsel zwischen den Bereichen bleibt sie stehen, nur darunter wechselt der
 * Inhalt. Der aktive Bereich ist unterstrichen; ▲ aus jedem Bildschirm führt
 * hierher.
 */
@Composable
fun AppTopBar(
    active: TopTab,
    onTab: (TopTab) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Brand.Background)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BrandMark()
        Spacer(Modifier.width(14.dp))
        TopTabButton(s.home, active == TopTab.HOME) { onTab(TopTab.HOME) }
        TopTabButton(s.liveTv, active == TopTab.LIVE) { onTab(TopTab.LIVE) }
        TopTabButton(s.movies, active == TopTab.MOVIES) { onTab(TopTab.MOVIES) }
        TopTabButton(s.series, active == TopTab.SERIES) { onTab(TopTab.SERIES) }
        TopTabButton(s.guide, active == TopTab.GUIDE) { onTab(TopTab.GUIDE) }
        Spacer(Modifier.weight(1f))
        TopIcon(Icons.Default.Search, s.search, onSearch)
        TopIcon(Icons.Default.Settings, s.settings, onSettings)
        Spacer(Modifier.width(8.dp))
        Clock(large = true)
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Brand.Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Tv, contentDescription = null, tint = Brand.Background, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text = "Karacast", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Brand.TextPrimary, maxLines = 1)
    }
}

/** Ein Reiter. Der aktive ist fett und unterstrichen — und bleibt anwählbar. */
@Composable
private fun TopTabButton(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = if (active) Brand.TextPrimary else Brand.TextSecondary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.alsoTappable(onClick)
    ) {
        Column(
            Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(22.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) Brand.Accent else Color.Transparent)
            )
        }
    }
}

@Composable
private fun TopIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = Brand.TextSecondary,
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

/**
 * „Suche“ in der Kopfleiste meint, was gerade darunter steht: Kanäle in Live TV,
 * Filme bei den Filmen, Serien bei den Serien. Die Leiste weiß davon nichts; sie
 * sagt nur Bescheid, und der Bildschirm darunter hört zu.
 */
object TopBarSearch {
    val requests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
