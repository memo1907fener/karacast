package com.safir.iptv.data.repository

import com.safir.iptv.domain.model.PlaylistSource

/**
 * Moving between saved accesses, and letting go of one.
 *
 * Its own small class because three repositories are involved and each of them
 * has to hear about it: the channel lists follow the active access by themselves,
 * but the film catalogue is held in memory and the guide is stored per access.
 */
class SourceSwitcher(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    private val catalog: VodRepository
) {

    /** Nothing has to be downloaded: every access keeps its channels and guide. */
    suspend fun switchTo(sourceId: Long) {
        playlists.activate(sourceId)
        catalog.clearCache()
    }

    /**
     * Removes [sourceId] with everything that belongs to it.
     * @return the access now on screen, or null when none is left.
     */
    suspend fun remove(sourceId: Long): PlaylistSource? {
        epg.clear(sourceId)
        playlists.removeSource(sourceId)
        catalog.clearCache()
        return playlists.ensureActive()
    }
}
