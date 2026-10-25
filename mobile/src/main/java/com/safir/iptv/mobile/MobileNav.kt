package com.safir.iptv.mobile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safir.iptv.IptvApp
import com.safir.iptv.data.repository.PlaylistRepository
import com.safir.iptv.domain.model.ALL_CATEGORY_ID
import com.safir.iptv.domain.model.Channel
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.domain.model.Program
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.login.SetupMode
import com.safir.iptv.ui.reminders.rememberReminderNotice

import com.safir.iptv.ui.vod.VodPlayback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object MobileRoutes {
    const val SETUP_PATTERN = "setup?mode={mode}"
    fun setup(mode: SetupMode) = "setup?mode=${mode.name}"
    const val MAIN = "main"
    const val SERIES = "series/{seriesId}"
    const val SETTINGS = "settings"
    const val UNLOCK = "unlock"

    fun series(id: Int) = "series/$id"
}

/** Ein angefangener Film oder eine Folge, wieder als Wiedergabe-Auftrag. */
fun ContinueItem.toRequest() = VodPlayback.Request(
    title = title,
    subtitle = subtitle,
    url = streamUrl,
    resumeKey = key,
    startMs = positionMs,
    posterUrl = posterUrl,
    isEpisode = isEpisode,
    seriesId = seriesId
)

/** Wo die App startet: ohne Zugang beim Einrichten, sonst auf der Startseite. */
@Composable
fun MobileRoot() {
    val container = (LocalContext.current.applicationContext as IptvApp).container
    var start by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val source = container.playlistRepository.ensureActive()
        val channels = container.playlistRepository.channelCount()
        start = if (source == null || channels == 0) MobileRoutes.setup(SetupMode.FIRST) else MobileRoutes.MAIN
    }

    val first = start
    if (first == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        MobileNavHost(first)
    }
}

