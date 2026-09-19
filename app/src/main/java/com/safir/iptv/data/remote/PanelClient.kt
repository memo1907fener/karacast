package com.safir.iptv.data.remote

import com.safir.iptv.domain.model.PlaylistSource
import com.safir.iptv.domain.model.SourceType
import com.safir.iptv.domain.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * What the panel sends back when a television reports in.
 *
 * Every field is optional on purpose. A panel that only wants to hand out accounts
 * implements [source] and nothing else; one that only wants to announce versions
 * implements [update]. The app copes with whatever it is given and ignores the rest,
 * which means the panel can grow without every television needing a new build first.
 */
data class PanelReply(
    val source: PlaylistSource? = null,
    val update: UpdateInfo? = null,
    /** A line to show once — maintenance, an expiry warning, a note from the owner. */
    val messageId: String = "",
    val messageTitle: String = "",
    val messageText: String = "",
    /** What the panel calls this set, so the settings page can say "Connected — …". */
    val deviceName: String = ""
) {
    val hasMessage: Boolean get() = messageText.isNotBlank()
}

/**
 * The client for a panel that does not exist yet.
 *
 * This is groundwork, and shaped so it stays honest while it waits: nothing here
 * runs unless an address has been typed into the settings by hand. No address, no
 * request, no identifier, no traffic at all.
 *
 * The exchange is one POST and one JSON answer — small enough that a panel can be a
 * single PHP file, which is what it will be at first.
 *
 *     POST <panel>/device/hello
 *     { "deviceId": "8f3c…", "code": "ABCD-1234", "app": "karacast",
 *       "versionCode": 11, "versionName": "1.8.0",
 *       "model": "TCL Smart TV Pro", "androidSdk": 34, "language": "de" }
 *
 *     200 { "status": "ok",
 *           "deviceName": "Wohnzimmer Oma",
 *           "source": { "type": "xtream", "name": "Familie",
 *                       "url": "http://host:8080", "username": "…",
 *                       "password": "…", "epgUrl": "" },
 *           "update": { "versionCode": 12, "versionName": "1.8.1",
 *                       "url": "https://…/karacast.apk", "notes": "…" },
 *           "message": { "id": "m7", "title": "Wartung", "text": "…" } }
 *
 * The full contract, including what a panel must do when a code is unknown, is
 * written down in `panel/API.md` next to the sources.
 */
object PanelClient {

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * @param baseUrl the panel root, without a trailing slash. Blank does nothing.
     * @return the panel's answer, or null when no panel is configured.
     * @throws IOException when a panel *is* configured and could not be reached —
     *   which the settings page shows, because a family that was promised automatic
     *   setup deserves to be told when it did not happen.
     */
    @Throws(IOException::class)
    suspend fun hello(
        baseUrl: String,
        deviceId: String,
        code: String,
        versionCode: Int,
        versionName: String,
        model: String,
        androidSdk: Int,
        language: String
    ): PanelReply? = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext null

        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("code", code)
            put("app", "karacast")
            put("versionCode", versionCode)
            put("versionName", versionName)
            put("model", model)
            put("androidSdk", androidSdk)
            put("language", language)
        }

        val request = Request.Builder()
            .url("$baseUrl/device/hello")
            .post(payload.toString().toRequestBody(JSON_TYPE))
            .build()

        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Empty response")
            parse(JSONObject(body))
        }
    }

    /**
     * Reads what it recognises and steps over what it does not.
     *
     * A panel written next year will send fields this build has never heard of; a
     * parser that fell over on those would make every future panel change a forced
     * update on every television in the family.
     */
    private fun parse(json: JSONObject): PanelReply {
        val source = json.optJSONObject("source")?.let { obj ->
            val url = obj.optString("url").trim()
            if (url.isBlank()) {
                null
            } else {
                PlaylistSource(
                    name = obj.optString("name").ifBlank { "Panel" },
                    type = if (obj.optString("type").equals("m3u", true)) {
                        SourceType.M3U
                    } else {
                        SourceType.XTREAM
                    },
                    url = url.trimEnd('/'),
                    username = obj.optString("username"),
                    password = obj.optString("password"),
                    epgUrl = obj.optString("epgUrl")
                )
            }
        }

        val update = json.optJSONObject("update")?.let { obj ->
            val code = obj.optInt("versionCode", 0)
            val download = obj.optString("url").trim()
            if (code <= 0 || download.isBlank()) {
                null
            } else {
                UpdateInfo(
                    versionCode = code,
                    versionName = obj.optString("versionName", code.toString()),
                    downloadUrl = download,
                    notes = obj.optString("notes")
                )
            }
        }

        val message = json.optJSONObject("message")
        return PanelReply(
            source = source,
            update = update,
            messageId = message?.optString("id").orEmpty(),
            messageTitle = message?.optString("title").orEmpty(),
            messageText = message?.optString("text").orEmpty(),
            deviceName = json.optString("deviceName")
        )
    }
}
