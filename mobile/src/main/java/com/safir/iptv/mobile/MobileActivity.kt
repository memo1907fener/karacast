package com.safir.iptv.mobile

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.safir.iptv.IptvApp
import com.safir.iptv.ui.i18n.AppLanguage
import com.safir.iptv.ui.i18n.AppLocale
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.i18n.stringsFor

import com.safir.iptv.util.PictureInPicture
import com.safir.iptv.util.SleepTimer
import com.safir.iptv.util.supportsPictureInPicture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Die eine Activity der Handy-App.
 *
 * Anders als am Fernseher bleiben Status- und Navigationsleiste sichtbar — auf
 * einem Handy sind sie Teil der Bedienung. Die App zeichnet bis unter sie
 * (edge-to-edge) und hält ihre Inhalte selbst frei; nur der Player blendet sie
 * im Vollbild aus und dreht ins Querformat (siehe [PlayerChrome]).
 */
class MobileActivity : ComponentActivity() {

    private val settings get() = (application as IptvApp).container.settings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = baseOrientation(this)
        setUpPictureInPicture()

        AppLocale.set(AppLanguage.fromTag(settings.language))

        setContent {
            val language by AppLocale.current.collectAsStateWithLifecycle()
            CompositionLocalProvider(
                LocalStrings provides stringsFor(language),
                LocalMobileStrings provides mobileStringsFor(language),
                LocalLayoutDirection provides
                    if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                KaracastMobileTheme {
                    Box(Modifier.fillMaxSize().screenBackground()) {
                        // Erst das Gerät (eine APK kann auf einem Fernseher landen),
                        // dann die Lizenz, dann die App.
                        TvGate {
                            MobileLicenseGate { MobileRoot() }
                        }
                        SleepWatcher(onExpired = { finishAndRemoveTask() })
                    }
                }
            }
        }
    }

    // --------------------------------------------------------- Bild-in-Bild

    private val canPip by lazy { supportsPictureInPicture(this) }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipParams(autoEnter: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(autoEnter)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()

    /** Wer die App mitten in einer Sendung verlässt, sieht sie in einer Ecke weiter. */
    private fun setUpPictureInPicture() {
        if (!canPip) return
        addOnPictureInPictureModeChangedListener { info ->
            PictureInPicture.setActive(info.isInPictureInPictureMode)
        }
        lifecycleScope.launch {
            PictureInPicture.playing.collect { playing ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { setPictureInPictureParams(pipParams(playing && settings.pictureInPicture)) }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (canPip &&
            Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S &&
            PictureInPicture.playing.value &&
            settings.pictureInPicture
        ) {
            runCatching { enterPictureInPictureMode(pipParams(autoEnter = false)) }
        }
    }
}

/** Der Sleep-Timer gilt für die ganze App, nicht für einen Bildschirm. */
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

/**
 * Handys hochkant — dafür ist die Oberfläche gebaut; quer geht nur der Player.
 * Tablets drehen frei.
 */
fun baseOrientation(context: Context): Int =
    if (context.resources.configuration.smallestScreenWidthDp in 1 until 600) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Alles, was der große Player am Fenster verstellt, und das Zurückstellen danach.
 *
 * Solange er offen ist, bleibt der Bildschirm an. Hochkant (wie bei YouTube)
 * bleiben die Leisten stehen und das Handy aufrecht; im Vollbild ([fullscreen])
 * dreht ein Handy ins Querformat und die Leisten verschwinden. Ein Tablet dreht
 * nie zwangsweise — es liegt ohnehin, wie man es hält.
 */
@Composable
fun PlayerChrome(fullscreen: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    val view = LocalView.current
    val phone = activity.resources.configuration.smallestScreenWidthDp in 1 until 600

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            // Zurück zur Grundlage, nicht zu „unbestimmt“: mit gesperrter
            // Drehung bliebe ein Handy sonst quer liegen, nachdem der Film vorbei ist.
            activity.requestedOrientation = baseOrientation(activity)
            WindowCompat.getInsetsController(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
    DisposableEffect(fullscreen) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (fullscreen) {
            if (phone) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = baseOrientation(activity)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
    if (phone) RotateToFullscreen(activity, fullscreen)
}
