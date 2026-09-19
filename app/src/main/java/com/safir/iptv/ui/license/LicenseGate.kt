package com.safir.iptv.ui.license

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.IptvApp
import com.safir.iptv.domain.model.LicenseStatus
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import kotlinx.coroutines.delay

/**
 * Stands in front of the whole app and decides whether it may be used.
 *
 * Placed around the navigation rather than around one screen, because "resume last
 * channel" can jump straight into the player and would otherwise walk past the
 * question entirely.
 *
 * What it does *not* do is nag. During the fortnight the app is simply the app; a
 * small note appears in the corner for a few seconds at start-up, and in the last
 * three days it stays a little longer. The wall only ever appears once the trial is
 * genuinely over.
 */
@Composable
fun LicenseGate(content: @Composable () -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val licenses = remember { (context.applicationContext as IptvApp).container.licenses }
    val state by licenses.state.collectAsStateWithLifecycle()

    var badgeVisible by remember { mutableStateOf(true) }

    // One quiet word with the panel per day, in the background. It can only ever
    // grant something — a licence bought on the telephone appears by itself — and a
    // server that is not there changes nothing at all.
    LaunchedEffect(Unit) {
        delay(4_000)
        licenses.sync()
    }

    LaunchedEffect(state.status, state.daysLeft) {
        badgeVisible = true
        delay(if (state.shouldWarn) 12_000 else 6_000)
        badgeVisible = false
    }

    Box(Modifier.fillMaxSize()) {
        when (state.status) {
            LicenseStatus.EXPIRED, LicenseStatus.BLOCKED -> ActivationScreen(
                blocked = state.status == LicenseStatus.BLOCKED,
                state = state,
                licenses = licenses
            )

            else -> {
                content()

                AnimatedVisibility(
                    visible = badgeVisible && state.status == LicenseStatus.TRIAL,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (state.daysLeft <= 1) {
                                t.license.lastDay
                            } else {
                                t.license.daysLeft(state.daysLeft)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            // Only the last three days are worth a colour. Before
                            // that it is information, not a warning.
                            color = if (state.shouldWarn) Brand.Accent else Brand.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
