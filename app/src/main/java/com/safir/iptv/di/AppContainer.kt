package com.safir.iptv.di

import android.content.Context
import com.safir.iptv.data.local.AppDatabase
import com.safir.iptv.data.prefs.LicenseStore
import com.safir.iptv.data.prefs.SettingsStore
import com.safir.iptv.data.repository.LicenseRepository
import com.safir.iptv.data.repository.EpgRepository
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.data.repository.VodRepository

/**
 * Hand-rolled dependency container. The graph is small enough that a DI framework
 * would add build complexity without buying anything.
 */
class AppContainer(context: Context) {

    private val database = AppDatabase.get(context)

    val settings = SettingsStore(context)

    /**
     * Kept apart from [settings] on purpose: "clear the app's data" is a sentence
     * that gets said on support calls, and it must not cost somebody the licence
     * they paid for. The ticket can always be fetched from the panel again, but a
     * separate file makes it obvious what is being thrown away.
     */
    val licenseStore = LicenseStore(context)

    val licenses = LicenseRepository(context.applicationContext, licenseStore)

    val playlistRepository = PlaylistRepository(
        database = database,
        sourceDao = database.sourceDao(),
        channelDao = database.channelDao(),
        favoriteDao = database.favoriteDao(),
        recentDao = database.recentDao(),
        settings = settings
    )

    /**
     * Der Programmführer wird erst als Datei geholt und dann ausgewertet — dafür
     * braucht er einen Platz zum Ablegen. Der Zwischenspeicher ist richtig: das
     * System darf ihn wegräumen, die Sendungen stehen danach in der Datenbank.
     */
    val epgRepository = EpgRepository(database.programDao(), context.cacheDir)

    /**
     * Films and series. Network-backed and memory-cached rather than stored: see
     * [VodRepository] for why the catalogue does not belong in the database.
     */
    val vodRepository = VodRepository(playlistRepository, settings)

    init {
        settings.applyToHttp()
    }
}
