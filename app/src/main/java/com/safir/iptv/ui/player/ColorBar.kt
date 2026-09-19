package com.safir.iptv.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.safir.iptv.ui.components.alsoTappable
import com.safir.iptv.ui.i18n.LocalStrings
import com.safir.iptv.ui.theme.Brand
import kotlinx.coroutines.delay

/**
 * Which of the four coloured keys an entry belongs to. The colours are the ones
 * printed on the remote, so they are fixed values rather than theme colours.
 */
enum class ColorKey(val swatch: Color) {
    RED(Color(0xFFE23B2E)),
    GREEN(Color(0xFF3FAE4B)),
    YELLOW(Color(0xFFE6B822)),
    BLUE(Color(0xFF2F7FE0))
}

/**
 * One entry of the bar: a coloured square, what it does, and what it is set to now.
 *
 * @param key null for the trailing "more" entry, which no colour triggers and which
 *   leads to everything the four colours have no room for.
 */
data class ColorAction(
    val key: ColorKey?,
    val label: String,
    val detail: String = "",
    val enabled: Boolean = true,
    val onSelect: () -> Unit
)

/**
 * The coloured-key bar, the way a satellite receiver has always done it.
 *
 * Pressing red brings this up; it says what each colour does, and only then does a
 * colour key act. That two-step is the whole point — nobody loses their picture
 * because they brushed a button, and nobody has to remember a legend that is
 * printed nowhere.
 *
 * The entries are also ordinary focusable rows, because the remotes that ship with
 * most Google TV boxes have no coloured keys at all. On those the bar is opened
 * with the menu or settings key and walked with the D-pad like anything else.
 */
@Composable
fun ColorBar(
    actions: List<ColorAction>,
    modifier: Modifier = Modifier,
    /**
     * False when the row is on screen but the D-pad still belongs to something else
     * — the seek bar above it, say. It only takes the focus when it is asked to.
     */
    autoFocus: Boolean = true,
    /** The line explaining the colours; off when the row sits inside another bar. */
    showHint: Boolean = true,
    background: Color = Color.Black.copy(alpha = 0.88f)
) {
    val t = LocalStrings.current
    val first = remember { FocusRequester() }

    LaunchedEffect(autoFocus) {
        if (!autoFocus) return@LaunchedEffect
        delay(60)
        runCatching { first.requestFocus() }
    }

    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = if (showHint) 20.dp else 0.dp, vertical = if (showHint) 14.dp else 0.dp)
    ) {
        if (showHint) {
            Text(
                text = t.colors.hint,
                style = MaterialTheme.typography.labelSmall,
                color = Brand.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.forEachIndexed { index, action ->
                ColorEntry(
                    action = action,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier
                )
            }
        }
    }
}

@Composable
private fun ColorEntry(action: ColorAction, modifier: Modifier = Modifier) {
    Surface(
        onClick = { if (action.enabled) action.onSelect() },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            focusedContainerColor = Brand.Accent,
            pressedContainerColor = Brand.Accent,
            contentColor = if (action.enabled) Brand.TextPrimary else Brand.TextSecondary,
            focusedContentColor = Brand.Background,
            pressedContentColor = Brand.Background
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier.alsoTappable({ if (action.enabled) action.onSelect() })
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (action.key == null) {
                Text(text = "⋯", style = MaterialTheme.typography.labelLarge)
            } else {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (action.enabled) {
                                action.key.swatch
                            } else {
                                action.key.swatch.copy(alpha = 0.3f)
                            }
                        )
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                if (action.detail.isNotBlank()) {
                    Text(text = action.detail, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * The coloured key this event belongs to, or null.
 *
 * Matched on the Android key code rather than on Compose's own constants: remotes
 * are a zoo, the codes are the part that is actually standardised, and both players
 * must read the same press the same way.
 */
fun Key.asColorKey(): ColorKey? = when (nativeKeyCode) {
    AndroidKeyEvent.KEYCODE_PROG_RED -> ColorKey.RED
    AndroidKeyEvent.KEYCODE_PROG_GREEN -> ColorKey.GREEN
    AndroidKeyEvent.KEYCODE_PROG_YELLOW -> ColorKey.YELLOW
    AndroidKeyEvent.KEYCODE_PROG_BLUE -> ColorKey.BLUE
    else -> null
}

/**
 * True for every key that should bring the bar up.
 *
 * Deliberately generous. A box that has coloured keys sends red; the many that do
 * not send whatever their one spare button is wired to, and there is no way to know
 * in advance which. Listening to all of them costs nothing and is the difference
 * between the audio track being reachable and not.
 */
fun Key.opensColorBar(): Boolean = when (nativeKeyCode) {
    AndroidKeyEvent.KEYCODE_PROG_RED,
    AndroidKeyEvent.KEYCODE_MENU,
    AndroidKeyEvent.KEYCODE_SETTINGS,
    AndroidKeyEvent.KEYCODE_BOOKMARK,
    AndroidKeyEvent.KEYCODE_TV_CONTENTS_MENU,
    AndroidKeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU -> true
    else -> false
}
