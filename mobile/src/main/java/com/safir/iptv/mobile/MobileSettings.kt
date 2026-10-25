package com.safir.iptv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safir.iptv.BuildConfig
import com.safir.iptv.IptvApp
import com.safir.iptv.data.remote.UpdateChecker
import com.safir.iptv.data.remote.UpdateInstaller
import com.safir.iptv.domain.model.LicenseStatus
import com.safir.iptv.domain.model.TrackLanguage
import com.safir.iptv.domain.model.UpdateInfo
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.i18n.AppLanguage
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.settings.SettingsViewModel

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Kc.TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Kc.Surface),
            content = content
        )
    }
}

@Composable
private fun LinkRow(title: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (!detail.isNullOrBlank()) Text(detail, fontSize = 13.sp, color = Kc.TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Kc.TextSecondary)
    }
}

@Composable
private fun SwitchRow(title: String, hint: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (!hint.isNullOrBlank()) Text(hint, fontSize = 12.sp, color = Kc.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Kc.Accent)
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Kc.Border.copy(alpha = 0.6f), modifier = Modifier.padding(start = 16.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(label: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, (text, selected) ->
                KcChip(label = text, selected = selected, onClick = { onPick(index) })
            }
        }
    }
}

/**
 * Die Einstellungen der Handy-App: weniger als am Fernseher (keine
 * Fernbedienung, kein Autostart, keine Startbildschirm-Reihe), dafür alles auf
 * einer Seite, nach Themen in Karten.
 */
@Composable
fun MobileSettingsScreen(
    onBack: () -> Unit,
    onEditSource: () -> Unit,
    onAddSource: () -> Unit,
    onSourceSwitched: () -> Unit,
    onDisconnected: () -> Unit,
    onOpenUnlock: () -> Unit
) {
    val t = LocalStrings.current
    val m = LocalMobileStrings.current
    val context = LocalContext.current
    val container = remember { (context.applicationContext as IptvApp).container }
    val viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sources by viewModel.savedSources.collectAsStateWithLifecycle()
    val license by container.licenses.state.collectAsStateWithLifecycle()
    val language by AppLocale.current.collectAsStateWithLifecycle()
    var autoNext by remember { mutableStateOf(container.settings.autoNextEpisode) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground()
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.back)
            }
            Text(t.settings, style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Was gerade läuft oder schiefging, oben, wo man es sieht.
            val note = state.busyMessage ?: state.error ?: state.message
            if (note != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.error != null) Kc.Danger.copy(alpha = 0.18f) else Kc.SurfaceHigh)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(note, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (state.busyMessage == null) {
                        TextButton(onClick = viewModel::dismissMessages) { Text(m.close) }
                    }
                }
            }

            // Nach einem Absturz: was es war, damit es sich beheben lässt.
            state.lastCrash?.let { crash ->
                SettingsCard(t.crash.title) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t.crash.explain, fontSize = 13.sp, color = Kc.TextSecondary)
                        Text(
                            java.text.DateFormat.getDateTimeInstance().format(java.util.Date(crash.atMs)),
                            fontSize = 13.sp
                        )
                        Text(crash.message, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Kc.Danger)
                        Text(
                            crash.trace.lineSequence().take(8).joinToString("\n"),
                            fontSize = 11.sp,
                            color = Kc.TextSecondary,
                            lineHeight = 14.sp
                        )
                        TextButton(onClick = viewModel::clearCrashReport) { Text(t.crash.forget) }
                    }
                }
            }

            // ------------------------------------------------------- Zugang
            SettingsCard(m.sectionAccess) {
                val source = state.source
                if (source != null) {
                    Column(Modifier.padding(16.dp)) {
                        Text(source.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            listOf(
                                m.nChannels(state.channelCount),
                                "${t.epgSection}: " + if (state.hasGuide) t.available else t.noData
                            ).joinToString(" · "),
                            fontSize = 13.sp,
                            color = Kc.TextSecondary
                        )
                    }
                    Divider()
                }
                LinkRow(t.editCredentials, onClick = onEditSource)
                Divider()
                LinkRow(t.reloadPlaylist, onClick = viewModel::resyncPlaylist)
                Divider()
                LinkRow(t.refreshEpg, onClick = viewModel::refreshGuide)
                if (sources.size > 1) {
                    Divider()
                    Text(
                        t.sources.title,
                        fontSize = 13.sp,
                        color = Kc.TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                    )
                    sources.forEach { saved ->
                        val active = saved.id == state.source?.id
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(saved.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            if (active) {
                                Text(t.sources.active, fontSize = 13.sp, color = Kc.Live)
                            } else {
                                TextButton(onClick = { viewModel.removeSource(saved) }) {
                                    Text(t.sources.remove, color = Kc.TextSecondary)
                                }
                                TextButton(onClick = { viewModel.switchSource(saved, onSourceSwitched) }) {
                                    Text(t.sources.switchTo)
                                }
                            }
                        }
                    }
                }
                Divider()
                LinkRow(t.sources.add, onClick = onAddSource)
                Divider()
                Row(
                    Modifier.fillMaxWidth().clickable { confirmDisconnect = true }.padding(16.dp)
                ) {
                    Text(t.disconnect, color = Kc.Danger, fontSize = 15.sp)
                }
            }

            // -------------------------------------------------- Abo & Lizenz
            SettingsCard(m.sectionSubscription) {
                val status = when (license.status) {
                    LicenseStatus.TRIAL -> t.license.daysLeft(license.daysLeft)
                    LicenseStatus.ACTIVE -> t.license.statusActive
                    LicenseStatus.EXPIRED -> t.license.statusExpired
                    LicenseStatus.BLOCKED -> t.license.statusBlocked
                }
                LinkRow(
                    title = if (license.status == LicenseStatus.ACTIVE) m.activeTitle else m.unlockTitle,
                    detail = status,
                    onClick = onOpenUnlock
                )
            }

            // -------------------------------------------------------- Sprache
            SettingsCard(m.sectionLanguage) {
                ChoiceChips(
                    label = m.sectionLanguage,
                    options = AppLanguage.entries.map { it.label to (it == language) }
                ) { index ->
                    val picked = AppLanguage.entries[index]
                    container.settings.language = picked.tag
                    AppLocale.set(picked)
                }
            }

            // ----------------------------------------------------- Wiedergabe
            SettingsCard(t.playbackSection) {
                SwitchRow(m.autoRecover, t.recovery.autoRecoverHint, state.autoRecover, viewModel::setAutoRecover)
                Divider()
                SwitchRow(t.pip.title, t.pip.hint, state.pictureInPicture, viewModel::setPictureInPicture)
                Divider()
                SwitchRow(t.vod.autoNext, null, autoNext) {
                    container.settings.autoNextEpisode = it
                    autoNext = it
                }
                Divider()
                SwitchRow(t.useHls, t.hlsAfterReload, state.preferHls, viewModel::setPreferHls)
                Divider()
                ChoiceChips(
                    label = m.audioPref,
                    options = TrackLanguage.entries.map { (if (it == TrackLanguage.AUTO) m.automatic else it.label) to (it == state.audioLanguage) }
                ) { viewModel.setAudioLanguage(TrackLanguage.entries[it]) }
                Divider()
                ChoiceChips(
                    label = m.subtitlePref,
                    options = TrackLanguage.entries.map { (if (it == TrackLanguage.AUTO) t.off else it.label) to (it == state.subtitleLanguage) }
                ) { viewModel.setSubtitleLanguage(TrackLanguage.entries[it]) }
            }

            // -------------------------------------------------------- Updates
            if (!BuildConfig.PLAY_STORE) {
                SettingsCard(t.updates) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${t.installedVersion}: ${BuildConfig.VERSION_NAME}", fontSize = 15.sp)
                        val info = state.updateInfo
                        if (info != null) {
                            Text(t.versionAvailable(info.versionName), color = Kc.AccentSoft, fontWeight = FontWeight.Bold)
                            if (info.notes.isNotBlank()) Text(info.notes, fontSize = 13.sp, color = Kc.TextSecondary)
                            if (state.downloading) {
                                if (state.downloadPercent >= 0) {
                                    LinearProgressIndicator(
                                        progress = { state.downloadPercent / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            } else {
                                Button(onClick = { viewModel.downloadAndInstall(context) }) { Text(t.update.installNow) }
                            }
                        } else {
                            OutlinedButton(onClick = viewModel::checkForUpdate) { Text(t.checkNow) }
                        }
                    }
                }
            }

            // ------------------------------------------------------------ Über
            SettingsCard(t.aboutSection) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Karacast Mobil · ${t.versionLabel(BuildConfig.VERSION_NAME)}", fontWeight = FontWeight.Bold)
                    }
                    Text(t.disclaimer, fontSize = 13.sp, color = Kc.TextSecondary)
                }
            }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(t.disconnect) },
            text = { Text(t.accountHint) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnect(onDisconnected)
                }) { Text(t.disconnect, color = Kc.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text(t.sources.cancel) }
            },
            containerColor = Kc.Surface
        )
    }
}

