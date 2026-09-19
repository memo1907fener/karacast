package com.safir.iptv.data.repository

import android.content.Context
import android.os.Build
import com.safir.iptv.BuildConfig
import com.safir.iptv.data.prefs.LicenseStore
import com.safir.iptv.data.prefs.TRIAL_LENGTH_MS
import com.safir.iptv.data.remote.LicenseClient
import com.safir.iptv.domain.model.LicenseState
import com.safir.iptv.domain.model.LicenseStatus
import com.safir.iptv.util.DeviceIdentity
import com.safir.iptv.util.DeviceParts
import com.safir.iptv.util.LicenseVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this television may be used, and why.
 *
 * The rule the whole design hangs from: **a verified ticket needs no server.** Once
 * somebody has paid, the signature on their ticket is checked here on the device and
 * that is the end of it — for ever, offline, whatever happens to karacast.de. The
 * panel is only ever consulted to *gain* a licence or to hear that one was withdrawn,
 * never to keep one.
 *
 * The second rule follows from the first: **a server that cannot be reached never
 * takes anything away.** A failed request leaves the state exactly as it was. The
 * alternative — locking a family out because a certificate expired overnight — is
 * how a small paid app earns a reputation it never recovers from.
 */
class LicenseRepository(
    private val context: Context,
    private val store: LicenseStore
) {

    private val _state = MutableStateFlow(LicenseState())
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    val parts: DeviceParts
        get() = DeviceIdentity.parts(context, store.installId)

    val deviceCode: String
        get() = DeviceIdentity.code(parts)

    /**
     * Wo das Panel steht. Der Aktivierungsbildschirm baut daraus die Adresse für
     * den QR-Code, damit beide nie auseinanderlaufen können: wer den Lizenzserver
     * umzieht, zieht die Kaufseite mit um, ohne dass jemand eine zweite Stelle im
     * Quelltext ändern müsste.
     */
    val licenseUrl: String
        get() = store.licenseUrl

    init {
        refresh()
    }

    /**
     * Works out where things stand from what is already on the device. No network,
     * no waiting — this runs before the first screen is drawn.
     */
    fun refresh() {
        val now = store.now()

        // The trial clock starts the first time the app is ever opened, not at
        // install: a box that sat in a cupboard for a month has lost nothing.
        if (store.trialStartedAt == 0L) store.trialStartedAt = now

        val status = when {
            store.blocked -> LicenseStatus.BLOCKED
            hasValidTicket() -> LicenseStatus.ACTIVE
            now < store.trialStartedAt + TRIAL_LENGTH_MS -> LicenseStatus.TRIAL
            else -> LicenseStatus.EXPIRED
        }

        val endsAt = store.trialStartedAt + TRIAL_LENGTH_MS
        _state.value = LicenseState(
            status = status,
            trialEndsAt = endsAt,
            // Rounded up, so the last partial day still reads as "1 day left"
            // rather than as zero while the app is plainly still working.
            daysLeft = if (status == LicenseStatus.TRIAL) {
                (((endsAt - now) + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(0)
            } else {
                0
            },
            deviceCode = deviceCode,
            activatedAt = if (status == LicenseStatus.ACTIVE) store.lastCheckAt else 0L
        )
    }

    private fun hasValidTicket(): Boolean {
        val ticket = store.ticket
        if (ticket.isBlank()) return false
        return LicenseVerifier.verify(ticket, parts.hashes) != null
    }

    /**
     * Asks the panel what it knows. Safe to call whenever; it never makes things worse.
     *
     * @return true when something changed and the caller should redraw.
     */
    suspend fun sync(): Boolean {
        val url = store.licenseUrl
        if (url.isBlank()) return false

        _state.value = _state.value.copy(checking = true, error = null)
        return try {
            val reply = LicenseClient.hello(
                baseUrl = url,
                parts = parts,
                deviceCode = deviceCode,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                language = java.util.Locale.getDefault().language
            )
            apply(reply)
        } catch (error: Exception) {
            // Deliberately quiet, and deliberately not a state change. Nobody is
            // told off because their internet blinked.
            _state.value = _state.value.copy(checking = false)
            false
        }
    }

    /** The eight characters somebody was sent when scanning the code was not an option. */
    suspend fun redeem(code: String): Boolean {
        val url = store.licenseUrl
        if (url.isBlank() || code.isBlank()) return false

        _state.value = _state.value.copy(checking = true, error = null)
        return try {
            apply(LicenseClient.redeem(url, code, parts, deviceCode))
        } catch (error: Exception) {
            _state.value = _state.value.copy(checking = false, error = error.message)
            false
        }
    }

    /**
     * Takes what the panel said, keeping only what improves the situation.
     *
     * A ticket is stored only once it has actually verified here — a server that
     * sends nonsense, or that somebody has redirected the app to, gets nowhere.
     */
    private fun apply(reply: com.safir.iptv.data.remote.LicenseReply?): Boolean {
        _state.value = _state.value.copy(checking = false)
        if (reply == null) return false

        store.lastCheckAt = System.currentTimeMillis()
        if (reply.serverTime > 0L) store.lastServerTime = reply.serverTime
        if (reply.trialStartedAt > 0L) store.trialStartedAt = reply.trialStartedAt

        val before = _state.value.status

        if (reply.ticket.isNotBlank() &&
            LicenseVerifier.verify(reply.ticket, parts.hashes) != null
        ) {
            store.ticket = reply.ticket
            store.blocked = false
        }

        // Only an explicit "blocked" locks a paid television, and only while the
        // panel keeps saying so. Silence is never taken as a withdrawal.
        store.blocked = reply.status == "blocked"

        refresh()
        return _state.value.status != before
    }

    // ------------------------------------------------------------- for testing

    /**
     * The three knobs that make the whole thing testable without a server, and that
     * exist **only in a debug build** — `BuildConfig.DEBUG` is a compile-time
     * constant, so in the release APK the bodies below are not merely unreachable,
     * they are not there at all. A shipped app has no way to grant itself a licence.
     */
    fun debugResetTrial() {
        if (!BuildConfig.DEBUG) return
        store.resetForTesting()
        refresh()
    }

    fun debugExpireTrial() {
        if (!BuildConfig.DEBUG) return
        store.ticket = ""
        store.trialStartedAt = System.currentTimeMillis() - TRIAL_LENGTH_MS - DAY_MS
        refresh()
    }

    fun debugActivate() {
        if (!BuildConfig.DEBUG) return
        _state.value = _state.value.copy(status = LicenseStatus.ACTIVE, daysLeft = 0)
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
