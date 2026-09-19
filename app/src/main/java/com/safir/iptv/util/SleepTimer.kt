package com.safir.iptv.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Stop in 30 minutes." Deliberately app-wide and not tied to the player: someone
 * who falls asleep in front of the television may well have wandered back into the
 * channel list first, and the box should still switch off.
 *
 * A plain object rather than an injected service — it holds one timestamp, needs no
 * context, and must outlive every screen that can set it.
 */
object SleepTimer {

    private val _endAt = MutableStateFlow(0L)

    /** Wall-clock time the app should close at, or 0 when no timer is running. */
    val endAt: StateFlow<Long> = _endAt.asStateFlow()

    val isActive: Boolean get() = _endAt.value > 0L

    val remainingMs: Long
        get() = if (isActive) (_endAt.value - System.currentTimeMillis()).coerceAtLeast(0L) else 0L

    val remainingMinutes: Int get() = ((remainingMs + 59_999L) / 60_000L).toInt()

    /** @param minutes 0 or less cancels a running timer. */
    fun start(minutes: Int) {
        _endAt.value = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
    }

    fun cancel() {
        _endAt.value = 0L
    }

    fun hasExpired(): Boolean = isActive && remainingMs == 0L
}
