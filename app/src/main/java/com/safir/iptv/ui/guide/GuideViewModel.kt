package com.safir.iptv.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.data.repository.EpgRepository
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.domain.model.ALL_CATEGORY_ID
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.Program
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Feeds the programme grid. The window is fixed when the screen opens: two hours of
 * hindsight (that is what catch-up is for) and twelve hours ahead.
 */
class GuideViewModel(
    playlists: PlaylistRepository,
    private val epg: EpgRepository,
    settings: SettingsStore
) : ViewModel() {

    val windowStartMs: Long = run {
        val half = 30 * 60 * 1000L
        (System.currentTimeMillis() / half) * half - 2 * 60 * 60 * 1000L
    }
    val windowEndMs: Long = windowStartMs + 14 * 60 * 60 * 1000L

    /**
     * The list Live TV works in — a grid over 1000 channels helps nobody, and
     * playback started from here has to inherit the same numbering.
     */
    val categoryId: String = settings.homeCategoryId

    val channels: StateFlow<List<Channel>> = playlists.observeChannels(categoryId)
        .map { it.take(MAX_ROWS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _programs = MutableStateFlow<Map<String, List<Program>>>(emptyMap())
    val programs: StateFlow<Map<String, List<Program>>> = _programs.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            channels.collect { list ->
                if (list.isEmpty()) return@collect
                _loading.value = true
                _programs.value = epg.window(
                    epgIds = list.mapNotNull { it.epgId },
                    fromMs = windowStartMs,
                    toMs = windowEndMs
                )
                _loading.value = false
            }
        }
    }

    private companion object {
        const val MAX_ROWS = 200
    }
}
