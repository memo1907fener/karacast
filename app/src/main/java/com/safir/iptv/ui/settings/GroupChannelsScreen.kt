package com.safir.iptv.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.ChannelLogo
import com.safir.iptv.ui.components.EmptyState
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand

/**
 * Tidies up one group: OK takes a channel out or puts it back, the two arrows move
 * it. The numbering shown on the left is what the group will actually hand out, so
 * the effect of every move is visible straight away.
 */
@Composable
fun GroupChannelsScreen(groupId: String, onBack: () -> Unit) {
    val t = LocalStrings.current
    val viewModel: GroupChannelsViewModel = viewModel(factory = AppViewModelFactory)
    LaunchedEffect(groupId) { viewModel.setGroup(groupId) }

    val group by viewModel.group.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val hidden = group?.hiddenChannelIds.orEmpty().toSet()
    val visibleCount = channels.count { it.id !in hidden }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
            .padding(horizontal = 36.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = group?.name ?: t.group,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = t.activeOfTotal(visibleCount, channels.size) +
                        " · " + t.groupEditHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.TextSecondary
                )
            }
            Spacer(Modifier.width(16.dp))
            TvButton(onClick = viewModel::showAll) { Text(t.showAllChannels) }
            Spacer(Modifier.width(10.dp))
            TvButton(onClick = viewModel::resetOrder) { Text(t.resetOrder) }
            Spacer(Modifier.width(10.dp))
            TvButton(onClick = onBack) { Text(t.done) }
        }

        Spacer(Modifier.height(18.dp))

        if (channels.isEmpty()) {
            EmptyState(
                title = t.noChannelsInGroup,
                message = t.noChannelsInGroupHint
            )
            return@Column
        }

        // Hidden channels take no number, so the list on the left reads exactly the
        // way the group will look on screen. Worked out up front rather than while
        // the rows compose: a LazyColumn only builds what is visible, so a running
        // counter inside `items` would restart halfway down the list.
        val numbers = buildMap<String, Int?> {
            var position = 0
            channels.forEach { channel ->
                put(channel.id, if (channel.id in hidden) null else ++position)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                val isHidden = channel.id in hidden
                EditRow(
                    channel = channel,
                    number = numbers[channel.id],
                    isHidden = isHidden,
                    onToggle = { viewModel.toggleHidden(channel.id) },
                    onUp = { viewModel.move(channel.id, -1) },
                    onDown = { viewModel.move(channel.id, +1) },
                    onTop = { viewModel.moveToTop(channel.id) },
                    hiddenLabel = t.hiddenLabel
                )
            }
        }
    }
}

@Composable
private fun EditRow(
    channel: Channel,
    number: Int?,
    isHidden: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onTop: () -> Unit,
    hiddenLabel: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onToggle,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isHidden) Brand.Surface.copy(alpha = 0.5f) else Brand.Surface,
                focusedContainerColor = Brand.Accent,
                pressedContainerColor = Brand.Accent,
                contentColor = if (isHidden) Brand.TextSecondary else Brand.TextPrimary,
                focusedContentColor = Brand.Background,
                pressedContentColor = Brand.Background
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.weight(1f).alsoTappable(onToggle)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isHidden) Color.Transparent else Brand.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isHidden) "—" else "✓",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHidden) Brand.TextSecondary else Brand.Background
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = number?.toString()?.padStart(3, '0') ?: "···",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(42.dp)
                )
                ChannelLogo(
                    url = channel.logoUrl,
                    name = channel.name,
                    modifier = Modifier.size(48.dp, 34.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isHidden) hiddenLabel else channel.categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        MoveButton("▲", onUp)
        Spacer(Modifier.width(5.dp))
        MoveButton("▼", onDown)
        Spacer(Modifier.width(5.dp))
        MoveButton("⤒", onTop)
    }
}

@Composable
private fun MoveButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.SurfaceHigh,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = Brand.TextSecondary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(7.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.size(40.dp).alsoTappable(onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 15.sp)
        }
    }
}
