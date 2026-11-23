package ar.joaquin.airmix

/**
 * MODO DEMO: simula una XR12 sin consola (igual que Demo.swift).
 * - Nada sale por la red mientras está prendido (MixerClient.demo descarta todo lo que se manda).
 * - Valores de ejemplo: nombres, faders, mutes, envíos, EQ y efectos; lo que no está en la tabla toma un valor neutro.
 * - Medidores (y RTA) con señal simulada desde el tick de 30 fps, sin asignaciones.
 * - Para SALA, escenas y el aviso de actualización es como estar sin conexión (isConnected() sigue en false).
 */
object Demo {
    @Volatile var on = false
        private set

    const val MODEL = "XR12"
    private const val CH = 12

    val NAMES = arrayOf(
        "VOZ GARKO", "VOZ PALMI", "GUITARRA", "CAJON", "BAJO", "TECLADO L",
        "TECLADO R", "PISTA L", "PISTA R", "CLICK", "PERC", "AMBIENTE"
    )
    private val FADERS = floatArrayOf(0.72f, 0.70f, 0.64f, 0.60f, 0.62f, 0.55f, 0.55f, 0.58f, 0.58f, 0.0f, 0.50f, 0.45f)
    private val PANS = floatArrayOf(0.4f, 0.6f, 0.3f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 1.0f, 0.5f, 0.7f, 0.5f)
    private val COLORS = intArrayOf(1, 1, 3, 3, 4, 5, 5, 6, 6, 7, 3, 2)
    /** Envío a los buses 1-2 (monitores) y a los FX 1-4. */
    private val MON1 = floatArrayOf(0.70f, 0.35f, 0.55f, 0.50f, 0.45f, 0.30f, 0.30f, 0.40f, 0.40f, 0.65f, 0.30f, 0f)
    private val MON2 = floatArrayOf(0.35f, 0.70f, 0.50f, 0.55f, 0.45f, 0.30f, 0.30f, 0.40f, 0.40f, 0.65f, 0.30f, 0f)
    private val REV = floatArrayOf(0.45f, 0.45f, 0.30f, 0.15f, 0f, 0.20f, 0.20f, 0f, 0f, 0f, 0.25f, 0f)
    /** Cada canal: nivel medio (dB), cuánto se mueve y fase (para que no se muevan todos juntos). */
    private val BASE = floatArrayOf(-16f, -18f, -20f, -14f, -17f, -22f, -22f, -15f, -15f, -12f, -24f, -32f)
    private val SWING = floatArrayOf(10f, 10f, 6f, 9f, 5f, 4f, 4f, 3f, 3f, 2f, 8f, 3f)
    private val PHASE = floatArrayOf(0f, 2.1f, 0.7f, 1.3f, 2.9f, 0.4f, 0.45f, 1.9f, 1.95f, 0f, 3.4f, 5.0f)
    private val FX_TYPES = intArrayOf(0, 10, 13, 21)

    // Direcciones precalculadas (el tick no arma strings).
    private val faderAddr = Array(CH) { "/ch/${Fmt.two(it + 1)}/mix/fader" }
    private val onAddr = Array(CH) { "/ch/${Fmt.two(it + 1)}/mix/on" }
    private val lvl = FloatArray(CH) { -90f }
    private var seed = 12345

    /** Prende el modo demo: borra lo que había de la consola real y carga la XR12 de ejemplo. */
    fun start() {
        on = true
        App.client?.demo = true
        java.util.Arrays.fill(lvl, -90f)
        App.store.resetValues()
        App.applyModel(MODEL)
        seedValues()
        App.store.fillMissing()
    }

    /** Apaga el modo demo: se olvidan los valores de ejemplo y se vuelve a pedir todo a la consola (si hay). */
    fun stop() {
        on = false
        App.client?.demo = false
        App.client?.let {
            java.util.Arrays.fill(it.meters, -100f)
            java.util.Arrays.fill(it.rta, -100f)
        }
        App.store.resetValues()
    }

    private fun put(a: String, v: Any) = App.store.incoming(a, v)

    private fun seedValues() {
        for (i in 0 until CH) {
            val p = "/ch/${Fmt.two(i + 1)}"
            put("$p/config/name", NAMES[i])
            put("$p/config/color", COLORS[i])
            put("$p/config/insrc", i)
            put("$p/mix/fader", FADERS[i])
            put("$p/mix/on", if (i == 9) 0 else 1) // el CLICK no va al PA
            put("$p/mix/pan", PANS[i])
            put("$p/mix/lr", if (i == 9) 0 else 1)
            put("$p/mix/01/level", MON1[i])
            put("$p/mix/02/level", MON2[i])
            put("$p/mix/07/level", REV[i])
            put("$p/mix/08/level", REV[i] * 0.6f)
            put("$p/mix/09/level", if (i < 2) 0.35f else 0f)
            put("$p/mix/10/level", if (i == 2) 0.3f else 0f)
            put("$p/dyn/on", if (i < 5) 1 else 0)
            put("$p/gate/on", 0)
        }
        put("/headamp/01/phantom", 0)
        put("/headamp/04/phantom", 1)
        put("/bus/1/config/name", "MON GARKO")
        put("/bus/2/config/name", "MON PALMI")
        put("/bus/1/config/color", 4)
        put("/bus/2/config/color", 5)
        val fxNames = arrayOf("REVERB", "PLACA", "DELAY", "CHORUS")
        for (n in 1..4) {
            put("/fx/$n/type", FX_TYPES[n - 1])
            for (k in 1..12) put("/fx/$n/par/${Fmt.two(k)}", (k * 7 % 10) / 10f)
            put("/fxsend/$n/config/name", fxNames[n - 1])
            put("/rtn/$n/config/name", fxNames[n - 1])
            put("/rtn/$n/config/color", 6)
            put("/rtn/$n/mix/fader", 0.6f)
        }
        put("/lr/config/name", "MAIN")
        put("/lr/mix/fader", 0.75f)
        put("/lr/mix/on", 1)
        put("/-stat/solo", 0)
    }

