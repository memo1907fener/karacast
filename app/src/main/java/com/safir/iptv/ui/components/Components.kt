package com.safir.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand

/** Channel logo with a readable fallback — provider logos are missing surprisingly often. */
@Composable
fun ChannelLogo(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 8
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brand.SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            LogoFallback(name)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                loading = { LogoFallback(name) },
                error = { LogoFallback(name) }
            )
        }
    }
}

@Composable
private fun LogoFallback(name: String) {
    val initials = name.trim()
        .split(' ', '-', '_')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "TV" }

    Text(
        text = initials,
        color = Brand.TextSecondary,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1
    )
}

/** Thin progress line used under a running programme. */
@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Brand.Border,
    barColor: Color = Brand.Accent
) {
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(barColor)
        )
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Brand.Live.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brand.Live)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = LocalStrings.current.live,
            color = Brand.Live,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun FullScreenLoading(
    message: String,
    detail: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Brand.Accent)
        Spacer(Modifier.height(24.dp))
        Text(message, style = MaterialTheme.typography.titleMedium, color = Brand.TextPrimary)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary)
        }
    }
}

@Composable
fun FullScreenError(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = Brand.Danger,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Brand.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            TvButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * On/off switch. tv-material3 ships no Switch, and a word — "AN" / "AUS" — has to be
 * read before it means anything; a thumb on a coloured track is understood at a glance
 * from across the room, which is the distance a TV is actually watched from.
 */
@Composable
fun TvSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onColor: Color = Brand.Accent
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) onColor else Brand.Border,
        label = "switch-track"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        label = "switch-thumb"
    )

    Box(
        modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .padding(start = thumbOffset)
                .align(Alignment.CenterStart)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Brand.Background else Brand.TextSecondary)
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(PaddingValues(32.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.LiveTv,
            contentDescription = null,
            tint = Brand.TextSecondary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Brand.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
