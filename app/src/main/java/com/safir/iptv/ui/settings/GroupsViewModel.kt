package com.safir.iptv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.CategoryGroup
import com.safir.iptv.domain.model.GROUP_PREFIX
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class GroupsViewModel(
    playlists: PlaylistRepository,
    private val settings: SettingsStore
) : ViewModel() {

    /** The provider's own categories — the raw material a group is built from. */
    val categories: StateFlow<List<Category>> = playlists.observeCategories()
        .map { list -> list.filterNot { it.id.startsWith(GROUP_PREFIX) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<CategoryGroup>> = settings.groups

    private val _editingId = MutableStateFlow<String?>(null)
    val editingId: StateFlow<String?> = _editingId.asStateFlow()

    val editing: CategoryGroup?
        get() = groups.value.firstOrNull { it.id == _editingId.value }

    fun select(groupId: String) {
        _editingId.value = groupId
    }

    fun createGroup() {
        val group = CategoryGroup(
            id = System.currentTimeMillis().toString(36),
            name = nextName(),
            categoryIds = emptyList()
        )
        settings.saveGroups(groups.value + group)
        _editingId.value = group.id
    }

    fun rename(name: String) {
        val id = _editingId.value ?: return
        settings.saveGroups(
            groups.value.map { if (it.id == id) it.copy(name = name) else it }
        )
    }

    fun toggleMember(categoryId: String) {
        val id = _editingId.value ?: return
        settings.saveGroups(
            groups.value.map { group ->
                if (group.id != id) {
                    group
                } else if (categoryId in group.categoryIds) {
                    group.copy(categoryIds = group.categoryIds - categoryId)
                } else {
                    group.copy(categoryIds = group.categoryIds + categoryId)
                }
            }
        )
    }

    fun deleteEditing() {
        val id = _editingId.value ?: return
        settings.saveGroups(groups.value.filterNot { it.id == id })
        _editingId.value = groups.value.firstOrNull()?.id
    }

    fun move(groupId: String, delta: Int) {
        val current = groups.value.toMutableList()
        val from = current.indexOfFirst { it.id == groupId }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, current.lastIndex)
        if (to == from) return
        val group = current.removeAt(from)
        current.add(to, group)
        settings.saveGroups(current)
    }

    private fun nextName(): String {
        val taken = groups.value.map { it.name }.toSet()
        val word = AppLocale.strings.group
        var index = groups.value.size + 1
        while ("$word $index" in taken) index++
        return "$word $index"
    }
}
