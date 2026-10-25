package com.safir.iptv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Merkt sich, welche Zeile einer Liste zuletzt den Fokus hatte, und bringt ihn
 * dorthin zurück, wenn man von außen wieder hineinspringt.
 *
 * Dasselbe, was `focusRestorer()` tut — nur ohne dessen Absturz: Compose hält
 * dafür die gemerkte Zeile fest („pinnt“ sie), und wird die Liste darunter
 * ausgetauscht (eine andere Gruppe), gibt es diese Halterung beim nächsten ▶
 * zweimal frei. Das endet in „Release should only be called once“ und einem
 * Neustart der App. Hier wird nichts festgehalten: ist die Zeile nicht mehr im
 * Bild, nimmt Compose einfach die nächstgelegene.
 */
class FocusMemory {
    var key: Any? by mutableStateOf(null)
    val requester = FocusRequester()
}

@Composable
fun rememberFocusMemory(): FocusMemory = remember { FocusMemory() }

/**
 * An die Liste: beim Hineinspringen zur gemerkten Zeile, falls sie gerade zu
 * sehen ist ([visibleKeys]), sonst zu [fallback].
 */
fun Modifier.restoreFocus(
    memory: FocusMemory,
    visibleKeys: () -> List<Any>,
    fallback: () -> FocusRequester = { FocusRequester.Default }
): Modifier = focusProperties {
    enter = {
        val key = memory.key
        if (key != null && key in visibleKeys()) memory.requester else fallback()
    }
}.focusGroup()

/** An jede Zeile: merkt sich, wenn sie den Fokus bekommt. */
fun Modifier.rememberFocus(memory: FocusMemory, key: Any): Modifier =
    (if (memory.key == key) focusRequester(memory.requester) else this)
        .onFocusChanged { if (it.isFocused) memory.key = key }
