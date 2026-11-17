package ar.joaquin.airmix

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock

/**
 * SALA: mapa de riesgo de acople sin conectar nada (mismo algoritmo y constantes que Room.swift).
 *
 * Con ruido rosa sonando (parlante de la tablet, apoyada cerca de los micrófonos) la app recorre la fuente del RTA
 * (/-stat/rta/source) por cada micrófono elegido y promedia ~2 s. Mide dos pasadas: al nivel actual de la salida
 * y con la salida +3 dB. Donde el lazo parlante → micrófono está cerca de acoplar, esa banda crece bastante más
 * que el resto (con ganancia de lazo G, la señal es N/(1-G): subir G un 41 % la dispara cerca de G = 1).
 * Al terminar (o al cancelar, salir de SALA o perder la conexión) todo vuelve como estaba.
 */
object Room {
    const val SETTLE_MS = 800L
    const val MEASURE_MS = 2200L
    const val BOOST_DB = 3f
    /** Bandas más bajas que esto (en la pasada normal) no cuentan: es piso de ruido. */
    const val FLOOR_DB = -75f
    /** Crecimiento por encima de la mediana: medio / alto. */
    const val MID_DB = 2f
    const val HIGH_DB = 4f
    /** Pico sobre las bandas vecinas (en la pasada normal) que también se marca. */
    const val PEAK_DB = 10f
    /** Corte de seguridad en la pasada +3 dB: una banda que sube esto sobre la normal (y pasa -30 dB) corta la prueba. */
    const val ABORT_DB = 12f
    const val ABORT_LEVEL = -30f
    /** Rango que se analiza: 60 Hz .. 12 kHz (bins 16..91 de 100). */
    const val LO_BIN = 16
    const val HI_BIN = 91
    const val BINS = MixerClient.RTA_BINS

    class Alert(val hz: Float, val high: Boolean, val chName: String, val db: Float, val note: String) {
        val text: String
            get() = (if (high) "Riesgo ALTO" else "Riesgo medio") + " en ${Fmt.hz(hz)}" +
                (if (chName.isNotEmpty()) " (sobre todo en $chName)" else "") + (if (note.isNotEmpty()) " · $note" else "")
    }

    class Result(
        val outBus: Int, val outName: String, val score: FloatArray, val spectrum: FloatArray,
        val alerts: List<Alert>, val aborted: Boolean, val at: Long
    )

    /** Canales elegidos como micrófonos (índice = canal - 1). userPicked: ya no se tildan solos. */
    val mics = BooleanArray(32)
    var userPicked = false
    /** Salida a probar: 0 = MAIN LR, 1.. = bus de monitor. */
    var outBus = 0

    var running = false
        private set
    var status = ""
        private set
    var result: Result? = null
        private set
    /** La pantalla de SALA se refresca con esto (no se llama en cada tick). */
    var onChange: (() -> Unit)? = null

    private var chans = IntArray(0)
    private var names = arrayOf<String>()
    private var pass = 0
    private var step = 0
    private var stepStart = 0L
    private val sum = DoubleArray(BINS)
    private var count = 0
    private var p0 = Array(0) { FloatArray(BINS) }
    private var p1 = Array(0) { FloatArray(BINS) }
    private var prevSrc: Int? = null
    private var faderAddr = ""
    private var faderOrig = 0f
    private var boosted = false

    fun outStrip(bus: Int = outBus): Strip = if (bus > 0) Strip(Kind.BUS, bus) else Strip(Kind.LR)

    private fun outName(bus: Int): String {
        val s = outStrip(bus)
        val n = App.store.get(s.nameAddr)?.toString()?.trim().orEmpty()
        return if (bus > 0) SendTarget(bus = bus).display(n) else "MAIN LR"
    }

    fun chName(ch: Int): String {
        val s = Strip(Kind.CH, ch)
        val n = App.store.get(s.nameAddr)?.toString()?.trim().orEmpty()
        return n.ifEmpty { s.title }
    }

    /** Arranca la medición. Devuelve un mensaje de error (o null si arrancó). */
    fun start(): String? {
        if (running) return null
        val c = App.client
        if (c == null || !c.isConnected()) return "Conectate a la consola primero."
        if (App.console.isX32) return "SALA por ahora es solo para X Air (XR12 / XR16 / XR18): en X32 la fuente del RTA no está verificada."
        val list = (1..App.console.channels).filter { mics[it - 1] }
        if (list.isEmpty()) return "Elegí al menos un micrófono."
        val out = outStrip()
        faderAddr = out.faderAddr
        val f = App.store.get(faderAddr)?.asF() ?: return "Todavía no llegó el nivel de ${outName(outBus)}: probá de nuevo en un segundo."
        if (f <= 0f) return "${outName(outBus)} está en -∞: subilo a su nivel de show."
        if (App.store.int(out.onAddr, 1) == 0) return "${outName(outBus)} está muteado: desmutealo a volumen moderado."
        if (Fmt.faderToDb(f) > 10f - 1f) return "${outName(outBus)} está casi al máximo: no hay lugar para la prueba de +3 dB."
        faderOrig = f
        boosted = false
        chans = list.toIntArray()
        names = Array(chans.size) { chName(chans[it]) }
        p0 = Array(chans.size) { FloatArray(BINS) }
        p1 = Array(chans.size) { FloatArray(BINS) }
        prevSrc = App.store.get(RTA_SOURCE)?.asI()
        pass = 0
        step = 0
        running = true
        PinkNoise.start()
        c.setRta(true)
        beginStep(SystemClock.uptimeMillis())
        return null
    }

