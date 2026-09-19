package com.safir.iptv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safir.iptv.data.repository.EpgRepository
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.data.repository.VodRepository
import com.safir.iptv.data.remote.redactCredentials
import com.safir.iptv.domain.model.PlaylistSource
import com.safir.iptv.domain.model.SourceType
import com.safir.iptv.domain.model.SyncState
import com.safir.iptv.ui.i18n.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val type: SourceType = SourceType.XTREAM,
    val name: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val sync: SyncState = SyncState.Idle
) {
    val isBusy: Boolean get() = sync is SyncState.Running

    val canSubmit: Boolean
        get() = !isBusy && when (type) {
            SourceType.XTREAM ->
                serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
            SourceType.M3U -> m3uUrl.isNotBlank()
        }
}

class SetupViewModel(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    private val catalog: VodRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun setType(type: SourceType) = _state.update { it.copy(type = type, sync = SyncState.Idle) }
    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setServerUrl(value: String) = _state.update { it.copy(serverUrl = value) }
    fun setUsername(value: String) = _state.update { it.copy(username = value) }
    fun setPassword(value: String) = _state.update { it.copy(password = value) }
    fun setM3uUrl(value: String) = _state.update { it.copy(m3uUrl = value) }
    fun setEpgUrl(value: String) = _state.update { it.copy(epgUrl = value) }
    fun dismissError() = _state.update { it.copy(sync = SyncState.Idle) }

    /** Prefills the form when a source already exists (used from Settings). */
    fun loadExisting() {
        viewModelScope.launch {
            val existing = playlists.currentSource() ?: return@launch
            _state.update {
                when (existing.type) {
                    SourceType.XTREAM -> it.copy(
                        type = SourceType.XTREAM,
                        name = existing.name,
                        serverUrl = existing.url,
                        username = existing.username,
                        password = existing.password,
                        epgUrl = existing.epgUrl
                    )
                    SourceType.M3U -> it.copy(
                        type = SourceType.M3U,
                        name = existing.name,
                        m3uUrl = existing.url,
                        epgUrl = existing.epgUrl
                    )
                }
            }
        }
    }

    fun connect(onDone: () -> Unit) {
        val form = _state.value
        if (form.isBusy) return

        // Say what is missing instead of doing nothing — on a remote, a button that
        // swallows the press is indistinguishable from a broken app.
        val missing = when (form.type) {
            SourceType.XTREAM -> buildList {
                if (form.serverUrl.isBlank()) add(AppLocale.strings.serverUrl)
                if (form.username.isBlank()) add(AppLocale.strings.username)
                if (form.password.isBlank()) add(AppLocale.strings.password)
            }
            SourceType.M3U -> buildList {
                if (form.m3uUrl.isBlank()) add(AppLocale.strings.playlistUrl)
            }
        }
        if (missing.isNotEmpty()) {
            _state.update {
                it.copy(
                    sync = SyncState.Failed(
                        AppLocale.strings.missingFields(missing.joinToString(", "))
                    )
                )
            }
            return
        }

        val source = PlaylistSource(
            name = form.name.ifBlank { AppLocale.strings.myProvider },
            type = form.type,
            url = if (form.type == SourceType.XTREAM) form.serverUrl.trim() else form.m3uUrl.trim(),
            username = form.username.trim(),
            password = form.password.trim(),
            epgUrl = form.epgUrl.trim()
        )

        viewModelScope.launch {
            _state.update { it.copy(sync = SyncState.Running(AppLocale.strings.connecting)) }
            try {
                val channels = playlists.sync(source) { step, fraction ->
                    _state.update { it.copy(sync = SyncState.Running(step, fraction)) }
                }
                playlists.saveSource(source)
                // New credentials, possibly a new panel: nothing cached about the
                // old one may survive into the new account's catalogue.
                catalog.clearCache()

                // The guide is a bonus: a provider without one must not fail the setup.
                val programs = try {
                    epg.refresh(source, playlists.knownEpgIds()) { step, fraction ->
                        _state.update { it.copy(sync = SyncState.Running(step, fraction)) }
                    } ?: 0
                } catch (error: Exception) {
                    0
                }

                _state.update { it.copy(sync = SyncState.Done(channels, programs)) }
                onDone()
            } catch (error: Exception) {
                _state.update {
                    it.copy(sync = SyncState.Failed(error.friendlyMessage()))
                }
            }
        }
    }
}

internal fun Exception.friendlyMessage(): String {
    val raw = (message ?: this::class.java.simpleName).redactCredentials()
    val t = AppLocale.strings
    return when {
        raw.contains("Unable to resolve host", true) -> t.errHostUnreachable
        raw.contains("timeout", true) || raw.contains("timed out", true) -> t.errTimeout
        raw.contains("HTTP 401") || raw.contains("HTTP 403") -> t.errAuth
        raw.contains("HTTP 404") -> t.errNotFound404
        raw.contains("CertPath", true) || raw.contains("SSL", true) -> t.errSsl
        else -> raw
    }
}
