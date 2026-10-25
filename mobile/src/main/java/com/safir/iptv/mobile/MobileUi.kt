package com.safir.iptv.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Die Bausteine der Handy-Oberfläche, im Stil von Entwurf A: abgerundete
 * Flächen, Chips statt Seitenleisten, ein kräftiges Orange.
 */

/**
 * Die Farben der Handy-App.
 *
 * Dasselbe Orange wie am Fernseher, aber ein helleres, wärmeres Dunkel: der
 * Fernseher steht in einem dunklen Zimmer drei Meter entfernt, das Handy liegt
 * in der Hand, oft bei Tageslicht — dort wirkte das fast schwarze TV-Schema
 * trist. Flächen heben sich deutlicher ab, Nebentext ist heller.
 */
object Kc {
    val Accent = Color(0xFFFF7A00)
    val AccentSoft = Color(0xFFFFB347)
    val AccentDeep = Color(0xFFFF4D1A)
    val Background = Color(0xFF0E1124)
    val Surface = Color(0xFF1B2140)
    val SurfaceHigh = Color(0xFF283058)
    val Border = Color(0xFF39416A)
    val TextPrimary = Color(0xFFF5F6FB)
    val TextSecondary = Color(0xFFAEB6D0)
    val Danger = Color(0xFFFF5A5A)
    val Live = Color(0xFF3DDC84)
    val NavBar = Color(0xFF141831)

    /** Orange Verlauf für Hauptknöpfe und den aktiven Chip. */
    val AccentBrush: Brush = Brush.linearGradient(listOf(Color(0xFFFF9A2E), AccentDeep))

    /**
     * Der Grund hinter allen Seiten: oben ein warmer Schimmer aus Orange und
     * Violett, nach unten das ruhige Dunkelblau.
     */
    val ScreenBrush: Brush = Brush.verticalGradient(
        0f to Color(0xFF241A3F),
        0.35f to Color(0xFF141834),
        1f to Background
    )
}

/**
 * Ein Titel ohne die Kürzel, die Anbieter davorschreiben: „NF - The Irishman“
 * → „The Irishman“, „[TR] TRT 1“ → „TRT 1“, „VOD - NETFLIX“ → „NETFLIX“. Nur
 * für die Anzeige; gesucht und abgespielt wird mit dem echten Namen.
 */
fun cleanTitle(raw: String): String {
    val cleaned = raw.replace(TitlePrefix, "").trim()
    return cleaned.ifBlank { raw.trim() }
}

private val TitlePrefix = Regex("""^(?:\s*(?:\[[^\]]{1,12}\]|\|[^|]{1,12}\||[A-Z0-9]{2,4}\s+[-|]\s))+""")

/** "7.576" → "7,6" (je nach Sprache), Unsinn bleibt weg. */
fun ratingOf(raw: String?): String? {
    val value = raw?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    return String.format(Locale.getDefault(), "%.1f", value)
}

/** Hintergrund einer ganzen Seite. */
fun Modifier.screenBackground(): Modifier = this.background(Kc.ScreenBrush)

private val MobileColors = darkColorScheme(
    primary = Kc.Accent,
    onPrimary = Color(0xFF101010),
    primaryContainer = Kc.Accent,
    onPrimaryContainer = Color(0xFF101010),
    secondary = Kc.AccentSoft,
    onSecondary = Color(0xFF101010),
    secondaryContainer = Kc.SurfaceHigh,
    onSecondaryContainer = Kc.TextPrimary,
    background = Kc.Background,
    onBackground = Kc.TextPrimary,
    surface = Kc.Background,
    onSurface = Kc.TextPrimary,
    surfaceVariant = Kc.SurfaceHigh,
    onSurfaceVariant = Kc.TextSecondary,
    surfaceContainerLowest = Kc.Background,
    surfaceContainerLow = Kc.NavBar,
    surfaceContainer = Kc.Surface,
    surfaceContainerHigh = Kc.Surface,
    surfaceContainerHighest = Kc.SurfaceHigh,
    outline = Kc.Border,
    outlineVariant = Kc.Border,
    error = Kc.Danger,
    onError = Color(0xFF101010)
)

private val MobileType = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Black),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold)
    )
}

@Composable
fun KaracastMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MobileColors, typography = MobileType, content = content)
}