    fun cancel(why: String) {
        if (!running) return
        restore()
        status = why
        onChange?.invoke()
    }

    private fun restore() {
        running = false
        if (boosted) {
            App.store.set(faderAddr, faderOrig)
            boosted = false
        }
        val prev = prevSrc
        if (prev != null) App.store.set(RTA_SOURCE, prev)
        App.client?.setRta(false)
        PinkNoise.stop()
    }

    private fun beginStep(now: Long) {
        App.store.set(RTA_SOURCE, chans[step] - 1)
        stepStart = now
        java.util.Arrays.fill(sum, 0.0)
        count = 0
        status = (if (pass == 0) "Midiendo al nivel actual" else "Midiendo con ${outName(outBus)} +3 dB") +
            " · ${names[step]} (${step + 1}/${chans.size})"
        onChange?.invoke()
    }

    /** Lo llama el tick de 30 fps de la pantalla principal. Sin asignaciones. */
    fun tick(now: Long) {
        if (!running) return
        val c = App.client
        if (c == null || !c.isConnected()) {
            cancel("Se cortó la conexión: la medición se canceló y quedó todo como estaba.")
            return
        }
        val el = now - stepStart
        if (el < SETTLE_MS) return
        val m = c.rta
        if (m[0] <= -99.5f && m[BINS / 2] <= -99.5f) return // todavía no llegó el RTA
        if (pass == 1) {
            val ref = p0[step]
            for (i in LO_BIN..HI_BIN) {
                if (m[i] > ABORT_LEVEL && m[i] > ref[i] + ABORT_DB) {
                    hot(i)
                    return
                }
            }
        }
        for (i in 0 until BINS) sum[i] += Math.pow(10.0, m[i] / 10.0)
        count++
        if (el < SETTLE_MS + MEASURE_MS) return
        val dst = if (pass == 0) p0[step] else p1[step]
        for (i in 0 until BINS) dst[i] = if (count > 0) (10.0 * Math.log10(sum[i] / count + 1e-12)).toFloat() else -100f
        step++
        if (step < chans.size) {
            beginStep(now)
            return
        }
        if (pass == 0) {
            pass = 1
            step = 0
            val db = Fmt.faderToDb(faderOrig) + BOOST_DB
            App.store.set(faderAddr, Fmt.dbToFader(db).coerceIn(0f, 1f))
            boosted = true
            beginStep(now)
            return
        }
        restore()
        result = analyze(false, -1)
        status = "Listo. Todo volvió como estaba."
        onChange?.invoke()
    }

    /** Una banda se disparó con +3 dB: se corta enseguida (casi acopla) y se informa. */
    private fun hot(bin: Int) {
        val ch = step
        restore()
        result = analyze(true, bin, ch)
        status = "Medición cortada: con +3 dB casi acopla en ${Fmt.hz(binHz(bin))}. Todo volvió como estaba."
        onChange?.invoke()
    }

    /** "2.5k" / "250" para el gráfico. */
    fun shortHz(hz: Float): String =
        if (hz >= 1000f) String.format(java.util.Locale.US, "%.1fk", hz / 1000f) else String.format(java.util.Locale.US, "%.0f", hz)

    fun binHz(i: Int): Float = (20.0 * Math.pow(1000.0, i / (BINS - 1.0))).toFloat()

