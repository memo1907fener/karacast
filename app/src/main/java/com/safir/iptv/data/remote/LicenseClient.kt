package com.safir.iptv.data.remote

import com.safir.iptv.util.DeviceParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * What the licence server says about one television.
 *
 * @param status `trial`, `active` or `blocked`. Anything unrecognised is treated as
 *   `trial`, because an app that locks itself over a word it does not know is an app
 *   that breaks the day somebody adds a new state to the server.
 * @param trialStartedAt when the panel first saw this device. Milliseconds, 0 when
 *   the panel does not track it.
 * @param serverTime the panel's own clock, which the app trusts over the television's.
 * @param ticket the signed licence, present once it has been paid for.
 */
data class LicenseReply(
    val status: String,
    val trialStartedAt: Long,
    val serverTime: Long,
    val ticket: String
)

/**
 * The conversation with the licence server. Two questions, both small.
 *
 * `hello` is asked now and then — at most once a day, and whenever the activation
 * screen is open. `redeem` is for the person who could not scan the code and was
 * sent eight characters by e-mail instead.
 *
 * Neither is ever required for the app to run: a television with a verified ticket
 * never needs this class again, and one still inside its fortnight counts the days
 * on its own.
 */
object LicenseClient {

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    @Throws(IOException::class)
    suspend fun hello(
        baseUrl: String,
        parts: DeviceParts,
        deviceCode: String,
        versionCode: Int,
        versionName: String,
        model: String,
        language: String
    ): LicenseReply? = post(
        baseUrl = baseUrl,
        path = "/license/hello",
        body = JSONObject().apply {
            put("device", deviceCode)
            put("ids", JSONArray(parts.hashes))
            put("app", "karacast")
            put("versionCode", versionCode)
            put("versionName", versionName)
            put("model", model)
            put("language", language)
        }
    )

    @Throws(IOException::class)
    suspend fun redeem(
        baseUrl: String,
        code: String,
        parts: DeviceParts,
        deviceCode: String
    ): LicenseReply? = post(
        baseUrl = baseUrl,
        path = "/license/redeem",
        body = JSONObject().apply {
            put("code", code.trim().uppercase())
            put("device", deviceCode)
            put("ids", JSONArray(parts.hashes))
        }
    )

    private suspend fun post(
        baseUrl: String,
        path: String,
        body: JSONObject
    ): LicenseReply? = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext null

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()

        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) throw IOException("Empty response")
            parse(JSONObject(text))
        }
    }

    /**
     * Reads what it knows and shrugs at the rest, so the panel can grow new fields
     * without every television in the field needing a new app first.
     */
    private fun parse(json: JSONObject): LicenseReply = LicenseReply(
        status = json.optString("status", "trial").lowercase(),
        // Seconds on the wire — a licence server should not have to care what
        // units somebody's phone app happens to use internally.
        trialStartedAt = json.optLong("trialStartedAt", 0L) * 1000L,
        serverTime = json.optLong("serverTime", 0L) * 1000L,
        ticket = json.optString("ticket")
    )
}
