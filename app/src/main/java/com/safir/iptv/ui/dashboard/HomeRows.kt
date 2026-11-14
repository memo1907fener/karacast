package com.safir.iptv.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
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
import com.safir.iptv.util.cleanVodTitle

/**
 * Die Reihen der modernen Startseite: womit man weitermachen will, ein Klick.
 *
 * Weiterschauen, zuletzt gesehene Sender, Lieblingssender, gemerkte Filme und
 * Serien. Leere Reihen erscheinen gar nicht. Die Größen richten sich nach dem
 * Bildschirm: Auf einem Handy im Querformat (kaum 400 dp hoch) werden Karten
 * und Ränder kleiner, damit mindestens anderthalb Reihen zu sehen sind — sonst
 * sieht man nur eine halbe Reihe und weiß nicht, dass darunter noch etwas kommt.
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
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    val t = LocalStrings.current
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 420.dp
        val sizes = if (compact) {
            RowSizes(poster = 78.dp, channel = 128.dp, side = 20.dp, gap = 10.dp, title = 14.sp)
        } else {
            RowSizes(poster = 108.dp, channel = 172.dp, side = 48.dp, gap = 14.dp, title = 17.sp)
        }
        // Wer den ersten Platz bekommt: die erste Karte der ersten Reihe.
        val firstRow = when {
            continueItems.isNotEmpty() -> "continue"
            recent.isNotEmpty() -> "recent"
            favoriteChannels.isNotEmpty() -> "favorites"
            else -> "mylist"
        }
        fun Modifier.firstIn(row: String, index: Int): Modifier =
            if (row == firstRow && index == 0) this.focusRequester(firstFocus) else this

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)
        ) {
            if (continueItems.isNotEmpty()) {
                item(key = "continue") {
                    HomeRow(t.vod.continueWatching, continueItems.size, sizes) {
                        itemsIndexed(continueItems, key = { _, it -> it.key }) { index, item ->
                            PosterCard(
                                title = cleanVodTitle(item.title),
                                subtitle = item.subtitle,
                                posterUrl = item.posterUrl,
                                progress = item.progress,
                                width = sizes.poster,
                                onClick = { onResume(item) },
                                modifier = Modifier.firstIn("continue", index)
                            )
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item(key = "recent") {
                    HomeRow(t.recentlyWatched, recent.size, sizes) {
                        itemsIndexed(recent, key = { _, it -> "r" + it.id }) { index, channel ->
                            ChannelCard(
                                channel = channel,
                                width = sizes.channel,
                                onClick = { onPlayChannel(channel, RECENT_CATEGORY_ID) },
                                modifier = Modifier.firstIn("recent", index)
                            )
                        }
                    }
                }
            }
            if (favoriteChannels.isNotEmpty()) {
                item(key = "favorites") {
                    HomeRow(t.vod.favoriteChannels, favoriteChannels.size, sizes) {
                        itemsIndexed(favoriteChannels, key = { _, it -> "f" + it.id }) { index, channel ->
                            ChannelCard(
                                channel = channel,
                                width = sizes.channel,
                                onClick = { onPlayChannel(channel, FAVORITES_CATEGORY_ID) },
                                modifier = Modifier.firstIn("favorites", index)
                            )
                        }
                    }
                }
            }
            if (vodFavorites.isNotEmpty()) {
                item(key = "mylist") {
                    HomeRow(t.vod.myList, vodFavorites.size, sizes) {
                        itemsIndexed(vodFavorites, key = { _, it -> it.key }) { index, favorite ->
                            PosterCard(
                                title = cleanVodTitle(favorite.title),
                                subtitle = "",
                                posterUrl = favorite.posterUrl,
                                progress = null,
                                width = sizes.poster,
                                onClick = { onOpenVodFavorite(favorite) },
                                modifier = Modifier.firstIn("mylist", index)
                            )
                        }
                    }
                }
            }
        }
    }
}

private class RowSizes(
    val poster: Dp,
    val channel: Dp,
    val side: Dp,
    val gap: Dp,
    val title: androidx.compose.ui.unit.TextUnit
)

@Composable
private fun HomeRow(
    title: String,
    count: Int,
    sizes: RowSizes,
    content: LazyListScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = sizes.side, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ein kurzer Akzent vor dem Titel macht die Reihen auf einen Blick
            // voneinander unterscheidbar.
            Box(
                Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brand.Accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = sizes.title, fontWeight = FontWeight.Bold, color = Brand.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text(count.toString(), fontSize = 12.sp, color = Brand.TextSecondary)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = sizes.side),
            horizontalArrangement = Arrangement.spacedBy(sizes.gap),
            content = content
        )
    }
}

/** Ein Film oder eine Serie: Cover, Titel darunter, bei Angefangenem ein Balken. */
@Composable
private fun PosterCard(
    title: String,
    subtitle: String,
    posterUrl: String?,
    progress: Float?,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Brand.SurfaceHigh,
                focusedContainerColor = Brand.SurfaceHigh,
                pressedContainerColor = Brand.SurfaceHigh
            ),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(3.dp, Brand.Accent), shape = shape)
            ),
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).alsoTappable(onClick)
        ) {
            Box(Modifier.fillMaxSize()) {
                // Der Titel steht hinter dem Bild: fehlt das Cover, ist er zu lesen.
                Text(
                    title,
                    fontSize = 11.sp,
                    color = Brand.TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp)
                )
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (progress != null && progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                                .background(Brand.Accent)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Brand.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                fontSize = 10.sp,
                color = Brand.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Ein Sender: Logo auf hellem Grund, darunter der Name. */
@Composable
private fun ChannelCard(
    channel: Channel,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Brand.SurfaceHigh,
                focusedContainerColor = Brand.SurfaceHigh,
                pressedContainerColor = Brand.SurfaceHigh
            ),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(3.dp, Brand.Accent), shape = shape)
            ),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).alsoTappable(onClick)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(listOf(Brand.SurfaceHigh, Brand.Surface))
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Kürzel hinter dem Logo: lädt das Logo nicht (oder gibt es keins),
                // steht hier trotzdem etwas Erkennbares statt einer leeren Fläche.
                Text(
                    text = initialsOf(channel.name),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Brand.TextSecondary
                )
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(10.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            channel.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Brand.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** „DE - RTL CRIME HD“ → „RC“: ohne Länderkürzel und Qualitätsangaben. */
private fun initialsOf(name: String): String {
    val noise = setOf("HD", "FHD", "UHD", "SD", "4K", "HEVC", "H265", "TV")
    val words = name
        .replace(Regex("""^\s*(\[[^\]]*]|\|[^|]*\||[A-Z]{2,3}\s*[-:|])\s*"""), "")
        .split(Regex("""[\s\-_.|:]+"""))
        .filter { it.isNotBlank() && it.uppercase() !in noise }
    return when {
        words.isEmpty() -> name.take(2).uppercase()
        words.size == 1 -> words[0].take(3).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
