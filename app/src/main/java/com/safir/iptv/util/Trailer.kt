package com.safir.iptv.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Öffnet den Trailer eines Films oder einer Serie in der YouTube-App.
 *
 * Anbieter liefern ihn als bloße Kennung („dQw4w9WgXcQ“) oder als ganze Adresse.
 * Der Fernseher entscheidet selbst, welche App die Adresse öffnet — auf Google TV
 * ist das YouTube, und Zurück führt von dort wieder hierher.
 */
object Trailer {

    private val IdOnly = Regex("""^[A-Za-z0-9_-]{6,20}$""")

    /** Die Adresse zum Trailer, oder null, wenn da nichts Brauchbares steht. */
    fun url(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "0" || value.equals("null", ignoreCase = true)) return null
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            IdOnly.matches(value) -> "https://www.youtube.com/watch?v=$value"
            else -> null
        }
    }

    fun open(context: Context, raw: String?, missingAppMessage: String) {
        val url = url(raw) ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, missingAppMessage, Toast.LENGTH_LONG).show()
        }
    }
}
