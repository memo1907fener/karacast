package com.safir.iptv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safir.iptv.BuildConfig
import com.safir.iptv.data.prefs.SettingsStore
import android.content.Context
import android.os.Build
import com.safir.iptv.data.remote.PanelClient
import com.safir.iptv.data.remote.UpdateChecker
import com.safir.iptv.data.remote.UpdateInstaller
import com.safir.iptv.data.repository.EpgRepository
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.data.repository.VodRepository
import com.safir.iptv.domain.model.ALL_CATEGORY_ID
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.ChannelSort
import com.safir.iptv.domain.model.FAVORITES_CATEGORY_ID
import com.safir.iptv.domain.model.GROUP_PREFIX
import com.safir.iptv.domain.model.PlaylistSource
import com.safir.iptv.domain.model.TrackLanguage
import com.safir.iptv.domain.model.UpdateInfo
import com.safir.iptv.util.CrashGuard
import com.safir.iptv.util.CrashReport
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.ui.login.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val source: PlaylistSource? = null,
    val channelCount: Int = 0,
    val hasGuide: Boolean = false,
    val preferHls: Boolean = false,
    val bufferSeconds: Int = 8,
    val autoRecover: Boolean = true,
    val audioLanguage: TrackLanguage = TrackLanguage.AUTO,
    val subtitleLanguage: TrackLanguage = TrackLanguage.AUTO,
    val userAgent: String = "",
    val showClock: Boolean = true,
    val resumeLastChannel: Boolean = false,
    val previewEnabled: Boolean = true,
    val previewMuted: Boolean = false,
    val groupCount: Int = 0,
    /** Null = Live TV shows the overview; otherwise the list it jumps straight into. */
    val liveDirectCategoryId: String? = null,
    val channelSort: ChannelSort = ChannelSort.NUMBER,
    val autoStartOnBoot: Boolean = false,
    val updateUrl: String = "",
    val updateInfo: UpdateInfo? = null,
    /** What the app died of last time, if it ever has. */
    val lastCrash: CrashReport? = null,
    val parentalEnabled: Boolean = false,
    /** True once a code has been chosen. Never the code itself. */
    val hasPin: Boolean = false,
    val parentalKeywords: List<String> = emptyList(),
    val autoUpdateCheck: Boolean = true,
    /** True while an update APK is coming down. -1 = the server did not say how big. */
    val downloading: Boolean = false,
    val downloadPercent: Int = -1,
    val panelUrl: String = "",
    val panelCode: String = "",
    val panelDeviceId: String = "",
    val panelLastSyncAt: Long = 0L,
    val panelBusy: Boolean = false,
    /** What the panel calls this set, once it has answered. */
    val panelDeviceName: String = "",
    /** An account the panel offered, waiting for somebody to accept it. */
    val panelSource: PlaylistSource? = null,
    val busyMessage: String? = null,
    val message: String? = null,
    val error: String? = null
)

