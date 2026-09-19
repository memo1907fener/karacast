package com.safir.iptv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.data.repository.EpgRepository
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.domain.model.ALL_CATEGORY_ID
import com.safir.iptv.domain.model.AspectMode
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.NowNext
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.util.swapStreamContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which page of the options panel is on screen, if any. */
enum class OptionsPage { NONE, ROOT, AUDIO, SUBTITLE, ASPECT, SLEEP }

data class PlayerUiState(
    val channel: Channel? = null,
    val playlist: List<Channel> = emptyList(),
    val nowNext: NowNext? = null,
    val isBuffering: Boolean = true,
    val error: String? = null,
    val infoVisible: Boolean = true,
    /** Set while a past programme is being replayed from the provider's archive. */
    val archiveLabel: String? = null,
    val channelListVisible: Boolean = false,
    val optionsPage: OptionsPage = OptionsPage.NONE,
    val aspectMode: AspectMode = AspectMode.FIT,
    /** 0 while the stream is healthy; otherwise which repair attempt is running. */
    val recoverAttempt: Int = 0,
    /** True once the repair has fallen back to the other container (TS ↔ HLS). */
    val switchedFormat: Boolean = false,
    /** The coloured-key bar, which has to be opened before a colour does anything. */
    val colorBarVisible: Boolean = false,
    /**
     * Wahr, wenn die Infoleiste da ist, weil jemand sie gerufen hat — und nicht,
     * weil gerade umgeschaltet wurde. Der Unterschied entscheidet, was die
     * Taste „runter" tut: beim Umschalten gehört sie dem nächsten Kanal, bei
     * einer gerufenen Leiste ihren Einträgen.
     */
    val infoPinned: Boolean = false,
    /** Name of the channel the picture format is filed against, for the panel. */
    val previousChannelName: String? = null
) {
    val recovering: Boolean get() = recoverAttempt > 0

    val optionsVisible: Boolean get() = optionsPage != OptionsPage.NONE

    val index: Int get() = playlist.indexOfFirst { it.id == channel?.id }
    val position: String
        get() = if (index >= 0 && playlist.isNotEmpty()) "${index + 1}/${playlist.size}" else ""
}

