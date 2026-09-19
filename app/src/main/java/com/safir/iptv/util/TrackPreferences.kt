package com.safir.iptv.util

import androidx.media3.common.C
import androidx.media3.common.Player
import com.safir.iptv.domain.model.TrackLanguage

/**
 * Tells the player which dub and which subtitles to reach for.
 *
 * These are preferences, not commands: ExoPlayer applies them to every stream it
 * opens and quietly falls back to the first available track when a film has no
 * German audio at all — which is exactly right, because a household that mostly
 * wants German should not be handed silence by a film that only has English.
 * A track chosen by hand in the options panel still overrides this for as long as
 * that stream is running.
 *
 * @param subtitle [TrackLanguage.AUTO] means "no subtitles". There is no sensible
 *   automatic choice for text: a subtitle nobody asked for is an annoyance, while
 *   an audio track nobody asked for is simply the film.
 */
fun Player.applyLanguagePreferences(audio: TrackLanguage, subtitle: TrackLanguage) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setPreferredAudioLanguage(audio.tag)
        .setPreferredTextLanguage(subtitle.tag)
        // Without this, a stream whose subtitle track carries no language tag at
        // all would be switched on the moment any subtitle language is wanted.
        .setSelectUndeterminedTextLanguage(false)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle.tag == null)
        .build()
}
