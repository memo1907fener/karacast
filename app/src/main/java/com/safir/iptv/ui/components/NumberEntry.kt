package com.safir.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.safir.iptv.ui.theme.Brand
import kotlinx.coroutines.delay

/**
 * Channel entry by remote-control digits, the way a television does it: the typed
 * number shows in the corner, another digit extends it, and after a short pause
 * — or immediately on OK — it jumps.
 */
@Stable
class NumberEntryState {

    var digits by mutableStateOf("")
        private set

    val isActive: Boolean get() = digits.isNotEmpty()

    fun append(digit: Char) {
        if (digits.length < MAX_DIGITS) digits += digit
    }

    fun value(): Int? = digits.toIntOrNull()

    fun clear() {
        digits = ""
    }

    private companion object {
        const val MAX_DIGITS = 4
    }
}

/**
 * @param onSubmit receives the typed number once the user stops typing.
 */
@Composable
fun rememberNumberEntry(onSubmit: (Int) -> Unit): NumberEntryState {
    val state = remember { NumberEntryState() }
    val currentOnSubmit by rememberUpdatedState(onSubmit)

    LaunchedEffect(state.digits) {
        if (state.digits.isEmpty()) return@LaunchedEffect
        delay(SUBMIT_DELAY_MS)
        state.value()?.let(currentOnSubmit)
        state.clear()
    }
    return state
}

/** Confirms the pending number right away; returns false when nothing was typed. */
fun NumberEntryState.submitNow(onSubmit: (Int) -> Unit): Boolean {
    val number = value() ?: return false
    clear()
    onSubmit(number)
    return true
}

/** Digit behind a key event, for both the number row and a numeric keypad. */
fun Key.asDigit(): Char? = when (this) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/** The typed number, shown large in the top-left corner. */
@Composable
fun NumberEntryOverlay(
    state: NumberEntryState,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    if (!state.isActive) return

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Text(
            text = state.digits.padStart(3, '—'),
            color = Brand.Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp,
            textAlign = TextAlign.Center
        )
        if (caption != null) {
            Text(
                text = caption,
                color = Brand.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

private const val SUBMIT_DELAY_MS = 1_800L
