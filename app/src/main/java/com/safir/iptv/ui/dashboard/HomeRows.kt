package com.safir.iptv.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.domain.model.FAVORITES_CATEGORY_ID
import com.safir.iptv.domain.model.FavoriteItem
import com.safir.iptv.domain.model.RECENT_CATEGORY_ID
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.ui.vod.PosterTile
import com.safir.iptv.util.cleanVodTitle

/**
 * Die Reihen der Startseite: was man zuletzt gemacht hat, mit einem Klick wieder.
 *
 * Weiterschauen, die zuletzt gesehenen Sender, die Lieblingssender und die
 * gemerkten Filme und Serien. Leere Reihen erscheinen gar nicht erst — wer die
 * App neu hat, sieht die großen Kacheln wie bisher.
 */
@Composable
fun HomeRows(
    continueItems: List<ContinueItem>,
    recent: List<Channel>,
    favoriteChannels: List<Channel>,
    vodFavorites: List<FavoriteItem>,
    onResume: (ContinueItem) -> Unit,
    onPlayChannel: (Channel, String) -> Unit,
    onOpenVodFavorite: (FavoriteItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalStrings.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (continueItems.isNotEmpty()) {
            item(key = "continue") {
                HomeRow(t.vod.continueWatching) {
                    items(continueItems, key = { it.key }) { item ->
                        PosterTile(
                            title = item.subtitle.takeIf { it.isNotBlank() }
                                ?.let { "${cleanVodTitle(item.title)} · $it" }
                                ?: cleanVodTitle(item.title),
                            posterUrl = item.posterUrl,
                            badge = null,
                            onClick = { onResume(item) },
                            modifier = Modifier.width(POSTER_WIDTH),
                            progress = item.progress
                        )
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent") {
                HomeRow(t.recentlyWatched) {
                    items(recent, key = { "r" + it.id }) { channel ->
                        ChannelCard(channel) { onPlayChannel(channel, RECENT_CATEGORY_ID) }
                    }
                }
            }
        }
        if (favoriteChannels.isNotEmpty()) {
            item(key = "favorites") {
                HomeRow(t.vod.favoriteChannels) {
                    items(favoriteChannels, key = { "f" + it.id }) { channel ->
                        ChannelCard(channel) { onPlayChannel(channel, FAVORITES_CATEGORY_ID) }
                    }
                }
            }
        }
        if (vodFavorites.isNotEmpty()) {
            item(key = "mylist") {
                HomeRow(t.vod.myList) {
                    items(vodFavorites, key = { it.key }) { favorite ->
                        PosterTile(
                            title = cleanVodTitle(favorite.title),
                            posterUrl = favorite.posterUrl,
                            badge = null,
                            onClick = { onOpenVodFavorite(favorite) },
                            modifier = Modifier.width(POSTER_WIDTH)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRow(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** Ein Sender: Logo groß, Name darunter. */
@Composable
private fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
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
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        modifier = Modifier.width(CHANNEL_WIDTH).alsoTappable(onClick)
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brand.Surface),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                } else {
                    Text(
                        text = channel.name.take(3).uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = channel.name,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private val POSTER_WIDTH = 104.dp
private val CHANNEL_WIDTH = 150.dp
