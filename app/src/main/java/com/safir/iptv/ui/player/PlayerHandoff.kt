package com.safir.iptv.ui.player

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import com.safir.iptv.domain.model.Channel

/**
 * Reicht den laufenden Player zwischen Vorschau und Vollbild weiter.
 *
 * Läuft ein Kanal schon in der Vorschau, muss das Vollbild ihn nicht noch
 * einmal verbinden: es übernimmt denselben Player samt Puffer, und das Bild
 * wird nur größer. Zurück geht er genauso an die Vorschau — auch wenn im
 * Vollbild umgeschaltet wurde, läuft dort dann der neue Kanal weiter.
 *
 * Wer ihn nicht innerhalb weniger Sekunden abholt, bekommt ihn nicht mehr: dann
 * wird er freigegeben, damit nie ein Player unsichtbar weiterspielt (und beim
 * Anbieter eine Verbindung belegt).
 */
object PlayerHandoff {

    class Taken(val player: ExoPlayer, val channel: Channel)

    private var player: ExoPlayer? = null
    private var channel: Channel? = null
    private val main = Handler(Looper.getMainLooper())
    private val expire = Runnable { drop() }

    /** Gibt [player] ab; der läuft weiter, bis ihn jemand übernimmt. */
    fun give(player: ExoPlayer, channel: Channel) {
        if (this.player !== player) drop()
        this.player = player
        this.channel = channel
        main.removeCallbacks(expire)
        main.postDelayed(expire, 4_000L)
    }

    /** Übernimmt den Player, wenn er gerade [channelId] spielt. */
    fun take(channelId: String): Taken? {
        if (channel?.id != channelId) return null
        return takeAny()
    }

    /** Übernimmt den Player, egal welcher Kanal darauf läuft. */
    fun takeAny(): Taken? {
        val p = player ?: return null
        val c = channel ?: return null
        main.removeCallbacks(expire)
        player = null
        channel = null
        return Taken(p, c)
    }

    private fun drop() {
        main.removeCallbacks(expire)
        runCatching { player?.release() }
        player = null
        channel = null
    }
}