class SettingsViewModel(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    private val settings: SettingsStore,
    private val catalog: VodRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Provider categories only — the user's own groups are managed on their own screen. */
    val categories: StateFlow<List<Category>> = playlists.observeCategories()
        .map { list -> list.filterNot { it.id.startsWith(GROUP_PREFIX) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Every provider category, locked ones included — the one list in the app that
     * is not filtered, because this is where locking happens and an invisible
     * category could never be let back out.
     */
    val allCategories: StateFlow<List<Category>> = playlists.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Which of them are locked, as bare ids the list can compare against. */
    val lockedCategoryIds: StateFlow<Set<String>> = settings.lockedLiveCategoryIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * What "Live TV" can be pointed at: the overview, the two always-present lists,
     * and every group the user built. Deliberately not the provider's own hundred-odd
     * categories — this is a decision made once with a remote, not a directory.
     */
    val startOptions: StateFlow<List<Category>> =
        combine(settings.groups, AppLocale.current) { groups, _ ->
            val t = AppLocale.strings
            buildList {
                add(Category(OVERVIEW_OPTION_ID, t.showOverview))
                add(Category(FAVORITES_CATEGORY_ID, t.favorites))
                add(Category(ALL_CATEGORY_ID, t.allChannels))
                groups.forEach { group ->
                    add(Category(GROUP_PREFIX + group.id, group.name, group.categoryIds.size))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    /** @param optionId [OVERVIEW_OPTION_ID] for "show the overview", else a list id. */
    fun setLiveDirect(optionId: String) {
        val value = optionId.takeUnless { it == OVERVIEW_OPTION_ID }
        settings.liveDirectCategoryId = value
        _state.update {
            it.copy(
                liveDirectCategoryId = value,
                message = if (value == null) {
                    AppLocale.strings.liveShowsOverviewAgain
                } else {
                    AppLocale.strings.liveJumpsDirect
                }
            )
        }
    }

    /**
     * Moves a category one place up or down. The whole visible order is written out,
     * not just the moved entry, so categories the user never touched keep their
     * places instead of drifting to the end.
     */
    fun moveCategory(categoryId: String, delta: Int) {
        val ids = categories.value.map { it.id }
        val from = ids.indexOf(categoryId)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (to == from) return
        val reordered = ids.toMutableList()
        reordered.removeAt(from)
        reordered.add(to, categoryId)
        settings.saveCategoryOrder(reordered)
    }

    fun setShowClock(value: Boolean) {
        settings.showClock = value
        _state.update { it.copy(showClock = value) }
    }

    fun setResumeLastChannel(value: Boolean) {
        settings.resumeLastChannel = value
        _state.update { it.copy(resumeLastChannel = value) }
    }

    fun setPreviewEnabled(value: Boolean) {
        settings.previewEnabled = value
        _state.update { it.copy(previewEnabled = value) }
    }

    fun setChannelSort(value: ChannelSort) {
        settings.setChannelSort(value)
        _state.update { it.copy(channelSort = value) }
    }

    fun setAutoStartOnBoot(value: Boolean) {
        settings.autoStartOnBoot = value
        _state.update { it.copy(autoStartOnBoot = value) }
    }

    fun setUpdateUrl(value: String) {
        settings.updateUrl = value
        _state.update { it.copy(updateUrl = settings.updateUrl, updateInfo = null) }
    }

    /** Asks the configured address whether a newer build exists. Installs nothing. */
    fun checkForUpdate() {
        val url = settings.updateUrl
        if (url.isBlank()) {
            _state.update { it.copy(error = AppLocale.strings.noUpdateAddress) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(message = null, error = null) }
            try {
                val info = UpdateChecker.check(url, BuildConfig.VERSION_CODE)
                _state.update {
                    it.copy(
                        updateInfo = info,
                        message = if (info == null) AppLocale.strings.alreadyLatest else null
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.friendlyMessage()) }
            }
        }
    }

    fun setPreviewMuted(value: Boolean) {
        settings.previewMuted = value
        _state.update { it.copy(previewMuted = value) }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    source = playlists.currentSource(),
                    channelCount = playlists.channelCount(),
                    hasGuide = epg.hasGuide(),
                    preferHls = settings.preferHls,
                    bufferSeconds = settings.bufferSeconds,
                    autoRecover = settings.autoRecover,
                    audioLanguage = settings.audioLanguage,
                    subtitleLanguage = settings.subtitleLanguage,
                    userAgent = settings.userAgent,
                    showClock = settings.showClock,
                    resumeLastChannel = settings.resumeLastChannel,
                    previewEnabled = settings.previewEnabled,
                    previewMuted = settings.previewMuted,
                    groupCount = settings.groups.value.size,
                    liveDirectCategoryId = settings.liveDirectCategoryId,
                    channelSort = settings.channelSort.value,
                    autoStartOnBoot = settings.autoStartOnBoot,
                    updateUrl = settings.updateUrl,
                    autoUpdateCheck = settings.autoUpdateCheck,
                    panelUrl = settings.panelUrl,
                    panelCode = settings.panelCode,
                    // Read rather than generated: the getter only mints an id the
                    // first time something actually asks for one.
                    panelDeviceId = settings.deviceId,
                    panelLastSyncAt = settings.panelLastSyncAt,
                    lastCrash = CrashGuard.lastCrash(),
                    parentalEnabled = settings.parentalEnabled,
                    hasPin = settings.parentalPin.isNotBlank(),
                    parentalKeywords = settings.parentalKeywords
                )
            }
        }
    }

    fun resyncPlaylist() = runTask(AppLocale.strings.reloadingPlaylist) { source ->
        val count = playlists.sync(source) { step, _ ->
            _state.update { it.copy(busyMessage = step) }
        }
        AppLocale.strings.nChannelsUpdated(count)
    }

    fun refreshGuide() = runTask(AppLocale.strings.loadingEpg) { source ->
        val programs = epg.refresh(source, playlists.knownEpgIds()) { step, _ ->
            _state.update { it.copy(busyMessage = step) }
        }
        when {
            programs == null -> AppLocale.strings.noEpgUrl
            programs == 0 -> AppLocale.strings.noProgramsDelivered
            else -> AppLocale.strings.nProgramsLoaded(programs)
        }
    }

    private fun runTask(initial: String, block: suspend (PlaylistSource) -> String) {
        viewModelScope.launch {
            val source = playlists.currentSource()
            if (source == null) {
                _state.update { it.copy(error = AppLocale.strings.noSourceConfigured) }
                return@launch
            }
            _state.update { it.copy(busyMessage = initial, message = null, error = null) }
            // A re-sync means the provider has reorganised something; the film
            // catalogue held in memory is about that provider too.
            catalog.clearCache()
            try {
                val result = block(source)
                _state.update { it.copy(busyMessage = null, message = result) }
            } catch (error: Exception) {
                _state.update { it.copy(busyMessage = null, error = error.friendlyMessage()) }
            }
            refresh()
        }
    }

    fun setAutoUpdateCheck(value: Boolean) {
        settings.autoUpdateCheck = value
        _state.update { it.copy(autoUpdateCheck = value) }
    }

    /**
     * Fetches the new build and hands it to Android, which asks before installing.
     *
     * [context] is passed in rather than held: a ViewModel outlives the screen that
     * created it, and a Context kept past that is a leak waiting to happen.
     */
    fun downloadAndInstall(context: Context) {
        val info = _state.value.updateInfo ?: return
        if (!UpdateInstaller.canInstall(context)) {
            UpdateInstaller.openInstallPermission(context)
            _state.update { it.copy(message = AppLocale.strings.update.installHint) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(downloading = true, downloadPercent = -1, error = null, message = null)
            }
            try {
                val file = UpdateInstaller.download(
                    context = context,
                    url = info.downloadUrl,
                    versionName = info.versionName,
                    onProgress = { percent ->
                        _state.update { it.copy(downloadPercent = percent) }
                    }
                )
                UpdateInstaller.install(context, file)
                _state.update {
                    it.copy(downloading = false, message = AppLocale.strings.update.readyToInstall)
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(downloading = false, error = AppLocale.strings.update.failed)
                }
            }
        }
    }

    // ----------------------------------------------------------------- the panel

    fun setPanelUrl(value: String) {
        settings.panelUrl = value
        _state.update { it.copy(panelUrl = settings.panelUrl, panelSource = null) }
    }

    fun setPanelCode(value: String) {
        settings.panelCode = value
        _state.update { it.copy(panelCode = settings.panelCode, panelSource = null) }
    }

    /**
     * Says hello to the panel and shows whatever comes back.
     *
     * Nothing is applied here. An account that arrives is *offered*, and somebody
     * has to accept it — a television that silently repointed itself at a different
     * provider because a server said so would be a very unpleasant surprise.
     */
    fun connectPanel() {
        val url = settings.panelUrl
        if (url.isBlank()) {
            _state.update { it.copy(error = AppLocale.strings.panel.notConfigured) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(panelBusy = true, message = null, error = null) }
            try {
                val reply = PanelClient.hello(
                    baseUrl = url,
                    deviceId = settings.deviceId,
                    code = settings.panelCode,
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = BuildConfig.VERSION_NAME,
                    model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    androidSdk = Build.VERSION.SDK_INT,
                    language = AppLocale.current.value.tag
                )
                settings.panelLastSyncAt = System.currentTimeMillis()
                _state.update { current ->
                    current.copy(
                        panelBusy = false,
                        panelLastSyncAt = settings.panelLastSyncAt,
                        panelDeviceName = reply?.deviceName.orEmpty(),
                        panelSource = reply?.source,
                        updateInfo = reply?.update ?: current.updateInfo,
                        message = when {
                            reply == null -> AppLocale.strings.panel.notConfigured
                            reply.source == null -> AppLocale.strings.panel.nothingReceived
                            else -> null
                        }
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(panelBusy = false, error = AppLocale.strings.panel.failed)
                }
            }
        }
    }

    /** Takes the offered account: saves it, loads its playlist, keeps the old one on failure. */
    fun applyPanelSource() {
        val source = _state.value.panelSource ?: return
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = AppLocale.strings.reloadingPlaylist) }
            catalog.clearCache()
            try {
                val channels = playlists.sync(source) { step, _ ->
                    _state.update { it.copy(busyMessage = step) }
                }
                playlists.saveSource(source)
                _state.update {
                    it.copy(
                        busyMessage = null,
                        panelSource = null,
                        message = AppLocale.strings.panel.sourceReceived(source.name) +
                            " · " + AppLocale.strings.nChannelsUpdated(channels)
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(busyMessage = null, error = error.friendlyMessage())
                }
            }
            refresh()
        }
    }

    // ------------------------------------------------------------ the child lock

    /**
     * Switching the lock changes what the catalogue is allowed to contain, and the
     * catalogue is held in memory for the evening — so it is thrown away here
     * rather than going on showing shelves that are now supposed to be gone.
     */
    fun setParentalEnabled(value: Boolean) {
        settings.parentalEnabled = value
        _state.update { it.copy(parentalEnabled = value) }
        viewModelScope.launch { catalog.clearCache() }
    }

    /** @return true when [input] is the code. Always false when none is set. */
    fun pinMatches(input: String): Boolean =
        settings.parentalPin.isNotBlank() && input.trim() == settings.parentalPin

    /** @return null when saved, otherwise what was wrong with it. */
    fun savePin(pin: String, repeat: String): String? {
        val value = pin.trim()
        val t = AppLocale.strings.parental
        if (value.length != PIN_LENGTH || value.any { !it.isDigit() }) return t.pinTooShort
        if (value != repeat.trim()) return t.pinMismatch
        settings.parentalPin = value
        _state.update { it.copy(hasPin = true, message = t.pinSaved) }
        return null
    }

    fun addKeyword(word: String) {
        val value = word.trim().lowercase()
        if (value.isBlank() || value in settings.parentalKeywords) return
        settings.parentalKeywords = settings.parentalKeywords + value
        _state.update { it.copy(parentalKeywords = settings.parentalKeywords) }
        viewModelScope.launch { catalog.clearCache() }
    }

    fun removeKeyword(word: String) {
        settings.parentalKeywords = settings.parentalKeywords - word
        _state.update { it.copy(parentalKeywords = settings.parentalKeywords) }
        viewModelScope.launch { catalog.clearCache() }
    }

    fun setCategoryLocked(categoryId: String, locked: Boolean) {
        settings.setLiveCategoryBlocked(categoryId, locked)
    }

    /** Wipes the note the crash guard left, once it has been read. */
    fun clearCrashReport() {
        CrashGuard.forget()
        _state.update { it.copy(lastCrash = null) }
    }

    fun setAudioLanguage(value: TrackLanguage) {
        settings.audioLanguage = value
        _state.update { it.copy(audioLanguage = value) }
    }

    fun setSubtitleLanguage(value: TrackLanguage) {
        settings.subtitleLanguage = value
        _state.update { it.copy(subtitleLanguage = value) }
    }

    fun setAutoRecover(value: Boolean) {
        settings.autoRecover = value
        _state.update { it.copy(autoRecover = value) }
    }

    fun setPreferHls(value: Boolean) {
        settings.preferHls = value
        _state.update { it.copy(preferHls = value, message = AppLocale.strings.hlsAfterReload) }
    }

    fun setBufferSeconds(value: Int) {
        settings.bufferSeconds = value
        _state.update { it.copy(bufferSeconds = settings.bufferSeconds) }
    }

    fun setUserAgent(value: String) {
        settings.userAgent = value
        _state.update { it.copy(userAgent = settings.userAgent) }
    }

    fun disconnect(onDone: () -> Unit) {
        viewModelScope.launch {
            epg.clear()
            playlists.clearEverything()
            catalog.clearCache()
            settings.lastChannelId = null
            settings.lastCategoryId = null
            onDone()
        }
    }

    fun dismissMessages() = _state.update { it.copy(message = null, error = null) }

    companion object {
        /** Stands in for "no direct list" so the picker can be a plain list of ids. */
        const val OVERVIEW_OPTION_ID = "__overview__"

        /** Four digits: short enough to remember, long enough not to be guessed. */
        const val PIN_LENGTH = 4
    }
}
