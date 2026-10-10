package com.safir.iptv.data.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The sideloaded APK buys its licence on karacast.de, not in a store, so here
 * there is nothing to connect to. This keeps the Play Billing library out of the
 * APK entirely.
 */
object BillingFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, listener: OwnershipListener): Billing = NoBilling
}

private object NoBilling : Billing {
    override val state: StateFlow<BillingState> =
        MutableStateFlow(BillingState(ready = true, unavailable = true))

    override fun refresh() = Unit
    override fun buy(activity: Activity, offerId: String) = Unit
    override fun manageSubscriptions(context: Context) = Unit
}
