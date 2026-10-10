package com.safir.iptv.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Picture-in-picture, as far as the screens are concerned.
 *
 * Two facts travel through here, one in each direction: a player tells the
 * activity that something is playing that is worth keeping on screen when the
 * viewer leaves ([playing]); the activity tells the screens when the app has
 * shrunk to a small window ([active]), so they can drop everything but the
 * picture — a channel list or an info bar is unreadable at that size.
 */
object PictureInPicture {

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun setPlaying(value: Boolean) {
        _playing.value = value
    }

    internal fun setActive(value: Boolean) {
        _active.value = value
    }
}

/**
 * Whether this device can show the small window at all — and whether it should.
 * Android TV technically can, but there the Home key means "leave", not "keep
 * watching in a corner", so televisions are left out on purpose.
 */
fun supportsPictureInPicture(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return false
    val pm = context.packageManager
    if (!pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
    val ui = context.getSystemService(android.app.UiModeManager::class.java)
    return ui?.currentModeType != android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
}
