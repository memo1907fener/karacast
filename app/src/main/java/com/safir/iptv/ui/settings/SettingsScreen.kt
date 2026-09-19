package com.safir.iptv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.safir.iptv.BuildConfig
import com.safir.iptv.IptvApp
import com.safir.iptv.domain.model.Category
import com.safir.iptv.domain.model.ChannelSort
import com.safir.iptv.domain.model.LicenseStatus
import com.safir.iptv.domain.model.SourceType
import com.safir.iptv.ui.AppViewModelFactory
import com.safir.iptv.ui.components.FullScreenLoading
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.components.TvFieldKind
import com.safir.iptv.ui.components.TvSwitch
import com.safir.iptv.ui.components.TvTextField
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.DeviceIdentity
import com.safir.iptv.util.formatRelativeSync
import kotlinx.coroutines.launch

/**
 * The areas the settings are divided into.
 *
 * One long page meant scrolling past the buffer size to reach the update address,
 * every single time. Nine boxes on a first screen is what the rest of the app
 * already looks like, and what a remote is actually good at: go there, do the one
 * thing, come back.
 */
private enum class SettingsPage {
    HUB, SOURCE, PLAYBACK, DISPLAY, PARENTAL, GROUPS, ORDER, UPDATES, PANEL, LICENSE,
    ACCOUNT, PROBLEMS
}

@Composable
fun SettingsScreen(
    onDisconnected: () -> Unit,
    onEditSource: () -> Unit,
    onManageGroups: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit
) {
    val t = LocalStrings.current
    val viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val lockedIds by viewModel.lockedCategoryIds.collectAsStateWithLifecycle()
    val startOptions by viewModel.startOptions.collectAsStateWithLifecycle()

    var page by rememberSaveable { mutableStateOf(SettingsPage.HUB) }
    // Deliberately not a saveable: walking away from the child lock and coming
    // back has to ask for the code again, or the lock is only a speed bump.
    var unlocked by remember { mutableStateOf(false) }

    state.busyMessage?.let { busy ->
        Box(Modifier.fillMaxSize().background(Brand.Background)) {
            FullScreenLoading(message = busy)
        }
        return
    }

    if (page == SettingsPage.HUB) {
        SettingsHub(
            hasCrash = state.lastCrash != null,
            parentalOn = state.parentalEnabled,
            onOpen = { page = it },
            onOpenAbout = onOpenAbout,
            onBack = onBack
        )
        return
    }

    val title = when (page) {
        SettingsPage.SOURCE -> t.hub.source
        SettingsPage.PLAYBACK -> t.hub.playback
        SettingsPage.DISPLAY -> t.hub.display
        SettingsPage.PARENTAL -> t.parental.title
        SettingsPage.GROUPS -> t.hub.groups
        SettingsPage.ORDER -> t.hub.order
        SettingsPage.UPDATES -> t.hub.updates
        SettingsPage.PANEL -> t.panel.title
        SettingsPage.LICENSE -> t.license.section
        SettingsPage.ACCOUNT -> t.hub.account
        SettingsPage.PROBLEMS -> t.hub.problems
        SettingsPage.HUB -> t.settings
    }

    val closePage = {
        unlocked = false
        page = SettingsPage.HUB
    }

    // The remote's own Back key. Without this it reaches the navigation graph, which
    // pops the whole settings screen — so one press inside "Display" landed on the
    // home screen instead of back on the tiles. A BackHandler gets it first.
    BackHandler(enabled = true, onBack = closePage)

    SettingsPageFrame(title = title, onClose = closePage) {
        when (page) {
            SettingsPage.SOURCE -> SourceSection(state, viewModel, onEditSource)
            SettingsPage.PLAYBACK -> PlaybackSection(state, viewModel)
            SettingsPage.DISPLAY -> DisplaySection(state, viewModel)
            SettingsPage.PARENTAL -> ParentalSection(
                state = state,
                viewModel = viewModel,
                categories = allCategories,
                lockedIds = lockedIds,
                unlocked = unlocked,
                onUnlocked = { unlocked = true }
            )

            SettingsPage.GROUPS -> GroupsSection(state, viewModel, startOptions, onManageGroups)
            SettingsPage.ORDER -> OrderSection(categories, viewModel)
            SettingsPage.UPDATES -> UpdatesSection(state, viewModel)
            SettingsPage.PANEL -> PanelSection(state, viewModel)
            SettingsPage.LICENSE -> LicenseSection()
            SettingsPage.ACCOUNT -> AccountSection(viewModel, onDisconnected)
            SettingsPage.PROBLEMS -> ProblemsSection(state, viewModel)
            SettingsPage.HUB -> Unit
        }

        Spacer(Modifier.height(20.dp))
        state.message?.let { StatusBanner(it, Brand.Live) }
        state.error?.let { StatusBanner(it, Brand.Danger) }
        Spacer(Modifier.height(32.dp))
        Text(
            t.disclaimer,
            style = MaterialTheme.typography.labelSmall,
            color = Brand.TextSecondary
        )
    }
}