/**
 * Einmal am Tag beim Öffnen: gibt es eine neuere APK? Nur die APK-Fassung —
 * die Play-Fassung bekommt Updates vom Play Store und darf nicht einmal fragen.
 */
@Composable
fun MobileUpdatePrompt() {
    if (BuildConfig.PLAY_STORE) return
    val t = LocalStrings.current
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as IptvApp).container.settings }
    val scope = rememberCoroutineScope()
    var offer by remember { mutableStateOf<UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var percent by remember { mutableIntStateOf(-1) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!settings.autoUpdateCheck) return@LaunchedEffect
        val url = settings.updateUrl
        if (url.isBlank()) return@LaunchedEffect
        if (System.currentTimeMillis() - settings.lastUpdateCheckAt < 24L * 60 * 60 * 1000) return@LaunchedEffect
        delay(2_500)
        val found = runCatching { UpdateChecker.check(url, BuildConfig.VERSION_CODE) }.getOrNull()
        settings.lastUpdateCheckAt = System.currentTimeMillis()
        if (found != null && found.versionCode != settings.skippedUpdateCode) offer = found
    }

    val info = offer ?: return
    AlertDialog(
        onDismissRequest = { if (!busy) offer = null },
        title = { Text(t.update.available) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t.versionAvailable(info.versionName), fontWeight = FontWeight.Bold)
                if (info.notes.isNotBlank()) Text(info.notes)
                if (busy) {
                    if (percent >= 0) {
                        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                problem?.let { Text(it, color = Kc.Danger) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    problem = null
                    if (!UpdateInstaller.canInstall(context)) {
                        UpdateInstaller.openInstallPermission(context)
                        return@TextButton
                    }
                    busy = true
                    scope.launch {
                        runCatching {
                            val file = UpdateInstaller.download(context, info.downloadUrl, info.versionName) { percent = it }
                            UpdateInstaller.install(context, file)
                        }.onFailure { problem = t.update.failed }
                        busy = false
                    }
                }
            ) { Text(t.update.installNow, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = {
                    settings.skippedUpdateCode = info.versionCode
                    offer = null
                }) { Text(t.update.skipVersion, color = Kc.TextSecondary) }
                TextButton(enabled = !busy, onClick = { offer = null }) { Text(t.update.later) }
            }
        },
        containerColor = Kc.Surface
    )
}