/** Das App-Symbol: oranges Quadrat, weißes Dreieck — gezeichnet, nicht geladen. */
@Composable
fun BrandMark(size: Dp = 34.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension * 0.28f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFFFA23A), Kc.Accent)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
        )
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.39f, h * 0.30f)
            lineTo(w * 0.39f, h * 0.70f)
            lineTo(w * 0.71f, h * 0.50f)
            close()
        }
        drawPath(path, Color.White)
    }
}

@Composable
fun BrandTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        Spacer(Modifier.width(10.dp))
        Text("Karacast", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp)
    }
}

/** Senderlogo mit Kürzel darunter, bis (oder falls nie) das Logo kommt. */
@Composable
fun ChannelBadge(
    url: String?,
    name: String,
    modifier: Modifier = Modifier.size(width = 52.dp, height = 36.dp),
    corner: Dp = 8.dp
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Kc.SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        var loaded by remember(url) { mutableStateOf(false) }
        if (!loaded) {
            Text(
                text = initials(name),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Kc.TextSecondary,
                maxLines = 1
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                onState = { loaded = it is AsyncImagePainter.State.Success }
            )
        }
    }
}

private fun initials(name: String): String = name.trim()
    .split(' ', '-', '_')
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifBlank { "TV" }

/**
 * Ein Plakat. Ohne Bild bleibt eine Fläche mit dem Titel darauf — eine Farbe aus
 * dem Titel gerechnet, damit nicht alle leeren Plakate gleich aussehen.
 */
@Composable
fun Poster(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    titleSize: Int = 14
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(placeholderColor(title)),
        contentAlignment = Alignment.BottomStart
    ) {
        var loaded by remember(url) { mutableStateOf(false) }
        if (!loaded) {
            Text(
                text = title,
                fontSize = titleSize.sp,
                fontWeight = FontWeight.Black,
                lineHeight = (titleSize + 2).sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp)
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { loaded = it is AsyncImagePainter.State.Success }
            )
        }
    }
}

private val PlaceholderColors = listOf(
    Color(0xFF2B3F5C), Color(0xFF3D2B5C), Color(0xFF5C4A2B), Color(0xFF2B5C4A),
    Color(0xFF5C2A1D), Color(0xFF1D4A5C), Color(0xFF4A1D3D), Color(0xFF2E2E48)
)

fun placeholderColor(seed: String): Color =
    PlaceholderColors[(seed.hashCode() and 0x7fffffff) % PlaceholderColors.size]

/** Dünner Fortschrittsbalken unter einer Sendung oder einem Film. */
@Composable
fun ThinProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    track: Color = Kc.Border,
    height: Dp = 3.dp
) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .background(Kc.Accent)
        )
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                text = action,
                color = Kc.AccentSoft,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

/** Ein Chip in der Gruppen- oder Kategorienleiste. Ausgewählt: Orange. */
@Composable
fun KcChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color.Transparent else Kc.Surface,
        contentColor = if (selected) Color.White else Kc.TextPrimary,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Kc.Border),
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .then(if (selected) Modifier.background(Kc.AccentBrush) else Modifier)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
            if (count != null && count > 0) {
                Text(
                    text = count.toString(),
                    fontSize = 13.sp,
                    color = chipCountColor(selected),
                    maxLines = 1
                )
            }
        }
    }
}

private fun chipCountColor(selected: Boolean): Color =
    if (selected) Color.White.copy(alpha = 0.8f) else Kc.TextSecondary

/** Eine waagrechte Chipleiste über die ganze Breite. */
@Composable
fun <T> ChipRow(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    chip: @Composable (T) -> Unit
) {
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = key) { chip(it) }
    }
}

/** Ein ruhiger Hinweis mitten auf leerer Fläche. */
@Composable
fun EmptyHint(title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = Kc.TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Grüner Punkt vor einem laufenden Kanal. */
@Composable
fun LiveDot(size: Dp = 7.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 2)).background(Kc.Live))
}

private val clockFormat get() = SimpleDateFormat("HH:mm", Locale.getDefault())

fun clockOf(ms: Long): String = clockFormat.format(Date(ms))

/** 1:23:45 oder 23:45. */
fun durationOf(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Ein Verlauf von unten, damit Text auf einem Bild lesbar bleibt. */
val BottomScrim: Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.45f to Color.Transparent,
    1f to Kc.Background.copy(alpha = 0.92f)
)
