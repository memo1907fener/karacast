package com.safir.iptv.util

/**
 * Swaps an Xtream stream URL between its two containers.
 *
 * A panel serves the same channel twice: `.ts` is the raw MPEG-TS mux, which
 * starts fastest, and `.m3u8` is the HLS version, which survives a wobbly line
 * far better because every segment is a fresh request. When one of them dies —
 * and on most providers exactly one of them does — the other usually keeps
 * working, so this is the single most effective repair the player can attempt
 * without asking anybody anything.
 *
 * @return the other form, or null when the URL is not one the trick applies to
 *   (an M3U playlist may hand out anything at all, and guessing there would only
 *   turn a working stream into a broken one).
 */
fun String.swapStreamContainer(): String? {
    val queryStart = indexOf('?')
    val path = if (queryStart >= 0) substring(0, queryStart) else this
    val query = if (queryStart >= 0) substring(queryStart) else ""
    return when {
        path.endsWith(".ts", ignoreCase = true) -> path.dropLast(3) + ".m3u8" + query
        path.endsWith(".m3u8", ignoreCase = true) -> path.dropLast(5) + ".ts" + query
        else -> null
    }
}
