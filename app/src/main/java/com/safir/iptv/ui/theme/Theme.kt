package com.safir.iptv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * A TV UI is viewed from three metres away in a dark room, so the palette is
 * deliberately dark with one high-chroma accent and nothing else competing.
 */
object Brand {
    val Accent = Color(0xFFFF7A00)
    val AccentSoft = Color(0xFFFFBA3C)
    val Background = Color(0xFF0A0C14)
    val Surface = Color(0xFF161A26)
    val SurfaceHigh = Color(0xFF20263A)
    val Border = Color(0xFF2C3348)
    val TextPrimary = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFF9AA3B8)
    val Danger = Color(0xFFFF5A5A)
    val Live = Color(0xFF3DDC84)
}

/** 48dp of overscan padding — the outer edge of a TV screen is often cropped. */
val ScreenPaddingHorizontal = 48.dp
val ScreenPaddingVertical = 32.dp

private val SafirColorScheme = darkColorScheme(
    primary = Brand.Accent,
    onPrimary = Color(0xFF101010),
    primaryContainer = Brand.Accent,
    onPrimaryContainer = Color(0xFF101010),
    secondary = Brand.AccentSoft,
    onSecondary = Color(0xFF101010),
    background = Brand.Background,
    onBackground = Brand.TextPrimary,
    surface = Brand.Surface,
    onSurface = Brand.TextPrimary,
    surfaceVariant = Brand.SurfaceHigh,
    onSurfaceVariant = Brand.TextSecondary,
    border = Brand.Border,
    error = Brand.Danger,
    onError = Color(0xFF101010)
)

@Composable
fun SafirTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SafirColorScheme, content = content)
}
