package com.safir.iptv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.safir.iptv.domain.model.Episode
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.EmptyState
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.asTimeLabel

/**
 * One series: the poster and the plot across the top, the seasons down the left,
 * the episodes beside them. Two columns rather than a drop-down, because a remote
 * moves sideways far more comfortably than it opens menus.
 */
@Composable
fun SeriesDetailScreen(
    seriesId: Int,
    onPlay: (seriesName: String, Episode, startMs: Long) -> Unit,
    onBack: () -> Unit
) {
    val t = LocalStrings.current
    val viewModel: SeriesDetailViewModel = viewModel(factory = AppViewModelFactory)
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
            .padding(horizontal = 36.dp, vertical = 24.dp)
    ) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.Accent)
            }
            return@Column
        }

        val detail = state.detail
        if (detail == null || detail.episodes.isEmpty()) {
            EmptyState(
                title = t.vod.noEpisodes,
                message = state.error ?: t.vod.emptyCategory,
                modifier = Modifier.weight(1f)
            )
            TvButton(onClick = onBack) { Text(t.back) }
            return@Column
        }

        // ----------------------------------------------------------- the header
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(120.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brand.SurfaceHigh)
            ) {
                detail.series.posterUrl?.takeIf { it.isNotBlank() }?.let { poster ->
                    SubcomposeAsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = detail.series.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val facts = listOfNotNull(
                    t.vod.nSeasons(detail.seasons.size),
                    t.vod.nEpisodes(detail.episodes.size),
                    detail.series.genre?.takeIf { it.isNotBlank() },
                    detail.series.released?.takeIf { it.isNotBlank() }
                )
                Text(
                    text = facts.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.AccentSoft
                )
                detail.series.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.TextSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                TvButton(onClick = viewModel::toggleFavorite) {
                    Text(if (state.isFavorite) t.vod.removeFavorite else t.vod.addFavorite)
                }
                Spacer(Modifier.height(8.dp))
                TvButton(onClick = onBack) { Text(t.back) }
            }
        }

        Spacer(Modifier.height(18.dp))

        // --------------------------------------------- seasons beside episodes
        Row(Modifier.weight(1f).fillMaxWidth()) {

            Column(Modifier.width(190.dp).fillMaxHeight()) {
                Text(
                    text = t.vod.seasonsTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(detail.seasons, key = { it }) { season ->
                        SeasonLine(
                            label = t.vod.season(season),
                            selected = season == state.selectedSeason,
                            onSelect = { viewModel.selectSeason(season) }
                        )
                    }
                }
            }

            Spacer(Modifier.width(24.dp))

            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = t.vod.episodesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t.vod.episodeHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    // The one thing about auto-play worth deciding is whether it
                    // happens at all, and this is where somebody is thinking about
                    // episodes — so it is settled here rather than three menus away.
                    TvButton(onClick = { viewModel.setAutoNext(!state.autoNext) }) {
                        Text(
                            t.vod.autoNext + "  " +
                                if (state.autoNext) t.on else t.off
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.episodes) { episode ->
                        val resume = viewModel.resumeMs(episode)
                        val season = state.episodes
                        EpisodeLine(
                            title = episode.label(t.vod.episode),
                            detail = listOfNotNull(
                                episode.durationSecs?.takeIf { it > 0 }
                                    ?.let { t.nMinutes(it / 60) },
                                resume.takeIf { it > 0L }
                                    ?.let { t.vod.continueAt(it.asTimeLabel()) }
                            ).joinToString("  ·  "),
                            plot = episode.plot,
                            onClick = {
                                // The rest of the season travels with the episode,
                                // so the player can go on by itself at the end of it.
                                VodPlayback.queue = season.map {
                                    VodPlayback.episodeRequest(
                                        seriesName = detail.series.name,
                                        seriesId = seriesId,
                                        episode = it,
                                        startMs = 0L
                                    )
                                }
                                onPlay(detail.series.name, episode, resume)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonLine(label: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
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
        modifier = Modifier.fillMaxWidth().alsoTappable(onSelect)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
        )
    }
}

@Composable
private fun EpisodeLine(
    title: String,
    detail: String,
    plot: String?,
    onClick: () -> Unit
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
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().alsoTappable(onClick)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (detail.isNotBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Text(text = detail, style = MaterialTheme.typography.labelSmall)
                }
            }
            plot?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
