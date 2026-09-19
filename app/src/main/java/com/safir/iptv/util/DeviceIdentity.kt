package com.safir.iptv.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.io.File
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.UUID

/**
 * Everything this television can be recognised by.
 *
 * A licence has to survive things an app normally does not care about: the app
 * being reinstalled, its data being cleared, the box being reset. So the identity
 * is not one value but several, ordered by how long they last.
 *
 * @param ethMac the wired adapter's address. The most durable thing on a television
 *   — it outlives factory resets — and the one people can read off the TV's own
 *   network screen when they ring for help.
 * @param wlanMac the wireless one. Collected too, because somebody who buys on a
 *   cable and later moves to Wi-Fi must not lose what they paid for.
 * @param androidId survives a reinstall, not a factory reset.
 * @param installId this app's own random number. Survives nothing, but it is always
 *   there, which matters on a box that hands out none of the above.
 */
data class DeviceParts(
    val ethMac: String = "",
    val wlanMac: String = "",
    val androidId: String = "",
    val installId: String = ""
) {
    /**
     * The parts that exist, strongest first.
     *
     * Order matters: the first entry is what the viewer is shown and what the panel
     * files the purchase under, so it should be the thing least likely to change.
     */
    val present: List<String>
        get() = listOf(ethMac, wlanMac, androidId, installId).filter { it.isNotBlank() }

    /**
     * Each part as a short hash.
     *
     * Hashed rather than sent raw for two reasons: a MAC address is personal data
     * that the panel has no business storing in the clear, and a hash is a fixed
     * length that fits in a licence ticket without making it unwieldy.
     */
    val hashes: List<String>
        get() = present.map { it.shortHash() }
}

object DeviceIdentity {

    @Volatile
    private var cached: DeviceParts? = null

    fun parts(context: Context, installId: String): DeviceParts = cached ?: DeviceParts(
        ethMac = macOf("eth0"),
        wlanMac = macOf("wlan0"),
        androidId = androidId(context),
        installId = installId
    ).also { cached = it }

    /**
     * What the viewer sees and reads out: twelve characters, grouped like a MAC
     * address because that is the shape people already know from their router and
     * from the television's own network page.
     *
     * Derived from the strongest part that exists, so two households never collide,
     * and stable as long as that part is.
     */
    fun code(parts: DeviceParts): String {
        val source = parts.present.firstOrNull() ?: return "------------"
        return source.shortHash().uppercase()
    }

    fun formatted(code: String): String = code.chunked(2).joinToString(":")

    /**
     * The address of one network interface, or blank.
     *
     * Two ways round, because neither works everywhere. Since Android 10 the public
     * API returns the same fake address on every device, but television boxes
     * routinely leave the underlying file readable — and a great many of them still
     * run Android 9 or 11 with permissive firmware. Whatever comes back is checked
     * against the known placeholders, so a fake never becomes somebody's licence.
     */
    private fun macOf(name: String): String {
        readFile("/sys/class/net/$name/address")?.let { if (it.isRealMac()) return it }

        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.hardwareAddress
                ?.joinToString(":") { byte -> "%02x".format(byte) }
                ?.takeIf { it.isRealMac() }
                .orEmpty()
        }.getOrDefault("")
    }

    private fun readFile(path: String): String? = runCatching {
        File(path).takeIf { it.canRead() }?.readText()?.trim()?.lowercase()
    }.getOrNull()

    @SuppressLint("HardwareIds")
    private fun androidId(context: Context): String = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            .orEmpty()
            // Some emulators and a few cloned firmwares hand out this one value to
            // every device. A shared identifier is worse than none at all.
            .takeUnless { it.isBlank() || it == "9774d56d682e549c" }
            .orEmpty()
    }.getOrDefault("")

    /** A fresh random number for a device that has never run this app before. */
    fun newInstallId(): String = UUID.randomUUID().toString().replace("-", "")
}

/**
 * False for the placeholders Android hands out instead of a real address, and for
 * the all-zero address some virtual adapters report.
 */
private fun String.isRealMac(): Boolean {
    val value = trim().lowercase()
    if (!Regex("^([0-9a-f]{2}:){5}[0-9a-f]{2}$").matches(value)) return false
    return value != "02:00:00:00:00:00" && value != "00:00:00:00:00:00"
}

/** Twelve hex characters of SHA-256 — short enough to read out, long enough not to clash. */
internal fun String.shortHash(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(trim().lowercase().toByteArray())
    return digest.take(6).joinToString("") { "%02x".format(it) }
}
