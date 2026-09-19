package com.safir.iptv.domain.model

/**
 * Where this installation stands.
 *
 * Four states and no more, because every extra one is another sentence somebody has
 * to explain on the telephone.
 */
enum class LicenseStatus {
    /** Inside the free fortnight. Everything works; a small note says for how long. */
    TRIAL,

    /** The fortnight is over and nothing was bought. The activation screen takes over. */
    EXPIRED,

    /** Paid for. Verified here on the device, and never asked about again. */
    ACTIVE,

    /**
     * Paid for once and withdrawn since — a chargeback, or the licence moved to
     * another television. Deliberately distinct from [EXPIRED] so the screen can
     * say something true rather than pretending the trial just ran out.
     */
    BLOCKED
}

/**
 * A licence ticket, after its signature has been checked.
 *
 * @param deviceHashes the device fingerprints this ticket was issued for. Several,
 *   not one: a television that was bought on a cable and later moved to Wi-Fi has a
 *   different MAC address afterwards, and the licence has to survive that. One match
 *   out of the list is enough.
 * @param issuedAt when the panel signed it.
 * @param plan `lifetime` today. The field exists so a yearly or multi-device licence
 *   can be introduced later without every television needing a new app first.
 * @param order the panel's own reference, so a support call can be traced to a payment.
 */
data class LicenseTicket(
    val version: Int,
    val deviceHashes: List<String>,
    val issuedAt: Long,
    val plan: String,
    val order: String
)

/**
 * What the app knows about its own licence right now.
 *
 * [status] is the only thing the rest of the app asks about; everything else exists
 * so the activation screen can say something specific instead of "not allowed".
 */
data class LicenseState(
    val status: LicenseStatus = LicenseStatus.TRIAL,
    /** Milliseconds. 0 before the first start has been recorded. */
    val trialEndsAt: Long = 0L,
    val daysLeft: Int = 0,
    /** The twelve characters the viewer reads out or scans. */
    val deviceCode: String = "",
    val activatedAt: Long = 0L,
    val checking: Boolean = false,
    val error: String? = null
) {
    /** True while the app may be used — the trial and a paid licence look the same here. */
    val allowed: Boolean
        get() = status == LicenseStatus.TRIAL || status == LicenseStatus.ACTIVE

    /** Worth mentioning on screen: the last three days of a trial, and nothing else. */
    val shouldWarn: Boolean
        get() = status == LicenseStatus.TRIAL && daysLeft <= 3
}
