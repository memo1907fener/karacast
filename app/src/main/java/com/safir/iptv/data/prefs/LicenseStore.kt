package com.safir.iptv.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.safir.iptv.util.DeviceIdentity

/**
 * Where the licence lives between sittings.
 *
 * Its own file rather than a corner of the settings, for one reason: a support call
 * that ends in "clear the app's data" must not cost somebody the licence they paid
 * for. Separate storage makes it obvious what is being wiped, and the ticket can be
 * fetched again from the panel afterwards in any case.
 */
class LicenseStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("karacast-license", Context.MODE_PRIVATE)

    /** This app's own random number, minted once and kept. */
    val installId: String
        get() = prefs.getString(KEY_INSTALL_ID, null) ?: DeviceIdentity.newInstallId()
            .also { fresh -> prefs.edit { putString(KEY_INSTALL_ID, fresh) } }

    /**
     * When the fortnight started, as far as this device knows.
     *
     * Only ever written once, and only ever moved *earlier*. The panel knows the real
     * date — it saw this television the first time it ever went online — and an
     * install that was wiped and set up again would otherwise start counting from
     * zero. Whichever date is earlier wins.
     */
    var trialStartedAt: Long
        get() = prefs.getLong(KEY_TRIAL_START, 0L)
        set(value) {
            if (value <= 0L) return
            val known = trialStartedAt
            if (known == 0L || value < known) prefs.edit { putLong(KEY_TRIAL_START, value) }
        }

    /** The signed ticket, exactly as the panel issued it. Empty while unlicensed. */
    var ticket: String
        get() = prefs.getString(KEY_TICKET, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_TICKET, value.trim()) }

    /** Set when the panel says a licence was withdrawn. Survives a restart. */
    var blocked: Boolean
        get() = prefs.getBoolean(KEY_BLOCKED, false)
        set(value) = prefs.edit { putBoolean(KEY_BLOCKED, value) }

    /**
     * The latest time the *server* ever reported.
     *
     * The guard against the oldest trick there is: set the television's clock back a
     * month and the fortnight never ends. [now] never goes behind this mark, so
     * winding the clock back buys nothing. Winding it forward only ends the trial
     * sooner, which is nobody's problem but the winder's.
     */
    var lastServerTime: Long
        get() = prefs.getLong(KEY_SERVER_TIME, 0L)
        set(value) {
            if (value > lastServerTime) prefs.edit { putLong(KEY_SERVER_TIME, value) }
        }

    var lastCheckAt: Long
        get() = prefs.getLong(KEY_CHECKED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_CHECKED_AT, value) }

    /** The clock this app trusts: its own, unless the server has seen a later moment. */
    fun now(): Long = maxOf(System.currentTimeMillis(), lastServerTime)

    /**
     * Where the panel lives. Blank means no panel yet, which is a normal state.
     *
     * A stored address that happens to be a *retired* default is not a choice
     * somebody made — it is a leftover from an older version that wrote it down.
     * Those are treated as if nothing were stored, so a television that has been
     * sitting in a living room since the first build follows the move to the new
     * panel by itself. Anything the owner actually typed is left alone.
     */
    var licenseUrl: String
        get() {
            val stored = prefs.getString(KEY_URL, null).orEmpty()
            return if (stored.isBlank() || stored.trimEnd('/') in RETIRED_URLS) {
                DEFAULT_LICENSE_URL
            } else {
                stored
            }
        }
        set(value) = prefs.edit { putString(KEY_URL, value.trim().trimEnd('/')) }

    /** Wipes the trial and the licence. Only reachable from a debug build. */
    fun resetForTesting() = prefs.edit {
        remove(KEY_TRIAL_START)
        remove(KEY_TICKET)
        remove(KEY_BLOCKED)
        remove(KEY_SERVER_TIME)
        remove(KEY_CHECKED_AT)
    }

    private companion object {
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_TRIAL_START = "trial_started_at"
        const val KEY_TICKET = "ticket"
        const val KEY_BLOCKED = "blocked"
        const val KEY_SERVER_TIME = "server_time"
        const val KEY_CHECKED_AT = "checked_at"
        const val KEY_URL = "license_url"

        const val DEFAULT_LICENSE_URL = "https://karacast.de/api"

        /**
         * Adressen, die einmal Standard waren und es nicht mehr sind. Steht eine
         * davon gespeichert, hat sie kein Mensch eingetippt — sie stammt aus einer
         * älteren Fassung. Neue kommen hier unten dazu, nie oben ersetzt.
         */
        val RETIRED_URLS = setOf(
            "https://sternweb.at/api",
            "http://sternweb.at/api",
            "https://panel.sternweb.at/api"
        )
    }
}

/** Fourteen days, in milliseconds. Long enough to watch a whole series over a weekend. */
const val TRIAL_LENGTH_MS = 14L * 24L * 60L * 60L * 1000L
