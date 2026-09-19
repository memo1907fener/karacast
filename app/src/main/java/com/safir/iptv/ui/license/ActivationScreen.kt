package com.safir.iptv.ui.license

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.BuildConfig
import com.safir.iptv.data.repository.LicenseRepository
import com.safir.iptv.domain.model.LicenseState
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.TvTextField
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The wall at the end of the fortnight.
 *
 * Built around one observation: nobody types card details with a television remote.
 * So the television's only job is to show a square and wait. The buying happens on a
 * telephone, in a browser, where entering an address and a card is ordinary — and
 * the moment it is done, this screen notices and lets go by itself.
 *
 * While it is open the app asks the panel every few seconds whether the payment has
 * arrived. That polling is deliberately confined to this screen: it is the one place
 * where somebody is actually standing there waiting for an answer.
 */
@Composable
fun ActivationScreen(
    blocked: Boolean,
    state: LicenseState,
    licenses: LicenseRepository
) {
    val t = LocalStrings.current
    val scope = rememberCoroutineScope()
    val firstButton = remember { FocusRequester() }

    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val buyUrl = remember(state.deviceCode, licenses.licenseUrl) {
        buyAddress(licenses.licenseUrl, state.deviceCode)
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstButton.requestFocus() }
    }

    // Every ten seconds, and only here. A licence that arrives while somebody is
    // looking at this screen should not need a button pressed afterwards.
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            licenses.sync()
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
            .padding(horizontal = 56.dp, vertical = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text(
                text = if (blocked) t.license.blockedTitle else t.license.expiredTitle,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (blocked) t.license.blockedBody else t.license.expiredBody,
                style = MaterialTheme.typography.bodyLarge,
                color = Brand.TextSecondary,
                modifier = Modifier.widthIn(max = 560.dp)
            )

            if (!blocked) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = t.license.price,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Brand.Accent
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = t.license.deviceCode,
                style = MaterialTheme.typography.labelMedium,
                color = Brand.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                // Grouped in pairs and spaced out, because this is the one string on
                // the screen somebody may have to read down a telephone line.
                text = com.safir.iptv.util.DeviceIdentity.formatted(state.deviceCode),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary
            )

            Spacer(Modifier.height(22.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvButton(
                    onClick = {
                        message = null
                        scope.launch { licenses.sync() }
                    },
                    modifier = Modifier.focusRequester(firstButton)
                ) {
                    Text(if (state.checking) t.license.waiting else t.license.checkNow)
                }

                if (BuildConfig.DEBUG) {
                    TvButton(onClick = { licenses.debugActivate() }) {
                        Text(t.license.simulateActive)
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Text(
                text = t.license.redeemTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = t.license.redeemHint,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(360.dp)) {
                TvTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(12) },
                    label = t.license.redeem
                )
            }
            Spacer(Modifier.height(10.dp))
            TvButton(
                onClick = {
                    scope.launch {
                        message = if (licenses.redeem(code)) {
                            t.license.activated
                        } else {
                            t.license.failed
                        }
                    }
                }
            ) {
                Text(t.license.redeem)
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it == t.license.activated) Brand.Live else Brand.Danger
                )
            }
        }

        Spacer(Modifier.width(40.dp))

        if (!blocked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QrCode(content = "https://$buyUrl", size = 300.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = buyUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.AccentSoft,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = t.license.howTo,
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.TextSecondary,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }
        }
    }
}

/**
 * Die Adresse, die als QR-Code auf dem Bildschirm landet.
 *
 * Abgeleitet aus der Lizenz-Adresse, nicht fest eingetragen: beide zeigen auf
 * dasselbe Haus, und zwei getrennte Stellen im Quelltext laufen früher oder später
 * auseinander — meist genau dann, wenn jemand vor dem Fernseher steht und kaufen
 * will. Steht dort nichts Brauchbares, bleibt die Standardadresse.
 */
private fun buyAddress(serverUrl: String, code: String): String {
    val host = serverUrl.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .ifBlank { DEFAULT_HOST }
    return "$host/aktivieren?d=$code"
}

private const val DEFAULT_HOST = "karacast.de"
