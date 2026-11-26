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

class MixerInfo(val ip: String, val port: Int, val name: String, val model: String, val firmware: String)

/**
 * Conexión UDP con la consola (X Air, puerto 10024).
 * - Hilo RX: recibe todo; los medidores se parsean sin crear objetos.
 * - Hilo TX: envía con coalescencia (si movés un fader rápido, solo sale el último valor).
 * - Hilo keepalive: /xremote + suscripción a /meters/1 cada 8 s; reabre el socket si se pierde la red.
 */
class MixerClient(private val ctx: Context) {
    companion object {
        const val PORT_XAIR = 10024
        const val PORT_X32 = 10023
        const val METER_COUNT = 96
        const val RTA_BINS = 100
        const val GR_COUNT = 39
        const val PING_EVERY_MS = 1000L
        const val LOST_MS = 400f
    }

    @Volatile private var socket: DatagramSocket? = null
    @Volatile var host: InetAddress? = null
        private set
    @Volatile private var running = false
    @Volatile var lastRx = 0L
        private set
    @Volatile private var lastKeep = 0L
    /** Puerto de la consola: 10024 (X Air) o 10023 (X32). 0 = todavía no se sabe, se prueban los dos. */
    @Volatile var port = 0
        private set
    /** Formato de medidores: X32 manda floats lineales, X Air int16 en dB*256. */
    @Volatile var x32 = false
    private var lastReopen = 0L

    /** MODO DEMO: no sale nada por la red y no se escucha a la consola (ver Demo). */
    @Volatile var demo = false

    /**
     * Calidad del wifi: tiempo de respuesta de la consola a /xinfo (un pedido liviano que siempre contesta),
     * suavizado. -1 = sin dato (sin conexión o en demo). Un pedido sin respuesta en 1 s cuenta como LOST_MS.
     */
    @Volatile var rttMs = -1f
        private set
    @Volatile private var pingAt = 0L
    private var lastPing = 0L

    private val pending = ConcurrentHashMap<String, ByteArray>()
    private val order = LinkedBlockingQueue<String>()
    val incoming = ConcurrentLinkedQueue<OscMessage>()
    val discovered = ConcurrentLinkedQueue<MixerInfo>()

    /** dB por medidor (índices del bloque /meters/1 de X Air). */
    val meters = FloatArray(METER_COUNT) { -100f }

    /** RTA de la consola: 100 bandas de 20 Hz a 20 kHz, en dB (X Air /meters/4, X32 /meters/15). */
    val rta = FloatArray(RTA_BINS) { -100f }
    /**
     * Reducción de ganancia (X Air /meters/6, dB): 16 gate de canal, 16 compresor de canal, 6 compresor de bus,
     * 1 compresor del main. Solo se pide mientras hay un GATE o un COMPRESOR en pantalla.
     */
    val gr = FloatArray(GR_COUNT)
    @Volatile var grWanted = false
        private set

    /** Solo se pide el RTA mientras hay un EQ en pantalla. */
    @Volatile var rtaWanted = false
        private set

    private val xremote = Osc.encode("/xremote")
    private val metersSubXair = Osc.encode("/meters", "/meters/1")
    private val metersSubX32a = Osc.encode("/meters", "/meters/0")
    private val metersSubX32b = Osc.encode("/meters", "/meters/2")
    private val rtaSubXair = Osc.encode("/meters", "/meters/4")
    private val grSubXair = Osc.encode("/meters", "/meters/6")
    private val rtaSubX32 = Osc.encode("/meters", "/meters/15")
    private val xinfo = Osc.encode("/xinfo")
    private val meterPrefix = "/meters/".toByteArray(Charsets.ISO_8859_1)

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

    /** knownPort = 0 si no se sabe (IP escrita a mano): se detecta con la primera respuesta. */
    fun connect(ip: String, knownPort: Int = 0): Boolean {
        val addr = parseIp(ip) ?: return false
        host = addr
        port = knownPort
        lastRx = 0L
        lastKeep = 0L
        rttMs = -1f
        pingAt = 0L
        pending.clear()
        order.clear()
        return true
    }