class PlayerViewModel(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState(aspectMode = settings.aspectMode))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** Bumped whenever the player must (re)load its media — including a retry. */
    private val _mediaToken = MutableStateFlow(0L)
    val mediaToken: StateFlow<Long> = _mediaToken.asStateFlow()

    private var loaded = false

    /** The pending repair, if one is waiting out its backoff. */
    private var recoverJob: Job? = null

    /** What ExoPlayer last complained about, shown only once repairs keep failing. */
    private var lastErrorMessage: String? = null

    /**
     * Where the red key goes back to. The single most-used button on any receiver:
     * two matches, two channels, and no walking through the list in between.
     */
    private var previousChannel: Channel? = null

    fun start(
        channelId: String,
        categoryId: String,
        archiveStartMs: Long = 0L,
        archiveDurationMin: Int = 0
    ) {
        if (loaded) return
        loaded = true

        viewModelScope.launch {
            if (archiveStartMs > 0L) {
                startArchive(channelId, archiveStartMs, archiveDurationMin)
            } else {
                val list = playlists.observeChannels(categoryId.ifBlank { ALL_CATEGORY_ID }).first()
                // From the list, not from the database: inside a group the channels
                // are renumbered from 1, and a fresh database row would carry the
                // provider's number instead — so the info bar would say 817 while
                // the list says 001, and typing 1 would mean two different things.
                val channel = list.firstOrNull { it.id == channelId }
                    ?: playlists.channelById(channelId)
                    ?: list.firstOrNull()
                _state.update { it.copy(playlist = list, channel = channel) }
                channel?.let { onChannelSelected(it) }
            }
        }

        // The info bar fades out on its own, the way a set-top box does it,
        // and the now/next line refreshes every half minute.
        viewModelScope.launch {
            var tick = 0
            while (true) {
                delay(1_000L)
                tick++
                val current = _state.value
                if (current.infoVisible &&
                    !current.channelListVisible &&
                    !current.optionsVisible &&
                    // Never fade the bar out from under a focused icon: that is
                    // exactly how a screen ends up with the focus nowhere.
                    !current.colorBarVisible &&
                    System.currentTimeMillis() - infoShownAt > INFO_TIMEOUT_MS
                ) {
                    // Mit der Leiste verfällt auch, dass sie gerufen war.
                    _state.update { it.copy(infoVisible = false, infoPinned = false) }
                }
                if (tick % 30 == 0) refreshNowNext()
            }
        }
    }

    /**
     * Catch-up: one channel, one programme, no zapping. The archive URL replaces the
     * live one on a copy of the channel, so everything downstream stays unchanged.
     */
    private suspend fun startArchive(channelId: String, startMs: Long, durationMin: Int) {
        val channel = playlists.channelById(channelId)
        if (channel == null) {
            _state.update { it.copy(error = AppLocale.strings.channelNotFound, isBuffering = false) }
            return
        }
        val url = playlists.archiveUrl(channelId, startMs, durationMin)
        if (url == null) {
            _state.update {
                it.copy(
                    channel = channel,
                    error = AppLocale.strings.noArchive,
                    isBuffering = false
                )
            }
            return
        }
        infoShownAt = System.currentTimeMillis()
        _state.update {
            it.copy(
                channel = channel.copy(streamUrl = url),
                playlist = listOf(channel),
                archiveLabel = AppLocale.strings.recording,
                infoVisible = true,
                infoPinned = false,
                isBuffering = true,
                error = null
            )
        }
        _mediaToken.value = System.currentTimeMillis()
    }

    private var infoShownAt = System.currentTimeMillis()

    private suspend fun refreshNowNext() {
        val epgId = _state.value.channel?.epgId
        _state.update {
            it.copy(nowNext = if (epgId.isNullOrBlank()) null else epg.nowNext(listOf(epgId))[epgId])
        }
    }

    private fun onChannelSelected(channel: Channel, keepListOpen: Boolean = false) {
        infoShownAt = System.currentTimeMillis()
        cancelRecovery()
        val leaving = _state.value.channel
        if (leaving != null && leaving.id != channel.id) previousChannel = leaving
        _state.update {
            it.copy(
                channel = channel,
                infoVisible = true,
                infoPinned = false,
                isBuffering = true,
                error = null,
                channelListVisible = keepListOpen,
                colorBarVisible = false,
                recoverAttempt = 0,
                switchedFormat = false,
                // Each channel keeps its own picture format; an old 4:3 station can
                // stay zoomed without dragging every other channel with it.
                aspectMode = settings.aspectFor(channel.id),
                previousChannelName = previousChannel?.name
            )
        }
        _mediaToken.value = System.currentTimeMillis()
        viewModelScope.launch {
            playlists.markWatched(channel.id)
            refreshNowNext()
        }
    }

    /**
     * A pick from the open channel list. The picture behind the list changes while
     * the list stays put, so you can taste your way through the channels without
     * reopening it every time. OK on the channel that is already running means
     * "that one" — and that is what closes the list.
     */
    fun select(channel: Channel) {
        val current = _state.value
        if (current.channelListVisible && current.channel?.id == channel.id) {
            setChannelListVisible(false)
            return
        }
        onChannelSelected(channel, keepListOpen = current.channelListVisible)
    }

    fun next() = step(+1)
    fun previous() = step(-1)

    private fun step(delta: Int) {
        val current = _state.value
        if (current.archiveLabel != null || current.playlist.isEmpty()) return
        val index = current.index.takeIf { it >= 0 } ?: 0
        val target = ((index + delta) % current.playlist.size + current.playlist.size) %
            current.playlist.size
        onChannelSelected(current.playlist[target])
    }

    /**
     * Direct channel entry by number, the way a television does it — against the
     * numbering of the list that is playing, so 1 means the same channel here as
     * it does in the list you came from. Falls back to the nearest lower number so
     * that typing past the end of the list still lands somewhere sensible.
     */
    fun jumpToNumber(number: Int) {
        val list = _state.value.playlist
        if (list.isEmpty()) return
        val target = list.firstOrNull { it.number == number }
            ?: list.lastOrNull { it.number < number }
            ?: list.first()
        onChannelSelected(target)
    }

    fun retry() {
        cancelRecovery()
        _state.update { it.copy(error = null, isBuffering = true, recoverAttempt = 0) }
        _mediaToken.value = System.currentTimeMillis()
    }

    fun showInfo() {
        infoShownAt = System.currentTimeMillis()
        _state.update { it.copy(infoVisible = true, infoPinned = true) }
    }

    fun toggleInfo() {
        infoShownAt = System.currentTimeMillis()
        _state.update {
            val showing = !it.infoVisible
            // The icon row lives inside the bar now, so it goes with it.
            it.copy(
                infoVisible = showing,
                colorBarVisible = showing && it.colorBarVisible,
                infoPinned = showing
            )
        }
    }

    /**
     * Raus aus der Farbleiste, und zwar ganz: die Leiste gilt danach nicht mehr
     * als gerufen. Sonst führte derselbe Tastendruck sofort wieder hinein.
     */
    fun leaveColorBar() {
        infoShownAt = System.currentTimeMillis()
        _state.update { it.copy(colorBarVisible = false, infoPinned = false) }
    }

    fun setChannelListVisible(visible: Boolean) {
        infoShownAt = System.currentTimeMillis()
        _state.update { it.copy(channelListVisible = visible, optionsPage = OptionsPage.NONE) }
    }

    // ------------------------------------------------------------------ options

    fun openOptions() {
        infoShownAt = System.currentTimeMillis()
        _state.update { it.copy(optionsPage = OptionsPage.ROOT, channelListVisible = false) }
    }

    fun showOptionsPage(page: OptionsPage) {
        infoShownAt = System.currentTimeMillis()
        _state.update { it.copy(optionsPage = page) }
    }

    /** @return true when something was closed, false when the panel was already gone. */
    fun closeOptions(): Boolean {
        val current = _state.value.optionsPage
        if (current == OptionsPage.NONE) return false
        // One step back inside the panel before the panel itself goes away.
        val target = if (current == OptionsPage.ROOT) OptionsPage.NONE else OptionsPage.ROOT
        _state.update { it.copy(optionsPage = target) }
        return true
    }

    /** Files the format against the channel that is playing, not against the app. */
    fun setAspectMode(mode: AspectMode) {
        _state.value.channel?.let { settings.saveAspectFor(it.id, mode) }
        _state.update { it.copy(aspectMode = mode) }
    }

    // ------------------------------------------------------------ coloured keys

    fun setColorBarVisible(visible: Boolean) {
        infoShownAt = System.currentTimeMillis()
        _state.update {
            it.copy(
                colorBarVisible = visible,
                // The icons are the bar's bottom row, so wanting them means
                // wanting the bar — opening one without the other shows a strip
                // of buttons floating over the picture with nothing to sit on.
                infoVisible = if (visible) true else it.infoVisible,
                infoPinned = if (visible) true else it.infoPinned,
                channelListVisible = if (visible) false else it.channelListVisible,
                optionsPage = if (visible) OptionsPage.NONE else it.optionsPage,
                previousChannelName = previousChannel?.name
            )
        }
    }

    /** Red: straight back to the channel before this one. */
    fun jumpToPreviousChannel() {
        val target = previousChannel ?: return
        setColorBarVisible(false)
        onChannelSelected(target)
    }

    val hasPreviousChannel: Boolean get() = previousChannel != null

    // ---------------------------------------------------------------- repairing

    fun onBuffering(buffering: Boolean) =
        _state.update { it.copy(isBuffering = buffering) }

    /**
     * A loud failure: ExoPlayer refused, gave up, or could not read what it got.
     * Treated exactly like a silent stall, because from the sofa they are the same
     * event and the same ladder of repairs fixes both.
     */
    fun onError(message: String) {
        lastErrorMessage = message
        beginRecovery()
    }

    /** The watchdog's verdict: nothing has moved for long enough to call it dead. */
    fun onStall() {
        lastErrorMessage = null
        beginRecovery()
    }

    /**
     * Puts the stream back without anybody pressing anything.
     *
     * The ladder: reconnect to the same URL; if that fails, swap the container,
     * because a provider whose TS mux has died will usually still serve HLS (and
     * the other way round); then keep alternating, with the wait growing to half
     * a minute and staying there. It never stops trying — a channel that comes
     * back at three in the morning should simply be playing again, and the
     * message on screen stays a quiet line rather than a wall until the fourth
     * attempt, when it is worth admitting that something is genuinely wrong.
     */
    private fun beginRecovery() {
        if (!settings.autoRecover) {
            _state.update {
                it.copy(
                    error = lastErrorMessage ?: AppLocale.strings.playbackFailed,
                    isBuffering = false
                )
            }
            return
        }
        if (recoverJob?.isActive == true) return

        val attempt = _state.value.recoverAttempt + 1
        // Every second attempt tries the other container, so the two are alternated
        // rather than one of them being hammered forever.
        val swap = attempt % 2 == 0
        _state.update {
            it.copy(
                recoverAttempt = attempt,
                switchedFormat = if (swap) !it.switchedFormat else it.switchedFormat,
                isBuffering = true,
                error = if (attempt > QUIET_ATTEMPTS) {
                    lastErrorMessage ?: AppLocale.strings.playbackFailed
                } else {
                    null
                }
            )
        }
        recoverJob = viewModelScope.launch {
            delay(waitBefore(attempt))
            if (swap) swapContainer()
            _mediaToken.value = System.currentTimeMillis()
        }
    }

    /** Backs off politely: a provider that is struggling is not helped by a flood. */
    private fun waitBefore(attempt: Int): Long = when (attempt) {
        1 -> 500L
        2 -> 3_000L
        3 -> 6_000L
        4 -> 12_000L
        else -> 30_000L
    }

    private fun swapContainer() {
        val channel = _state.value.channel ?: return
        val other = channel.streamUrl.swapStreamContainer() ?: return
        _state.update { it.copy(channel = channel.copy(streamUrl = other)) }
    }

    /** Playback is moving again — forget that anything was ever wrong. */
    fun onPlaybackHealthy() {
        val current = _state.value
        if (!current.recovering && current.error == null) return
        recoverJob?.cancel()
        lastErrorMessage = null
        _state.update { it.copy(recoverAttempt = 0, error = null) }
    }

    private fun cancelRecovery() {
        recoverJob?.cancel()
        recoverJob = null
        lastErrorMessage = null
    }

    val autoRecover: Boolean get() = settings.autoRecover

    fun toggleFavorite() {
        val channel = _state.value.channel ?: return
        viewModelScope.launch {
            playlists.toggleFavorite(channel.id)
            val updated = playlists.channelById(channel.id)
            if (updated != null) {
                _state.update { current ->
                    current.copy(
                        channel = updated,
                        playlist = current.playlist.map { if (it.id == updated.id) updated else it }
                    )
                }
            }
        }
    }

    val audioLanguage get() = settings.audioLanguage
    val subtitleLanguage get() = settings.subtitleLanguage

    val bufferSeconds: Int get() = settings.bufferSeconds
    val showClock: Boolean get() = settings.showClock

    override fun onCleared() {
        recoverJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val INFO_TIMEOUT_MS = 6_000L

        /**
         * How many repairs run behind a quiet one-line notice before the full error
         * is shown. Four covers a router reboot and a provider restart, which is
         * most of what actually happens; past that the viewer deserves to know.
         */
        const val QUIET_ATTEMPTS = 4
    }
}
