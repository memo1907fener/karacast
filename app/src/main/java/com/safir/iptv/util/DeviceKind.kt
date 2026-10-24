package com.safir.iptv.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Was für ein Gerät das ist — für die Frage, welche der beiden Apps hierher gehört.
 *
 * Karacast gibt es zweimal: die TV-App für Fernseher, Boxen und Tablets, und
 * Karacast Mobil für Handys und Tablets. Im Play Store sortiert Google das selbst
 * aus; eine APK dagegen lässt sich auf jedes Gerät legen. Deshalb fragt jede App
 * beim Start hier nach und zeigt, wenn sie auf dem falschen Gerät gelandet ist,
 * statt der Oberfläche einen Weg zur richtigen.
 *
 * Bewusst vorsichtig: ein Fernseher, der fälschlich für ein Handy gehalten würde,
 * wäre ausgesperrt. Darum gilt als Handy nur, was eindeutig eins ist.
 */
object DeviceKind {

    /**
     * Fernseher, TV-Box, Projektor: Leanback, TV-Modus — oder gar kein Touchscreen.
     * Das Letzte fängt die vielen Boxen und Beamer, die sich als gewöhnliches
     * Android ausgeben, aber nur mit einer Fernbedienung zu bedienen sind.
     */
    fun isTelevision(context: Context): Boolean {
        val pm = context.packageManager
        val ui = context.getSystemService(UiModeManager::class.java)
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        if (pm.hasSystemFeature(FEATURE_LEANBACK_ONLY)) return true
        return !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    /**
     * Ein Handy: Touch, kein Fernseher, kleiner Bildschirm — und Telefonie.
     *
     * Die Telefonie ist die Absicherung: billige Boxen melden manchmal einen
     * Touchscreen und einen kleinen Bildschirm (1080p bei hoher Dichte ergibt
     * 540 dp), aber keine hat ein Funkmodul. Ein Handy ohne SIM-Karte hat es
     * trotzdem; ein kleines WLAN-Tablet nicht — und Tablets dürfen beide Apps.
     */
    fun isPhone(context: Context): Boolean {
        if (isTelevision(context)) return false
        val pm = context.packageManager
        val small = context.resources.configuration.smallestScreenWidthDp in 1 until TABLET_MIN_DP
        return small && pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    /** Ab hier zählt ein Bildschirm als Tablet — Androids eigene Grenze. */
    const val TABLET_MIN_DP = 600

    private const val FEATURE_LEANBACK_ONLY = "android.software.leanback_only"
}

/**
 * Wo es die jeweils andere App gibt.
 *
 * Die APK-Fassungen holen sie über dieselbe kleine Update-Datei, die sie für
 * ihre eigenen Updates lesen; die Play-Fassungen schicken in den Play Store.
 */
object CompanionApps {
    const val TV_UPDATE_URL =
        "https://raw.githubusercontent.com/memo1907fener/karacast/main/update/karacast.json"
    const val MOBILE_UPDATE_URL =
        "https://raw.githubusercontent.com/memo1907fener/karacast/main/update/karacast-mobile.json"

    const val TV_PLAY_PACKAGE = "com.sternweb.karacast"
    const val MOBILE_PLAY_PACKAGE = "com.sternweb.karacast.mobile"

    fun playStoreUri(packageName: String): String = "market://details?id=$packageName"
    fun playWebUri(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"
}
