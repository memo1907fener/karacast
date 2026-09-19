package com.safir.iptv.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.data.repository.EpgRepository
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.domain.model.ALL_CATEGORY_ID
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.FAVORITES_CATEGORY_ID
import com.safir.iptv.domain.model.NowNext
import com.safir.iptv.domain.model.RECENT_CATEGORY_ID
import com.safir.iptv.ui.i18n.AppLocale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelsViewModel(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    private val settings: SettingsStore
) : ViewModel() {

    /**
     * Set in settings when the user wants Live TV to behave like a satellite
     * receiver: straight into one fixed list, no overview of groups and favourites
     * in between. Read once — changing it mid-session would move the ground under
     * the list that is on screen.
     */
    private val directCategoryId: String? = settings.liveDirectCategoryId

    /** True while Live TV is pinned to one list, so Back leads out instead of up. */
    val directStart: Boolean = directCategoryId != null

    private val _selectedCategoryId = MutableStateFlow(
        directCategoryId ?: settings.lastCategoryId ?: ALL_CATEGORY_ID
    )
    val selectedCategoryId: StateFlow<String> = _selectedCategoryId.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _nowNext = MutableStateFlow<Map<String, NowNext>>(emptyMap())
    val nowNext: StateFlow<Map<String, NowNext>> = _nowNext.asStateFlow()

    /**
     * Two levels, like a set-top box: the category list, and the inside of one
     * category. Kept in the ViewModel so that coming back from the player returns
     * to the category you were in rather than dumping you at the top.
     */
    private val _inCategory = MutableStateFlow(
        directCategoryId != null || settings.lastCategoryId != null
    )
    val inCategory: StateFlow<Boolean> = _inCategory.asStateFlow()

    private val _searchMode = MutableStateFlow(false)
    val searchMode: StateFlow<Boolean> = _searchMode.asStateFlow()

    /** Channel the list should scroll to and focus — set by number entry. */
    private val _focusTargetId = MutableStateFlow<String?>(null)
    val focusTargetId: StateFlow<String?> = _focusTargetId.asStateFlow()

    /** Set while a typed number matches no channel, so the overlay can say so. */
    private val _numberMiss = MutableStateFlow<String?>(null)
    val numberMiss: StateFlow<String?> = _numberMiss.asStateFlow()

    val previewEnabled: Boolean get() = settings.previewEnabled
    val previewMuted: Boolean get() = settings.previewMuted

    /** Provider categories with the three virtual ones pinned to the top. */
    val categories: StateFlow<List<Category>> =
        combine(playlists.observeCategories(), AppLocale.current) { provided, _ ->
            val t = AppLocale.strings
            buildList {
                add(Category(FAVORITES_CATEGORY_ID, t.favorites))
                add(Category(RECENT_CATEGORY_ID, t.recentlyWatched))
                add(Category(ALL_CATEGORY_ID, t.allChannels, provided.sumOf { it.channelCount }))
                addAll(provided)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channels: StateFlow<List<Channel>> =
        combine(_selectedCategoryId, _query) { categoryId, query -> categoryId to query }
            .flatMapLatest { (categoryId, query) ->
                if (query.isBlank()) {
                    playlists.observeChannels(categoryId)
                } else {
                    playlists.search(query)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Keep now/next fresh for whatever list is on screen without hammering the DB.
        viewModelScope.launch {
            channels.collect { list -> refreshNowNext(list) }
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                refreshNowNext(channels.value)
            }
        }
    }

    private suspend fun refreshNowNext(list: List<Channel>) {
        val ids = list.mapNotNull { it.epgId }.filter { it.isNotBlank() }
        _nowNext.value = if (ids.isEmpty()) emptyMap() else epg.nowNext(ids)
    }

    fun enterCategory(id: String) {
        selectCategory(id)
        _searchMode.value = false
        _inCategory.value = true
    }

    fun enterSearch() {
        selectCategory(ALL_CATEGORY_ID)
        _searchMode.value = true
        _inCategory.value = true
    }

    /**
     * Back out one step. With Live TV pinned to a single list there is no category
     * level to fall back to, so leaving search returns to that list and leaving the
     * list itself is refused — the screen then closes instead.
     *
     * @return false when there is nothing left to back out of here.
     */
    fun leaveCategory(): Boolean {
        if (!_inCategory.value) return false
        val leavingSearch = _searchMode.value
        _searchMode.value = false
        _query.value = ""

        if (directCategoryId != null) {
            if (!leavingSearch) return false
            selectCategory(directCategoryId)
            return true
        }

        _inCategory.value = false
        return true
    }

    fun selectCategory(id: String) {
        _selectedCategoryId.value = id
        settings.lastCategoryId = id
        if (_query.value.isNotBlank()) _query.value = ""
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    /**
     * Jumps to a channel by its number. Falls back to the nearest lower number, so
     * typing 250 on a list that stops at 243 still lands somewhere sensible rather
     * than doing nothing.
     */
    fun jumpToNumber(number: Int) {
        val list = channels.value
        if (list.isEmpty()) return
        val exact = list.firstOrNull { it.number == number }
        val target = exact ?: list.lastOrNull { it.number < number } ?: list.first()
        _numberMiss.value = if (exact == null) AppLocale.strings.numberMissing(number) else null
        _focusTargetId.value = target.id
    }

    fun consumeFocusTarget() {
        _focusTargetId.value = null
    }

    fun clearNumberMiss() {
        _numberMiss.value = null
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { playlists.toggleFavorite(channel.id) }
    }

    fun nowNextFor(channel: Channel): NowNext? =
        channel.epgId?.let { _nowNext.value[it] }
}
