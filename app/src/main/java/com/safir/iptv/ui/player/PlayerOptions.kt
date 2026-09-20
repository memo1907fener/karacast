package com.safir.iptv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.safir.iptv.domain.model.AspectMode
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.SleepTimer
import kotlinx.coroutines.delay
import java.util.Locale

/** One selectable line: title, optional detail on the right, a dot when it is active. */
private data class OptionRow(
    val title: String,
    val detail: String = "",
    val selected: Boolean = false,
    val onClick: () -> Unit
)

private val SLEEP_CHOICES = listOf(15, 30, 45, 60, 90, 120)

/**
 * Everything the remote cannot reach with a dedicated key: audio track, subtitles,
 * picture format, sleep timer, favourite. One panel on the right, opened with the
 * menu key — the channel list owns the left side, so the two never fight for space.
 */
@Composable
fun PlayerOptionsPanel(
    page: OptionsPage,
    player: ExoPlayer,
    aspectMode: AspectMode,
    /**
     * What the picture format is filed against — a channel's name, a film's title.
     * Shown on the format page, because the choice applies only to that one thing
     * and a setting whose reach is invisible is a setting people fight with.
     */
    aspectScope: String? = null,
    isFavorite: Boolean,
    onPage: (OptionsPage) -> Unit,
    onAspect: (AspectMode) -> Unit,
    /** Null leaves the favourite line out — a film cannot be a favourite channel. */
    onToggleFavorite: (() -> Unit)?,
    onClose: () -> Unit
) {
    // Re-read on every page change: a track list only fills up once the stream has
    // been running for a moment, so it must not be captured once and kept.
    val t = LocalStrings.current
    val tracks = player.currentTracks

    val (title, rows) = when (page) {
        OptionsPage.AUDIO -> t.audioTrack to trackRows(player, tracks, C.TRACK_TYPE_AUDIO)
        OptionsPage.SUBTITLE -> t.subtitles to subtitleRows(player, tracks)
        OptionsPage.ASPECT -> t.aspect to aspectRows(aspectMode, onAspect)
        OptionsPage.SLEEP -> t.sleepTimer to sleepRows(onClose)
        else -> t.options to rootRows(
            tracks = tracks,
            aspectMode = aspectMode,
            isFavorite = isFavorite,
            onPage = onPage,
            onToggleFavorite = onToggleFavorite
        )
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(page) {
        delay(60)
        runCatching { focusRequester.requestFocus() }
    }

    // Die Breite war einmal fest: 420dp. Auf einem Fernseher ist das fast die
    // halbe Bildbreite — man stellt das Bildformat ein und sieht dabei kaum noch
    // Bild. Jetzt wird sie aus der Bildschirmbreite gerechnet und bleibt in
    // vernünftigen Grenzen: gut ein Drittel auf dem Fernseher, auf einem
    // schmalen Telefon höchstens zwei Drittel, nie breiter als 360dp.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val panelWidth = (screenWidth * 0.34f)
        .coerceIn(200.dp, 360.dp)
        .coerceAtMost(screenWidth * 0.7f)

    Column(
        Modifier
            .width(panelWidth)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
        )
        Text(
            text = when {
                page == OptionsPage.ASPECT && !aspectScope.isNullOrBlank() ->
                    t.colors.onlyFor(aspectScope)

                page == OptionsPage.ROOT -> t.backCloses
                else -> t.backOneLevel
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (page == OptionsPage.ASPECT && !aspectScope.isNullOrBlank()) {
                Brand.AccentSoft
            } else {
                Brand.TextSecondary
            },
            modifier = Modifier.padding(start = 12.dp, bottom = 14.dp)
        )

        if (rows.isEmpty()) {
            Text(
                text = t.nothingToChoose,
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.TextSecondary,
                modifier = Modifier.padding(12.dp)
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxHeight().focusRequester(focusRequester),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(rows, key = { it.title + it.detail }) { row -> OptionLine(row) }
        }
    }
}

@Composable
private fun OptionLine(row: OptionRow) {
    Surface(
        onClick = row.onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (row.selected) Brand.SurfaceHigh else Color.Transparent,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = if (row.selected) Brand.TextPrimary else Brand.TextSecondary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().alsoTappable(row.onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(18.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (row.selected) Brand.Accent else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (row.selected) "●" else "",
                    fontSize = 11.sp,
                    color = Brand.Background
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (row.detail.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = row.detail,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

// ------------------------------------------------------------------ page content

private fun rootRows(
    tracks: Tracks,
    aspectMode: AspectMode,
    isFavorite: Boolean,
    onPage: (OptionsPage) -> Unit,
    onToggleFavorite: (() -> Unit)?
): List<OptionRow> {
    val t = AppLocale.strings
    val audioCount = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.sumOf { it.length }
    val textCount = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.sumOf { it.length }
    return listOfNotNull(
        OptionRow(
            title = t.audioTrack,
            detail = if (audioCount > 1) "$audioCount ›" else "›",
            onClick = { onPage(OptionsPage.AUDIO) }
        ),
        OptionRow(
            title = t.subtitles,
            detail = if (textCount > 0) "$textCount ›" else "${t.none} ›",
            onClick = { onPage(OptionsPage.SUBTITLE) }
        ),
        OptionRow(
            title = t.aspect,
            detail = aspectMode.label() + " ›",
            onClick = { onPage(OptionsPage.ASPECT) }
        ),
        OptionRow(
            title = t.sleepTimer,
            detail = if (SleepTimer.isActive) t.nMinutesShort(SleepTimer.remainingMinutes) + " ›" else "${t.off} ›",
            onClick = { onPage(OptionsPage.SLEEP) }
        ),
        onToggleFavorite?.let { toggle ->
            OptionRow(
                title = if (isFavorite) t.removeFavorite else t.addFavorite,
                selected = isFavorite,
                onClick = toggle
            )
        }
    )
}

private fun trackRows(player: ExoPlayer, tracks: Tracks, type: Int): List<OptionRow> =
    tracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).map { index ->
            OptionRow(
                title = group.getTrackFormat(index).describe(index),
                detail = group.getTrackFormat(index).channelLabel(),
                selected = group.isTrackSelected(index),
                onClick = { player.selectTrack(group, index, type) }
            )
        }
    }

private fun subtitleRows(player: ExoPlayer, tracks: Tracks): List<OptionRow> {
    val t = AppLocale.strings
    val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val off = OptionRow(
        title = t.off,
        selected = disabled,
        onClick = {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    )
    val available = trackRows(player, tracks, C.TRACK_TYPE_TEXT)
        .map { if (disabled) it.copy(selected = false) else it }
    return listOf(off) + available
}

private fun aspectRows(current: AspectMode, onAspect: (AspectMode) -> Unit): List<OptionRow> =
    AspectMode.entries.map { mode ->
        OptionRow(
            title = mode.label(),
            detail = mode.hint(),
            selected = mode == current,
            onClick = { onAspect(mode) }
        )
    }

private fun sleepRows(onClose: () -> Unit): List<OptionRow> {
    val t = AppLocale.strings
    val off = OptionRow(
        title = t.off,
        selected = !SleepTimer.isActive,
        onClick = { SleepTimer.cancel(); onClose() }
    )
    return listOf(off) + SLEEP_CHOICES.map { minutes ->
        OptionRow(
            title = t.nMinutes(minutes),
            selected = SleepTimer.isActive && SleepTimer.remainingMinutes == minutes,
            onClick = { SleepTimer.start(minutes); onClose() }
        )
    }
}

// ------------------------------------------------------------------- small print

/** The same word, but callable from the other screens that show the setting. */
fun AspectMode.shortLabel(): String = label()

private fun AspectMode.label(): String = when (this) {
    AspectMode.FIT -> AppLocale.strings.aspectFit
    AspectMode.FILL -> AppLocale.strings.aspectFill
    AspectMode.ZOOM -> AppLocale.strings.aspectZoom
}

private fun AspectMode.hint(): String = when (this) {
    AspectMode.FIT -> AppLocale.strings.aspectFitHint
    AspectMode.FILL -> AppLocale.strings.aspectFillHint
    AspectMode.ZOOM -> AppLocale.strings.aspectZoomHint
}

/**
 * What to call a track. Providers fill these fields carelessly, so every fallback
 * matters: a label if there is one, otherwise the language spelled out in full,
 * otherwise at least a number the user can count through.
 */
private fun androidx.media3.common.Format.describe(index: Int): String {
    label?.takeIf { it.isNotBlank() }?.let { return it }
    val tag = language?.takeIf { it.isNotBlank() && it != "und" }
    if (tag != null) {
        val display = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault())
        if (display.isNotBlank()) return display.replaceFirstChar { it.uppercase() }
        return tag.uppercase()
    }
    return AppLocale.strings.trackNumber(index + 1)
}

private fun androidx.media3.common.Format.channelLabel(): String = when {
    channelCount == 6 -> "5.1"
    channelCount == 2 -> AppLocale.strings.stereo
    channelCount == 1 -> AppLocale.strings.mono
    else -> ""
}

/** Pins one track so the player stops picking for itself. */
private fun ExoPlayer.selectTrack(group: Tracks.Group, index: Int, type: Int) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(type, false)
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        .build()
}
