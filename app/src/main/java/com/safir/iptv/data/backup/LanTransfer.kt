package com.safir.iptv.data.backup

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume

/** Another box on the same network that is offering its settings. */
data class FoundDevice(val name: String, val host: String, val port: Int)

class WrongCodeException : Exception("wrong code")

/**
 * Box to box over the home network, with no server in between.
 *
 * The sending box listens on a port and announces itself (mDNS), the receiving
 * box finds it and asks. What travels is encrypted with a six-digit code that is
 * only ever shown on the sender's screen and typed in on the receiver — it is
 * never sent itself, so a neighbour on the same Wi-Fi who catches the transfer
 * has the provider's passwords in a locked box without the key.
 *
 * Why not a QR code: a television has no camera to read one with.
 */
object LanTransfer {

    /** Fixed, so the address can be typed in when discovery finds nothing. */
    const val PORT = 47123
    private const val SERVICE_TYPE = "_karacast._tcp."
    private const val HELLO = "KARACAST/1"
    private const val MAGIC = "KCX1"
    private const val MAX_BYTES = 20 * 1024 * 1024
    private const val TIMEOUT_MS = 10_000

    private val random = SecureRandom()

    fun newCode(): String = (0 until 6).joinToString("") { random.nextInt(10).toString() }

    /** This box's address on the home network, for showing on screen. */
    fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .sortedByDescending { it.isSiteLocalAddress }
            .firstOrNull()?.hostAddress
    }.getOrNull()

    // ------------------------------------------------------------ sending

    /**
     * Offers [payload] until the coroutine is cancelled. Anyone may ask; only who
     * has the code can read the answer, so a wrong code can simply be tried again.
     */
    suspend fun serve(context: Context, deviceName: String, payload: ByteArray, code: String) =
        withContext(Dispatchers.IO) {
            val sealed = seal(payload, code)
            val server = ServerSocket()
            server.reuseAddress = true
            runCatching { server.bind(InetSocketAddress(PORT)) }
                .onFailure { server.bind(InetSocketAddress(0)) }
            server.soTimeout = 1_000

            val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            val registration = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) = Unit
                override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = Unit
                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            }
            val info = NsdServiceInfo().apply {
                serviceName = "Karacast · $deviceName"
                serviceType = SERVICE_TYPE
                port = server.localPort
            }
            runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }

            try {
                while (true) {
                    ensureActive()
                    val client = try {
                        server.accept()
                    } catch (idle: SocketTimeoutException) {
                        continue
                    }
                    runCatching { answer(client, sealed) }
                }
            } finally {
                runCatching { nsd.unregisterService(registration) }
                runCatching { server.close() }
            }
        }

    private fun answer(client: Socket, sealed: ByteArray) {
        client.use { socket ->
            socket.soTimeout = TIMEOUT_MS
            val input = DataInputStream(socket.getInputStream())
            if (input.readUTF() != HELLO) return
            DataOutputStream(socket.getOutputStream()).apply {
                writeInt(sealed.size)
                write(sealed)
                flush()
            }
        }
    }

    // ---------------------------------------------------------- receiving

    /** Boxes currently offering their settings; the list grows as they answer. */
    fun discover(context: Context): Flow<List<FoundDevice>> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = LinkedHashMap<String, FoundDevice>()
        // The system resolves one service at a time and refuses a second meanwhile.
        val resolving = Mutex()

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                launch {
                    val resolved = resolving.withLock { resolve(nsd, service) } ?: return@launch
                    val host = resolved.host?.hostAddress ?: return@launch
                    val device = FoundDevice(
                        name = resolved.serviceName.removePrefix("Karacast · "),
                        host = host,
                        port = resolved.port
                    )
                    trySend(synchronized(found) {
                        found[resolved.serviceName] = device
                        found.values.toList()
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                synchronized(found) {
                    if (found.remove(service.serviceName) != null) trySend(found.values.toList())
                }
            }
        }

        trySend(emptyList())
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }
        awaitClose { runCatching { nsd.stopServiceDiscovery(discovery) } }
    }

    @Suppress("DEPRECATION") // The replacement needs API 34; this one works everywhere.
    private suspend fun resolve(nsd: NsdManager, service: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    if (continuation.isActive) continuation.resume(info)
                }
            }
            runCatching { nsd.resolveService(service, listener) }
                .onFailure { if (continuation.isActive) continuation.resume(null) }
        }

    /**
     * Fetches and opens what [host] is offering.
     * @throws WrongCodeException when [code] is not the one on the sender's screen.
     */
    suspend fun fetch(host: String, port: Int, code: String): ByteArray =
        withContext(Dispatchers.IO) {
            val sealed = Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                DataOutputStream(socket.getOutputStream()).apply {
                    writeUTF(HELLO)
                    flush()
                }
                val input = DataInputStream(socket.getInputStream())
                val size = input.readInt()
                require(size in 1..MAX_BYTES) { "bad size" }
                ByteArray(size).also { input.readFully(it) }
            }
            open(sealed, code)
        }

    // ------------------------------------------------------------- crypto

    private fun key(code: String, salt: ByteArray): SecretKeySpec {
        // SHA-1 rather than SHA-256: the latter only exists from Android 8 on, and
        // the oldest boxes this app runs on are Android 6.
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val raw = factory.generateSecret(PBEKeySpec(code.toCharArray(), salt, 20_000, 256)).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun seal(plain: ByteArray, code: String): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(code, salt), GCMParameterSpec(128, iv))
        return MAGIC.toByteArray() + salt + iv + cipher.doFinal(plain)
    }

    private fun open(sealed: ByteArray, code: String): ByteArray {
        val magic = MAGIC.toByteArray()
        require(sealed.size > magic.size + 28 && sealed.copyOf(magic.size).contentEquals(magic)) {
            "not a Karacast transfer"
        }
        val salt = sealed.copyOfRange(magic.size, magic.size + 16)
        val iv = sealed.copyOfRange(magic.size + 16, magic.size + 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(code, salt), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(sealed, magic.size + 28, sealed.size - magic.size - 28)
        } catch (wrong: AEADBadTagException) {
            throw WrongCodeException()
        }
    }
}
