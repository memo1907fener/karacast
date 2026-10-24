package com.safir.iptv.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.safir.iptv.BuildConfig
import com.safir.iptv.data.remote.UpdateChecker
import com.safir.iptv.data.remote.UpdateInstaller
import java.io.IOException

/**
 * Holt die jeweils andere Karacast-App aufs Gerät.
 *
 * Die APK-Fassung lädt sie über die Update-Datei der anderen App herunter und
 * übergibt sie Androids Installer — derselbe Weg wie bei einem eigenen Update,
 * Android fragt also selbst, ob installiert werden darf. Die Play-Fassung darf
 * das nicht (Google erlaubt keiner Play-App, Apps am Store vorbei zu
 * installieren) und öffnet stattdessen den Eintrag im Play Store.
 */
object CompanionInstall {

    /** Öffnet die andere App im Play Store, notfalls im Browser. */
    fun openStore(context: Context, packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(CompanionApps.playStoreUri(packageName)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(CompanionApps.playWebUri(packageName)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }
            .recoverCatching { context.startActivity(web) }
    }

    /**
     * Lädt die andere App herunter und öffnet die Installation.
     *
     * @param updateUrl die Update-Datei der anderen App ([CompanionApps]).
     * @return false, wenn erst die Erlaubnis „Apps installieren“ fehlte — die
     *   Einstellungsseite dafür ist dann schon offen, danach einfach noch einmal.
     * @throws IOException wenn Datei oder Download nicht zu bekommen waren.
     */
    suspend fun downloadAndInstall(
        context: Context,
        updateUrl: String,
        onProgress: (Int) -> Unit
    ): Boolean {
        if (!UpdateInstaller.canInstall(context)) {
            UpdateInstaller.openInstallPermission(context)
            return false
        }
        // Version 0: jede veröffentlichte Fassung ist „neuer“, die Datei kommt also
        // immer zurück, solange es überhaupt eine gibt.
        val info = UpdateChecker.check(updateUrl, 0)
            ?: throw IOException("No release at $updateUrl")
        val file = UpdateInstaller.download(
            context = context,
            url = info.downloadUrl,
            versionName = "companion-" + info.versionName,
            onProgress = onProgress
        )
        UpdateInstaller.install(context, file)
        return true
    }

    /** Ob diese Fassung aus dem Play Store kommt — dann nur der Weg über den Store. */
    val viaStore: Boolean get() = BuildConfig.PLAY_STORE
}