    fun isConnected(): Boolean = host != null && SystemClock.uptimeMillis() - lastRx < 4000

    fun send(address: String, value: Any) = enqueue(address, Osc.encode(address, value))

    /** Prende/apaga el RTA. Al prender se suscribe enseguida (y el keepalive lo renueva). */
    fun setRta(on: Boolean) {
        rtaWanted = on
        java.util.Arrays.fill(rta, -100f)
        if (on) enqueue("sub:rta", if (x32) rtaSubX32 else rtaSubXair)
    }

    /** Medidor de reducción del gate y del compresor (solo X Air). */
    fun setGr(on: Boolean) {
        grWanted = on && !x32
        java.util.Arrays.fill(gr, 0f)
        if (grWanted) enqueue("sub:gr", grSubXair)
    }

    fun query(address: String) = enqueue("$address?", Osc.encode(address))

    /** Pide un bloque completo en formato texto (para escenas .scn). */
    fun node(path: String) = enqueue("node:$path", Osc.encode("/node", path))

    private fun enqueue(key: String, bytes: ByteArray) {
        if (demo) return
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
                try { s.send(DatagramPacket(xinfo, xinfo.size, t, PORT_XAIR)) } catch (_: Exception) {}
                try { s.send(DatagramPacket(xinfo, xinfo.size, t, PORT_X32)) } catch (_: Exception) {}
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
            if (demo) continue
            val fromHost = pkt.address == host
            if (fromHost) {
                lastRx = SystemClock.uptimeMillis()
                if (port == 0) port = pkt.port
            }
            if (fromHost && parseMeters(buf, len)) continue
            val msg = Osc.decode(buf, len) ?: continue
            if (fromHost && msg.address == "/xinfo") {
                val sent = pingAt
                if (sent != 0L) {
                    pingAt = 0L
                    rttSample((SystemClock.uptimeMillis() - sent).toFloat())
                }
            }
            if (msg.address == "/xinfo" && msg.args.size >= 4) {
                discovered.add(
                    MixerInfo(
                        pkt.address.hostAddress ?: "", pkt.port,
                        msg.args[1].toString(), msg.args[2].toString(), msg.args[3].toString()
                    )
                )
            }
            if (fromHost) incoming.add(msg)
        }
    }

    /**
     * Medidores, sin asignar memoria.
     * X Air /meters/1: int32 LE (cantidad) + int16 LE por medidor (dB * 256).
     * X32 /meters/0: 70 floats LE lineales (32 ch, 8 aux, 8 fx rtn, 16 bus, 6 mtx).
     * X32 /meters/2: buses/matrices/main; tomamos Main L/R (índices 22/23) y los guardamos en 70/71.
     */
    private fun parseMeters(b: ByteArray, len: Int): Boolean {
        if (len < 24) return false
        for (i in meterPrefix.indices) if (b[i] != meterPrefix[i]) return false
        val d0 = b[8].toInt() - '0'.code
        val which = when {
            b[9] == 0.toByte() -> d0
            b[10] == 0.toByte() && b[9].toInt() in 48..57 -> d0 * 10 + (b[9].toInt() - '0'.code)
            else -> return false
        }
        if (b[12] != ','.code.toByte() || b[13] != 'b'.code.toByte()) return true
        val blobLen = ((b[16].toInt() and 0xff) shl 24) or ((b[17].toInt() and 0xff) shl 16) or
            ((b[18].toInt() and 0xff) shl 8) or (b[19].toInt() and 0xff)
        val end = minOf(len, 20 + blobLen)
        var p = 24
        if ((!x32 && which == 4) || (x32 && which == 15)) {
            // RTA: int16 LE en dB*256 (igual en X Air y X32).
            var i = 0
            while (p + 1 < end && i < RTA_BINS) {
                val v = ((b[p + 1].toInt() shl 8) or (b[p].toInt() and 0xff)).toShort().toInt()
                rta[i] = v / 256f
                i++
                p += 2
            }
            return true
        }
        if (!x32 && which == 6) {
            var i = 0
            while (p + 1 < end && i < GR_COUNT) {
                val v = ((b[p + 1].toInt() shl 8) or (b[p].toInt() and 0xff)).toShort().toInt()
                gr[i] = Math.abs(v / 256f)
                i++
                p += 2
            }
            return true
        }
        if (!x32) {
            if (which != 1) return true
            var i = 0
            while (p + 1 < end && i < METER_COUNT) {
                val v = ((b[p + 1].toInt() shl 8) or (b[p].toInt() and 0xff)).toShort().toInt()
                meters[i] = v / 256f
                i++
                p += 2
            }
        } else {
            var i = 0
            while (p + 3 < end) {
                val bits = (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8) or
                    ((b[p + 2].toInt() and 0xff) shl 16) or ((b[p + 3].toInt() and 0xff) shl 24)
                val v = java.lang.Float.intBitsToFloat(bits)
                val db = if (v > 0.00001f) (20.0 * Math.log10(v.toDouble())).toFloat() else -100f
                if (which == 0 && i < 70) meters[i] = db
                if (which == 2 && i == 22) meters[70] = db
                if (which == 2 && i == 23) meters[71] = db
                i++
                p += 4
                if (which == 2 && i > 23) break
            }
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
            sendToMixer(bytes, h)
            if (key.endsWith("?")) SystemClock.sleep(2) // no saturar la consola con pedidos
        }
    }

    private fun rawSend(bytes: ByteArray, to: InetAddress, p: Int) {
        try {
            socket?.send(DatagramPacket(bytes, bytes.size, to, p))
        } catch (_: Exception) {}
    }

    /** Si todavía no sabemos el puerto, mandamos a los dos (solo una de las dos consolas existe en esa IP). */
    private fun sendToMixer(bytes: ByteArray, to: InetAddress) {
        val p = port
        if (p != 0) rawSend(bytes, to, p)
        else {
            rawSend(bytes, to, PORT_XAIR)
            rawSend(bytes, to, PORT_X32)
        }
    }

    private fun keepAliveLoop() {
        while (running) {
            val now = SystemClock.uptimeMillis()
            if (socket == null) {
                try { openSocket() } catch (_: Exception) {}
            }
            val h = host
            if (demo) {
                rttMs = -1f
                pingAt = 0L
            } else if (h != null) {
                val alive = now - lastRx < 4000
                // Calidad del wifi: un /xinfo por segundo mientras hay conexión.
                if (!alive) {
                    rttMs = -1f
                    pingAt = 0L
                } else if (now - lastPing >= PING_EVERY_MS) {
                    if (pingAt != 0L) rttSample(LOST_MS)
                    lastPing = now
                    pingAt = now
                    sendToMixer(xinfo, h)
                }
                if (!alive && lastRx != 0L && now - lastReopen > 6000) {
                    // Se perdió la consola: puede haber cambiado la red. Reabrimos el socket.
                    lastReopen = now
                    try { openSocket() } catch (_: Exception) {}
                }
                if (now - lastKeep > (if (alive) 8000 else 1500)) {
                    lastKeep = now
                    sendToMixer(xremote, h)
                    if (x32) {
                        sendToMixer(metersSubX32a, h)
                        sendToMixer(metersSubX32b, h)
                    } else {
                        sendToMixer(metersSubXair, h)
                    }
                    if (rtaWanted) sendToMixer(if (x32) rtaSubX32 else rtaSubXair, h)
                    if (grWanted && !x32) sendToMixer(grSubXair, h)
                    if (!alive) sendToMixer(xinfo, h)
                }
            }
            SystemClock.sleep(250)
        }
    }

    private fun rttSample(ms: Float) {
        val r = rttMs
        rttMs = if (r < 0f) ms else r * 0.7f + ms * 0.3f
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
