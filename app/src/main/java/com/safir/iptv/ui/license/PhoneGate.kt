package com.safir.iptv.ui.license

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.CompanionApps
import com.safir.iptv.util.CompanionInstall
import com.safir.iptv.util.DeviceKind
import kotlinx.coroutines.launch

/**
 * Die TV-App auf einem Handy: statt der Oberfläche ein Hinweis auf Karacast Mobil.
 *
 * Im Play Store kommt es dazu gar nicht erst, dort ist die TV-App für Handys
 * ausgeblendet. Eine APK aber lässt sich überallhin legen — und eine Oberfläche,
 * die für drei Meter Abstand und eine Fernbedienung gebaut ist, macht auf einem
 * Handy niemanden glücklich. Tablets sind ausdrücklich willkommen.
 */
@Composable
fun PhoneGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val onPhone = remember { DeviceKind.isPhone(context) }
    if (!onPhone) {
        content()
        return
    }

    val t = LocalStrings.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var percent by remember { mutableIntStateOf(-1) }
    var problem by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Brand.TextPrimary, fontSize = 28.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = t.device.tvOnPhoneTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = t.device.tvOnPhoneBody,
                style = MaterialTheme.typography.bodyLarge,
                color = Brand.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            TvButton(
                onClick = {
                    if (busy) return@TvButton
                    problem = null
                    if (CompanionInstall.viaStore) {
                        CompanionInstall.openStore(context, CompanionApps.MOBILE_PLAY_PACKAGE)
                        return@TvButton
                    }
                    busy = true
                    percent = -1
                    scope.launch {
                        runCatching {
                            CompanionInstall.downloadAndInstall(
                                context = context,
                                updateUrl = CompanionApps.MOBILE_UPDATE_URL,
                                onProgress = { percent = it }
                            )
                        }.onFailure { problem = t.device.failed }
                        busy = false
                    }
                }
            ) {
                Text(
                    when {
                        busy && percent >= 0 -> "${t.update.downloading} $percent %"
                        busy -> t.update.downloading
                        else -> t.device.getMobile
                    }
                )
            }
            problem?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = Brand.Danger, textAlign = TextAlign.Center)
            }
        }
    }
}
