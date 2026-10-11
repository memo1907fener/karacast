package com.safir.iptv.navigation

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safir.iptv.util.PictureInPicture
import android.net.Uri
import com.safir.iptv.ui.reminders.rememberReminderNotice
import com.safir.iptv.ui.reminders.ReminderNotice
import com.safir.iptv.IptvApp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safir.iptv.domain.model.ALL_CATEGORY_ID
import com.safir.iptv.domain.model.ContinueItem
import com.safir.iptv.ui.channels.ChannelsScreen
import com.safir.iptv.ui.dashboard.DashboardScreen
import com.safir.iptv.ui.guide.GuideScreen
import com.safir.iptv.ui.login.LoginScreen
import com.safir.iptv.ui.login.SetupMode
import com.safir.iptv.data.repository.PlaylistRepository
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.safir.iptv.ui.player.PlayerScreen
import com.safir.iptv.ui.settings.AboutScreen
import com.safir.iptv.ui.settings.BackupScreen
import com.safir.iptv.ui.settings.GroupChannelsScreen
import com.safir.iptv.ui.settings.GroupsScreen
import com.safir.iptv.ui.settings.SettingsScreen
import com.safir.iptv.ui.update.UpdateGate
import com.safir.iptv.ui.vod.MoviesScreen
import com.safir.iptv.ui.vod.SeriesDetailScreen
import com.safir.iptv.ui.vod.SeriesScreen
import com.safir.iptv.ui.vod.VodPlayback
import com.safir.iptv.ui.vod.VodPlayerScreen

object Routes {
    /** One screen, three entry points; see [SetupMode]. */
    const val SETUP_PATTERN = "setup?mode={mode}"
    const val SETUP = "setup?mode=FIRST"
    const val SETUP_EDIT = "setup?mode=EDIT"
    const val SETUP_ADD = "setup?mode=ADD"

    /** Taking over another box's settings before this one has any of its own. */
    const val TAKE_OVER = "takeover"
    const val DASHBOARD = "dashboard"

    /** One screen, two entry points: browsing categories and jumping into search. */
    const val CHANNELS_PATTERN = "channels?search={search}"
    const val CHANNELS = "channels?search=false"
    const val CHANNELS_SEARCH = "channels?search=true"
    const val GUIDE = "guide"
    const val SETTINGS = "settings"
    const val GROUPS = "groups"
    const val ABOUT = "about"
    const val GROUP_CHANNELS = "groups/{groupId}/channels"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val SERIES_DETAIL = "series/{seriesId}"

    /** The film itself travels in memory, not in the route — see [VodPlayback]. */
    const val VOD_PLAYER = "vodplayer"
    const val PLAYER = "player/{channelId}/{categoryId}"
    const val ARCHIVE = "archive/{channelId}/{start}/{duration}"

    fun groupChannels(groupId: String): String = "groups/${Uri.encode(groupId)}/channels"

    fun seriesDetail(seriesId: Int): String = "series/$seriesId"

    fun player(channelId: String, categoryId: String): String =
        "player/${Uri.encode(channelId)}/${Uri.encode(categoryId)}"

    fun archive(channelId: String, startMs: Long, durationMinutes: Int): String =
        "archive/${Uri.encode(channelId)}/$startMs/$durationMinutes"
}

/**
 * Carrying on from the "keep watching" row: everything the player needs was
 * written down when the viewer walked away, so nothing has to be looked up in a
 * catalogue that may not even be loaded.
 */
private fun ContinueItem.toRequest() = VodPlayback.Request(
    title = title,
    subtitle = subtitle,
    url = streamUrl,
    resumeKey = key,
    startMs = positionMs,
    posterUrl = posterUrl,
    isEpisode = isEpisode,
    seriesId = seriesId
)

