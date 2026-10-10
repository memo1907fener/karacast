package com.safir.iptv.ui.license

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.data.billing.Billing
import com.safir.iptv.data.billing.Offer
import com.safir.iptv.data.repository.LicenseRepository
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.i18n.PlayStrings
import com.safir.iptv.ui.theme.Brand
import kotlinx.coroutines.delay

/**
 * The wall at the end of the fortnight, Play version.
 *
 * Where the APK shows a QR code and waits for a payment on a telephone, here the
 * store's own payment sheet opens on the television itself — Google Play handles
 * the card, the account and the receipt. The moment the purchase is through, the
 * ownership listener in [LicenseRepository] lets this screen go by itself.
 */
@Composable
fun PlayActivationScreen(licenses: LicenseRepository) {
    val t = LocalStrings.current
    val license = t.license

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = license.expiredTitle,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = t.play.expiredBody,
            style = MaterialTheme.typography.bodyLarge,
            color = Brand.TextSecondary,
            modifier = Modifier.widthIn(max = 640.dp)
        )
        Spacer(Modifier.height(26.dp))
        PlayOffers(billing = licenses.billing, takeFocus = true)
    }
}

/**
 * The offers as buttons, plus "restore" — shared by the wall and by the licence
 * page in the settings, where somebody can buy before the trial runs out.
 */
@Composable
fun PlayOffers(billing: Billing, takeFocus: Boolean = false) {
    val t = LocalStrings.current.play
    val context = LocalContext.current
    val state by billing.state.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) { billing.refresh() }

    // The first button takes the focus as soon as there is one; before the store
    // has answered there is nothing on screen that could hold it.
    LaunchedEffect(takeFocus, state.offers.isNotEmpty()) {
        if (!takeFocus) return@LaunchedEffect
        delay(150)
        runCatching { first.requestFocus() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.ready) {
            Text(t.loading, style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary)
        }

        state.offers.forEachIndexed { index, offer ->
            OfferButton(
                offer = offer,
                strings = t,
                onClick = { context.findActivity()?.let { billing.buy(it, offer.id) } },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier
            )
        }

        if (state.offers.isNotEmpty()) {
            Text(t.sameAccount, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                onClick = billing::refresh,
                modifier = if (state.offers.isEmpty()) Modifier.focusRequester(first) else Modifier
            ) {
                Text(t.restore)
            }
            if (state.offers.any { it.isSubscription }) {
                TvButton(onClick = { billing.manageSubscriptions(context) }) {
                    Text(t.manage)
                }
            }
        }

        val note = when {
            state.pending -> t.pending
            state.unavailable -> t.unavailable
            state.error != null -> t.failed(state.error.orEmpty())
            else -> null
        }
        note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.pending) Brand.Accent else Brand.Danger,
                modifier = Modifier.widthIn(max = 640.dp)
            )
        }
    }
}

@Composable
private fun OfferButton(
    offer: Offer,
    strings: PlayStrings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvButton(onClick = onClick, modifier = modifier.widthIn(min = 360.dp)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = offer.period?.let { strings.planName(it) } ?: strings.lifetime,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = offer.price + (offer.period?.let { " " + strings.perPeriod(it) } ?: ""),
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = when {
                    offer.period == null -> strings.lifetimeNote
                    offer.freeTrial != null -> "${strings.withTrial} · ${strings.cancelAnytime}"
                    else -> strings.cancelAnytime
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** "P1M" → "Monatsabo". Anything unusual falls back to the month count. */
private fun PlayStrings.planName(period: String): String = when (val months = period.months()) {
    null -> if (period == "P1W") weekly else monthly
    1 -> monthly
    12 -> yearly
    else -> everyMonths(months)
}

private fun PlayStrings.perPeriod(period: String): String = when (val months = period.months()) {
    null -> if (period == "P1W") perWeek else perMonth
    1 -> perMonth
    12 -> perYear
    else -> perMonths(months)
}

/** ISO-8601 periods as Play uses them: P1M, P3M, P6M, P1Y. Weeks give null. */
private fun String.months(): Int? {
    val match = Regex("""P(\d+)([MY])""").matchEntire(this) ?: return null
    val n = match.groupValues[1].toInt()
    return if (match.groupValues[2] == "Y") n * 12 else n
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
