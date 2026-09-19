package com.safir.iptv.ui.vod

import com.safir.iptv.domain.model.Episode

/**
 * The handover between a catalogue screen and the film player.
 *
 * A stream URL carries the account's user name and password and can run past two
 * hundred characters; threading that through a navigation route means encoding it,
 * logging it, and keeping credentials in the back stack. One object passed in
 * memory avoids all three, and a film that is somehow lost on the way — a killed
 * process, nothing else does it — simply closes the player again.
 */
object VodPlayback {

    data class Request(
        val title: String,
        /** "S2 · E3 · Der Fall", or empty for a film. */
        val subtitle: String,
        val url: String,
        /** Where the viewer's position is filed between sittings. */
        val resumeKey: String,
        val startMs: Long,
        /** Carried so the "keep watching" row can draw a cover without a lookup. */
        val posterUrl: String? = null,
        val isEpisode: Boolean = false,
        val seriesId: Int = 0
    )

    @Volatile
    var pending: Request? = null

    /**
     * The rest of the season, in order, so the player can go on by itself.
     *
     * Set by the series screen at the moment an episode is started, because that is
     * the only place the whole season is already in hand. A film clears it: there
     * is no such thing as the next film, and quietly starting an unrelated one
     * would be the single most annoying thing this app could do.
     */
    @Volatile
    var queue: List<Request> = emptyList()

    /** The episode after this one, or null at the end of the season. */
    fun after(current: Request): Request? {
        val index = queue.indexOfFirst { it.resumeKey == current.resumeKey }
        return if (index >= 0 && index < queue.lastIndex) queue[index + 1] else null
    }

    /** One place builds an episode's request, so the queue and the opened episode agree. */
    fun episodeRequest(
        seriesName: String,
        seriesId: Int,
        episode: Episode,
        startMs: Long
    ) = Request(
        title = seriesName,
        subtitle = "S${episode.season} · E${episode.number}" +
            episode.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
        url = episode.streamUrl,
        resumeKey = episode.resumeKey,
        startMs = startMs,
        posterUrl = episode.imageUrl,
        isEpisode = true,
        seriesId = seriesId
    )
}