    /**
     * Valor neutro para una dirección que no está en la tabla (igual que Demo.valueFor en iOS).
     * Las consolas X Air usan 0..1 para casi todo: 0,5 queda en el medio (EQ en 0 dB, paneo al centro).
     */
    fun valueFor(a: String): Any {
        if (a.endsWith("/name")) return ""
        if (a.endsWith("/on")) return if (a.contains("/gate/")) 0 else 1
        if (a.endsWith("/mix/lr") || a.endsWith("/mix/st")) return 1
        if (a.endsWith("/fader")) return 0.75f
        if (a.endsWith("/level")) return 0.4f
        if (a.startsWith("/-stat/") || a.startsWith("/config/")) return 0
        val eq = a.indexOf("/eq/")
        if (eq >= 0) {
            val band = a.substring(eq + 4).substringBefore('/').toIntOrNull()
            if (band != null) {
                val last = if (a.startsWith("/ch/") || a.startsWith("/rtn/")) 4 else 6
                if (a.endsWith("/type")) return when (band) { 1 -> 1; last -> 4; else -> 2 }
                if (a.endsWith("/f")) return (band.toFloat() / (last + 1)).coerceIn(0.05f, 0.95f)
            }
        }
        return 0.5f
    }

    /** Señal simulada: la llama el tick de 30 fps. Sin asignaciones. */
    fun tick(m: FloatArray, rta: FloatArray, rtaOn: Boolean, nowMs: Long) {
        val t = nowMs / 1000.0
        var sumL = 0.0
        var sumR = 0.0
        var mon1 = 0.0
        var mon2 = 0.0
        for (i in 0 until CH) {
            // Frases: cada tanto el canal "descansa" (voces sobre todo).
            val phrase = Math.sin(t * 0.37 + PHASE[i] * 1.7)
            val rest = i < 2 && phrase < -0.35
            val wob = Math.sin(t * (2.3 + i * 0.41) + PHASE[i]) * 0.6 + Math.sin(t * (5.1 + i * 0.27) + PHASE[i] * 2) * 0.4
            var db = BASE[i] + SWING[i] * 0.5f * wob.toFloat() + noise() * 2.5f
            if (i == 9) db = if ((t * 2.0) % 1.0 < 0.12) -12f else -60f // CLICK: golpes
            if (rest) db = -55f + noise() * 3f
            db = db.coerceIn(-90f, -3f)
            // Caída suave (como un medidor real).
            lvl[i] = if (db > lvl[i]) db else maxOf(db, lvl[i] - 1.2f)
            m[i] = lvl[i]
            val st = App.store
            val f = st.float(faderAddr[i], 0f)
            if (st.int(onAddr[i], 1) != 0 && f > 0f) {
                val p = Math.pow(10.0, (lvl[i] + Fmt.faderToDb(f)) / 10.0)
                val pan = PANS[i].toDouble()
                sumL += p * (1.0 - pan) * 2.0
                sumR += p * pan * 2.0
            }
            val pw = Math.pow(10.0, lvl[i] / 10.0)
            mon1 += pw * MON1[i]
            mon2 += pw * MON2[i]
        }
        for (i in CH until 16) m[i] = -100f
        m[16] = -100f; m[17] = -100f
        // FX returns (8 medidores, L/R) y FX sends: siguen a las voces.
        val voice = maxOf(lvl[0], lvl[1])
        for (k in 0 until 4) {
            val v = (voice - 14f - k * 4f + noise()).coerceIn(-90f, -6f)
            m[18 + 2 * k] = v
            m[19 + 2 * k] = v - 1f
            m[32 + k] = v + 3f
        }
        m[26] = db(mon1) - 4f
        m[27] = db(mon2) - 4f
        for (i in 28..31) m[i] = -100f
        val lrF = App.store.float("/lr/mix/fader", 0.75f)
        val lrOn = App.store.int("/lr/mix/on", 1) != 0
        val lrDb = if (lrOn && lrF > 0f) Fmt.faderToDb(lrF) else -200f
        m[36] = (db(sumL) + lrDb).coerceIn(-100f, -2f)
        m[37] = (db(sumR) + lrDb).coerceIn(-100f, -2f)
        if (rtaOn) {
            val ph = t * 0.8
            for (b in rta.indices) {
                val shape = -28f - b * 0.22f + 7f * Math.sin(b / 6.5 + ph).toFloat() + 3f * Math.sin(b / 2.3 - ph * 1.7).toFloat()
                rta[b] = (shape + noise() * 2f).coerceIn(-90f, -6f)
            }
        }
    }

    private fun db(p: Double): Float = if (p > 1e-12) (10.0 * Math.log10(p)).toFloat() else -100f

    /** Ruido -1..1 (LCG): sin Random ni asignaciones. */
    private fun noise(): Float {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 8) and 0xffff) / 32768f - 1f
    }
}