@Composable
private fun MobileNavHost(startDestination: String) {
    val nav = rememberNavController()
    val container = (LocalContext.current.applicationContext as IptvApp).container
    val scope = rememberCoroutineScope()

    // Erinnerungen aus dem Programm: kurz vor Beginn ein Hinweis oben.
    val notice = rememberReminderNotice(container.reminders) { reminder ->
        scope.launch {
            PlaylistRepository.sourceIdOf(reminder.channelId)?.let { container.sources.switchTo(it) }
            PlayerLayer.playLive(reminder.channelId, container.settings.homeCategoryId)
        }
    }

    fun openPlayer(channel: Channel, categoryId: String) {
        PlayerLayer.playLive(channel, categoryId, expand = true)
    }

    fun openArchive(channel: Channel, program: Program) {
        PlayerLayer.playArchive(channel, program)
    }

    // Das kleine Fenster sitzt über der Reiterleiste, wenn es sie gibt, sonst
    // am unteren Rand.
    val backStack by nav.currentBackStackEntryAsState()
    val onMain = backStack?.destination?.route == MobileRoutes.MAIN
    val navBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val miniInset = navBars + if (onMain) 81.dp else 0.dp

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = startDestination) {
            composable(
                route = MobileRoutes.SETUP_PATTERN,
                arguments = listOf(navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = SetupMode.FIRST.name
                })
            ) { entry ->
                val mode = runCatching {
                    SetupMode.valueOf(entry.arguments?.getString("mode").orEmpty())
                }.getOrDefault(SetupMode.FIRST)
                MobileSetupScreen(
                    mode = mode,
                    onConnected = {
                        PlayerLayer.close()
                        nav.navigate(MobileRoutes.MAIN) { popUpTo(0) { inclusive = true } }
                    },
                    onCancel = if (mode == SetupMode.FIRST) null else { { nav.popBackStack() } }
                )
            }

            composable(MobileRoutes.MAIN) {
                MainTabs(
                    onOpenPlayer = { c, cat -> openPlayer(c, cat) },
                    onOpenArchive = { c, p -> openArchive(c, p) },
                    onPlayVod = { PlayerLayer.playVod() },
                    onOpenSeries = { nav.navigate(MobileRoutes.series(it)) },
                    onOpenSettings = { nav.navigate(MobileRoutes.SETTINGS) }
                )
            }

            composable(
                route = MobileRoutes.SERIES,
                arguments = listOf(navArgument("seriesId") { type = NavType.IntType })
            ) { entry ->
                MobileSeriesDetail(
                    seriesId = entry.arguments?.getInt("seriesId") ?: 0,
                    onBack = { nav.popBackStack() },
                    onPlay = { PlayerLayer.playVod() }
                )
            }

            composable(MobileRoutes.SETTINGS) {
                MobileSettingsScreen(
                    onBack = { nav.popBackStack() },
                    onEditSource = { nav.navigate(MobileRoutes.setup(SetupMode.EDIT)) },
                    onAddSource = { nav.navigate(MobileRoutes.setup(SetupMode.ADD)) },
                    onSourceSwitched = {
                        PlayerLayer.close()
                        nav.navigate(MobileRoutes.MAIN) { popUpTo(0) { inclusive = true } }
                    },
                    onDisconnected = {
                        PlayerLayer.close()
                        nav.navigate(MobileRoutes.setup(SetupMode.FIRST)) { popUpTo(0) { inclusive = true } }
                    },
                    onOpenUnlock = { nav.navigate(MobileRoutes.UNLOCK) }
                )
            }

            composable(MobileRoutes.UNLOCK) {
                val licenses = remember { container.licenses }
                val state by licenses.state.collectAsStateWithLifecycle()
                UnlockScreen(licenses = licenses, state = state, onClose = { nav.popBackStack() })
            }
        }

        // Der Player über allen Seiten: groß oder als kleines Fenster.
        PlayerLayerHost(bottomInset = miniInset)

        // Die Erinnerung, oben über allem.
        notice.current?.let { reminder ->
            val t = LocalStrings.current.reminders
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Kc.SurfaceHigh,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.soon, fontSize = 12.sp, color = Kc.AccentSoft, fontWeight = FontWeight.Bold)
                        Text(reminder.title, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            t.startsAt(clockOf(reminder.startMs), reminder.channelName),
                            fontSize = 13.sp,
                            color = Kc.TextSecondary,
                            maxLines = 1
                        )
                    }
                    TextButton(onClick = { notice.close() }) { Text(t.dismiss, color = Kc.TextSecondary) }
                    TextButton(onClick = { notice.switch() }) { Text(t.switchNow, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

enum class MainTab { START, LIVE, MOVIES, SERIES, GUIDE }

/**
 * Die fünf Reiter mit der Leiste unten — und darüber, wenn etwas läuft, der
 * Mini-Player. Ein Tipp auf einen Kanal spielt ihn dort; ein zweiter Tipp auf
 * denselben Kanal (oder auf den Mini-Player) öffnet das Vollbild.
 */
@Composable
private fun MainTabs(
    onOpenPlayer: (Channel, String) -> Unit,
    onOpenArchive: (Channel, Program) -> Unit,
    onPlayVod: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val t = LocalStrings.current
    var tab by rememberSaveable { mutableStateOf(MainTab.START) }
    var liveSearch by rememberSaveable { mutableStateOf(false) }
    val playingId by PlayerLayer.liveChannelId.collectAsStateWithLifecycle()
    val playerItem by PlayerLayer.item.collectAsStateWithLifecycle()
    val playerExpanded by PlayerLayer.expanded.collectAsStateWithLifecycle()
    // Platz für das kleine Fenster, damit es nichts von den Listen verdeckt.
    val miniShown = playerItem != null && !playerExpanded
    val movies: com.safir.iptv.ui.vod.MoviesViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = com.safir.iptv.ui.AppViewModelFactory)

    // Zurück führt erst zum Start, dann aus der App.
    BackHandler(enabled = tab != MainTab.START) { tab = MainTab.START }

    // Erster Tipp: der Kanal läuft klein unten. Zweiter Tipp: groß.
    fun tapChannel(channel: Channel, categoryId: String) {
        if (playingId == channel.id) {
            PlayerLayer.expand()
        } else {
            PlayerLayer.playLive(channel, categoryId, expand = false)
        }
    }

    Scaffold(
        modifier = Modifier.screenBackground(),
        containerColor = Color.Transparent,
        bottomBar = {
            Column {
                if (miniShown) Spacer(Modifier.height(PlayerLayer.MiniHeight))
                HorizontalDivider(color = Kc.Border.copy(alpha = 0.6f), thickness = 1.dp)
                NavigationBar(containerColor = Kc.NavBar, tonalElevation = 0.dp) {
                    MainTab.entries.forEach { entry ->
                        val (label, icon) = entry.labelAndIcon(t)
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = {
                                if (entry != MainTab.LIVE) liveSearch = false
                                tab = entry
                            },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Kc.Accent,
                                selectedTextColor = Kc.Accent,
                                indicatorColor = Kc.Accent.copy(alpha = 0.18f),
                                unselectedIconColor = Kc.TextSecondary,
                                unselectedTextColor = Kc.TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                MainTab.START -> MobileStartScreen(
                    playingId = playingId,
                    onTapChannel = { c, cat -> tapChannel(c, cat) },
                    onPlayVod = onPlayVod,
                    onOpenTab = { tab = it },
                    onOpenMovie = { movie ->
                        // Dasselbe ViewModel wie im Reiter Filme: dort geht das
                        // Blatt zum Film gleich auf.
                        movies.open(movie)
                        tab = MainTab.MOVIES
                    },
                    onOpenSeries = onOpenSeries,
                    onSearch = {
                        liveSearch = true
                        tab = MainTab.LIVE
                    },
                    onOpenSettings = onOpenSettings
                )

                MainTab.LIVE -> MobileLiveScreen(
                    playingId = playingId,
                    startInSearch = liveSearch,
                    onSearchHandled = { liveSearch = false },
                    onTapChannel = { c, cat -> tapChannel(c, cat) }
                )

                MainTab.MOVIES -> MobileMoviesScreen(onPlayVod = onPlayVod)

                MainTab.SERIES -> MobileSeriesScreen(
                    onOpenSeries = onOpenSeries,
                    onPlayVod = onPlayVod
                )

                MainTab.GUIDE -> MobileGuideScreen(
                    onPlayLive = onOpenPlayer,
                    onPlayArchive = onOpenArchive
                )
            }
        }
    }

    MobileUpdatePrompt()
}

private fun MainTab.labelAndIcon(t: com.safir.iptv.ui.i18n.Strings): Pair<String, ImageVector> = when (this) {
    MainTab.START -> t.home to Icons.Filled.Home
    MainTab.LIVE -> t.liveTv to Icons.Filled.LiveTv
    MainTab.MOVIES -> t.movies to Icons.Filled.Movie
    MainTab.SERIES -> t.series to Icons.Filled.VideoLibrary
    MainTab.GUIDE -> t.guide to Icons.Filled.DateRange
}
