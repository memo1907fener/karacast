package com.safir.iptv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.domain.model.CategoryGroup
import com.safir.iptv.domain.model.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * The channel editor for one group: take channels out, put them back, move them.
 * Nothing is deleted from the catalogue — the group only remembers which ids it
 * skips and in which order it hands the rest out.
 */
class GroupChannelsViewModel(
    private val playlists: PlaylistRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _groupId = MutableStateFlow<String?>(null)

    val group: StateFlow<CategoryGroup?> =
        combine(_groupId, settings.groups) { id, groups ->
            groups.firstOrNull { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Hidden channels included — they have to stay reachable to be switched back on. */
    val channels: StateFlow<List<Channel>> = group
        .flatMapLatest { current ->
            if (current == null) flowOf(emptyList()) else playlists.observeGroupChannels(current)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setGroup(id: String) {
        if (_groupId.value != id) _groupId.value = id
    }

    fun toggleHidden(channelId: String) {
        val current = group.value ?: return
        val hidden = if (channelId in current.hiddenChannelIds) {
            current.hiddenChannelIds - channelId
        } else {
            current.hiddenChannelIds + channelId
        }
        // Pin the order that is on screen at the same time: without it, a channel
        // coming back would reappear at the provider's position, not the user's.
        save(current.copy(hiddenChannelIds = hidden, channelOrder = currentOrder(current)))
    }

    fun showAll() {
        val current = group.value ?: return
        if (current.hiddenChannelIds.isEmpty()) return
        save(current.copy(hiddenChannelIds = emptyList()))
    }

    fun move(channelId: String, delta: Int) {
        val current = group.value ?: return
        val ids = channels.value.map { it.id }.toMutableList()
        val from = ids.indexOf(channelId)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (to == from) return
        ids.removeAt(from)
        ids.add(to, channelId)
        save(current.copy(channelOrder = ids))
    }

    /** Sends one channel to the very top — the fast way to build a top ten. */
    fun moveToTop(channelId: String) {
        val current = group.value ?: return
        val ids = channels.value.map { it.id }.toMutableList()
        if (!ids.remove(channelId)) return
        ids.add(0, channelId)
        save(current.copy(channelOrder = ids))
    }

    fun resetOrder() {
        val current = group.value ?: return
        if (current.channelOrder.isEmpty()) return
        save(current.copy(channelOrder = emptyList()))
    }

    private fun currentOrder(current: CategoryGroup): List<String> =
        current.channelOrder.ifEmpty { channels.value.map { it.id } }

    private fun save(updated: CategoryGroup) {
        settings.saveGroups(
            settings.groups.value.map { if (it.id == updated.id) updated else it }
        )
    }
}
