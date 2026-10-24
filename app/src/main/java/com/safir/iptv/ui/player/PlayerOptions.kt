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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
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

/**
 * One selectable line: title, optional detail on the right, a dot when it is active.
 *
 * [id] ist der Schlüssel in der Liste und muss eindeutig sein. Früher war es der
 * sichtbare Text — und genau das hat die App bei Filmen abstürzen lassen: zwei
 * Tonspuren, die beide „Englisch · 5.1“ heißen, oder drei Untertitel ohne Namen,
 * die alle „Spur 1“ hießen, ergaben denselben Schlüssel zweimal, und Compose bricht
 * dann hart ab. Bei Live-Kanälen kommt das selten vor, bei Filmen ständig.
 */
private data class OptionRow(
    val id: String,
    val title: String,
    val detail: String = "",
    val selected: Boolean = false,
    /** False für Spuren, die dieses Gerät nicht abspielen kann — sichtbar, aber nicht wählbar. */
    val enabled: Boolean = true,
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
    val t = LocalStrings.current

    // Der Player ist kein Compose-Zustand: was er über seine Spuren weiß, ändert sich,
    // ohne dass Compose davon erfährt. Einmal gelesen, blieb die Anzeige deshalb
    // stehen — man wählte einen Untertitel, er wurde auch eingeschaltet, aber der
    // Punkt blieb bei „Aus“ und es sah aus, als ginge die Auswahl nicht. Jetzt hört
    // das Menü dem Player zu und zeichnet sich neu, sobald er umgeschaltet hat.
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var params by remember(player) { mutableStateOf(player.trackSelectionParameters) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                params = parameters
            }
        }
        player.addListener(listener)
        // Was sich zwischen dem ersten Lesen und dem Anmelden getan hat, nachholen.
        tracks = player.currentTracks
        params = player.trackSelectionParameters
        onDispose { player.removeListener(listener) }
    }

    val (title, rows) = when (page) {
        OptionsPage.AUDIO -> t.audioTrack to trackRows(player, tracks, C.TRACK_TYPE_AUDIO)
        OptionsPage.SUBTITLE -> t.subtitles to subtitleRows(player, tracks, params)
        OptionsPage.ASPECT -> t.aspect to aspectRows(aspectMode, onAspect)
        OptionsPage.SLEEP -> t.sleepTimer to sleepRows(onClose)
        else -> t.options to rootRows(
            tracks = tracks,
            params = params,
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

        // Leer (die Spuren sind noch nicht da, oder der Film hat keine): eine Zeile
        // „Zurück“ statt gar nichts. Ohne etwas Anwählbares fiel der Fokus ins Leere,
        // und Zurück beendete dann den ganzen Film statt nur diese Seite.
        val shown = if (rows.isEmpty()) {
            Text(
                text = t.nothingToChoose,
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.TextSecondary,
                modifier = Modifier.padding(12.dp)
            )
            listOf(
                OptionRow(
                    id = "empty-back",
                    title = "‹ " + t.back,
                    onClick = { onPage(OptionsPage.ROOT) }
                )
            )
        } else {
            rows
        }

        LazyColumn(
            modifier = Modifier.fillMaxHeight().focusRequester(focusRequester),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(shown, key = { it.id }) { row -> OptionLine(row) }
        }
    }
}

