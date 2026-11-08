package com.safir.iptv.data.repository

import androidx.room.withTransaction
import com.safir.iptv.data.local.AppDatabase
import com.safir.iptv.data.local.FavoriteEntity
import com.safir.iptv.data.local.RecentEntity
import com.safir.iptv.data.prefs.SettingsStore

/**
 * Wechselt zwischen den Profilen eines Geräts.
 *
 * Senderfavoriten und „Zuletzt gesehen“ liegen in der Datenbank, und jede Liste
 * im Programm liest sie von dort. Statt jede Abfrage um ein Profil zu erweitern,
 * gehört die Datenbank immer dem aktiven Profil: beim Wechsel wird ihr Inhalt
 * beim bisherigen Profil abgelegt und der des neuen eingespielt. Alle Listen
 * ziehen dabei von selbst nach, weil sie die Tabellen beobachten.
 *
 * Was in den Einstellungen je Profil liegt (Film-Favoriten, Weiterschauen),
 * tauscht [SettingsStore.switchProfile]. Der Film-Katalog wird verworfen, weil
 * ein Kinderprofil andere Kategorien sieht.
 */
class ProfileSwitcher(
    private val database: AppDatabase,
    private val settings: SettingsStore,
    private val catalog: VodRepository
) {

    suspend fun switchTo(profileId: Int) {
        val from = settings.activeProfileId.value
        if (from == profileId) return
        if (settings.profiles.value.none { it.id == profileId }) return

        val favoriteDao = database.favoriteDao()
        val recentDao = database.recentDao()

        database.withTransaction {
            settings.saveProfileChannels(
                profileId = from,
                favorites = favoriteDao.all().sortedBy { it.addedAt }.map { it.channelId },
                recents = recentDao.all().map { it.channelId }
            )
            favoriteDao.clear()
            recentDao.clear()

            val (favorites, recents) = settings.loadProfileChannels(profileId)
            val now = System.currentTimeMillis()
            // Die Reihenfolge bleibt: ältester Favorit zuerst, neuester Verlauf zuerst.
            favoriteDao.addAll(favorites.mapIndexed { index, id -> FavoriteEntity(id, now - (favorites.size - index)) })
            recentDao.markAll(recents.mapIndexed { index, id -> RecentEntity(id, now - index) })
        }

        settings.switchProfile(profileId)
        catalog.clearCache()
    }

    /** Löscht ein Profil. Ist es gerade aktiv, geht es vorher zurück zu Profil 1. */
    suspend fun delete(profileId: Int) {
        if (profileId == 1) return
        if (settings.activeProfileId.value == profileId) switchTo(1)
        settings.deleteProfile(profileId)
    }
}
