package com.safir.iptv.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.domain.model.FAVORITES_CATEGORY_ID
import com.safir.iptv.ui.i18n.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Karacast on the television's own home screen, without opening the app:
 *
 *  - a row of its own with the favourite channels of the access on screen;
 *  - the half-watched films and episodes in the system's "watch next" row.
 *
 * Kept up to date from the same flows the app draws its lists from, so a channel
 * starred in the app shows up on the home screen a moment later, and one locked
 * by the child lock disappears from it too. Only on Android TV 8 and later;
 * phones and older boxes have no such home screen and are left alone.
 *
 * What a launcher does with the rows is up to the launcher. The classic Android
 * TV home screen shows both; Google TV shows "watch next" ("Continue watching")
 * but no longer shows app rows of its own.
 */
class HomeScreen(
    private val context: Context,
    private val playlists: PlaylistRepository,
    private val settings: SettingsStore
) {

    /** The ids written last time, so they can be taken out again — and only ours. */
    private val prefs = context.getSharedPreferences("karacast-homescreen", Context.MODE_PRIVATE)
    private val lock = Mutex()

    val isSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    val enabled get() = settings.homeScreenRow

    fun setEnabled(value: Boolean) = settings.setHomeScreenRow(value)

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (!isSupported) return
        scope.launch {
            combine(
                playlists.observeChannels(FAVORITES_CATEGORY_ID),
                settings.continueWatching,
                settings.homeScreenRow,
                settings.activeSourceId
            ) { favorites, continuing, enabled, sourceId ->
                Snapshot(favorites, continuing, enabled, sourceId)
            }
                // A quick run of stars in the app is one update, not ten.
                .debounce(1_500)
                .collectLatest { publish(it) }
        }
    }

    private data class Snapshot(
        val favorites: List<Channel>,
        val continuing: List<ContinueItem>,
        val enabled: Boolean,
        val sourceId: Long
    )

    private suspend fun publish(snapshot: Snapshot) = lock.withLock {
        withContext(Dispatchers.IO) {
            // A launcher that misbehaves, or a provider that is missing on some
            // odd box, must never take the app down with it.
            runCatching {
                val helper = PreviewChannelHelper(context)
                if (!snapshot.enabled) {
                    clearPrograms()
                    clearWatchNext()
                    channelId()?.let { runCatching { helper.deletePreviewChannel(it) } }
                    prefs.edit { remove(KEY_CHANNEL) }
                    return@runCatching
                }
                publishFavorites(helper, snapshot.favorites)
                publishWatchNext(helper, snapshot.continuing, snapshot.sourceId)
            }
        }
    }

    // ------------------------------------------------------- favourites row

    private fun publishFavorites(helper: PreviewChannelHelper, favorites: List<Channel>) {
        val channel = PreviewChannel.Builder()
            .setDisplayName(AppLocale.strings.homeRow.favorites)
            .setAppLinkIntentUri(appLink())
            .setInternalProviderId(PROVIDER_ID)
            .setLogo(context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256))
            .build()

        // The launcher may have dropped the row (the viewer removed it, or the
        // data was cleared) — then it is created afresh rather than updated.
        val known = channelId()?.takeIf { id -> helper.allChannels.any { it.id == id } }
        val id = if (known != null) {
            helper.updatePreviewChannel(known, channel)
            known
        } else {
            val created = helper.publishChannel(channel)
            prefs.edit { putLong(KEY_CHANNEL, created) }
            // The first row an app asks for is shown without asking the viewer.
            if (!prefs.getBoolean(KEY_ASKED, false)) {
                runCatching { TvContractCompat.requestChannelBrowsable(context, created) }
                prefs.edit { putBoolean(KEY_ASKED, true) }
            }
            created
        }

        clearPrograms()
        val written = favorites.take(MAX_CHANNELS).mapIndexed { index, favorite ->
            val program = PreviewProgram.Builder()
                .setChannelId(id)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                .setTitle(favorite.name)
                .setDescription(favorite.categoryName)
                .setPosterArtUri(favorite.logoUrl?.let(Uri::parse) ?: banner())
                .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
                .setLive(true)
                .setWeight(MAX_CHANNELS - index)
                .setInternalProviderId(favorite.id)
                .setIntentUri(DeepLinks.channelUri(context, favorite.id))
                .build()
            helper.publishPreviewProgram(program)
        }
        prefs.edit { putStringSet(KEY_PROGRAMS, written.map { it.toString() }.toSet()) }
    }

    @SuppressLint("RestrictedApi")
    private fun clearPrograms() {
        prefs.getStringSet(KEY_PROGRAMS, emptySet()).orEmpty().forEach { id ->
            id.toLongOrNull()?.let {
                runCatching {
                    context.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(it), null, null)
                }
            }
        }
        prefs.edit { remove(KEY_PROGRAMS) }
    }

    // ------------------------------------------------------------ watch next

    private fun publishWatchNext(
        helper: PreviewChannelHelper,
        continuing: List<ContinueItem>,
        sourceId: Long
    ) {
        clearWatchNext()
        val written = continuing.take(MAX_CONTINUE).map { item ->
            val program = WatchNextProgram.Builder()
                .setType(
                    if (item.isEpisode) {
                        TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                    } else {
                        TvContractCompat.WatchNextPrograms.TYPE_MOVIE
                    }
                )
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(item.updatedAt)
                .setLastPlaybackPositionMillis(item.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                .setDurationMillis(item.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                .setTitle(item.title)
                .setEpisodeTitle(item.subtitle.takeIf { it.isNotBlank() })
                .setPosterArtUri(item.posterUrl?.let(Uri::parse) ?: banner())
                .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_MOVIE_POSTER)
                .setInternalProviderId("$sourceId|${item.key}")
                .setIntentUri(DeepLinks.continueUri(context, sourceId, item.key))
                .build()
            helper.publishWatchNextProgram(program)
        }
        prefs.edit { putStringSet(KEY_WATCH_NEXT, written.map { it.toString() }.toSet()) }
    }

    @SuppressLint("RestrictedApi")
    private fun clearWatchNext() {
        prefs.getStringSet(KEY_WATCH_NEXT, emptySet()).orEmpty().forEach { id ->
            id.toLongOrNull()?.let {
                runCatching {
                    context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null)
                }
            }
        }
        prefs.edit { remove(KEY_WATCH_NEXT) }
    }

    // ----------------------------------------------------------------- bits

    private fun channelId(): Long? = prefs.getLong(KEY_CHANNEL, -1L).takeIf { it >= 0L }

    /** Opens the app itself, when the row's own icon is clicked. */
    private fun appLink(): Uri = Uri.parse(
        context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
            ?.toUri(android.content.Intent.URI_INTENT_SCHEME)
            ?: "package:${context.packageName}"
    )

    /** For a channel or film the provider sends no picture for. */
    private fun banner(): Uri = Uri.parse("android.resource://${context.packageName}/drawable/app_banner")

    private companion object {
        const val PROVIDER_ID = "favorites"
        const val KEY_CHANNEL = "channel_id"
        const val KEY_ASKED = "asked_browsable"
        const val KEY_PROGRAMS = "program_ids"
        const val KEY_WATCH_NEXT = "watch_next_ids"
        const val MAX_CHANNELS = 30
        const val MAX_CONTINUE = 10
    }
}