// ------------------------------------------------------------------ the hub

@Composable
private fun SettingsHub(
    hasCrash: Boolean,
    parentalOn: Boolean,
    onOpen: (SettingsPage) -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit
) {
    val t = LocalStrings.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
            .padding(horizontal = 64.dp, vertical = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.settings,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    t.hub.hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.TextSecondary
                )
            }
            TvButton(onClick = onBack) { Text(t.back) }
        }

        Spacer(Modifier.height(24.dp))

        // The picture is what a remote actually aims at: on a wall of nine boxes
        // the eye finds the padlock long before it has read any of the words.
        val tiles = buildList {
            add(HubEntry(t.hub.source, t.hub.sourceHint, Icons.Default.Dns) {
                onOpen(SettingsPage.SOURCE)
            })
            add(HubEntry(t.hub.playback, t.hub.playbackHint, Icons.Default.PlayArrow) {
                onOpen(SettingsPage.PLAYBACK)
            })
            add(HubEntry(t.hub.display, t.hub.displayHint, Icons.Default.Tv) {
                onOpen(SettingsPage.DISPLAY)
            })
            add(
                HubEntry(
                    title = t.parental.title,
                    subtitle = t.parental.tileHint,
                    icon = Icons.Default.Lock,
                    badge = if (parentalOn) t.on else t.off
                ) { onOpen(SettingsPage.PARENTAL) }
            )
            add(HubEntry(t.hub.groups, t.hub.groupsHint, Icons.Default.List) {
                onOpen(SettingsPage.GROUPS)
            })
            add(HubEntry(t.hub.order, t.hub.orderHint, Icons.Default.SwapVert) {
                onOpen(SettingsPage.ORDER)
            })
            add(HubEntry(t.hub.updates, t.hub.updatesHint, Icons.Default.Refresh) {
                onOpen(SettingsPage.UPDATES)
            })
            add(HubEntry(t.license.section, t.license.tileHint, Icons.Default.VerifiedUser) {
                onOpen(SettingsPage.LICENSE)
            })
            add(HubEntry(t.panel.title, t.panel.tileHint, Icons.Default.Cloud) {
                onOpen(SettingsPage.PANEL)
            })
            add(HubEntry(t.hub.account, t.hub.accountHint, Icons.Default.AccountCircle) {
                onOpen(SettingsPage.ACCOUNT)
            })
            add(HubEntry(t.hub.about, t.hub.aboutHint, Icons.Default.Info) { onOpenAbout() })
            if (hasCrash) {
                add(
                    HubEntry(
                        title = t.hub.problems,
                        subtitle = t.hub.problemsHint,
                        icon = Icons.Default.Warning,
                        badge = "!"
                    ) { onOpen(SettingsPage.PROBLEMS) }
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tiles, key = { it.title }) { entry ->
                HubTile(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    icon = entry.icon,
                    badge = entry.badge,
                    onClick = entry.onOpen
                )
            }
        }
    }
}