    private fun analyze(aborted: Boolean, hotBin: Int, hotCh: Int = -1): Result {
        val n = chans.size
        val done = if (aborted) pass.let { if (it == 1) step else 0 } else n
        val score = FloatArray(BINS) { -100f }
        val worst = IntArray(BINS) { -1 }
        val tmp = FloatArray(BINS)
        val grow = FloatArray(BINS)
        for (k in 0 until done) {
            var nv = 0
            for (i in LO_BIN..HI_BIN) if (p0[k][i] > FLOOR_DB) tmp[nv++] = p1[k][i] - p0[k][i]
            if (nv < 10) continue
            java.util.Arrays.sort(tmp, 0, nv)
            val med = tmp[nv / 2]
            for (i in 0 until BINS) grow[i] = if (p0[k][i] > FLOOR_DB) p1[k][i] - p0[k][i] - med else 0f
            for (i in LO_BIN..HI_BIN) {
                val e = (grow[i - 1] + grow[i] + grow[i + 1]) / 3f
                if (e > score[i]) {
                    score[i] = e
                    worst[i] = k
                }
            }
        }
        // Espectro promedio (potencia) de la pasada normal y picos sobre las vecinas.
        val spec = FloatArray(BINS)
        val have = if (pass == 0 && aborted) 0 else n
        for (i in 0 until BINS) {
            var s = 0.0
            for (k in 0 until have) s += Math.pow(10.0, p0[k][i] / 10.0)
            spec[i] = if (have > 0) (10.0 * Math.log10(s / have + 1e-12)).toFloat() else -100f
        }
        val risk = FloatArray(BINS)
        val peakAt = BooleanArray(BINS)
        for (i in LO_BIN..HI_BIN) {
            var around = 0f
            for (d in 3..6) around += spec[i - d] + spec[i + d]
            around /= 8f
            val peak = spec[i] > FLOOR_DB && spec[i] - around >= PEAK_DB
            peakAt[i] = peak
            risk[i] = maxOf(score[i], if (peak) MID_DB else 0f)
        }
        val alerts = ArrayList<Alert>()
        if (hotBin >= 0) {
            alerts.add(Alert(binHz(hotBin), true, if (hotCh >= 0) names[hotCh] else "", ABORT_DB, "casi acopla con +3 dB"))
            risk[hotBin] = 99f
        }
        val used = BooleanArray(BINS)
        if (hotBin >= 0) for (d in -3..3) if (hotBin + d in 0 until BINS) used[hotBin + d] = true
        while (alerts.size < 5) {
            var best = -1
            for (i in LO_BIN..HI_BIN) if (!used[i] && risk[i] >= MID_DB && (best < 0 || risk[i] > risk[best])) best = i
            if (best < 0) break
            for (d in -3..3) if (best + d in 0 until BINS) used[best + d] = true
            val high = score[best] >= HIGH_DB
            val who = worst[best]
            val note = if (peakAt[best] && score[best] < MID_DB) "pico en la sala" else ""
            alerts.add(Alert(binHz(best), high, if (who >= 0 && score[best] >= MID_DB) names[who] else "", maxOf(score[best], 0f), note))
        }
        return Result(outBus, outName(outBus), score, spec, alerts, aborted, System.currentTimeMillis())
    }

    /** Para las capturas: un resultado de ejemplo. */
    internal fun demo() {
        val score = FloatArray(BINS) { 0.4f * Math.sin(it / 5.0).toFloat() }
        val spec = FloatArray(BINS) { -38f - it * 0.12f + 3f * Math.sin(it / 7.0).toFloat() }
        score[73] = 5.6f; score[72] = 3.9f; score[74] = 3.5f
        score[55] = 2.7f; score[56] = 2.1f
        spec[38] += 12f
        result = Result(0, "MAIN LR", score, spec, listOf(
            Alert(binHz(73), true, "VOZ GARKO", 5.6f, ""),
            Alert(binHz(55), false, "VOZ PALMI", 2.7f, ""),
            Alert(binHz(38), false, "", 0f, "pico en la sala")
        ), false, System.currentTimeMillis())
        status = "Listo. Todo volvió como estaba."
        mics[0] = true; mics[1] = true; mics[2] = true
        userPicked = true
    }
}

/** Ruido rosa por el parlante de la tablet (filtro de Paul Kellet sobre ruido blanco). Igual que PinkNoise en iOS. */
object PinkNoise {
    /** Nivel de salida (0..1): moderado; el volumen final lo pone la tablet. */
    const val LEVEL = 0.3f
    @Volatile private var on = false
    /** Cada arranque es una generación nueva: el hilo anterior (si quedaba) se apaga con su fundido. */
    @Volatile private var gen = 0
    val isOn: Boolean get() = on

    fun start() {
        if (on) return
        on = true
        val g = ++gen
        Thread({ run(g) }, "pink-noise").start()
    }

    fun stop() {
        on = false
    }

    private fun run(myGen: Int) {
        val sr = 48000
        val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            return
        }
        val buf = ShortArray(1024)
        val rnd = java.util.Random()
        var b0 = 0f; var b1 = 0f; var b2 = 0f; var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f
        var gain = 0f
        val step = 1f / (sr * 0.3f) // fundido de 0,3 s (sin golpes)
        try {
            track.play()
            while (true) {
                val alive = on && gen == myGen
                val target = if (alive) 1f else 0f
                for (j in buf.indices) {
                    val w = rnd.nextFloat() * 2f - 1f
                    b0 = 0.99886f * b0 + w * 0.0555179f
                    b1 = 0.99332f * b1 + w * 0.0750759f
                    b2 = 0.96900f * b2 + w * 0.1538520f
                    b3 = 0.86650f * b3 + w * 0.3104856f
                    b4 = 0.55000f * b4 + w * 0.5329522f
                    b5 = -0.7616f * b5 - w * 0.0168980f
                    val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f) * 0.11f
                    b6 = w * 0.115926f
                    gain = if (gain < target) minOf(gain + step, target) else maxOf(gain - step, target)
                    buf[j] = (pink * gain * LEVEL * 32767f).coerceIn(-32767f, 32767f).toInt().toShort()
                }
                track.write(buf, 0, buf.size)
                if (!alive && gain <= 0f) break
            }
            track.stop()
        } catch (_: Exception) {
        } finally {
            track.release()
        }
    }
}
