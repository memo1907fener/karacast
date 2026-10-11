package com.safir.iptv.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.TvFieldKind
import com.safir.iptv.ui.components.TvTextField
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import kotlinx.coroutines.delay

/**
 * Everything of this box onto another one: over the home network for a
 * television, as a file for a phone. Applying a backup replaces what is here,
 * so it is always shown first and only applied when confirmed.
 */
@Composable
internal fun BackupPage(onRestored: () -> Unit) {
    val t = LocalStrings.current.backup
    val viewModel: BackupViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) { onDispose { viewModel.stopAll() } }
    LaunchedEffect(state.finished) { if (state.finished) onRestored() }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::saveTo) }
    val loadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::loadFrom) }

    state.busy?.let { busy ->
        SettingsCard(title = t.title) {
            Text(busy, style = MaterialTheme.typography.bodyLarge, color = Brand.TextPrimary)
        }
        return
    }

    state.pending?.let { backup ->
        val applyFocus = remember { FocusRequester() }
        LaunchedEffect(backup) {
            delay(80)
            runCatching { applyFocus.requestFocus() }
        }
        SettingsCard(title = t.confirmTitle) {
            Text(
                t.confirmText(backup.sources.size, backup.device),
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.TextPrimary
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    onClick = viewModel::apply,
                    modifier = Modifier.focusRequester(applyFocus),
                    colors = ButtonDefaults.colors(
                        containerColor = Brand.Accent,
                        contentColor = Brand.Background
                    )
                ) { Text(t.apply, fontWeight = FontWeight.SemiBold) }
                TvButton(onClick = viewModel::cancelPending) { Text(t.cancel) }
            }
        }
        return
    }

    SettingsCard(title = t.lanTitle) {
        Text(t.lanHint, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
        Spacer(Modifier.height(14.dp))

        val code = state.sendCode
        when {
            code != null -> {
                Text(t.sendingHint, style = MaterialTheme.typography.bodyMedium, color = Brand.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text(
                    code.chunked(3).joinToString(" "),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Brand.Accent
                )
                state.sendAddress?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(t.address(it), style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
                }
                Spacer(Modifier.height(14.dp))
                TvButton(onClick = viewModel::stopAll) { Text(t.stop) }
            }

            state.receiving -> {
                Text(
                    if (state.found.isEmpty()) t.noneFound else t.searching,
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.TextSecondary
                )
                if (state.found.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.found.forEach { device ->
                            val chosen = device.host == state.address && device.port == state.port
                            TvButton(
                                onClick = { viewModel.pick(device) },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (chosen) Brand.Accent else Brand.SurfaceHigh,
                                    contentColor = if (chosen) Brand.Background else Brand.TextPrimary
                                )
                            ) { Text(device.name) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TvTextField(
                    value = state.address,
                    onValueChange = viewModel::setAddress,
                    label = t.addressLabel,
                    placeholder = "192.168.1.23",
                    kind = TvFieldKind.URL
                )
                Spacer(Modifier.height(10.dp))
                TvTextField(
                    value = state.code,
                    onValueChange = viewModel::setCode,
                    label = t.codeLabel,
                    placeholder = "123456",
                    kind = TvFieldKind.NUMBER
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        onClick = viewModel::fetch,
                        colors = ButtonDefaults.colors(
                            containerColor = Brand.Accent,
                            contentColor = Brand.Background
                        )
                    ) { Text(t.fetch, fontWeight = FontWeight.SemiBold) }
                    TvButton(onClick = viewModel::stopAll) { Text(t.stop) }
                }
            }

            else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = viewModel::startSending) { Text(t.send) }
                TvButton(onClick = viewModel::startReceiving) { Text(t.receive) }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    SettingsCard(title = t.fileTitle) {
        Text(t.fileHint, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Many television boxes come without the system's file picker; the
            // launch then fails outright, and that is said instead of crashing.
            TvButton(onClick = {
                try {
                    saveLauncher.launch(viewModel.fileName())
                } catch (missing: ActivityNotFoundException) {
                    viewModel.noFilePicker()
                }
            }) { Text(t.save) }
            TvButton(onClick = {
                try {
                    loadLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                } catch (missing: ActivityNotFoundException) {
                    viewModel.noFilePicker()
                }
            }) { Text(t.load) }
        }
    }

    Column {
        state.message?.let {
            Spacer(Modifier.height(16.dp))
            StatusBanner(it, Brand.Live)
        }
        state.error?.let {
            Spacer(Modifier.height(16.dp))
            StatusBanner(it, Brand.Danger)
        }
    }
}

/**
 * The same page on its own, for a box that has not been set up yet and so has
 * no settings to open it from.
 */
@Composable
fun BackupScreen(onBack: () -> Unit, onRestored: () -> Unit) {
    val t = LocalStrings.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 64.dp, vertical = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.backup.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            TvButton(onClick = onBack) { Text(t.back) }
        }
        Spacer(Modifier.height(24.dp))
        BackupPage(onRestored = onRestored)
    }
}
