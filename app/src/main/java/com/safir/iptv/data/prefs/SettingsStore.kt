package com.safir.iptv.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.safir.iptv.data.remote.DEFAULT_USER_AGENT
import com.safir.iptv.data.remote.Http
import com.safir.iptv.domain.model.ALL_CATEGORY_ID
import com.safir.iptv.domain.model.AspectMode
import com.safir.iptv.domain.model.CategoryGroup
import com.safir.iptv.domain.model.ChannelSort
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.domain.model.FavoriteItem
import com.safir.iptv.domain.model.ParentalRevision
import com.safir.iptv.domain.model.TrackLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Settings bag. Two kinds of value live here:
 *
 * - plain scalars, read and written synchronously;
 * - the category groups and the category order, exposed as flows because the
 *   sidebar has to rebuild the moment they change. They sit in preferences and
 *   not in the database on purpose — a playlist re-sync deletes and rewrites
 *   every category row, and a user's own arrangement must outlive that.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("safir-settings", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }
    private val groupListSerializer = ListSerializer(CategoryGroup.serializer())
    private val stringListSerializer = ListSerializer(String.serializer())
    private val continueSerializer = ListSerializer(ContinueItem.serializer())
    private val favoriteSerializer = ListSerializer(FavoriteItem.serializer())

    // ------------------------------------------------------------- connection

    var userAgent: String
        get() = prefs.getString(KEY_USER_AGENT, DEFAULT_USER_AGENT) ?: DEFAULT_USER_AGENT
        set(value) {
            val effective = value.ifBlank { DEFAULT_USER_AGENT }
            prefs.edit { putString(KEY_USER_AGENT, effective) }
            Http.userAgent = effective
        }

    /** `.m3u8` instead of `.ts` for Xtream live streams. Helps on flaky connections. */
    var preferHls: Boolean
        get() = prefs.getBoolean(KEY_PREFER_HLS, false)
        set(value) = prefs.edit { putBoolean(KEY_PREFER_HLS, value) }

    /** Seconds of media buffered before playback starts. Higher = more stable, slower zap. */
    var bufferSeconds: Int
        get() = prefs.getInt(KEY_BUFFER_SECONDS, 8)
        set(value) = prefs.edit { putInt(KEY_BUFFER_SECONDS, value.coerceIn(2, 30)) }

    // ---------------------------------------------------------------- display

    /** Live preview of the focused channel while browsing the list. */
    var previewEnabled: Boolean
        get() = prefs.getBoolean(KEY_PREVIEW, true)
        set(value) = prefs.edit { putBoolean(KEY_PREVIEW, value) }

    var previewMuted: Boolean
        get() = prefs.getBoolean(KEY_PREVIEW_MUTED, false)
        set(value) = prefs.edit { putBoolean(KEY_PREVIEW_MUTED, value) }

    /** Reopen the app straight into the channel that was last watched. */
    var resumeLastChannel: Boolean
        get() = prefs.getBoolean(KEY_RESUME_LAST, false)
        set(value) = prefs.edit { putBoolean(KEY_RESUME_LAST, value) }

    /** Digital clock in the top-right corner during playback. */
    var showClock: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CLOCK, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_CLOCK, value) }

    /** How a channel list is ordered. Own groups keep the user's own order regardless. */
    private val _channelSort = MutableStateFlow(readSort())
    val channelSort: StateFlow<ChannelSort> = _channelSort.asStateFlow()

    fun setChannelSort(value: ChannelSort) {
        prefs.edit { putString(KEY_CHANNEL_SORT, value.name) }
        _channelSort.value = value
    }

    private fun readSort(): ChannelSort =
        runCatching { ChannelSort.valueOf(prefs.getString(KEY_CHANNEL_SORT, null) ?: "") }
            .getOrDefault(ChannelSort.NUMBER)

    /** How the picture is fitted into the screen during playback. */
    var aspectMode: AspectMode
        get() = runCatching { AspectMode.valueOf(prefs.getString(KEY_ASPECT, null) ?: "") }
            .getOrDefault(AspectMode.FIT)
        set(value) = prefs.edit { putString(KEY_ASPECT, value.name) }

    /**
     * The picture format for one particular channel or film.
     *
     * Broadcasters are not consistent and never will be: one channel is still 4:3,
     * the next is 16:9, a film arrives letterboxed at 21:9. A single setting for all
     * of them is therefore always wrong somewhere — so the choice is filed against
     * whatever was playing when it was made, and everything untouched keeps
     * [aspectMode] as its default.
     */
    fun aspectFor(key: String): AspectMode = runCatching {
        AspectMode.valueOf(prefs.getString(ASPECT_PREFIX + key, null) ?: "")
    }.getOrDefault(aspectMode)

    fun saveAspectFor(key: String, mode: AspectMode) =
        prefs.edit { putString(ASPECT_PREFIX + key, mode.name) }

    /**
     * Let the player put a dead stream back together on its own — reconnect, and
     * swap the container if that does not help. On by default: a television that
     * needs someone to press a button before the picture comes back is a television
     * that stays black while its owner is asleep.
     */
    var autoRecover: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECOVER, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_RECOVER, value) }

    // --------------------------------------------------- the language of a track

    /**
     * Which dub to pick when a stream carries several, and which subtitles to show.
     * [TrackLanguage.AUTO] means "whatever the stream leads with" for audio and
     * "none" for subtitles — the two sensible defaults, and the two that most
     * streams can actually honour.
     */
    var audioLanguage: TrackLanguage
        get() = TrackLanguage.fromTag(prefs.getString(KEY_AUDIO_LANGUAGE, null))
        set(value) = prefs.edit {
            if (value.tag == null) remove(KEY_AUDIO_LANGUAGE) else putString(KEY_AUDIO_LANGUAGE, value.tag)
        }

    var subtitleLanguage: TrackLanguage
        get() = TrackLanguage.fromTag(prefs.getString(KEY_SUBTITLE_LANGUAGE, null))
        set(value) = prefs.edit {
            if (value.tag == null) {
                remove(KEY_SUBTITLE_LANGUAGE)
            } else {
                putString(KEY_SUBTITLE_LANGUAGE, value.tag)
            }
        }

    // --------------------------------------------------------- the child lock

    /**
     * Hides whole categories from everyone who does not know the code.
     *
     * Provider catalogues put adult categories in the same alphabetical list as
     * everything else, and this app is handed to families. So the lock works by
     * making those categories *not exist* rather than by asking for a code when
     * one is opened: a child who cannot see a door does not rattle it, and an
     * elderly viewer is never confronted with a prompt they did not expect.
     */
    var parentalEnabled: Boolean
        get() = _parentalRevision.value.enabled
        set(value) {
            prefs.edit { putBoolean(KEY_PARENTAL, value) }
            bumpParental(enabled = value)
        }

    /**
     * Anything about the lock, as a flow.
     *
     * The channel list is assembled from flows, so switching the lock on has to be
     * something a flow can carry — otherwise the categories it now hides would go
     * on being drawn until the next re-sync. The revision number is what makes the
     * lists rebuild; the actual reading is still done by [isBlocked].
     */
    private val _parentalRevision = MutableStateFlow(
        ParentalRevision(prefs.getBoolean(KEY_PARENTAL, false), 0)
    )
    val parentalRevision: StateFlow<ParentalRevision> = _parentalRevision.asStateFlow()

    private fun bumpParental(enabled: Boolean = _parentalRevision.value.enabled) {
        _parentalRevision.value = ParentalRevision(enabled, _parentalRevision.value.at + 1)
    }

    /** Four digits. Empty means the lock has been switched on but never set up. */
    var parentalPin: String
        get() = prefs.getString(KEY_PARENTAL_PIN, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_PARENTAL_PIN, value.trim()) }

    /**
     * Words that mark a category as adult. Matched case-insensitively against the
     * name, because provider categories are not labelled by any standard and this
     * is what they actually call them.
     */
    var parentalKeywords: List<String>
        get() = prefs.getString(KEY_PARENTAL_WORDS, null)
            ?.let { runCatching { json.decodeFromString(stringListSerializer, it) }.getOrNull() }
            ?: DEFAULT_PARENTAL_KEYWORDS
        set(value) {
            prefs.edit {
                putString(KEY_PARENTAL_WORDS, json.encodeToString(stringListSerializer, value))
            }
            bumpParental()
        }

    /** Categories the user locked by hand, on top of whatever the words catch. */
    private val _blockedCategoryIds = MutableStateFlow(readBlocked())
    val blockedCategoryIds: StateFlow<List<String>> = _blockedCategoryIds.asStateFlow()

    fun setCategoryBlocked(categoryId: String, blocked: Boolean) {
        val current = _blockedCategoryIds.value
        val updated = if (blocked) {
            if (categoryId in current) current else current + categoryId
        } else {
            current - categoryId
        }
        prefs.edit { putString(KEY_PARENTAL_IDS, json.encodeToString(stringListSerializer, updated)) }
        _blockedCategoryIds.value = updated
        bumpParental()
    }

    private fun readBlocked(): List<String> {
        val raw = prefs.getString(KEY_PARENTAL_IDS, null) ?: return emptyList()
        return runCatching { json.decodeFromString(stringListSerializer, raw) }
            .getOrDefault(emptyList())
    }

    /** True when this category must not appear anywhere while the lock is on. */
    fun isBlocked(categoryId: String, name: String): Boolean {
        if (!parentalEnabled) return false
        if (categoryId in _blockedCategoryIds.value) return true
        val haystack = name.lowercase()
        return parentalKeywords.any { it.isNotBlank() && haystack.contains(it.lowercase()) }
    }

    /*
     * Live categories and film categories are numbered by the provider in two
     * separate series, so "5" means one thing in the channel list and quite
     * another in the catalogue. Locking a live category by its bare id would
     * therefore take an unrelated film category down with it. The live ones are
     * prefixed to keep the two apart; the catalogue is covered by the words,
     * which is what actually labels those shelves anyway.
     */

    fun isLiveBlocked(categoryId: String, name: String): Boolean =
        isBlocked(LIVE_PREFIX + categoryId, name)

    fun setLiveCategoryBlocked(categoryId: String, blocked: Boolean) =
        setCategoryBlocked(LIVE_PREFIX + categoryId, blocked)

    fun isLiveCategoryBlocked(categoryId: String): Boolean =
        (LIVE_PREFIX + categoryId) in _blockedCategoryIds.value

    /** The locked live categories, as the bare ids a settings list can compare to. */
    val lockedLiveCategoryIds: Flow<Set<String>> = _blockedCategoryIds.map { ids ->
        ids.filter { it.startsWith(LIVE_PREFIX) }
            .map { it.removePrefix(LIVE_PREFIX) }
            .toSet()
    }

    /** After a film ends, the next one in the same season starts by itself. */
    var autoNextEpisode: Boolean
        get() = prefs.getBoolean(KEY_AUTO_NEXT, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_NEXT, value) }

    // ------------------------------------------------------ films kept aside

    /**
     * Favourite films and series. Live channels keep theirs in the database, where
     * they can be joined against the channel list; a catalogue has no such list to
     * join against, so these carry everything they need to be drawn.
     */
    private val _vodFavorites = MutableStateFlow(readFavorites())
    val vodFavorites: StateFlow<List<FavoriteItem>> = _vodFavorites.asStateFlow()

    fun isFavorite(key: String): Boolean = _vodFavorites.value.any { it.key == key }

    /** @return true when the item is a favourite afterwards. */
    fun toggleFavorite(item: FavoriteItem): Boolean {
        val current = _vodFavorites.value
        val existing = current.firstOrNull { it.key == item.key }
        val updated = if (existing != null) {
            current - existing
        } else {
            listOf(item.copy(addedAt = System.currentTimeMillis())) + current
        }
        writeFavorites(updated)
        return existing == null
    }

    private fun writeFavorites(items: List<FavoriteItem>) {
        prefs.edit { putString(KEY_VOD_FAVORITES, json.encodeToString(favoriteSerializer, items)) }
        _vodFavorites.value = items
    }

    private fun readFavorites(): List<FavoriteItem> {
        val raw = prefs.getString(KEY_VOD_FAVORITES, null) ?: return emptyList()
        return runCatching { json.decodeFromString(favoriteSerializer, raw) }
            .getOrDefault(emptyList())
    }

    // ----------------------------------------------------- where a film stopped

    /**
     * Half-watched films and episodes, newest first.
     *
     * One list rather than a scattering of numbers, because the row that offers to
     * carry on needs a cover and a title as much as it needs a position — and the
     * catalogue those would otherwise come from is fetched per category, so it is
     * usually not loaded at the moment the row has to be drawn.
     */
    private val _continueWatching = MutableStateFlow(readContinue())
    val continueWatching: StateFlow<List<ContinueItem>> = _continueWatching.asStateFlow()

    fun playbackPosition(key: String): Long =
        _continueWatching.value.firstOrNull { it.key == key }?.positionMs ?: 0L

    /**
     * Files where the viewer got to. Anything under a minute in, or inside the last
     * two minutes, is dropped instead: offering to resume during the opening titles
     * is pointless, and offering to resume three seconds before the credits is worse
     * than offering nothing at all.
     */
    fun saveProgress(item: ContinueItem) {
        val finished = item.durationMs > 0L && item.positionMs > item.durationMs - FINISHED_TAIL_MS
        if (item.positionMs < RESUME_FLOOR_MS || finished) {
            forgetProgress(item.key)
            return
        }
        val updated = buildList {
            add(item)
            addAll(_continueWatching.value.filterNot { it.key == item.key })
        }.take(CONTINUE_LIMIT)
        writeContinue(updated)
    }

    fun forgetProgress(key: String) {
        val remaining = _continueWatching.value.filterNot { it.key == key }
        if (remaining.size != _continueWatching.value.size) writeContinue(remaining)
    }

    /**
     * Empties one half of the list. Films and series each have their own row on
     * their own screen, and clearing the one in front of you must not quietly take
     * the other with it.
     */
    fun clearContinueWatching(episodes: Boolean) =
        writeContinue(_continueWatching.value.filter { it.isEpisode != episodes })

    private fun writeContinue(items: List<ContinueItem>) {
        prefs.edit { putString(KEY_CONTINUE, json.encodeToString(continueSerializer, items)) }
        _continueWatching.value = items
    }

    private fun readContinue(): List<ContinueItem> {
        val raw = prefs.getString(KEY_CONTINUE, null) ?: return emptyList()
        return runCatching { json.decodeFromString(continueSerializer, raw) }
            .getOrDefault(emptyList())
    }

    /** Start the app by itself once the box has finished booting. */
    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTOSTART, value) }

    /**
     * Where to look for a newer build. A sideloaded app has no store behind it, so
     * this is the only way it can tell its owner that an update exists. Empty by
     * default — nothing is contacted until an address is entered.
     */
    var updateUrl: String
        get() = prefs.getString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL).orEmpty()
        set(value) = prefs.edit { putString(KEY_UPDATE_URL, value.trim()) }

    /** Ask once a day at start-up, not on every trip back to the home screen. */
    var autoUpdateCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_UPDATE, value) }

    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_UPDATE_CHECKED, 0L)
        set(value) = prefs.edit { putLong(KEY_UPDATE_CHECKED, value) }

    /**
     * The version somebody said "later" to.
     *
     * Without this the same prompt would appear every single day until they gave in,
     * which is how an update notice turns into something people learn to dismiss
     * without reading. Saying "skip" to one version says nothing about the next.
     */
    var skippedUpdateCode: Int
        get() = prefs.getInt(KEY_UPDATE_SKIPPED, 0)
        set(value) = prefs.edit { putInt(KEY_UPDATE_SKIPPED, value) }

    // ------------------------------------------------------------------- panel

    /**
     * Where this television fetches its account from, once such a place exists.
     *
     * Empty by default and empty until somebody types an address: no identifier is
     * generated, no request is made, nothing leaves the device. An app that reports
     * in before being asked to is not one you hand to your family.
     */
    var panelUrl: String
        get() = prefs.getString(KEY_PANEL_URL, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_PANEL_URL, value.trim().trimEnd('/')) }

    /** The short code the panel shows for this set. Typed once, kept afterwards. */
    var panelCode: String
        get() = prefs.getString(KEY_PANEL_CODE, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_PANEL_CODE, value.trim()) }

    var panelLastSyncAt: Long
        get() = prefs.getLong(KEY_PANEL_SYNCED, 0L)
        set(value) = prefs.edit { putLong(KEY_PANEL_SYNCED, value) }

    /**
     * A name for this television, so a panel can tell two sets in one household
     * apart. Random, generated the first time it is actually needed, and tied to
     * nobody — an install that is wiped gets a new one and that is fine.
     */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: java.util.UUID.randomUUID()
            .toString()
            .replace("-", "")
            .take(16)
            .also { generated -> prefs.edit { putString(KEY_DEVICE_ID, generated) } }

    /** BCP-47 tag of the chosen interface language; null follows the system. */
    var language: String?
        get() = prefs.getString(KEY_LANGUAGE, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, value)
        }

    /**
     * The list "Live TV" opens straight into — a group id (with the `grp:` prefix),
     * a virtual list, or null for the overview of groups, favourites and the rest.
     * Set, the app behaves like a satellite receiver: one press and you are in the
     * channels, with no menu in between.
     */
    var liveDirectCategoryId: String?
        get() = prefs.getString(KEY_LIVE_DIRECT, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_LIVE_DIRECT) else putString(KEY_LIVE_DIRECT, value)
        }

    /**
     * The one list Live TV works in, and therefore the one numbering that counts.
     * Everything that starts playback — resuming at launch, the programme guide,
     * a tile — must go through here, or the numbers on screen would stop matching
     * the numbers you type on the remote.
     */
    val homeCategoryId: String
        get() = liveDirectCategoryId ?: lastCategoryId ?: ALL_CATEGORY_ID

    // ----------------------------------------------------------- last session

    var lastChannelId: String?
        get() = prefs.getString(KEY_LAST_CHANNEL, null)
        set(value) = prefs.edit { putString(KEY_LAST_CHANNEL, value) }

    var lastCategoryId: String?
        get() = prefs.getString(KEY_LAST_CATEGORY, null)
        set(value) = prefs.edit { putString(KEY_LAST_CATEGORY, value) }

    // -------------------------------------------------- groups and ordering

    private val _groups = MutableStateFlow(readGroups())
    val groups: StateFlow<List<CategoryGroup>> = _groups.asStateFlow()

    /** Provider category ids in the order the user arranged them. Empty = provider order. */
    private val _categoryOrder = MutableStateFlow(readCategoryOrder())
    val categoryOrder: StateFlow<List<String>> = _categoryOrder.asStateFlow()

    fun saveGroups(groups: List<CategoryGroup>) {
        prefs.edit { putString(KEY_GROUPS, json.encodeToString(groupListSerializer, groups)) }
        _groups.value = groups
    }

    fun saveCategoryOrder(categoryIds: List<String>) {
        prefs.edit {
            putString(KEY_CATEGORY_ORDER, json.encodeToString(stringListSerializer, categoryIds))
        }
        _categoryOrder.value = categoryIds
    }

    private fun readGroups(): List<CategoryGroup> {
        val raw = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        return runCatching { json.decodeFromString(groupListSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun readCategoryOrder(): List<String> {
        val raw = prefs.getString(KEY_CATEGORY_ORDER, null) ?: return emptyList()
        return runCatching { json.decodeFromString(stringListSerializer, raw) }.getOrDefault(emptyList())
    }

    /** Restores the persisted UA into the HTTP layer at app start. */
    fun applyToHttp() {
        Http.userAgent = userAgent
    }

    private companion object {
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_PREFER_HLS = "prefer_hls"
        const val KEY_BUFFER_SECONDS = "buffer_seconds"
        const val KEY_PREVIEW = "preview_enabled"
        const val KEY_PREVIEW_MUTED = "preview_muted"
        const val KEY_SHOW_CLOCK = "show_clock"
        const val KEY_LIVE_DIRECT = "live_direct_category"
        const val KEY_CHANNEL_SORT = "channel_sort"
        const val KEY_ASPECT = "aspect_mode"
        const val KEY_AUTOSTART = "auto_start_on_boot"
        const val KEY_AUTO_RECOVER = "auto_recover"
        const val KEY_AUDIO_LANGUAGE = "audio_language"
        const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        const val KEY_CONTINUE = "continue_watching"
        const val ASPECT_PREFIX = "aspect:"
        const val KEY_PARENTAL = "parental_enabled"
        const val KEY_PARENTAL_PIN = "parental_pin"
        const val KEY_PARENTAL_WORDS = "parental_keywords"
        const val KEY_PARENTAL_IDS = "parental_categories"
        const val KEY_VOD_FAVORITES = "vod_favorites"
        const val KEY_AUTO_NEXT = "auto_next_episode"

        /** Keeps live category ids out of the catalogue's numbering. */
        const val LIVE_PREFIX = "live:"

        /** What providers actually call these categories, in the wild. */
        val DEFAULT_PARENTAL_KEYWORDS = listOf(
            "xxx", "adult", "erotic", "erotik", "porn", "18+", "+18", "fsk18"
        )

        /** Below this, the viewer has barely started — nothing worth resuming. */
        const val RESUME_FLOOR_MS = 60_000L

        /** Inside the last two minutes counts as watched to the end. */
        const val FINISHED_TAIL_MS = 120_000L

        /** A row on a television holds a handful; twenty is already generous. */
        const val CONTINUE_LIMIT = 20
        const val KEY_UPDATE_URL = "update_url"
        const val KEY_AUTO_UPDATE = "update_auto_check"
        const val KEY_UPDATE_CHECKED = "update_checked_at"
        const val KEY_UPDATE_SKIPPED = "update_skipped_code"
        const val KEY_PANEL_URL = "panel_url"
        const val KEY_PANEL_CODE = "panel_code"
        const val KEY_PANEL_SYNCED = "panel_synced_at"
        const val KEY_DEVICE_ID = "device_id"

        /**
         * Where the app looks for a newer build unless the owner points it elsewhere.
         * A raw GitHub URL rather than the repository page: the latter serves a web
         * page, and the app needs the JSON itself.
         */
        const val DEFAULT_UPDATE_URL =
            "https://raw.githubusercontent.com/memo1907fener/karacast/main/update/karacast.json"
        const val KEY_LANGUAGE = "language"
        const val KEY_RESUME_LAST = "resume_last_channel"
        const val KEY_LAST_CHANNEL = "last_channel"
        const val KEY_LAST_CATEGORY = "last_category"
        const val KEY_GROUPS = "category_groups"
        const val KEY_CATEGORY_ORDER = "category_order"
    }
}
