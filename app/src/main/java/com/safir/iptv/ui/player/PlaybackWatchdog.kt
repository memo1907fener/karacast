package com.safir.iptv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/**
 * Watches a running stream and says when it has died quietly.
 *
 * ExoPlayer reports the loud failures by itself — a refused connection, a 404, a
 * codec it cannot drive — through `onPlayerError`. The failure that actually
 * ruins an evening is the silent one: the provider stops sending, the socket
 * stays open, and the player sits in BUFFERING forever, or keeps the last frame
 * on screen and reports READY while the clock stands still. Nothing is thrown,
 * nothing is logged, and without something like this the television simply stays
 * frozen until somebody presses a button.
 *
 * So this polls the two things that cannot lie: what the player claims to be
 * doing, and whether the playback position is actually moving. If neither has
 * moved for [STALL_AFTER_MS], the stream is declared dead and [onStalled] fires
 * once. [onHealthy] fires while the position is advancing, which is what lets
 * the repair counter reset after a recovery has worked.
 *
 * @param restartKey change this whenever the player is re-prepared; the watch
 *   starts over from zero rather than counting a fresh stream's start-up as more
 *   of the old stream's silence.
 */
@Composable
fun PlaybackWatchdog(
    player: Player,
    restartKey: Any?,
    enabled: Boolean,
    onStalled: () -> Unit,
    onHealthy: () -> Unit
) {
    LaunchedEffect(player, restartKey, enabled) {
        if (!enabled) return@LaunchedEffect

        var lastPosition = -1L
        var lastProgressAt = System.currentTimeMillis()

        while (true) {
            delay(TICK_MS)
            val now = System.currentTimeMillis()

            // Paused on purpose — in the background, or by the lifecycle observer.
            // A standing clock is correct then, so the timer is held open.
            if (!player.playWhenReady) {
                lastProgressAt = now
                lastPosition = player.currentPosition
                continue
            }

            val position = player.currentPosition
            if (position > lastPosition + MIN_ADVANCE_MS) {
                lastPosition = position
                lastProgressAt = now
                onHealthy()
                continue
            }

            // IDLE means the player has given up, BUFFERING that it is waiting for
            // bytes that may never come, and READY-without-progress that the picture
            // is frozen. All three are the same thing to a viewer: nothing happens.
            val waiting = when (player.playbackState) {
                Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY -> true
                else -> false
            }
            if (waiting && now - lastProgressAt > STALL_AFTER_MS) {
                onStalled()
                return@LaunchedEffect
            }
        }
    }
}

/** A second is short enough to react to and cheap enough to ignore. */
private const val TICK_MS = 1_000L

/**
 * Anything under this is measurement noise rather than playback — a live stream
 * that is genuinely running moves a full second every second.
 */
private const val MIN_ADVANCE_MS = 250L

/**
 * Long enough that an ordinary hiccup — a re-buffer on a busy line, a keyframe
 * gap after a channel change — heals by itself and is never counted as a fault;
 * short enough that nobody sits and stares at a frozen frame wondering.
 */
private const val STALL_AFTER_MS = 9_000L
