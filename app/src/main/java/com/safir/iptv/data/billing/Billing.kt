package com.safir.iptv.data.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Something the viewer can buy, as the store describes it.
 *
 * The price is never written down in the app: it comes from the Play Console,
 * already formatted in the viewer's currency. Change it there, and every
 * television shows the new one the next time it asks — no new build needed.
 */
data class Offer(
    /** What [Billing.buy] needs to start exactly this purchase. */
    val id: String,
    /** "9,99 €" — formatted by Google for the viewer's country. */
    val price: String,
    /**
     * Null for a one-time purchase; otherwise the ISO-8601 billing period of the
     * subscription ("P1M", "P1Y"), which the screen turns into words.
     */
    val period: String?,
    /** ISO-8601 length of a free trial in front of the subscription, if the plan has one. */
    val freeTrial: String? = null
) {
    val isSubscription: Boolean get() = period != null
}

data class BillingState(
    /** True once the store has answered at least once. */
    val ready: Boolean = false,
    val offers: List<Offer> = emptyList(),
    /** A purchase was made but is not paid yet (cash at a kiosk, a bank transfer). */
    val pending: Boolean = false,
    val busy: Boolean = false,
    /** The store could not be reached — no Play Store, no account, no network. */
    val unavailable: Boolean = false,
    val error: String? = null
)

/**
 * Paying through the store the app was installed from.
 *
 * Only the Play version has a real one; the sideloaded APK sells its licence
 * through karacast.de and gets a [Billing] that does nothing. Which one is built in
 * is decided by the flavour — see `BillingFactory` under src/play and src/direct.
 */
interface Billing {
    val state: StateFlow<BillingState>

    /** Connects and asks what is for sale and what is already owned. Safe to repeat. */
    fun refresh()

    /** Opens the store's own payment sheet for [offerId]. */
    fun buy(activity: Activity, offerId: String)

    /** Where a subscriber cancels or changes their plan — always the store's page. */
    fun manageSubscriptions(context: Context)
}

/**
 * Told whenever the store has given a definite answer about ownership.
 *
 * Only definite answers: a store that could not be reached says nothing, and
 * nothing that was bought is taken away because the network blinked.
 */
fun interface OwnershipListener {
    fun onOwnership(owned: Boolean)
}

/** The products as they are set up in the Play Console. Change both together. */
object Products {
    /** One-time purchase ("In-app product"), unlocks for good. */
    const val LIFETIME = "karacast_lifetime"

    /** Subscription; its base plans (monthly, yearly, …) are all offered. */
    const val SUBSCRIPTION = "karacast_abo"
}
