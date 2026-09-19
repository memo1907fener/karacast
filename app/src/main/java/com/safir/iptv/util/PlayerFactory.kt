package com.safir.iptv.util

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.safir.iptv.data.remote.Http
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * One place that builds a player, so every screen gets the same decoders.
 *
 * The three players in this app — live, film, and the little preview in the channel
 * list — were each assembling their own, which meant a decoder setting fixed in one
 * of them stayed broken in the other two. They now all come from here.
 *
 * @param minBufferMs how much is buffered before playback starts.
 * @param maxBufferMs the ceiling; live streams are given far more than the preview.
 */
fun buildPlayer(
    context: Context,
    minBufferMs: Int,
    maxBufferMs: Int,
    bufferForPlaybackMs: Int = 1_500,
    bufferAfterRebufferMs: Int = 3_000
): ExoPlayer {
    val dataSourceFactory = DefaultDataSource.Factory(
        context,
        OkHttpDataSource.Factory(Http.client).setUserAgent(Http.userAgent)
    )

    /*
     * Why this matters for the channels that arrive without sound.
     *
     * A television box usually has several decoders that claim the same codec, and
     * ExoPlayer takes the one the system ranks first. When that one refuses the
     * stream — a slightly odd AC-3 header, a sample rate it does not really do —
     * the default behaviour is to give up on the whole renderer, and the channel
     * plays picture with silence. With fallback switched on the next decoder in the
     * list gets a turn instead, which is what fixes most silent channels outright.
     *
     * And when no decoder on the box will take the stream at all, there is a second
     * bench to fall back to: [NextRenderersFactory] is a DefaultRenderersFactory that
     * also carries FFmpeg built for Android, which decodes AC-3, E-AC-3, DTS, TrueHD,
     * MP2 and a good deal else in software. A television that was never sold with a
     * Dolby licence therefore still produces sound — it just spends a little processor
     * time doing it, which on a device that is otherwise idle is free.
     *
     * PREFER puts FFmpeg **in front of** the box's own decoders. For sound that is
     * exactly right. For picture it is a catastrophe — see [AudioFirstRenderersFactory].
     */
    val renderers = AudioFirstRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

    /*
     * And why the constraints are loosened: the defaults are written for phones on
     * mobile data, where refusing a track that is too big for the screen is a
     * kindness. On a television that same caution can leave a stream with no audio
     * track selected at all rather than one that is merely imperfect. Something
     * playing beats nothing playing.
     */
    val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setExceedAudioConstraintsIfNecessary(true)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .build()
        )
    }

    return ExoPlayer.Builder(context, renderers)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    minBufferMs,
                    maxBufferMs,
                    bufferForPlaybackMs,
                    bufferAfterRebufferMs
                )
                .build()
        )
        .build()
        .apply { setHandleAudioBecomingNoisy(true) }
}

/**
 * FFmpeg first for sound, the television's own chip first for picture.
 *
 * ### What went wrong
 *
 * `EXTENSION_RENDERER_MODE_PREFER` is not a preference for the *better* decoder; it
 * is an instruction to put FFmpeg **ahead of** the hardware one, for audio and video
 * alike. NextLib carries an FFmpeg video decoder too, so every channel was suddenly
 * being decoded in software: H.264 1080p through the processor of a television box
 * that was never meant to do that. The picture still arrived — at a handful of frames
 * per second. It looked like a bad stream or a bad connection, and it was neither.
 *
 * The chip in even a cheap box decodes 1080p in dedicated silicon, drawing almost no
 * power. Nothing in software comes close, and nothing needs to: hardware video
 * decoding is not a fallback, it is the only way this works at all.
 *
 * ### What this does
 *
 * Sound keeps PREFER, because that is what makes the channels without a Dolby licence
 * audible — and decoding audio in software costs a percent or two of one core.
 * Picture is handed the mode ON instead, which appends FFmpeg **behind** the hardware
 * decoder: it sits there unused until something turns up that the box genuinely
 * cannot decode, and then it quietly takes over.
 */
private class AudioFirstRenderersFactory(context: Context) : NextRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        super.buildVideoRenderers(
            context,
            // The one line this whole class exists for.
            if (extensionRendererMode == DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER) {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            } else {
                extensionRendererMode
            },
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
    }
}

/**
 * Picture but no sound: find an audio track this box can actually decode.
 *
 * Providers habitually put the AC-3 or DTS track first because that is what a
 * receiver wants, and carry a plain stereo AAC track behind it for everything else.
 * ExoPlayer selects the first one, the box cannot decode it, and the channel plays
 * in silence — with no error, because nothing actually failed.
 *
 * So: if nothing that is selected is also supported, take the first track anywhere
 * in the stream that *is* supported and insist on it. Called whenever the track
 * list changes, which is once per channel and costs nothing when all is well.
 *
 * @return true when a different track was forced, false when there was no problem
 *   or nothing better to switch to.
 */
fun Player.repairSilentAudio(): Boolean {
    val groups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    if (groups.isEmpty()) return false

    val alreadyAudible = groups.any { group ->
        (0 until group.length).any { group.isTrackSelected(it) && group.isTrackSupported(it) }
    }
    if (alreadyAudible) return false

    groups.forEach { group ->
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                // Cleared first, or a repair made three channels ago would still be
                // sitting in the parameters when this one is put right.
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                .build()
            return true
        }
    }
    // Every audio track in this stream is beyond the box. Nothing to be done here —
    // but the picture keeps running, which is still better than an error dialog.
    return false
}