/** Title, one line of what is inside, and a badge when there is something to say. */
private data class HubEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String? = null,
    val onOpen: () -> Unit
)

@Composable
private fun HubTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = Brand.TextPrimary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = Modifier.fillMaxWidth().height(128.dp).alsoTappable(onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!badge.isNullOrBlank()) {
                    Text(text = badge, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * One area, opened from the hub. Back closes it rather than leaving the settings,
 * so the remote's own back key does the obvious thing at every depth.
 */
@Composable
private fun SettingsPageFrame(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    val t = LocalStrings.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Background)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                if (event.key == Key.Back || event.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 64.dp, vertical = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Brand.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            TvButton(onClick = onClose) { Text(t.back) }
        }
        Spacer(Modifier.height(24.dp))
        content()
    }
}

// --------------------------------------------------------------- the sections

@Composable
private fun SourceSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onEditSource: () -> Unit
) {
    val t = LocalStrings.current
    val source = state.source
    SettingsCard(title = t.sourceSection) {
        InfoLine(t.name, source?.name ?: "—")
        InfoLine(
            t.type,
            when (source?.type) {
                SourceType.XTREAM -> "Xtream Codes"
                SourceType.M3U -> t.m3uPlaylist
                null -> "—"
            }
        )
        InfoLine(t.server, source?.url.orEmpty().ifBlank { "—" })
        InfoLine(t.channels, state.channelCount.toString())
        InfoLine(t.lastLoaded, source?.lastSyncAt?.let { formatRelativeSync(it) } ?: "—")
        InfoLine(t.epgSection, if (state.hasGuide) t.available else t.noData)

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(onClick = viewModel::resyncPlaylist) { Text(t.reloadPlaylist) }
            TvButton(onClick = viewModel::refreshGuide) { Text(t.refreshEpg) }
            TvButton(onClick = onEditSource) { Text(t.editCredentials) }
        }
    }
}

@Composable
private fun PlaybackSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val t = LocalStrings.current
    SettingsCard(title = t.playbackSection) {
        ToggleRow(
            title = t.useHls,
            subtitle = t.useHlsHint,
            checked = state.preferHls,
            onToggle = { viewModel.setPreferHls(!state.preferHls) }
        )
        Spacer(Modifier.height(12.dp))
        StepperRow(
            title = t.buffer,
            value = "${state.bufferSeconds} s",
            subtitle = t.bufferHint,
            onDecrease = { viewModel.setBufferSeconds(state.bufferSeconds - 2) },
            onIncrease = { viewModel.setBufferSeconds(state.bufferSeconds + 2) }
        )
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = t.recovery.autoRecover,
            subtitle = t.recovery.autoRecoverHint,
            checked = state.autoRecover,
            onToggle = { viewModel.setAutoRecover(!state.autoRecover) }
        )
        Spacer(Modifier.height(12.dp))
        InfoLine(t.userAgent, state.userAgent)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(onClick = { viewModel.setUserAgent("VLC/3.0.20 LibVLC/3.0.20") }) {
                Text("VLC")
            }
            TvButton(onClick = { viewModel.setUserAgent("Lavf/60.16.100") }) { Text("FFmpeg") }
            TvButton(onClick = { viewModel.setUserAgent("okhttp/4.12.0") }) { Text("OkHttp") }
        }
    }
}

