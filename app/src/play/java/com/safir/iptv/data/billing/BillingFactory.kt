package com.safir.iptv.data.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object BillingFactory {
    fun create(context: Context, listener: OwnershipListener): Billing =
        PlayBilling(context.applicationContext, listener)
}

/**
 * Google Play Billing, for the Play version.
 *
 * Two things for sale, both set up in the Play Console: a one-time unlock
 * ([Products.LIFETIME]) and a subscription ([Products.SUBSCRIPTION]) with as many
 * base plans as the owner cares to add. Names and prices come from the Console;
 * the app only shows what it is given.
 *
 * Ownership is decided by Google alone: whatever `queryPurchases` returns as
 * purchased is owned, a refund or a lapsed subscription disappears from that list
 * by itself. Every purchase is acknowledged straight away — Google refunds one that
 * stays unacknowledged for three days.
 */
private class PlayBilling(
    private val context: Context,
    private val listener: OwnershipListener
) : Billing, PurchasesUpdatedListener {

    private val _state = MutableStateFlow(BillingState())
    override val state: StateFlow<BillingState> = _state.asStateFlow()

    /** What the store said about each product, needed again to start a purchase. */
    private var details: Map<String, ProductDetails> = emptyMap()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    private var connecting = false

    override fun refresh() {
        if (client.isReady) {
            loadProducts()
            loadPurchases()
            return
        }
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update { it.copy(unavailable = false) }
                    loadProducts()
                    loadPurchases()
                } else {
                    // No Play Store on this box, or no Google account on it. Nothing
                    // owned is touched — that stays as the device last knew it.
                    _state.update { it.copy(ready = true, unavailable = true) }
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
            }
        })
    }

    // ------------------------------------------------------------ what is for sale

    private fun loadProducts() {
        query(Products.LIFETIME, BillingClient.ProductType.INAPP)
        query(Products.SUBSCRIPTION, BillingClient.ProductType.SUBS)
    }

    /** One query per type: Play refuses to mix one-time products and subscriptions. */
    private fun query(productId: String, type: String) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(type)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, found ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.update { it.copy(ready = true) }
                return@queryProductDetailsAsync
            }
            found.productDetailsList.forEach { product ->
                details = details + (product.productId to product)
            }
            publishOffers()
        }
    }

    private fun publishOffers() {
        val offers = buildList {
            details[Products.LIFETIME]?.let { product ->
                @Suppress("DEPRECATION")
                product.oneTimePurchaseOfferDetails?.let { add(Offer(LIFETIME_OFFER, it.formattedPrice, null)) }
            }
            details[Products.SUBSCRIPTION]?.subscriptionOfferDetails
                ?.groupBy { it.basePlanId }
                ?.forEach { (_, plans) ->
                    // Google only hands out offers this account may still take, so
                    // a free trial in the list is one the viewer is entitled to.
                    val chosen = plans.firstOrNull { it.hasFreeTrial() }
                        ?: plans.firstOrNull { it.offerId == null }
                        ?: plans.first()
                    val phases = chosen.pricingPhases.pricingPhaseList
                    val regular = phases.last()
                    add(
                        Offer(
                            id = chosen.offerToken,
                            price = regular.formattedPrice,
                            period = regular.billingPeriod,
                            freeTrial = phases.firstOrNull { it.priceAmountMicros == 0L }?.billingPeriod
                        )
                    )
                }
        }
        _state.update { it.copy(ready = true, offers = offers) }
    }

    private fun ProductDetails.SubscriptionOfferDetails.hasFreeTrial(): Boolean =
        pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }

    // -------------------------------------------------------------- what is owned

    private fun loadPurchases() {
        var inApp: List<Purchase>? = null
        var subs: List<Purchase>? = null

        fun both() {
            val a = inApp ?: return
            val b = subs ?: return
            settle(a + b)
        }

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            inApp = list
            both()
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            subs = list
            both()
        }
    }

    /** The full, definite list of what this account holds. */
    private fun settle(purchases: List<Purchase>) {
        val ours = purchases.filter { purchase ->
            purchase.products.any { it == Products.LIFETIME || it == Products.SUBSCRIPTION }
        }
        val paid = ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        paid.filterNot { it.isAcknowledged }.forEach(::acknowledge)
        _state.update {
            it.copy(
                pending = ours.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING },
                busy = false
            )
        }
        listener.onOwnership(paid.isNotEmpty())
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        // A failure here is retried on the next refresh, well inside Google's
        // three days.
        client.acknowledgePurchase(params) { }
    }

    // ------------------------------------------------------------------ buying

    override fun buy(activity: Activity, offerId: String) {
        val productParams = if (offerId == LIFETIME_OFFER) {
            val product = details[Products.LIFETIME] ?: return
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .build()
        } else {
            val product = details[Products.SUBSCRIPTION] ?: return
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .setOfferToken(offerId)
                .build()
        }
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        _state.update { it.copy(busy = true, error = null) }
        val result = client.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update { it.copy(busy = false, error = result.responseCode.toString()) }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            // The sheet only reports this one purchase; the full list is what
            // decides, so it is fetched again rather than pieced together.
            BillingClient.BillingResponseCode.OK,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> loadPurchases()

            BillingClient.BillingResponseCode.USER_CANCELED ->
                _state.update { it.copy(busy = false) }

            else -> _state.update {
                it.copy(busy = false, error = result.responseCode.toString())
            }
        }
    }

    override fun manageSubscriptions(context: Context) {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${Products.SUBSCRIPTION}&package=${context.packageName}"
        )
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private companion object {
        /** The one-time product has a single price and needs no offer token. */
        const val LIFETIME_OFFER = "lifetime"
    }
}
