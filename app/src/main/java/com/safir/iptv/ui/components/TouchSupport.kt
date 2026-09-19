package com.safir.iptv.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults

/**
 * Compose-for-TV drives everything through focus plus the OK key: its Surface and
 * Button never install a tap handler at all. That is right for a remote and leaves
 * the app dead under a finger or a mouse — on a phone, a tablet, a touch-capable
 * TV, or a mirrored screen. This adds the missing half without disturbing the
 * D-pad path, which keeps working exactly as before.
 *
 * Pass [onClick] by name: a trailing lambda would bind to [onLongClick], since
 * Kotlin always attaches it to the last parameter.
 */
fun Modifier.alsoTappable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = this.pointerInput(onClick, onLongClick) {
    val longPress: ((Offset) -> Unit)? = onLongClick?.let { handler -> { _: Offset -> handler() } }
    detectTapGestures(
        onTap = { onClick() },
        onLongPress = longPress
    )
}

/** The TV button, plus tap handling. Use this instead of `androidx.tv.material3.Button`. */
@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.colors(),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.alsoTappable(onClick),
        colors = colors,
        content = content
    )
}