@Composable
private fun DisplaySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val t = LocalStrings.current
    SettingsCard(title = t.displaySection) {
        ToggleRow(
            title = t.clockWhilePlaying,
            subtitle = t.clockWhilePlayingHint,
            checked = state.showClock,
            onToggle = { viewModel.setShowClock(!state.showClock) }
        )
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = t.resumeLast,
            subtitle = t.resumeLastHint,
            checked = state.resumeLastChannel,
            onToggle = { viewModel.setResumeLastChannel(!state.resumeLastChannel) }
        )
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = t.previewWhileBrowsing,
            subtitle = t.previewWhileBrowsingHint,
            checked = state.previewEnabled,
            onToggle = { viewModel.setPreviewEnabled(!state.previewEnabled) }
        )
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = t.previewMutedTitle,
            subtitle = t.previewMutedHint,
            checked = state.previewMuted,
            onToggle = { viewModel.setPreviewMuted(!state.previewMuted) }
        )
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = t.sortAlphabetically,
            subtitle = t.sortAlphabeticallyHint,
            checked = state.channelSort == ChannelSort.NAME,
            onToggle = {
                viewModel.setChannelSort(
                    if (state.channelSort == ChannelSort.NAME) {
                        ChannelSort.NUMBER
                    } else {
                        ChannelSort.NAME
                    }
                )
            }
        )
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = t.startOnBoot,
            subtitle = t.startOnBootHint,
            checked = state.autoStartOnBoot,
            onToggle = { viewModel.setAutoStartOnBoot(!state.autoStartOnBoot) }
        )
    }
}

// ------------------------------------------------------------- the child lock

/**
 * The lock itself.
 *
 * Guarded by the code once one is set — including on the way *in*, because a
 * section that can switch itself off is worth no more than the door in front of it.
 */
