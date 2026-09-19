package com.safir.iptv.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Fetching a new version of an app that never came from a store.
 *
 * Two steps, and the split matters. The app downloads the file itself, because it
 * knows where from and can show how far along it is. It does **not** install it:
 * that is handed to Android, which asks the person in front of the television
 * whether they agree. A set-top box that replaces its own software while nobody is
 * looking is a set-top box nobody should own, and the platform is right to insist.
 *
 * The first time, Android will also want this app to be allowed to install
 * packages at all. [canInstall] answers whether that has happened;
 * [openInstallPermission] takes the viewer to the one screen where it is granted.
 */
object UpdateInstaller {

    /**
     * Downloads the APK into the cache.
     *
     * The cache, deliberately: if the installation never happens the file is rubbish
     * the system may delete whenever it likes, and nobody has to remember to clean
     * up after a television that ran out of space.
     *
     * @param onProgress 0..100, or -1 while the server declines to say how big the
     *   file is — which happens often enough that the bar has to cope with it.
     */
    @Throws(IOException::class)
    suspend fun download(
        context: Context,
        url: String,
        versionName: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        // One file per version, overwritten on a retry: a half-finished download
        // from yesterday must never be what gets installed today.
        val target = File(folder, "karacast-$versionName.apk")

        val request = Request.Builder().url(url).get().build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val total = body.contentLength()

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastReported = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val percent = if (total > 0L) {
                            ((written * 100L) / total).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        // Reported only when the number actually changes; a progress
                        // bar redrawn per 64 KB is how a cheap box drops frames.
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        }

        if (target.length() <= 0L) throw IOException("Downloaded file is empty")
        target
    }

    /** Hands the file to Android's package installer. It asks; this app does not. */
    fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** False when the viewer has not yet allowed this app to install packages. */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            // Before Android 8 the permission in the manifest was the whole story.
            true
        }

    /** Opens the single settings screen where that permission is given. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Some television firmwares ship without that screen at all. Falling back to
        // the app's own details page is better than a crash on a device whose maker
        // decided this setting was not worth including.
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
