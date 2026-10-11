package com.safir.iptv.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.safir.iptv.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Something on the television's home screen that was clicked. */
sealed interface DeepLink {
    /** A live channel, by its id ("2:1234" — the access is part of it). */
    data class Channel(val channelId: String) : DeepLink

    /** A half-watched film or episode, filed under [key] in access [sourceId]. */
    data class Continue(val sourceId: Long, val key: String) : DeepLink
}

/**
 * Hands a click from outside to the navigation, which may not exist yet.
 *
 * The launcher starts the activity — or, since it is singleTask, delivers to the
 * one already running — and the activity drops the link here. The navigation
 * picks it up once it is on screen, which on a cold start is only after the
 * licence check and the database have had their say.
 */
object DeepLinks {

    private const val SCHEME = "karacast"

    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun offer(intent: Intent?) {
        parse(intent?.data)?.let { _pending.value = it }
    }

    fun consume(): DeepLink? = _pending.value.also { _pending.value = null }

    fun channelUri(context: Context, channelId: String): Uri =
        explicit(context, Uri.parse("$SCHEME://channel?id=${Uri.encode(channelId)}"))

    fun continueUri(context: Context, sourceId: Long, key: String): Uri =
        explicit(context, Uri.parse("$SCHEME://continue?source=$sourceId&key=${Uri.encode(key)}"))

    /**
     * Wrapped as an explicit intent for this very activity, so no intent filter
     * has to be opened to the rest of the system for links nobody else should send.
     */
    private fun explicit(context: Context, data: Uri): Uri = Uri.parse(
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(data)
            .toUri(Intent.URI_INTENT_SCHEME)
    )

    private fun parse(uri: Uri?): DeepLink? {
        if (uri == null || uri.scheme != SCHEME) return null
        return when (uri.host) {
            "channel" -> uri.getQueryParameter("id")?.takeIf { it.isNotBlank() }
                ?.let { DeepLink.Channel(it) }
            "continue" -> {
                val source = uri.getQueryParameter("source")?.toLongOrNull()
                val key = uri.getQueryParameter("key")
                if (source != null && !key.isNullOrBlank()) DeepLink.Continue(source, key) else null
            }
            else -> null
        }
    }
}
