package com.safir.iptv.data.remote

import com.safir.iptv.domain.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A sideloaded app has no store behind it, so it has to ask for itself whether a
 * newer build exists. The owner hosts one small file anywhere reachable:
 *
 *     { "versionCode": 3,
 *       "versionName": "1.2.0",
 *       "url": "https://example.com/karacast-1.2.0.apk",
 *       "notes": "Serien und Filme" }
 *
 * Nothing is contacted until an address is configured, and nothing is installed
 * automatically — the app only says that something newer is there.
 */
object UpdateChecker {

    /** @return the newer build, or null when the address is empty or already current. */
    suspend fun check(url: String, currentVersionCode: Int): UpdateInfo? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            val json = JSONObject(Http.getString(url))
            val code = json.optInt("versionCode", 0)
            if (code <= currentVersionCode) return@withContext null
            val download = json.optString("url").ifBlank { return@withContext null }
            UpdateInfo(
                versionCode = code,
                versionName = json.optString("versionName", code.toString()),
                downloadUrl = download,
                notes = json.optString("notes")
            )
        }
}
