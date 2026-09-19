package com.safir.iptv.util

import android.util.Base64
import com.safir.iptv.domain.model.LicenseTicket
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Checks that a licence ticket really came from the panel, without asking anybody.
 *
 * This is the piece that lets a paid television keep working when the licence server
 * is down, moved, or gone for good. The panel signs the ticket once with a private
 * key that never leaves the server; the app carries only the matching public key and
 * can verify it for ever, offline, in about a millisecond.
 *
 * A ticket looks like this, three parts separated by dots:
 *
 *     KC1.<base64url(payload)>.<base64url(signature)>
 *
 * and the signature covers the first two parts exactly as they are written, so
 * nothing can be re-encoded on the way.
 *
 * ### Why ECDSA and not Ed25519
 *
 * Ed25519 is the nicer algorithm and it is what I would pick on a phone. Android
 * only learned it in version 13, and the television boxes this app is for run 9, 10
 * and 11. ECDSA over P-256 has been in Android since the beginning, is just as
 * sound, and the signatures are still small. PHP signs it in one line with
 * `openssl_sign($data, $sig, $key, OPENSSL_ALGO_SHA256)`.
 */
object LicenseVerifier {

    /**
     * Die öffentlichen Schlüssel des Panels — X.509 SubjectPublicKeyInfo, Base64.
     *
     * **Eine Liste, kein einzelner Wert.** Das ist der Unterschied zwischen einer
     * Unannehmlichkeit und einem Desaster, und er wurde teuer gelernt: geht der
     * private Schlüssel auf dem Server verloren, muss ein neuer her — und mit einem
     * einzigen fest eingetragenen Schlüssel wäre in genau dieser Sekunde jedes
     * bereits bezahlte Ticket wertlos, auf jedem Fernseher, auch auf denen, die nie
     * wieder ins Internet kommen.
     *
     * Mit einer Liste wird der neue Schlüssel oben angefügt, der alte bleibt stehen,
     * und beide Sorten Tickets gelten weiter. Ein Schlüsselwechsel kostet dann eine
     * neue App-Fassung — aber niemandem seine Lizenz.
     *
     * Ein alter Schlüssel fliegt erst dann raus, wenn kein Gerät mehr ein Ticket von
     * ihm hat. Das ist eine Entscheidung für übermorgen, nicht für heute.
     *
     * Hier steht nichts Geheimes: ein öffentlicher Schlüssel kann Unterschriften
     * prüfen und keine erzeugen. Er ist in einer APK, die jeder öffnen kann, sicher.
     */
    private val PUBLIC_KEYS = listOf(
        // karacast.de, erzeugt am 17. September 2026. Der zugehörige private Teil
        // ging beim Hochladen verloren — ausgestellte Tickets bleiben damit gültig,
        // neue kommen von seinem Nachfolger.
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEf77+935W4CAU7PhtTxKKDnHU84E5Vew" +
            "vAlPjiKF96MGbgeMW+8g21kS8Cvc/k+0jm+NVgRvqbJQjEtT2cXURow=="
    )

    private const val PREFIX = "KC1"

    /**
     * @param ticket the whole three-part string as the panel issued it.
     * @param deviceHashes this television's fingerprints, from [DeviceParts.hashes].
     * @return the ticket's contents when the signature is sound **and** it was issued
     *   for this device; null in every other case. Null is never explained further —
     *   a forger learns nothing from the difference between "bad signature" and
     *   "wrong device", and the viewer is told the same thing either way.
     */
    fun verify(ticket: String, deviceHashes: Collection<String>): LicenseTicket? {
        val parts = ticket.trim().split('.')
        if (parts.size != 3 || parts[0] != PREFIX) return null

        val signedData = "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)
        val signature = parts[2].decodeBase64Url() ?: return null
        if (!signatureIsValid(signedData, signature)) return null

        val payload = parts[1].decodeBase64Url()?.toString(Charsets.UTF_8) ?: return null
        val parsed = runCatching { parse(payload) }.getOrNull() ?: return null

        // Signed by the right server, but for whose television? Without this line a
        // single purchased ticket would unlock every set it was copied to.
        val mine = deviceHashes.map { it.lowercase() }.toSet()
        if (parsed.deviceHashes.none { it.lowercase() in mine }) return null

        return parsed
    }

    /**
     * Passt die Unterschrift zu **irgendeinem** der bekannten Schlüssel?
     *
     * Reihum, bis einer passt. Das kostet bei einem Fehlschlag ein paar Millisekunden
     * je Schlüssel — einmal beim Start, und nur dann. Ein `runCatching` je Schlüssel,
     * damit ein unbrauchbarer Eintrag in der Liste nicht die übrigen mitreißt.
     */
    private fun signatureIsValid(data: ByteArray, signature: ByteArray): Boolean =
        PUBLIC_KEYS.any { encoded ->
            runCatching {
                val keyBytes = Base64.decode(encoded, Base64.NO_WRAP)
                val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(key)
                    update(data)
                    verify(signature)
                }
            }.getOrDefault(false)
        }

    private fun parse(payload: String): LicenseTicket {
        val json = JSONObject(payload)
        val ids = json.optJSONArray("ids")
        return LicenseTicket(
            version = json.optInt("v", 1),
            deviceHashes = buildList {
                for (index in 0 until (ids?.length() ?: 0)) {
                    ids?.optString(index)?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            },
            // Seconds on the wire, milliseconds everywhere in this app.
            issuedAt = json.optLong("iat", 0L) * 1000L,
            plan = json.optString("plan", "lifetime"),
            order = json.optString("ord")
        )
    }
}

/** URL-safe base64 without padding, which is what the ticket uses. */
private fun String.decodeBase64Url(): ByteArray? = runCatching {
    Base64.decode(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}.getOrNull()
