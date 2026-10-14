package ar.joaquin.airmix

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MixerInfo(val ip: String, val name: String, val model: String, val firmware: String)

/**
 * Conexión UDP con la consola (X Air, puerto 10024).
 * - Hilo RX: recibe todo; los medidores se parsean sin crear objetos.
 * - Hilo TX: envía con coalescencia (si movés un fader rápido, solo sale el último valor).
 * - Hilo keepalive: /xremote + suscripción a /meters/1 cada 8 s; reabre el socket si se pierde la red.
 */
class MixerClient(private val ctx: Context) {
    companion object {
        const val PORT = 10024
        const val METER_COUNT = 40
    }

    @Volatile private var socket: DatagramSocket? = null
    @Volatile var host: InetAddress? = null
        private set
    @Volatile private var running = false
    @Volatile var lastRx = 0L
        private set
    @Volatile private var lastKeep = 0L
    private var lastReopen = 0L

    private val pending = ConcurrentHashMap<String, ByteArray>()
    private val order = LinkedBlockingQueue<String>()
    val incoming = ConcurrentLinkedQueue<OscMessage>()
    val discovered = ConcurrentLinkedQueue<MixerInfo>()

    /** dB por medidor (índices del bloque /meters/1 de X Air). */
    val meters = FloatArray(METER_COUNT) { -100f }

    private val xremote = Osc.encode("/xremote")
    private val metersSub = Osc.encode("/meters", "/meters/1")
    private val xinfo = Osc.encode("/xinfo")
    private val meters1 = "/meters/1".toByteArray(Charsets.ISO_8859_1)

    fun start() {
        if (running) return
        running = true
        try { openSocket() } catch (_: Exception) {}
        spawn("osc-rx") { rxLoop() }
        spawn("osc-tx") { txLoop() }
        spawn("osc-keepalive") { keepAliveLoop() }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    fun connect(ip: String): Boolean {
        val addr = parseIp(ip) ?: return false
        host = addr
        lastRx = 0L
        lastKeep = 0L
        pending.clear()
        order.clear()
        return true
    }

    fun isConnected(): Boolean = host != null && SystemClock.uptimeMillis() - lastRx < 4000

    fun send(address: String, value: Any) = enqueue(address, Osc.encode(address, value))

    fun query(address: String) = enqueue("$address?", Osc.encode(address))

    private fun enqueue(key: String, bytes: ByteArray) {
        if (pending.put(key, bytes) == null) order.offer(key)
    }

    /** Busca consolas: broadcast de /xinfo en todas las redes + IP por defecto del access point. */
    fun discover() {
        spawn("osc-discover") {
            val s = socket ?: return@spawn
            val targets = ArrayList<InetAddress>()
            parseIp("255.255.255.255")?.let { targets.add(it) }
            try {
                for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (ia in ni.interfaceAddresses) ia.broadcast?.let { targets.add(it) }
                }
            } catch (_: Exception) {}
            parseIp("192.168.1.1")?.let { targets.add(it) }
            for (t in targets) {
                try { s.send(DatagramPacket(xinfo, xinfo.size, t, PORT)) } catch (_: Exception) {}
            }
        }
    }

    // ---------------------------------------------------------------- internos

    private fun spawn(name: String, body: () -> Unit) {
        val t = Thread(body, name)
        t.isDaemon = true
        t.start()
    }

    @Synchronized
    private fun openSocket() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        val s = DatagramSocket()
        s.broadcast = true
        s.soTimeout = 500
        bindToWifi(s)
        socket = s
    }

    /** Si la tablet tiene datos móviles, forzamos que el tráfico vaya por WiFi (donde está la consola). */
    private fun bindToWifi(s: DatagramSocket) {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            for (n in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    n.bindSocket(s)
                    return
                }
            }
        } catch (_: Exception) {}
    }

    private fun rxLoop() {
        val buf = ByteArray(8192)
        val pkt = DatagramPacket(buf, buf.size)
        while (running) {
            val s = socket
            if (s == null) {
                SystemClock.sleep(300)
                continue
            }
            try {
                pkt.length = buf.size
                s.receive(pkt)
            } catch (e: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                SystemClock.sleep(200)
                continue
            }
            val len = pkt.length
            val fromHost = pkt.address == host
            if (fromHost) lastRx = SystemClock.uptimeMillis()
            if (fromHost && parseMeters(buf, len)) continue
            val msg = Osc.decode(buf, len) ?: continue
            if (msg.address == "/xinfo" && msg.args.size >= 4) {
                discovered.add(
                    MixerInfo(
                        pkt.address.hostAddress ?: "",
                        msg.args[1].toString(), msg.args[2].toString(), msg.args[3].toString()
                    )
                )
            }
            if (fromHost) incoming.add(msg)
        }
    }

    /** /meters/1: blob con int32 LE (cantidad) + int16 LE por medidor (dB * 256). Sin asignar memoria. */
    private fun parseMeters(b: ByteArray, len: Int): Boolean {
        if (len < 24) return false
        for (i in meters1.indices) if (b[i] != meters1[i]) return false
        if (b[9] != 0.toByte()) return false
        if (b[12] != ','.code.toByte() || b[13] != 'b'.code.toByte()) return true
        val blobLen = ((b[16].toInt() and 0xff) shl 24) or ((b[17].toInt() and 0xff) shl 16) or
            ((b[18].toInt() and 0xff) shl 8) or (b[19].toInt() and 0xff)
        var p = 24
        val end = minOf(len, 20 + blobLen)
        var i = 0
        while (p + 1 < end && i < METER_COUNT) {
            val v = ((b[p + 1].toInt() shl 8) or (b[p].toInt() and 0xff)).toShort().toInt()
            meters[i] = v / 256f
            i++
            p += 2
        }
        return true
    }

    private fun txLoop() {
        while (running) {
            val key = try {
                order.poll(500, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                null
            } ?: continue
            val bytes = pending.remove(key) ?: continue
            val h = host ?: continue
            rawSend(bytes, h)
            if (key.endsWith("?")) SystemClock.sleep(1) // no saturar la consola con pedidos
        }
    }

    private fun rawSend(bytes: ByteArray, to: InetAddress) {
        try {
            socket?.send(DatagramPacket(bytes, bytes.size, to, PORT))
        } catch (_: Exception) {}
    }

    private fun keepAliveLoop() {
        while (running) {
            val now = SystemClock.uptimeMillis()
            if (socket == null) {
                try { openSocket() } catch (_: Exception) {}
            }
            val h = host
            if (h != null) {
                val alive = now - lastRx < 4000
                if (!alive && lastRx != 0L && now - lastReopen > 6000) {
                    // Se perdió la consola: puede haber cambiado la red. Reabrimos el socket.
                    lastReopen = now
                    try { openSocket() } catch (_: Exception) {}
                }
                if (now - lastKeep > (if (alive) 8000 else 1500)) {
                    lastKeep = now
                    rawSend(xremote, h)
                    rawSend(metersSub, h)
                    if (!alive) rawSend(xinfo, h)
                }
            }
            SystemClock.sleep(250)
        }
    }

    private fun parseIp(ip: String): InetAddress? {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return null
        val b = ByteArray(4)
        for (i in 0..3) {
            val n = parts[i].toIntOrNull() ?: return null
            if (n !in 0..255) return null
            b[i] = n.toByte()
        }
        return try { InetAddress.getByAddress(b) } catch (e: Exception) { null }
    }
}