@Composable
fun AppNavigation(
    startDestination: String,
    /** Player route to open once at start — "resume last channel" in settings. */
    resumeRoute: String? = null,
    navController: NavHostController = rememberNavController()
) {
    // Guarded so it fires once: the channel list stays underneath in the back stack,
    // so Back out of the player lands there instead of quitting the app.
    var resumeHandled by rememberSaveable { mutableStateOf(false) }

    val container = (LocalContext.current.applicationContext as IptvApp).container
    val inPip by PictureInPicture.active.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val notice = rememberReminderNotice(container.reminders) { reminder ->
        scope.launch {
            // A reminder set while another access was on screen takes the viewer
            // there: the channel only exists in its own access's lists.
            PlaylistRepository.sourceIdOf(reminder.channelId)?.let { container.sources.switchTo(it) }
            // From one channel to the next rather than a player on top of a player,
            // so Back still leads to where the viewer was before.
            if (navController.currentDestination?.route == Routes.PLAYER) {
                navController.popBackStack()
            }
            navController.navigate(
                Routes.player(reminder.channelId, container.settings.homeCategoryId)
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { notice.handleKey(it) }
    ) {
        NavHost(navController = navController, startDestination = startDestination) {

            composable(
                route = Routes.SETUP_PATTERN,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = SetupMode.FIRST.name
                    }
                )
            ) { entry ->
                val mode = runCatching {
                    SetupMode.valueOf(entry.arguments?.getString("mode").orEmpty())
                }.getOrDefault(SetupMode.FIRST)
                LoginScreen(
                    onConnected = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    mode = mode,
                    onCancel = if (mode == SetupMode.FIRST) {
                        null
                    } else {
                        { navController.popBackStack() }
                    },
                    onTakeOver = if (mode == SetupMode.FIRST) {
                        { navController.navigate(Routes.TAKE_OVER) }
                    } else {
                        null
                    }
                )
            }

            composable(Routes.TAKE_OVER) {
                BackupScreen(
                    onBack = { navController.popBackStack() },
                    onRestored = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.DASHBOARD) {
                // "Resume last channel" opens the player, but pushes the channel list
                // underneath it first so Back walks out the way the user came in.
                LaunchedEffect(Unit) {
                    if (!resumeHandled && resumeRoute != null) {
                        resumeHandled = true
                        navController.navigate(Routes.CHANNELS)
                        navController.navigate(resumeRoute)
                    }
                }
                // The one screen the app always comes back to, so the one place worth
                // mentioning a new version — once a day, and never while watching.
                UpdateGate {
                    DashboardScreen(
                        onOpenLive = { navController.navigate(Routes.CHANNELS) },
                        onOpenSeries = { navController.navigate(Routes.SERIES) },
                        onOpenMovies = { navController.navigate(Routes.MOVIES) },
                        onOpenSearch = { navController.navigate(Routes.CHANNELS_SEARCH) },
                        onOpenGuide = { navController.navigate(Routes.GUIDE) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onAddSource = { navController.navigate(Routes.SETUP_ADD) }
                    )
                }
            }

            composable(
                route = Routes.CHANNELS_PATTERN,
                arguments = listOf(
                    navArgument("search") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                ChannelsScreen(
                    onPlay = { channel, categoryId ->
                        navController.navigate(Routes.player(channel.id, categoryId))
                    },
                    onOpenGuide = { navController.navigate(Routes.GUIDE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onBackToDashboard = {
                        // Reset rather than pop: the dashboard is the home screen, so
                        // it should always be the bottom of the stack afterwards.
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    startInSearch = entry.arguments?.getBoolean("search") ?: false
                )
            }

            composable(Routes.GUIDE) {
                GuideScreen(
                    onPlayLive = { channel, categoryId ->
                        navController.navigate(Routes.player(channel.id, categoryId))
                    },
                    onPlayArchive = { channel, program ->
                        val minutes = (program.durationMs / 60_000L).toInt().coerceAtLeast(1)
                        navController.navigate(
                            Routes.archive(channel.id, program.startMs, minutes)
                        )
                    }
                )
            }

            composable(
                route = Routes.ARCHIVE,
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType },
                    navArgument("start") { type = NavType.LongType },
                    navArgument("duration") { type = NavType.IntType }
                )
            ) { entry ->
                PlayerScreen(
                    channelId = entry.arguments?.getString("channelId").orEmpty(),
                    categoryId = ALL_CATEGORY_ID,
                    onExit = { navController.popBackStack() },
                    archiveStartMs = entry.arguments?.getLong("start") ?: 0L,
                    archiveDurationMin = entry.arguments?.getInt("duration") ?: 0
                )
            }

            composable(Routes.MOVIES) {
                MoviesScreen(
                    onPlay = { movie, startMs ->
                        // There is no "next film", so nothing must be left over from
                        // an episode watched earlier in the evening.
                        VodPlayback.queue = emptyList()
                        VodPlayback.pending = VodPlayback.Request(
                            title = movie.name,
                            subtitle = "",
                            url = movie.streamUrl,
                            resumeKey = movie.resumeKey,
                            startMs = startMs,
                            posterUrl = movie.posterUrl
                        )
                        navController.navigate(Routes.VOD_PLAYER)
                    },
                    onResume = { item ->
                        // Carried on from the "keep watching" row: the season it came
                        // from was never loaded, so there is nothing to queue behind it.
                        VodPlayback.queue = emptyList()
                        VodPlayback.pending = item.toRequest()
                        navController.navigate(Routes.VOD_PLAYER)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SERIES) {
                SeriesScreen(
                    onOpenSeries = { navController.navigate(Routes.seriesDetail(it)) },
                    onResume = { item ->
                        // Carried on from the "keep watching" row: the season it came
                        // from was never loaded, so there is nothing to queue behind it.
                        VodPlayback.queue = emptyList()
                        VodPlayback.pending = item.toRequest()
                        navController.navigate(Routes.VOD_PLAYER)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.SERIES_DETAIL,
                arguments = listOf(navArgument("seriesId") { type = NavType.IntType })
            ) { entry ->
                val seriesId = entry.arguments?.getInt("seriesId") ?: 0
                SeriesDetailScreen(
                    seriesId = seriesId,
                    onPlay = { seriesName, episode, startMs ->
                        // The queue itself was filled in by the screen, which is the
                        // only place that has the whole season in hand.
                        VodPlayback.pending = VodPlayback.episodeRequest(
                            seriesName = seriesName,
                            seriesId = seriesId,
                            episode = episode,
                            startMs = startMs
                        )
                        navController.navigate(Routes.VOD_PLAYER)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.VOD_PLAYER) {
                VodPlayerScreen(onExit = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onDisconnected = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onEditSource = { navController.navigate(Routes.SETUP_EDIT) },
                    onAddSource = { navController.navigate(Routes.SETUP_ADD) },
                    onSourceSwitched = {
                        // Back to the start, where everything is drawn afresh from the
                        // access that is now on screen.
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onManageGroups = { navController.navigate(Routes.GROUPS) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.GROUPS) {
                GroupsScreen(
                    onBack = { navController.popBackStack() },
                    onEditChannels = { groupId ->
                        navController.navigate(Routes.groupChannels(groupId))
                    }
                )
            }

            composable(
                route = Routes.GROUP_CHANNELS,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { entry ->
                GroupChannelsScreen(
                    groupId = entry.arguments?.getString("groupId").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType },
                    navArgument("categoryId") { type = NavType.StringType }
                )
            ) { entry ->
                PlayerScreen(
                    channelId = entry.arguments?.getString("channelId").orEmpty(),
                    categoryId = entry.arguments?.getString("categoryId") ?: ALL_CATEGORY_ID,
                    onExit = { navController.popBackStack() }
                )
            }
        }

        // Below the clock the player keeps in that corner, not on top of it.
        if (!inPip) ReminderNotice(
            notice,
            Modifier.align(Alignment.TopEnd).padding(top = 96.dp, end = 24.dp)
        )
    }
}
