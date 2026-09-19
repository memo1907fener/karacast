package com.safir.iptv.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
private val dayFormat = DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.GERMANY)

fun Long.asClock(zone: ZoneId = ZoneId.systemDefault()): String =
    clockFormat.format(Instant.ofEpochMilli(this).atZone(zone))

fun Long.asDay(zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        today.minusDays(1) -> "Gestern"
        else -> dayFormat.format(date.atStartOfDay(zone))
    }
}

/** "1 Std 35 Min" / "45 Min" */
fun formatDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours Std $minutes Min"
        hours > 0 -> "$hours Std"
        else -> "$minutes Min"
    }
}

/**
 * "1:24:30", or "4:07" under an hour — the shape a position on a progress bar has
 * to have. Digits only, so it reads the same in every language the app speaks.
 */
fun Long.asTimeLabel(): String {
    val total = (this / 1000L).coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatRelativeSync(timestampMs: Long): String {
    if (timestampMs <= 0L) return "noch nie"
    val delta = System.currentTimeMillis() - timestampMs
    return when {
        delta < 60_000L -> "gerade eben"
        delta < 3_600_000L -> "vor ${delta / 60_000L} Min"
        delta < 86_400_000L -> "vor ${delta / 3_600_000L} Std"
        else -> "${timestampMs.asDay()}, ${timestampMs.asClock()}"
    }
}
