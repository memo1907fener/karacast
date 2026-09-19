package com.safir.iptv.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Many IPTV panels reject requests whose User-Agent they do not recognise, and a
 * large share of them are configured around VLC. Sending a VLC UA by default is
 * the single most effective thing for "works everywhere".
 */
const val DEFAULT_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

object Http {

    @Volatile
    var userAgent: String = DEFAULT_USER_AGENT

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(UserAgentInterceptor())
            .build()
    }

    private class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request: Request = chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .build()
            return chain.proceed(request)
        }
    }

    /** Blocking GET returning the body as text. Call from Dispatchers.IO. */
    @Throws(IOException::class)
    fun getString(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${url.redactCredentials()}")
            }
            return response.body?.string().orEmpty()
        }
    }

    /**
     * Blocking GET handing the raw stream to [block], transparently gunzipping when
     * the payload actually is gzip — XMLTV guides are usually served as .gz, and
     * headers lie often enough that the magic bytes are the only reliable signal.
     * (OkHttp already handles `Content-Encoding: gzip` itself and strips the header,
     * so anything still gzipped here is a gzip *file*, not a gzip transfer encoding.)
     */
    @Throws(IOException::class)
    fun <T> getStream(url: String, block: (InputStream) -> T): T {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${url.redactCredentials()}")
            }
            val body = response.body ?: throw IOException("Empty response")
            return readMaybeGzip(body.byteStream(), block)
        }
    }

    /**
     * Gunzips [input] when it actually is gzip, otherwise passes it through.
     * The stream is closed afterwards.
     */
    @Throws(IOException::class)
    fun <T> readMaybeGzip(input: InputStream, block: (InputStream) -> T): T {
        val pushback = PushbackInputStream(input.buffered(), 2)
        val header = ByteArray(2)
        val read = pushback.read(header, 0, 2)
        if (read > 0) pushback.unread(header, 0, read)
        val isGzip = read == 2 &&
            (header[0].toInt() and 0xff) == 0x1f &&
            (header[1].toInt() and 0xff) == 0x8b

        return if (isGzip) GZIPInputStream(pushback).use(block) else pushback.use(block)
    }

    /**
     * Lädt [url] am Stück in [target].
     *
     * Warum überhaupt erst in eine Datei? Weil die Gegenstelle die Verbindung
     * zumacht, wenn zwischen zwei Lesevorgängen zu viel Zeit vergeht. Wer einen
     * 100-MB-Programmführer liest *und dabei* Zeile für Zeile in die Datenbank
     * schreibt, lässt den Draht sekundenlang brachliegen — und bekommt irgendwann
     * mitten im Text ein „unexpected end of stream". Herunterladen und Auswerten
     * müssen also zwei Schritte sein, nicht einer.
     *
     * Abgerissene Downloads werden bis zu [attempts] Mal neu begonnen; ein „das
     * gibt es nicht" (4xx) wird nicht wiederholt, das wird beim zweiten Mal auch
     * nicht wahrer.
     */
    @Throws(IOException::class)
    fun downloadToFile(
        url: String,
        target: File,
        attempts: Int = 3,
        onBytes: (Long) -> Unit = {}
    ): Long {
        var last: IOException? = null
        for (attempt in 0 until attempts) {
            if (attempt > 0) {
                try {
                    Thread.sleep(1500L * attempt)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Abgebrochen", interrupted)
                }
            }
            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (response.code in 400..499) {
                        // Kein Wiederholen: das ist eine Antwort, kein Unfall.
                        throw NotRetryable("HTTP ${response.code} for ${url.redactCredentials()}")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} for ${url.redactCredentials()}")
                    }
                    val body = response.body ?: throw IOException("Leere Antwort")
                    val expected = body.contentLength()
                    var written = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                onBytes(written)
                            }
                            output.flush()
                        }
                    }
                    if (written == 0L) throw IOException("Keine Daten empfangen")
                    if (expected > 0 && written < expected) {
                        throw IOException("Nur $written von $expected Bytes empfangen")
                    }
                    return written
                }
            } catch (stop: NotRetryable) {
                target.delete()
                throw IOException(stop.message)
            } catch (error: IOException) {
                last = error
                target.delete()
            }
        }
        throw last ?: IOException("Download fehlgeschlagen")
    }

    private class NotRetryable(message: String) : IOException(message)
}

/** Hides username/password query parameters before a URL reaches a log or a toast. */
fun String.redactCredentials(): String =
    replace(Regex("(?i)(username|password)=[^&]*"), "$1=***")