@Composable
private fun ParentalSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    categories: List<Category>,
    lockedIds: Set<String>,
    unlocked: Boolean,
    onUnlocked: () -> Unit
) {
    val t = LocalStrings.current

    if (state.hasPin && !unlocked) {
        PinGate(
            onSubmit = { entered ->
                if (viewModel.pinMatches(entered)) {
                    onUnlocked()
                    null
                } else {
                    t.parental.wrongPin
                }
            }
        )
        return
    }

    SettingsCard(title = t.parental.title) {
        Text(
            t.parental.explain,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        ToggleRow(
            title = t.parental.enable,
            subtitle = t.parental.enableHint,
            checked = state.parentalEnabled,
            onToggle = { viewModel.setParentalEnabled(!state.parentalEnabled) }
        )
    }

    Spacer(Modifier.height(20.dp))

    SettingsCard(title = t.parental.pinTitle) {
        Text(
            t.parental.pinHint,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        InfoLine(
            t.parental.pinTitle,
            if (state.hasPin) t.parental.pinSet else t.parental.noPinYet
        )
        Spacer(Modifier.height(12.dp))

        var pin by remember { mutableStateOf("") }
        var repeat by remember { mutableStateOf("") }
        var problem by remember { mutableStateOf<String?>(null) }

        TvTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(4) },
            label = t.parental.newPin,
            kind = TvFieldKind.PASSWORD
        )
        Spacer(Modifier.height(10.dp))
        TvTextField(
            value = repeat,
            onValueChange = { repeat = it.filter(Char::isDigit).take(4) },
            label = t.parental.repeatPin,
            kind = TvFieldKind.PASSWORD
        )
        problem?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Brand.Danger)
        }
        Spacer(Modifier.height(12.dp))
        TvButton(
            onClick = {
                problem = viewModel.savePin(pin, repeat)
                if (problem == null) {
                    pin = ""
                    repeat = ""
                }
            }
        ) {
            Text(if (state.hasPin) t.parental.changePin else t.parental.setPin)
        }
    }

    Spacer(Modifier.height(20.dp))

    SettingsCard(title = t.parental.keywordsTitle) {
        Text(
            t.parental.keywordsHint,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        state.parentalKeywords.forEach { word ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                TvButton(onClick = { viewModel.removeKeyword(word) }) { Text("✕") }
            }
        }

        Spacer(Modifier.height(12.dp))
        var word by remember { mutableStateOf("") }
        TvTextField(
            value = word,
            onValueChange = { word = it },
            label = t.parental.newKeyword
        )
        Spacer(Modifier.height(10.dp))
        TvButton(
            onClick = {
                viewModel.addKeyword(word)
                word = ""
            }
        ) {
            Text(t.parental.addKeyword)
        }
    }

    Spacer(Modifier.height(20.dp))

    SettingsCard(title = t.parental.categoriesTitle) {
        Text(
            t.parental.categoriesHint,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        if (categories.isEmpty()) {
            Text(
                t.parental.noCategories,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextSecondary
            )
        } else {
            InfoLine(t.parental.categoriesTitle, t.parental.nBlocked(lockedIds.size))
            Spacer(Modifier.height(8.dp))
            categories.forEach { category ->
                val locked = category.id in lockedIds
                ChoiceRow(
                    title = category.name,
                    detail = if (locked) t.parental.locked else t.parental.free,
                    selected = locked,
                    onSelect = { viewModel.setCategoryLocked(category.id, !locked) }
                )
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

/** The code, asked for once, before anything in the lock can be seen or changed. */
@Composable
private fun PinGate(onSubmit: (String) -> String?) {
    val t = LocalStrings.current
    var entered by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }

    SettingsCard(title = t.parental.enterPin) {
        Text(
            t.parental.pinHint,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = entered,
            onValueChange = { entered = it.filter(Char::isDigit).take(4) },
            label = t.parental.pinTitle,
            kind = TvFieldKind.PASSWORD
        )
        problem?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Brand.Danger)
        }
        Spacer(Modifier.height(12.dp))
        TvButton(
            onClick = {
                problem = onSubmit(entered)
                entered = ""
            }
        ) {
            Text(t.parental.save)
        }
    }
}

// --------------------------------------------------------------------- the rest

@Composable
private fun GroupsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    startOptions: List<Category>,
    onManageGroups: () -> Unit
) {
    val t = LocalStrings.current
    SettingsCard(title = t.ownGroups) {
        Text(
            t.ownGroupsHint,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        InfoLine(t.createdCount, state.groupCount.toString())
        Spacer(Modifier.height(14.dp))
        TvButton(onClick = onManageGroups) { Text(t.manageGroups) }

        Spacer(Modifier.height(22.dp))
        Text(
            t.whatLiveOpens,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            t.whatLiveOpensHint,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        startOptions.forEachIndexed { index, option ->
            val optionId = option.id
            ChoiceRow(
                title = option.name,
                detail = when {
                    optionId == SettingsViewModel.OVERVIEW_OPTION_ID -> t.overviewDefault
                    option.channelCount > 0 -> t.nCategories(option.channelCount)
                    else -> ""
                },
                selected = (state.liveDirectCategoryId
                    ?: SettingsViewModel.OVERVIEW_OPTION_ID) == optionId,
                onSelect = { viewModel.setLiveDirect(optionId) }
            )
            if (index != startOptions.lastIndex) Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun OrderSection(
    categories: List<Category>,
    viewModel: SettingsViewModel
) {
    val t = LocalStrings.current
    SettingsCard(title = t.categoryOrder) {
        if (categories.isEmpty()) {
            Text(
                t.noCategoriesLoaded,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextSecondary
            )
        } else {
            Text(
                t.categoryOrderHint,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            categories.forEachIndexed { index, category ->
                ReorderRow(
                    position = index + 1,
                    title = category.name,
                    detail = t.nChannels(category.channelCount),
                    onUp = { viewModel.moveCategory(category.id, -1) },
                    onDown = { viewModel.moveCategory(category.id, +1) }
                )
                if (index != categories.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun UpdatesSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val t = LocalStrings.current
    SettingsCard(title = t.updates) {
        Text(
            t.updatesHint,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = state.updateUrl,
            onValueChange = viewModel::setUpdateUrl,
            label = t.updateAddress,
            placeholder = "https://…/karacast.json",
            kind = TvFieldKind.URL
        )
        Spacer(Modifier.height(12.dp))
        InfoLine(t.installedVersion, BuildConfig.VERSION_NAME)

        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = t.update.autoCheck,
            subtitle = t.update.autoCheckHint,
            checked = state.autoUpdateCheck,
            onToggle = { viewModel.setAutoUpdateCheck(!state.autoUpdateCheck) }
        )

        state.updateInfo?.let { info ->
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brand.Accent.copy(alpha = 0.15f))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        t.versionAvailable(info.versionName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Brand.Accent,
                        fontWeight = FontWeight.Bold
                    )
                    if (info.notes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.TextSecondary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        info.downloadUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            if (state.downloading) {
                Text(
                    text = if (state.downloadPercent >= 0) {
                        "${t.update.downloading}  ${t.update.percent(state.downloadPercent)}"
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
                    Box(
                        Modifier
                            .fillMaxWidth(
                                if (state.downloadPercent >= 0) {
                                    state.downloadPercent / 100f
                                } else {
                                    1f
                                }
                            )
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brand.Accent)
                    )
                }
            } else {
                val context = LocalContext.current
                TvButton(onClick = { viewModel.downloadAndInstall(context) }) {
                    Text(t.update.installNow)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    t.update.installHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        TvButton(onClick = viewModel::checkForUpdate) { Text(t.checkNow) }
    }
}

/**
 * What this installation's licence looks like.
 *
 * Reads the repository straight out of the container rather than going through the
 * settings ViewModel: the licence is not a setting. It is not changed from here, it
 * is only reported — and it has to be readable even when everything else on this
 * screen is in a bad way.
 */
@Composable
private fun LicenseSection() {
    val t = LocalStrings.current
    val context = LocalContext.current
    val licenses = remember { (context.applicationContext as IptvApp).container.licenses }
    val store = remember { (context.applicationContext as IptvApp).container.licenseStore }
    val state by licenses.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var address by remember { mutableStateOf(store.licenseUrl) }

    SettingsCard(title = t.license.section) {
        InfoLine(
            t.license.status,
            when (state.status) {
                LicenseStatus.TRIAL ->
                    "${t.license.statusTrial} · ${t.license.daysLeft(state.daysLeft)}"
                LicenseStatus.ACTIVE -> t.license.statusActive
                LicenseStatus.EXPIRED -> t.license.statusExpired
                LicenseStatus.BLOCKED -> t.license.statusBlocked
            }
        )
        InfoLine(t.license.deviceCode, DeviceIdentity.formatted(state.deviceCode))
        if (state.activatedAt > 0L) {
            InfoLine(t.license.activatedOn(formatRelativeSync(state.activatedAt)), "")
        }

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = address,
            onValueChange = {
                address = it
                store.licenseUrl = it
            },
            label = t.license.serverAddress,
            placeholder = "https://karacast.de/api",
            kind = TvFieldKind.URL
        )

        Spacer(Modifier.height(14.dp))
        TvButton(onClick = { scope.launch { licenses.sync() } }) {
            Text(if (state.checking) t.license.waiting else t.license.checkNow)
        }
    }

    // Only in the developer build. BuildConfig.DEBUG is a compile-time constant, so
    // in the release APK this whole block is removed by the compiler — a shipped app
    // has no button that hands itself a licence.
    if (BuildConfig.DEBUG) {
        Spacer(Modifier.height(20.dp))
        SettingsCard(title = t.license.testingTitle) {
            Text(
                t.license.testingHint,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextSecondary
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = licenses::debugResetTrial) { Text(t.license.resetTrial) }
                TvButton(onClick = licenses::debugExpireTrial) { Text(t.license.expireTrial) }
                TvButton(onClick = licenses::debugActivate) { Text(t.license.simulateActive) }
            }
        }
    }
}

/**
 * The panel page.
 *
 * Everything here is switched off until an address is typed. That is the whole
 * point of the page as it stands: it is scaffolding for something that does not
 * exist yet, and scaffolding that quietly phones home would be worse than none.
 */
@Composable
private fun PanelSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val t = LocalStrings.current

    SettingsCard(title = t.panel.title) {
        Text(
            t.panel.explain,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = state.panelUrl,
            onValueChange = viewModel::setPanelUrl,
            label = t.panel.address,
            placeholder = "https://karacast.de/api",
            kind = TvFieldKind.URL
        )

        // Said plainly rather than buried in a manual: over plain http the account
        // this page exists to fetch travels where anyone on the line can read it.
        if (state.panelUrl.startsWith("http://", ignoreCase = true)) {
            Spacer(Modifier.height(10.dp))
            Text(
                t.panel.insecure,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Danger
            )
        }

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = state.panelCode,
            onValueChange = viewModel::setPanelCode,
            label = t.panel.code
        )
        Spacer(Modifier.height(6.dp))
        Text(
            t.panel.codeHint,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        InfoLine(t.panel.deviceId, state.panelDeviceId)
        InfoLine(
            t.panel.lastSync,
            if (state.panelLastSyncAt > 0L) formatRelativeSync(state.panelLastSyncAt) else "—"
        )
        if (state.panelDeviceName.isNotBlank()) {
            InfoLine(t.panel.title, t.panel.connected(state.panelDeviceName))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            t.panel.deviceIdHint,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(onClick = viewModel::connectPanel) {
                Text(if (state.panelBusy) t.vod.loading else t.panel.connect)
            }
            state.panelSource?.let { source ->
                TvButton(onClick = viewModel::applyPanelSource) {
                    Text("${t.panel.applySource} · ${source.name}")
                }
            }
        }
    }
}

@Composable
private fun AccountSection(
    viewModel: SettingsViewModel,
    onDisconnected: () -> Unit
) {
    val t = LocalStrings.current
    SettingsCard(title = t.accountSection) {
        Text(
            t.accountHint,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        InfoLine(t.installedVersion, BuildConfig.VERSION_NAME)
        Spacer(Modifier.height(14.dp))
        TvButton(
            onClick = { viewModel.disconnect(onDisconnected) },
            colors = ButtonDefaults.colors(
                containerColor = Brand.Danger.copy(alpha = 0.18f),
                contentColor = Brand.Danger
            )
        ) {
            Text(t.disconnect)
        }
    }
}

@Composable
private fun ProblemsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val t = LocalStrings.current
    val crash = state.lastCrash
    SettingsCard(title = t.crash.title) {
        if (crash == null) {
            Text(
                t.noData,
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextSecondary
            )
            return@SettingsCard
        }
        Text(
            t.crash.explain,
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        InfoLine(t.crash.whenLabel, formatRelativeSync(crash.atMs))
        InfoLine(t.crash.what, crash.message)
        Spacer(Modifier.height(14.dp))
        TvButton(onClick = viewModel::clearCrashReport) { Text(t.crash.forget) }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brand.Surface)
            .padding(24.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Brand.TextPrimary
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.TextSecondary,
            modifier = Modifier.width(220.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.SurfaceHigh,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = Brand.TextPrimary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().alsoTappable(onToggle)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            TvSwitch(checked = checked)
        }
    }
}

/** One option of a single-choice list, marked with a filled dot when it is active. */
@Composable
private fun ChoiceRow(
    title: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Brand.SurfaceHigh else Brand.Surface,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = if (selected) Brand.TextPrimary else Brand.TextSecondary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().alsoTappable(onSelect)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Brand.Accent else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selected) "●" else "○",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Brand.Background else Brand.TextSecondary
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (detail.isNotEmpty()) {
                Text(text = detail, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: String,
    subtitle: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Brand.SurfaceHigh)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Brand.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
        }
        TvButton(onClick = onDecrease) { Text("−") }
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = Brand.TextPrimary,
            modifier = Modifier.width(56.dp)
        )
        Spacer(Modifier.width(12.dp))
        TvButton(onClick = onIncrease) { Text("+") }
    }
}

/** One line of the ordering list: position, name, and the two move buttons. */
@Composable
private fun ReorderRow(
    position: Int,
    title: String,
    detail: String,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Brand.SurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = position.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = Brand.TextSecondary,
            modifier = Modifier.width(34.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = Brand.TextSecondary
            )
        }
        TvButton(onClick = onUp) { Text("▲") }
        Spacer(Modifier.width(8.dp))
        TvButton(onClick = onDown) { Text("▼") }
    }
}

@Composable
private fun StatusBanner(message: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(14.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
