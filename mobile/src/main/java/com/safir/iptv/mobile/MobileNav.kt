package com.safir.iptv.mobile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
    const val PLAYER = "player/{channelId}/{categoryId}"
    const val ARCHIVE = "archive/{channelId}/{start}/{duration}"
    const val VOD = "vod"
    const val SERIES = "series/{seriesId}"
    const val SETTINGS = "settings"
    const val UNLOCK = "unlock"

    fun player(channelId: String, categoryId: String) =
        "player/${Uri.encode(channelId)}/${Uri.encode(categoryId)}"

    fun archive(channelId: String, startMs: Long, minutes: Int) =
        "archive/${Uri.encode(channelId)}/$startMs/$minutes"

    fun series(id: Int) = "series/$id"
}

/**
 * Was im Mini-Player läuft. Lebt außerhalb der Bildschirme, weil er über allen
 * Reitern liegt und nach dem Vollbild weiterlaufen soll.
 */
object MiniPlayback {
    data class Item(val channel: Channel, val categoryId: String)

    val current = MutableStateFlow<Item?>(null)

    fun show(channel: Channel, categoryId: String) {
        current.value = Item(channel, categoryId)
    }

    fun close() {
        current.value = null
    }
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
            if (nav.currentDestination?.route == MobileRoutes.PLAYER) nav.popBackStack()
            nav.navigate(MobileRoutes.player(reminder.channelId, container.settings.homeCategoryId))
        }
    }

    fun openPlayer(channel: Channel, categoryId: String) {
        // Das Vollbild übernimmt den Stream: der Mini-Player gibt seinen vorher
        // ab — viele Anbieter erlauben nur eine Verbindung gleichzeitig — und
        // kommt erst wieder, wenn das Vollbild ganz weg ist (siehe PLAYER).
        MiniPlayback.close()
        nav.navigate(MobileRoutes.player(channel.id, categoryId))
    }

    fun openArchive(channel: Channel, program: Program) {
        val minutes = (program.durationMs / 60_000L).toInt().coerceAtLeast(1)
        nav.navigate(MobileRoutes.archive(channel.id, program.startMs, minutes))
    }

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
                        MiniPlayback.close()
                        nav.navigate(MobileRoutes.MAIN) { popUpTo(0) { inclusive = true } }
                    },
                    onCancel = if (mode == SetupMode.FIRST) null else { { nav.popBackStack() } }
                )
            }

            composable(MobileRoutes.MAIN) {
                MainTabs(
                    onOpenPlayer = { c, cat -> openPlayer(c, cat) },
                    onOpenArchive = { c, p -> openArchive(c, p) },
                    onPlayVod = { nav.navigate(MobileRoutes.VOD) },
                    onOpenSeries = { nav.navigate(MobileRoutes.series(it)) },
                    onOpenSettings = { nav.navigate(MobileRoutes.SETTINGS) }
                )
            }

            composable(
                route = MobileRoutes.PLAYER,
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType },
                    navArgument("categoryId") { type = NavType.StringType }
                )
            ) { entry ->
                val categoryId = entry.arguments?.getString("categoryId") ?: ALL_CATEGORY_ID
                var lastPlayed by remember { mutableStateOf<Channel?>(null) }
                // Vor dem Player angemeldet, also nach ihm abgebaut: erst wenn sein
                // Stream freigegeben ist, spielt der Mini-Player weiter.
                DisposableEffect(Unit) {
                    onDispose { lastPlayed?.let { MiniPlayback.show(it, categoryId) } }
                }
                MobileLivePlayer(
                    channelId = entry.arguments?.getString("channelId").orEmpty(),
                    categoryId = categoryId,
                    onExit = { last ->
                        lastPlayed = last
                        nav.popBackStack()
                    }
                )
            }

            composable(
                route = MobileRoutes.ARCHIVE,
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType },
                    navArgument("start") { type = NavType.LongType },
                    navArgument("duration") { type = NavType.IntType }
                )
            ) { entry ->
                MobileLivePlayer(
                    channelId = entry.arguments?.getString("channelId").orEmpty(),
                    categoryId = ALL_CATEGORY_ID,
                    archiveStartMs = entry.arguments?.getLong("start") ?: 0L,
                    archiveDurationMin = entry.arguments?.getInt("duration") ?: 0,
                    onExit = { nav.popBackStack() }
                )
            }

            composable(MobileRoutes.VOD) {
                MobileVodPlayer(onExit = { nav.popBackStack() })
            }

            composable(
                route = MobileRoutes.SERIES,
                arguments = listOf(navArgument("seriesId") { type = NavType.IntType })
            ) { entry ->
                MobileSeriesDetail(
                    seriesId = entry.arguments?.getInt("seriesId") ?: 0,
                    onBack = { nav.popBackStack() },
                    onPlay = { nav.navigate(MobileRoutes.VOD) }
                )
            }

            composable(MobileRoutes.SETTINGS) {
                MobileSettingsScreen(
                    onBack = { nav.popBackStack() },
                    onEditSource = { nav.navigate(MobileRoutes.setup(SetupMode.EDIT)) },
                    onAddSource = { nav.navigate(MobileRoutes.setup(SetupMode.ADD)) },
                    onSourceSwitched = {
                        MiniPlayback.close()
                        nav.navigate(MobileRoutes.MAIN) { popUpTo(0) { inclusive = true } }
                    },
                    onDisconnected = {
                        MiniPlayback.close()
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

        // Die Erinnerung, oben über allem — aber nicht über einem laufenden Film.
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
    val mini by MiniPlayback.current.collectAsStateWithLifecycle()
    val movies: com.safir.iptv.ui.vod.MoviesViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = com.safir.iptv.ui.AppViewModelFactory)

    // Zurück führt erst zum Start, dann aus der App.
    BackHandler(enabled = tab != MainTab.START) { tab = MainTab.START }

    fun tapChannel(channel: Channel, categoryId: String) {
        if (mini?.channel?.id == channel.id) {
            onOpenPlayer(channel, categoryId)
        } else {
            MiniPlayback.show(channel, categoryId)
        }
    }

    Scaffold(
        modifier = Modifier.screenBackground(),
        containerColor = Color.Transparent,
        bottomBar = {
            Column {
                mini?.let { item ->
                    MiniPlayer(
                        item = item,
                        onExpand = { onOpenPlayer(item.channel, item.categoryId) },
                        onClose = { MiniPlayback.close() }
                    )
                }
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
                    playingId = mini?.channel?.id,
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
                    playingId = mini?.channel?.id,
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