@Composable
private fun OptionLine(row: OptionRow) {
    Surface(
        onClick = row.onClick,
        enabled = row.enabled,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (row.selected) Brand.SurfaceHigh else Color.Transparent,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = if (row.selected) Brand.TextPrimary else Brand.TextSecondary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Brand.TextSecondary.copy(alpha = 0.5f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (row.enabled) Modifier.alsoTappable(row.onClick) else Modifier)
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
    params: TrackSelectionParameters,
    aspectMode: AspectMode,
    isFavorite: Boolean,
    onPage: (OptionsPage) -> Unit,
    onToggleFavorite: (() -> Unit)?
): List<OptionRow> {
    val t = AppLocale.strings
    val audioCount = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.sumOf { it.length }
    val textCount = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.sumOf { it.length }
    // Rechts steht, was gerade läuft — „Deutsch ›“ sagt mehr als „3 ›“, und man
    // muss die Seite nicht erst öffnen, um zu sehen, ob sie richtig steht.
    val audioNow = selectedTrackName(tracks, C.TRACK_TYPE_AUDIO)
    val subtitlesOff = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val subtitleNow = if (subtitlesOff) null else selectedTrackName(tracks, C.TRACK_TYPE_TEXT)
    return listOfNotNull(
        OptionRow(
            id = "root-audio",
            title = t.audioTrack,
            detail = when {
                audioNow != null && audioCount > 1 -> "$audioNow ›"
                audioCount > 1 -> "$audioCount ›"
                else -> "›"
            },
            onClick = { onPage(OptionsPage.AUDIO) }
        ),
        OptionRow(
            id = "root-subtitles",
            title = t.subtitles,
            detail = when {
                textCount == 0 -> "${t.none} ›"
                subtitleNow != null -> "$subtitleNow ›"
                else -> "${t.off} ›"
            },
            onClick = { onPage(OptionsPage.SUBTITLE) }
        ),
        OptionRow(
            id = "root-aspect",
            title = t.aspect,
            detail = aspectMode.label() + " ›",
            onClick = { onPage(OptionsPage.ASPECT) }
        ),
        OptionRow(
            id = "root-sleep",
            title = t.sleepTimer,
            detail = if (SleepTimer.isActive) t.nMinutesShort(SleepTimer.remainingMinutes) + " ›" else "${t.off} ›",
            onClick = { onPage(OptionsPage.SLEEP) }
        ),
        onToggleFavorite?.let { toggle ->
            OptionRow(
                id = "root-favorite",
                title = if (isFavorite) t.removeFavorite else t.addFavorite,
                selected = isFavorite,
                onClick = toggle
            )
        }
    )
}

/** Name der Spur, die der Player für [type] gerade spielt — oder null. */
private fun selectedTrackName(tracks: Tracks, type: Int): String? {
    var number = 0
    for (group in tracks.groups) {
        if (group.type != type) continue
        for (index in 0 until group.length) {
            number++
            if (group.isTrackSelected(index)) return group.getTrackFormat(index).describe(number)
        }
    }
    return null
}

/**
 * Eine Zeile pro Spur, über alle Gruppen hinweg durchgezählt.
 *
 * Durchgezählt, weil viele Anbieter jede Spur in eine eigene Gruppe packen: mit
 * der Nummer innerhalb der Gruppe hieß dann jede namenlose Spur „Spur 1“. Jetzt
 * heißen sie Spur 1, 2, 3 — und wenn zwei trotzdem gleich heißen (zweimal
 * „Englisch · Stereo“, einmal AAC und einmal AC3), bekommt die zweite eine Nummer
 * dazu, damit man sie auseinanderhalten kann.
 */
private fun trackRows(player: ExoPlayer, tracks: Tracks, type: Int): List<OptionRow> {
    val t = AppLocale.strings
    val rows = mutableListOf<OptionRow>()
    var number = 0
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != type) return@forEachIndexed
        for (index in 0 until group.length) {
            number++
            val format = group.getTrackFormat(index)
            // Ein Untertitel als Bild (PGS, DVB) oder ein Tonformat ohne passenden
            // Decoder lässt sich auswählen, spielt dann aber nichts — oder bricht
            // die Wiedergabe ab. Lieber sichtbar und ausgegraut als eine Falle.
            val playable = group.isTrackSupported(index, true)
            val detail = when {
                !playable -> t.tracks.notPlayable
                type == C.TRACK_TYPE_AUDIO -> format.channelLabel()
                else -> format.subtitleFlags()
            }
            rows += OptionRow(
                id = "track-$type-$groupIndex-$index",
                title = format.describe(number),
                detail = detail,
                selected = group.isTrackSelected(index),
                enabled = playable,
                onClick = { player.selectTrack(group, index, type) }
            )
        }
    }
    return rows.disambiguated()
}

/** Gleich lautende Zeilen bekommen ab der zweiten eine laufende Nummer. */
private fun List<OptionRow>.disambiguated(): List<OptionRow> {
    val seen = HashMap<String, Int>()
    return map { row ->
        val label = row.title + "\u0000" + row.detail
        val count = (seen[label] ?: 0) + 1
        seen[label] = count
        if (count == 1) row else row.copy(title = "${row.title} ($count)")
    }
}

private fun subtitleRows(
    player: ExoPlayer,
    tracks: Tracks,
    params: TrackSelectionParameters
): List<OptionRow> {
    val t = AppLocale.strings
    val disabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val available = trackRows(player, tracks, C.TRACK_TYPE_TEXT)
        .map { if (disabled) it.copy(selected = false) else it }
    // „Aus“ ist auch dann die richtige Markierung, wenn der Player von sich aus
    // keinen Untertitel gewählt hat — sonst stünde gar kein Punkt da.
    val off = OptionRow(
        id = "subtitle-off",
        title = t.off,
        selected = disabled || available.none { it.selected },
        onClick = {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    )
    return listOf(off) + available
}

private fun aspectRows(current: AspectMode, onAspect: (AspectMode) -> Unit): List<OptionRow> =
    AspectMode.entries.map { mode ->
        OptionRow(
            id = "aspect-${mode.name}",
            title = mode.label(),
            detail = mode.hint(),
            selected = mode == current,
            onClick = { onAspect(mode) }
        )
    }

private fun sleepRows(onClose: () -> Unit): List<OptionRow> {
    val t = AppLocale.strings
    val off = OptionRow(
        id = "sleep-off",
        title = t.off,
        selected = !SleepTimer.isActive,
        onClick = { SleepTimer.cancel(); onClose() }
    )
    return listOf(off) + SLEEP_CHOICES.map { minutes ->
        OptionRow(
            id = "sleep-$minutes",
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
private fun androidx.media3.common.Format.describe(number: Int): String {
    label?.takeIf { it.isNotBlank() }?.let { return it }
    val tag = language?.takeIf { it.isNotBlank() && it != "und" }
    if (tag != null) {
        val display = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault())
        if (display.isNotBlank()) return display.replaceFirstChar { it.uppercase() }
        return tag.uppercase()
    }
    return AppLocale.strings.trackNumber(number)
}

/** „erzwungen“, „für Hörgeschädigte“ — das, was zwei gleichsprachige Untertitel unterscheidet. */
private fun androidx.media3.common.Format.subtitleFlags(): String {
    val t = AppLocale.strings.tracks
    val parts = buildList {
        if (selectionFlags and C.SELECTION_FLAG_FORCED != 0) add(t.forced)
        if (roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0) add(t.hearingImpaired)
    }
    return parts.joinToString(" · ")
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
