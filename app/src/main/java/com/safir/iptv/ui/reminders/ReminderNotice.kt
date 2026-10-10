package com.safir.iptv.ui.reminders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.data.prefs.Reminder
import com.safir.iptv.data.prefs.ReminderStore
import com.safir.iptv.ui.components.TvButton
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import com.safir.iptv.util.asClock
import kotlinx.coroutines.delay

/** How early the notice comes: enough to finish a sentence, not to forget again. */
private const val LEAD_MS = 2 * 60 * 1000L

/** A notice nobody answers goes away by itself; the reminder is then spent. */
private const val SHOW_FOR_MS = 90 * 1000L

/**
 * Watches the reminders and, when one is due, shows a small notice in the corner.
 *
 * Deliberately not a dialog and deliberately not focusable: a dialog would take
 * the focus away from whatever is on screen, and on a television a focus that
 * does not come back after the notice closes is a remote that has stopped
 * working. Instead the notice is answered through [handleKey], which the caller
 * puts in front of everything else: OK switches, Back closes, every other key goes
 * on to the screen underneath as if the notice were not there.
 */
class ReminderNoticeState(
    private val store: ReminderStore,
    private val onSwitch: (Reminder) -> Unit
) {
    var current by mutableStateOf<Reminder?>(null)
        internal set

    internal var shownAt by mutableLongStateOf(0L)

    fun switch() {
        val reminder = current ?: return
        close()
        onSwitch(reminder)
    }

    fun close() {
        current?.let(store::remove)
        current = null
    }

    /** @return true when the key belonged to the notice. */
    fun handleKey(event: KeyEvent): Boolean {
        if (current == null) return false
        val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter
        val isBack = event.key == Key.Back || event.key == Key.Escape
        if (!isOk && !isBack) return false
        // Both halves of the press are taken: the lists underneath act on the
        // key-down of OK, the player on the key-up — either would otherwise
        // react to the same press as well.
        if (event.type == KeyEventType.KeyUp) {
            if (isOk) switch() else close()
        }
        return true
    }
}

@Composable
fun rememberReminderNotice(
    store: ReminderStore,
    onSwitch: (Reminder) -> Unit
): ReminderNoticeState {
    val state = remember(store) { ReminderNoticeState(store, onSwitch) }
    val reminders by store.reminders.collectAsStateWithLifecycle()

    // Once every few seconds is plenty: the notice is two minutes early anyway.
    LaunchedEffect(state, reminders) {
        while (true) {
            val now = System.currentTimeMillis()
            if (state.current == null) {
                reminders.firstOrNull { now >= it.startMs - LEAD_MS && now < it.endMs }?.let {
                    state.current = it
                    state.shownAt = now
                }
            } else if (now - state.shownAt > SHOW_FOR_MS) {
                state.close()
            }
            delay(5_000)
        }
    }
    return state
}

@Composable
fun ReminderNotice(state: ReminderNoticeState, modifier: Modifier = Modifier) {
    val t = LocalStrings.current.reminders
    // Kept while the notice fades out, so the text does not vanish before the box.
    var shown by remember { mutableStateOf<Reminder?>(null) }
    state.current?.let { shown = it }

    AnimatedVisibility(
        visible = state.current != null,
        enter = fadeIn() + slideInHorizontally { it / 3 },
        exit = fadeOut(),
        modifier = modifier
    ) {
        val reminder = shown ?: return@AnimatedVisibility
        val running = System.currentTimeMillis() >= reminder.startMs
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.88f))
                .border(2.dp, Brand.Accent, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Brand.Accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = t.soon,
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.Accent,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.titleMedium,
                color = Brand.TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (running) {
                    t.running(reminder.channelName)
                } else {
                    t.startsAt(reminder.startMs.asClock(), reminder.channelName)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            // Buttons for a finger; the remote uses OK and Back (see handleKey).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(onClick = state::switch) { Text(t.switchNow) }
                TvButton(onClick = state::close) { Text(t.dismiss) }
            }
            Spacer(Modifier.height(6.dp))
            Text(t.keys, style = MaterialTheme.typography.labelSmall, color = Brand.TextSecondary)
        }
    }
}
