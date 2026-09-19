package com.safir.iptv.ui.guide

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.Program
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.ChannelLogo
import com.safir.iptv.ui.components.EmptyState
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.asClock

/** Horizontal scale of the grid. 4dp a minute puts a half-hour show at 120dp. */
private val MINUTE_WIDTH = 4.dp
private val CHANNEL_COLUMN = 220.dp
private val ROW_HEIGHT = 62.dp

private fun Long.toWidth(): Dp = (this / 60_000f * MINUTE_WIDTH.value).dp

/**
 * The programme grid: channels down, time across, one shared horizontal scroll so
 * every row stays on the same clock. A programme that has already ended can be
 * replayed when the provider keeps an archive for that channel.
 */
@Composable
fun GuideScreen(
    onPlayLive: (Channel, categoryId: String) -> Unit,
    onPlayArchive: (Channel, Program) -> Unit
) {
    val s = LocalStrings.current
    val viewModel: GuideViewModel = viewModel(factory = AppViewModelFactory)
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val scroll = rememberScrollState()
    val windowStart = viewModel.windowStartMs
    val windowEnd = viewModel.windowEndMs
    val now = remember { System.currentTimeMillis() }

    Column(Modifier.fillMaxSize().background(Brand.Background)) {

        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.tvGuide,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = s.guideHint,
                style = MaterialTheme.typography.labelSmall,
                color = Brand.TextSecondary
            )
        }

        if (channels.isEmpty()) {
            EmptyState(s.noChannels, s.loadPlaylistFirst)
            return@Column
        }

        TimeRuler(
            windowStart = windowStart,
            windowEnd = windowEnd,
            nowMs = now,
            scroll = scroll
        )

        if (loading && programs.isEmpty()) {
            EmptyState(s.loadingGuide, s.oneMoment)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                GuideRow(
                    channel = channel,
                    programs = channel.epgId?.let { programs[it] }.orEmpty(),
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    nowMs = now,
                    scroll = scroll,
                    onPlayLive = { onPlayLive(channel, viewModel.categoryId) },
                    onPlayArchive = { program -> onPlayArchive(channel, program) }
                )
            }
        }
    }
}

@Composable
private fun TimeRuler(
    windowStart: Long,
    windowEnd: Long,
    nowMs: Long,
    scroll: ScrollState
) {
    val s = LocalStrings.current
    val halfHour = 30 * 60 * 1000L
    val slots = ((windowEnd - windowStart) / halfHour).toInt()

    Row(
        Modifier
            .fillMaxWidth()
            .background(Brand.SurfaceHigh)
            .height(30.dp)
    ) {
        Box(
            Modifier.width(CHANNEL_COLUMN).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "  ${s.now}: ${nowMs.asClock()}",
                style = MaterialTheme.typography.labelMedium,
                color = Brand.Accent,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            Modifier
                .fillMaxHeight()
                .horizontalScroll(scroll)
        ) {
            repeat(slots) { index ->
                val slotStart = windowStart + index * halfHour
                Box(
                    Modifier
                        .width(halfHour.toWidth())
                        .fillMaxHeight()
                        .background(
                            if (index % 2 == 0) Color.Transparent else Brand.Surface.copy(alpha = 0.5f)
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = slotStart.asClock(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (slotStart <= nowMs) Brand.TextSecondary else Brand.TextPrimary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    programs: List<Program>,
    windowStart: Long,
    windowEnd: Long,
    nowMs: Long,
    scroll: ScrollState,
    onPlayLive: () -> Unit,
    onPlayArchive: (Program) -> Unit
) {
    val s = LocalStrings.current
    Row(Modifier.height(ROW_HEIGHT)) {

        // The channel column stays put while the timeline scrolls under it.
        Surface(
            onClick = onPlayLive,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Brand.Surface,
                focusedContainerColor = Brand.Accent,
                pressedContainerColor = Brand.Accent,
                contentColor = Brand.TextPrimary,
                focusedContentColor = Brand.Background,
                pressedContentColor = Brand.Background
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxHeight()
                .padding(end = 4.dp)
                .alsoTappable(onPlayLive)
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.number.toString().padStart(3, '0'),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(34.dp)
                )
                ChannelLogo(
                    url = channel.logoUrl,
                    name = channel.name,
                    modifier = Modifier.size(40.dp, 28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            Modifier
                .fillMaxHeight()
                .horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (programs.isEmpty()) {
                Box(
                    Modifier
                        .width((windowEnd - windowStart).toWidth())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brand.Surface.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "  ${s.noGuideData}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.TextSecondary
                    )
                }
                return@Row
            }

            var cursor = windowStart
            programs.forEach { program ->
                val start = program.startMs.coerceAtLeast(windowStart)
                val end = program.endMs.coerceAtMost(windowEnd)
                if (end <= start) return@forEach

                if (start > cursor) {
                    Spacer(Modifier.width((start - cursor).toWidth()))
                }
                cursor = end

                val isPast = program.endMs <= nowMs
                val isLive = program.isLiveAt(nowMs)
                val replayable = isPast && channel.hasArchive

                ProgramBlock(
                    program = program,
                    width = (end - start).toWidth(),
                    isLive = isLive,
                    isPast = isPast,
                    replayable = replayable,
                    onClick = {
                        if (replayable) onPlayArchive(program) else onPlayLive()
                    }
                )
            }
        }
    }
}

@Composable
private fun ProgramBlock(
    program: Program,
    width: Dp,
    isLive: Boolean,
    isPast: Boolean,
    replayable: Boolean,
    onClick: () -> Unit
) {
    val s = LocalStrings.current
    val container = when {
        isLive -> Brand.SurfaceHigh
        isPast && !replayable -> Brand.Surface.copy(alpha = 0.45f)
        else -> Brand.Surface
    }

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = if (isPast && !replayable) Brand.TextSecondary else Brand.TextPrimary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .width(width.coerceAtLeast(24.dp))
            .fillMaxHeight()
            .alsoTappable(onClick)
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = program.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (replayable) {
                    "${program.startMs.asClock()} · ↺ ${s.archive}"
                } else {
                    program.startMs.asClock()
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}
