package com.safir.iptv.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
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
        // Über [downloadClient], weil hier gelesen *und* ausgewertet wird: eine
        // große Senderliste kann länger dauern als das Gesamtlimit des normalen
        // Drahts, und dann bricht mitten im Lesen die Verbindung weg.
        downloadClient.newCall(request).execute().use { response ->
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
     * Der Draht für große Dateien.
     *
     * Der gewöhnliche [client] hat ein Gesamtlimit von drei Minuten pro Aufruf —
     * richtig für eine Senderliste, tödlich für einen Programmführer: ein
     * 80-MB-Paket über eine müde Leitung braucht länger, OkHttp bricht mitten im
     * Strom ab, und was beim Leser ankommt, heißt „unexpected end of stream". Das
     * sah nach einem Fehler der Gegenstelle aus und war einer von uns.
     *
     * Hier gibt es deshalb kein Gesamtlimit, sondern nur eines pro Lesevorgang:
     * solange Daten fließen, darf es dauern; bleibt zwei Minuten lang alles
     * still, ist die Leitung tot und wir merken es trotzdem. Dazu HTTP/1.1 —
     * manche Anbieterserver sprechen ein unsauberes HTTP/2 und lassen genau bei
     * langen Antworten die Verbindung fallen.
     */
    private val downloadClient: OkHttpClient by lazy {
        client.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    /** Weniger als das ist kein halber Programmführer, sondern Schrott. */
    private const val SALVAGE_MIN_BYTES = 64L * 1024L

    /**
     * Lädt [url] am Stück in [target] und gibt die Anzahl Bytes zurück.
     *
     * Warum überhaupt erst in eine Datei? Weil die Gegenstelle die Verbindung
     * zumacht, wenn zwischen zwei Lesevorgängen zu viel Zeit vergeht. Wer einen
     * 100-MB-Programmführer liest *und dabei* Zeile für Zeile in die Datenbank
     * schreibt, lässt den Draht sekundenlang brachliegen — und bekommt irgendwann
     * mitten im Text ein „unexpected end of stream". Herunterladen und Auswerten
     * müssen also zwei Schritte sein, nicht einer.
     *
     * Drei Vorkehrungen gegen Abbrüche, in dieser Reihenfolge:
     *
     *  1. **Fortsetzen statt neu anfangen.** Bricht die Leitung nach 40 MB ab,
     *     wird mit `Range: bytes=40000000-` weitergemacht. Kann der Server das
     *     nicht, fängt er von vorn an — dann merken wir es an seiner Antwort und
     *     werfen das Bisherige weg, statt zwei Anfänge aneinanderzukleben.
     *  2. **Keine durchsichtige Entpackung.** Mit `identity` kommt an, was
     *     dasteht. Sonst entpackt OkHttp unterwegs, und ein abgerissener
     *     gzip-Strom ist nicht fortsetzbar — die Bytezahl stimmt dann mit nichts
     *     mehr überein.
     *  3. **Retten, was da ist.** Wenn nach allen Versuchen ein brauchbares Stück
     *     auf der Platte liegt, wird es zurückgegeben statt weggeworfen. Ein
     *     Programmführer bis Donnerstag ist mehr wert als gar keiner.
     */
    @Throws(IOException::class)
    fun downloadToFile(
        url: String,
        target: File,
        attempts: Int = 4,
        onBytes: (Long) -> Unit = {}
    ): Long {
        target.delete()
        var have = 0L
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
                val request = Request.Builder().url(url).get()
                    .header("Accept-Encoding", "identity")
                    .apply { if (have > 0) header("Range", "bytes=$have-") }
                    .build()

                downloadClient.newCall(request).execute().use { response ->
                    // 416 heißt „so weit reicht die Datei nicht" — bei einem
                    // Fortsetzungsversuch bedeutet das schlicht: sie ist fertig.
                    if (response.code == 416 && have > 0) return have
                    if (response.code in 400..499) {
                        throw NotRetryable("HTTP ${response.code} for ${url.redactCredentials()}")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} for ${url.redactCredentials()}")
                    }

                    // 206 = der Server setzt fort. Alles andere heißt: er fängt
                    // von vorn an, also muss auch die Datei von vorn anfangen.
                    val resuming = response.code == 206 && have > 0
                    if (!resuming) have = 0L

                    val body = response.body ?: throw IOException("Leere Antwort")
                    val remaining = body.contentLength()
                    val expected = if (remaining > 0) have + remaining else -1L

                    body.byteStream().use { input ->
                        FileOutputStream(target, resuming).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                have += read
                                onBytes(have)
                            }
                            output.flush()
                        }
                    }

                    if (have == 0L) throw IOException("Keine Daten empfangen")
                    if (expected > 0 && have < expected) {
                        throw IOException("Nur $have von $expected Bytes empfangen")
                    }
                    return have
                }
            } catch (stop: NotRetryable) {
                target.delete()
                throw IOException(stop.message)
            } catch (error: IOException) {
                last = error
                // Nichts löschen: das Bisherige ist der Anfang des nächsten
                // Versuchs — und notfalls das, was gerettet wird.
                have = target.length()
            }
        }

        if (have >= SALVAGE_MIN_BYTES) return have
        target.delete()
        throw last ?: IOException("Download fehlgeschlagen")
    }

    private class NotRetryable(message: String) : IOException(message)
}

/** Hides username/password query parameters before a URL reaches a log or a toast. */
fun String.redactCredentials(): String =
    replace(Regex("(?i)(username|password)=[^&]*"), "$1=***")
