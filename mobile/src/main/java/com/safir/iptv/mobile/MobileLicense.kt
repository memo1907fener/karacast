package com.safir.iptv.mobile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safir.iptv.BuildConfig
import com.safir.iptv.IptvApp
import com.safir.iptv.data.billing.Offer
import com.safir.iptv.data.repository.LicenseRepository
import com.safir.iptv.domain.model.LicenseState
import com.safir.iptv.domain.model.LicenseStatus
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.i18n.PlayStrings

import com.safir.iptv.util.CompanionApps
import com.safir.iptv.util.CompanionInstall
import com.safir.iptv.util.DeviceIdentity
import com.safir.iptv.util.DeviceKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Karacast Mobil auf einem Fernseher: statt der Handy-Oberfläche ein Hinweis auf
 * die TV-App, samt Weg dorthin. Im Play Store kommt es dazu nicht — dort ist die
 * App für Fernseher unsichtbar —, wohl aber mit der APK.
 */
@Composable
fun TvGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val onTv = remember { DeviceKind.isTelevision(context) }
    if (!onTv) {
        content()
        return
    }
    val t = LocalStrings.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var percent by remember { mutableIntStateOf(-1) }
    var problem by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            BrandMark(64.dp)
            Text(
                t.device.mobileOnTvTitle,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                t.device.mobileOnTvBody,
                color = Kc.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (busy) return@Button
                problem = null
                if (CompanionInstall.viaStore) {
                    CompanionInstall.openStore(context, CompanionApps.TV_PLAY_PACKAGE)
                    return@Button
                }
                busy = true
                scope.launch {
                    runCatching {
                        CompanionInstall.downloadAndInstall(context, CompanionApps.TV_UPDATE_URL) {
                            percent = it
                        }
                    }.onFailure { problem = t.device.failed }
                    busy = false
                }
            }) {
                Text(
                    when {
                        busy && percent >= 0 -> "${t.update.downloading} $percent %"
                        busy -> t.update.downloading
                        else -> t.device.getTv
                    }
                )
            }
            problem?.let { Text(it, color = Kc.Danger, textAlign = TextAlign.Center) }
        }
    }
}

/**
 * Steht vor der ganzen App und entscheidet, ob sie benutzt werden darf — dieselbe
 * Lizenzlogik wie am Fernseher ([LicenseRepository]): vierzehn Tage zum Testen,
 * danach Google Play (Play-Fassung) oder eine karacast.de-Lizenz (APK).
 */
@Composable
fun MobileLicenseGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val t = LocalStrings.current
    val licenses = remember { (context.applicationContext as IptvApp).container.licenses }
    val state by licenses.state.collectAsStateWithLifecycle()

    // Einmal im Hintergrund nachfragen. Das kann nur etwas freischalten, nie sperren.
    LaunchedEffect(Unit) {
        delay(4_000)
        licenses.sync()
    }

    when (state.status) {
        LicenseStatus.EXPIRED, LicenseStatus.BLOCKED ->
            UnlockScreen(licenses = licenses, state = state, onClose = null)

        else -> Box(Modifier.fillMaxSize()) {
            content()

            var pill by remember { mutableStateOf(true) }
            LaunchedEffect(state.status, state.daysLeft) {
                pill = true
                delay(if (state.shouldWarn) 10_000 else 5_000)
                pill = false
            }
            AnimatedVisibility(
                visible = pill && state.status == LicenseStatus.TRIAL,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = if (state.daysLeft <= 1) t.license.lastDay else t.license.daysLeft(state.daysLeft),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (state.shouldWarn) Kc.Accent else Kc.TextPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/**
 * Freischalten — als Wand nach der Testphase ([onClose] null) oder als Seite aus
 * den Einstellungen, wo man schon vorher kaufen oder sein Abo verwalten kann.
 */
@Composable
fun UnlockScreen(
    licenses: LicenseRepository,
    state: LicenseState,
    onClose: (() -> Unit)?
) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val blocked = state.status == LicenseStatus.BLOCKED
    val active = state.status == LicenseStatus.ACTIVE
    val wall = onClose == null

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onClose != null) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = m.close)
                }
            }
        } else {
            Spacer(Modifier.height(32.dp))
        }

        Column(
            Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BrandMark(72.dp)
            Text(
                text = when {
                    active -> m.activeTitle
                    blocked -> t.license.blockedTitle
                    wall -> t.license.expiredTitle
                    else -> m.unlockTitle
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            val body = when {
                active -> if (BuildConfig.PLAY_STORE) t.play.viaPlay else
                    t.license.activatedOn(
                        java.text.DateFormat.getDateInstance().format(java.util.Date(state.activatedAt))
                    )
                blocked -> t.license.blockedBody
                wall && BuildConfig.PLAY_STORE -> t.play.expiredBody
                wall -> t.license.expiredBody
                state.status == LicenseStatus.TRIAL -> m.unlockEarly(state.daysLeft)
                else -> ""
            }
            if (body.isNotBlank()) {
                Text(body, color = Kc.TextSecondary, textAlign = TextAlign.Center)
            }

            if (!active && !blocked) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Kc.Surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    m.perks.forEach { perk ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Kc.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(perk, fontSize = 15.sp)
                        }
                    }
                }
            }

            if (BuildConfig.PLAY_STORE) {
                PlayOffersMobile(licenses = licenses, showOffers = !active)
            } else if (!active) {
                DirectActivation(licenses = licenses, state = state, blocked = blocked)
            }

            if (BuildConfig.DEBUG && !active) {
                TextButton(onClick = { licenses.debugActivate() }) {
                    Text(t.license.simulateActive)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// -------------------------------------------------------------- Google Play

@Composable
private fun PlayOffersMobile(licenses: LicenseRepository, showOffers: Boolean) {
    val t = LocalStrings.current.play
    val context = LocalContext.current
    val billing = licenses.billing
    val state by billing.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { billing.refresh() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showOffers) {
            if (!state.ready) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(t.loading, color = Kc.TextSecondary)
                }
            }
            // Abos zuerst, der Einmalkauf als letzte, hervorgehobene Karte.
            val ordered = state.offers.sortedBy { if (it.isSubscription) 0 else 1 }
            ordered.forEach { offer ->
                OfferCard(
                    offer = offer,
                    strings = t,
                    highlight = !offer.isSubscription,
                    enabled = !state.busy,
                    onClick = { context.findActivity()?.let { billing.buy(it, offer.id) } }
                )
            }
            if (state.offers.isNotEmpty()) {
                Text(
                    t.sameAccount,
                    fontSize = 12.sp,
                    color = Kc.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(onClick = billing::refresh) { Text(t.restore) }
            if (state.offers.any { it.isSubscription } || !showOffers) {
                OutlinedButton(onClick = { billing.manageSubscriptions(context) }) { Text(t.manage) }
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
                it,
                color = if (state.pending) Kc.Accent else Kc.Danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OfferCard(
    offer: Offer,
    strings: PlayStrings,
    highlight: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) Kc.Accent else Kc.Surface,
        contentColor = if (highlight) Color(0xFF101010) else Kc.TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlight) Modifier else Modifier.border(1.dp, Kc.Border, RoundedCornerShape(16.dp))
            )
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = offer.period?.let { strings.planNameOf(it) } ?: strings.lifetime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = when {
                        offer.period == null -> strings.lifetimeNote
                        offer.freeTrial != null -> "${strings.withTrial} · ${strings.cancelAnytime}"
                        else -> strings.cancelAnytime
                    },
                    fontSize = 13.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(offer.price, fontWeight = FontWeight.Black, fontSize = 20.sp)
                offer.period?.let { Text(strings.perPeriodOf(it), fontSize = 12.sp) }
            }
        }
    }
}

