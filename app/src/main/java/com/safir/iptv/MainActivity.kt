package com.safir.iptv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safir.iptv.navigation.AppNavigation
import com.safir.iptv.navigation.Routes
import com.safir.iptv.ui.components.FullScreenLoading
import com.safir.iptv.ui.i18n.AppLanguage
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.i18n.stringsFor
import com.safir.iptv.ui.license.LicenseGate
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.ui.theme.SafirTheme
import com.safir.iptv.util.SleepTimer
import kotlinx.coroutines.delay

/** Where the app opens, decided once from what is already stored on the device. */
private data class Entry(
    val startDestination: String,
    val resumeRoute: String? = null
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A television must never dim, blank or drop into standby while its player
        // is on screen — not during a match, not while someone reads the programme
        // guide. Set on the window rather than on single screens, so it covers the
        // whole app; Android clears it by itself once the app leaves the foreground.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        AppLocale.set(AppLanguage.fromTag((application as IptvApp).container.settings.language))

        setContent {
            val language by AppLocale.current.collectAsStateWithLifecycle()
            CompositionLocalProvider(
                LocalStrings provides stringsFor(language),
                // Arabic reads the other way round, and Compose mirrors every Row,
                // padding and alignment off this one value.
                LocalLayoutDirection provides
                    if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                SafirTheme {
                    Box(Modifier.fillMaxSize().background(Brand.Background)) {
                        // Around everything, not around one screen: "resume last
                        // channel" opens the player directly and would otherwise
                        // walk straight past the question.
                        LicenseGate { Root() }
                        SleepWatcher(onExpired = { finishAndRemoveTask() })
                    }
                }
            }
        }
    }

    /**
     * Watches the sleep timer for the whole app rather than for one screen: whoever
     * set it may well have wandered back to the channel list before dozing off.
     */
    @Composable
    private fun SleepWatcher(onExpired: () -> Unit) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(5_000L)
                if (SleepTimer.hasExpired()) {
                    SleepTimer.cancel()
                    onExpired()
                    return@LaunchedEffect
                }
            }
        }
    }

    @Composable
    private fun Root() {
        val container = (application as IptvApp).container
        var entry by remember { mutableStateOf<Entry?>(null) }

        LaunchedEffect(Unit) {
            // A source with at least one channel means the user is already set up.
            val source = container.playlistRepository.currentSource()
            val channels = container.playlistRepository.channelCount()
            val settings = container.settings

            entry = if (source == null || channels == 0) {
                Entry(Routes.SETUP)
            } else {
                val lastChannel = settings.lastChannelId
                val resume = if (settings.resumeLastChannel && !lastChannel.isNullOrBlank()) {
                    Routes.player(lastChannel, settings.homeCategoryId)
                } else {
                    null
                }
                Entry(Routes.DASHBOARD, resume)
            }
        }

        val current = entry
        if (current == null) {
            FullScreenLoading(message = LocalStrings.current.startingUp)
        } else {
            AppNavigation(
                startDestination = current.startDestination,
                resumeRoute = current.resumeRoute
            )
        }
    }
}
