package com.safir.iptv.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.BuildConfig
import com.safir.iptv.IptvApp
import com.safir.iptv.data.remote.UpdateChecker
import com.safir.iptv.data.remote.UpdateInstaller
import com.safir.iptv.domain.model.UpdateInfo
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wraps the home screen and, once a day, mentions that a newer version exists.
 *
 * Deliberately a wrapper and not part of the home screen itself: the question has
 * nothing to do with what that screen is for, and a family member who is not
 * interested should be able to press "later" and never think about it again.
 *
 * The rules it plays by, all of them on purpose:
 *
 * - it asks at most once a day, and only when the app is opened;
 * - it never asks again about a version somebody chose to skip;
 * - it downloads only after a yes, and installs nothing itself — Android asks
 *   that question, and the answer is not this app's to give.
 */
@Composable
fun UpdateGate(content: @Composable () -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as IptvApp).container.settings }
    val scope = rememberCoroutineScope()

    var offer by remember { mutableStateOf<UpdateInfo?>(null) }
    var percent by remember { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var needsPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!settings.autoUpdateCheck) return@LaunchedEffect
        val url = settings.updateUrl
        if (url.isBlank()) return@LaunchedEffect
        val since = System.currentTimeMillis() - settings.lastUpdateCheckAt
        if (since < CHECK_INTERVAL_MS) return@LaunchedEffect

        // A breath first: the home screen should be on the television before
        // anything goes near the network, or the app opens onto a spinner.
        delay(2_500)
        val found = runCatching {
            UpdateChecker.check(url, BuildConfig.VERSION_CODE)
        }.getOrNull()
        settings.lastUpdateCheckAt = System.currentTimeMillis()
        if (found != null && found.versionCode != settings.skippedUpdateCode) {
            offer = found
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()

        offer?.let { info ->
            UpdateCard(
                info = info,
                busy = busy,
                percent = percent,
                problem = problem,
                needsPermission = needsPermission,
                onInstall = {
                    problem = null
                    if (!UpdateInstaller.canInstall(context)) {
                        needsPermission = true
                        UpdateInstaller.openInstallPermission(context)
                        return@UpdateCard
                    }
                    busy = true
                    percent = -1
                    scope.launch {
                        runCatching {
                            val file = UpdateInstaller.download(
                                context = context,
                                url = info.downloadUrl,
                                versionName = info.versionName,
                                onProgress = { percent = it }
                            )
                            UpdateInstaller.install(context, file)
                        }.onFailure {
                            problem = t.update.failed
                        }
                        busy = false
                    }
                },
                onLater = { offer = null },
                onSkip = {
                    settings.skippedUpdateCode = info.versionCode
                    offer = null
                }
            )
        }
    }
}

@Composable
private fun UpdateCard(
    info: UpdateInfo,
    busy: Boolean,
    percent: Int,
    problem: String?,
    needsPermission: Boolean,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit
) {
    val t = LocalStrings.current
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brand.Surface)
                .padding(32.dp)
        ) {
            Text(
                text = t.update.available,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = t.update.versionIsOut(info.versionName),
                style = MaterialTheme.typography.titleSmall,
                color = Brand.AccentSoft
            )

            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = info.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = t.update.installHint,
                style = MaterialTheme.typography.bodySmall,
                // Said once, in grey, until it is the thing standing in the way —
                // then the same sentence in the accent colour, because repeating it
                // underneath itself would just look like the screen stuttered.
                color = if (needsPermission) Brand.Accent else Brand.TextSecondary
            )

            if (busy) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (percent >= 0) {
                        "${t.update.downloading}  ${t.update.percent(percent)}"
                    } else {
                        t.update.downloading
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = Brand.Accent
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brand.SurfaceHigh)
                ) {
                    // An unknown length still gets a bar, just a full one: a strip
                    // that never moves reads as broken, and most servers do say.
                    Box(
                        Modifier
                            .fillMaxWidth(if (percent >= 0) percent / 100f else 1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brand.Accent)
                    )
                }
            }

            problem?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Brand.Danger)
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    onClick = onInstall,
                    modifier = Modifier.focusRequester(first)
                ) {
                    Text(t.update.installNow)
                }
                TvButton(onClick = onLater) { Text(t.update.later) }
                TvButton(onClick = onSkip) { Text(t.update.skipVersion) }
            }
        }
    }
}

/** Once a day. More often is nagging; less often and a fix waits a week. */
private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