/** "P1M" → "Monatsabo". Was nicht in Monate aufgeht, wird nach Wochen benannt. */
private fun PlayStrings.planNameOf(period: String): String = when (val months = period.monthsOf()) {
    null -> if (period == "P1W") weekly else monthly
    1 -> monthly
    12 -> yearly
    else -> everyMonths(months)
}

private fun PlayStrings.perPeriodOf(period: String): String = when (val months = period.monthsOf()) {
    null -> if (period == "P1W") perWeek else perMonth
    1 -> perMonth
    12 -> perYear
    else -> perMonths(months)
}

private fun String.monthsOf(): Int? {
    val match = Regex("""P(\d+)([MY])""").matchEntire(this) ?: return null
    val n = match.groupValues[1].toInt()
    return if (match.groupValues[2] == "Y") n * 12 else n
}

// ------------------------------------------------------------ karacast.de (APK)

/**
 * Die APK-Fassung kauft ihre Lizenz auf karacast.de. Am Fernseher geht das über
 * einen QR-Code, den man mit dem Handy abscannt — hier *ist* man schon am Handy,
 * also öffnet ein Knopf dieselbe Seite direkt im Browser.
 */
@Composable
private fun DirectActivation(licenses: LicenseRepository, state: LicenseState, blocked: Boolean) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    // Solange diese Seite offen ist, alle zehn Sekunden: wer gerade im Browser
    // bezahlt hat, soll beim Zurückkommen nichts mehr drücken müssen.
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            licenses.sync()
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!blocked) {
            Text(
                t.license.price,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = Kc.Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val url = "https://" + buyAddress(licenses.licenseUrl, state.deviceCode)
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(m.buyOnline, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(m.buyHint, fontSize = 13.sp, color = Kc.TextSecondary, textAlign = TextAlign.Center)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Kc.Surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(t.license.deviceCode, fontSize = 12.sp, color = Kc.TextSecondary)
            Text(
                DeviceIdentity.formatted(state.deviceCode),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = {
                    message = null
                    scope.launch { licenses.sync() }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.checking) t.license.waiting else t.license.checkNow)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Kc.Surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(t.license.redeemTitle, fontWeight = FontWeight.Bold)
            Text(t.license.redeemHint, fontSize = 13.sp, color = Kc.TextSecondary)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(12) },
                label = { Text(t.license.redeem) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    scope.launch {
                        message = if (licenses.redeem(code)) t.license.activated else t.license.failed
                    }
                },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(t.license.redeem)
            }
            message?.let {
                Text(it, color = if (it == t.license.activated) Kc.Live else Kc.Danger)
            }
        }
    }
}

/** Dieselbe Kaufadresse wie im QR-Code am Fernseher. */
private fun buyAddress(serverUrl: String, code: String): String {
    val host = serverUrl.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .ifBlank { "karacast.de" }
    return "$host/aktivieren?d=$code"
}
