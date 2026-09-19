package com.safir.iptv.ui.dashboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safir.iptv.IptvApp
import com.safir.iptv.ui.i18n.AppLanguage
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.asClock
import kotlinx.coroutines.delay

/**
 * The home screen. Three large targets in the middle — live television on the
 * left, series and films stacked on the right — and everything else on a thin
 * row underneath. Nothing here needs to be read: each tile carries an icon big
 * enough to recognise from the sofa.
 */
@Composable
fun DashboardScreen(
    onOpenLive: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val language by AppLocale.current.collectAsStateWithLifecycle()

    val liveFocus = remember { FocusRequester() }
    var languageOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { liveFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Brand.Background)) {
        Column(Modifier.fillMaxSize()) {

            // ------------------------------------------------------------ header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brand.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        tint = Brand.Background,
                        modifier = Modifier.size(22.dp)
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
                LanguageButton(
                    current = language,
                    onClick = { languageOpen = true }
                )
                Spacer(Modifier.width(20.dp))
                DashboardClock()
            }

            // ------------------------------------------------------- three tiles
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Tile(
                    icon = Icons.Default.LiveTv,
                    label = s.liveTv,
                    caption = s.liveTvCaption,
                    iconSize = 64.dp,
                    labelSize = 28.sp,
                    captionSize = 13.sp,
                    contentPadding = 16.dp,
                    onClick = onOpenLive,
                    modifier = Modifier
                        .weight(1.9f)
                        .fillMaxHeight()
                        .focusRequester(liveFocus)
                )

                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Tile(
                        icon = Icons.Default.Theaters,
                        label = s.series,
                        caption = s.vod.seriesCaption,
                        iconSize = 28.dp,
                        labelSize = 18.sp,
                        captionSize = 11.sp,
                        contentPadding = 6.dp,
                        onClick = onOpenSeries,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    Tile(
                        icon = Icons.Default.Movie,
                        label = s.movies,
                        caption = s.vod.moviesCaption,
                        iconSize = 28.dp,
                        labelSize = 18.sp,
                        captionSize = 11.sp,
                        contentPadding = 6.dp,
                        onClick = onOpenMovies,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }

            // ------------------------------------------------------- bottom row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SmallAction(
                    icon = Icons.Default.Search,
                    label = s.search,
                    onClick = onOpenSearch,
                    modifier = Modifier.weight(1f)
                )
                SmallAction(
                    icon = Icons.Default.Tv,
                    label = s.guide,
                    onClick = onOpenGuide,
                    modifier = Modifier.weight(1f)
                )
                SmallAction(
                    icon = Icons.Default.Settings,
                    label = s.settings,
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (languageOpen) {
            LanguagePicker(
                current = language,
                onPick = { picked ->
                    AppLocale.set(picked)
                    (context.applicationContext as IptvApp)
                        .container.settings.language = picked.tag
                    languageOpen = false
                },
                onDismiss = { languageOpen = false }
            )
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    caption: String,
    iconSize: androidx.compose.ui.unit.Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
    captionSize: androidx.compose.ui.unit.TextUnit,
    contentPadding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    val shape = RoundedCornerShape(18.dp)

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (dimmed) Brand.Surface else Brand.SurfaceHigh,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = if (dimmed) Brand.TextSecondary else Brand.TextPrimary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Brand.AccentSoft), shape = shape)
        ),
        modifier = modifier.alsoTappable(onClick)
    ) {
        Column(
            Modifier.fillMaxSize().padding(contentPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = labelSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = caption,
                fontSize = captionSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SmallAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier.alsoTappable(onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardClock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000L)
        }
    }
    Text(
        text = now.asClock(),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Brand.TextSecondary
    )
}

/**
 * One flag, not four. A row of them was fine while there were four languages and
 * would be a mess at eight, so the header carries the current flag and the list
 * drops down from it — the shape every menu on this box already has, and one that
 * stays the same width whatever we add later.
 */
@Composable
private fun LanguageButton(current: AppLanguage, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.SurfaceHigh,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = Brand.TextPrimary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        modifier = Modifier.alsoTappable(onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = current.flag, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = current.tag.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "▾", fontSize = 12.sp)
        }
    }
}

/**
 * The dropped-down list: flag and the language's own name, so nobody has to read a
 * language they do not speak in order to find the one they do. Opens on the entry
 * that is already active, which makes the current setting obvious and puts the
 * D-pad one press away from its neighbours.
 */
@Composable
private fun LanguagePicker(
    current: AppLanguage,
    onPick: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedRow = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { selectedRow.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .alsoTappable(onDismiss)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                if (event.key == Key.Back || event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 74.dp, end = 138.dp)
                .width(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brand.Surface)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppLanguage.entries.forEach { language ->
                val selected = language == current
                Surface(
                    onClick = { onPick(language) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) Brand.SurfaceHigh else Color.Transparent,
                        focusedContainerColor = Brand.Accent,
                        pressedContainerColor = Brand.Accent,
                        contentColor = if (selected) Brand.TextPrimary else Brand.TextSecondary,
                        focusedContentColor = Brand.Background,
                        pressedContentColor = Brand.Background
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alsoTappable({ onPick(language) })
                        .then(
                            if (selected) Modifier.focusRequester(selectedRow) else Modifier
                        )
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = language.flag, fontSize = 20.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = language.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) Text(text = "●", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
