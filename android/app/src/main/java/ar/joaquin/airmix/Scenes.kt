package ar.joaquin.airmix

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Locale

/**
 * Escenas .scn compatibles con X-Air Edit / X32-Edit.
 *
 * GUARDAR: se le pide a la consola cada bloque con "/node ,s ch/01/config" y la respuesta
 * ya viene en el mismo formato de texto que usan los .scn. Se escriben tal cual.
 *
 * CARGAR: las X Air NO aceptan ese texto de vuelta, así que cada línea se traduce a mensajes OSC
 * con tipo (int/float/string). Para no mandar nada mal traducido, antes de cargar se "calibra":
 * se leen algunos canales de muestra en texto y en valores reales, y solo se cargan los campos
 * cuya traducción coincide con lo que reporta la consola.
 */
object Scenes {
    var onStatus: ((String) -> Unit)? = null
    var onSaveReady: ((String, String) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------ lista de bloques

    fun nodeList(): List<String> {
        val c = App.console
        val l = ArrayList<String>()
        if (!c.isX32) {
            for (i in 1..c.channels) {
                val p = "ch/${Fmt.two(i)}"
                for (s in listOf("config", "preamp", "gate", "gate/filter", "dyn", "dyn/filter", "insert", "eq")) l.add("$p/$s")
                for (b in 1..4) l.add("$p/eq/$b")
                l.add("$p/mix")
                for (b in 1..c.buses) l.add("$p/mix/${Fmt.two(b)}")
                for (f in 7..10) l.add("$p/mix/${Fmt.two(f)}")
                l.add("$p/grp")
                l.add("$p/automix")
            }
            for (i in 1..c.headamps) l.add("headamp/${Fmt.two(i)}")
            val rtns = listOf("rtn/aux") + (1..4).map { "rtn/$it" }
            for (p in rtns) {
                for (s in listOf("config", "preamp", "eq")) l.add("$p/$s")
                for (b in 1..4) l.add("$p/eq/$b")
                l.add("$p/mix")
                for (b in 1..c.buses) l.add("$p/mix/${Fmt.two(b)}")
                l.add("$p/grp")
            }
            for (i in 1..c.buses) {
                val p = "bus/$i"
                for (s in listOf("config", "dyn", "dyn/filter", "insert", "eq")) l.add("$p/$s")
                for (b in 1..6) l.add("$p/eq/$b")
                l.add("$p/mix")
                l.add("$p/grp")
            }
            for (i in 1..4) {
                l.add("fxsend/$i/config"); l.add("fxsend/$i/mix"); l.add("fxsend/$i/grp")
            }
            for (s in listOf("config", "dyn", "dyn/filter", "insert", "eq")) l.add("lr/$s")
            for (b in 1..6) l.add("lr/eq/$b")
            l.add("lr/mix")
            for (i in 1..c.dcas) { l.add("dca/$i"); l.add("dca/$i/config") }
            for (i in 1..c.fxUnits) { l.add("fx/$i"); l.add("fx/$i/par") }
        } else {
            for (i in 1..32) {
                val p = "ch/${Fmt.two(i)}"
                for (s in listOf("config", "delay", "preamp", "gate", "gate/filter", "dyn", "dyn/filter", "insert", "eq")) l.add("$p/$s")
                for (b in 1..4) l.add("$p/eq/$b")
                l.add("$p/mix")
                for (b in 1..16) l.add("$p/mix/${Fmt.two(b)}")
                l.add("$p/grp")
                l.add("$p/automix")
            }
            for (i in 1..8) {
                val p = "auxin/${Fmt.two(i)}"
                for (s in listOf("config", "preamp", "eq")) l.add("$p/$s")
                for (b in 1..4) l.add("$p/eq/$b")
                l.add("$p/mix")
                for (b in 1..16) l.add("$p/mix/${Fmt.two(b)}")
                l.add("$p/grp")
            }
            for (i in 1..8) {
                val p = "fxrtn/${Fmt.two(i)}"
                for (s in listOf("config", "eq")) l.add("$p/$s")
                for (b in 1..4) l.add("$p/eq/$b")
                l.add("$p/mix")
                for (b in 1..16) l.add("$p/mix/${Fmt.two(b)}")
                l.add("$p/grp")
            }
            for (i in 1..16) {
                val p = "bus/${Fmt.two(i)}"
                for (s in listOf("config", "dyn", "dyn/filter", "insert", "eq")) l.add("$p/$s")
                for (b in 1..6) l.add("$p/eq/$b")
                l.add("$p/mix")
                for (m in 1..6) l.add("$p/mix/${Fmt.two(m)}")
                l.add("$p/grp")
            }
            for (i in 1..6) {
                val p = "mtx/${Fmt.two(i)}"
                for (s in listOf("config", "preamp", "dyn", "dyn/filter", "insert", "eq")) l.add("$p/$s")
                for (b in 1..6) l.add("$p/eq/$b")
                l.add("$p/mix")
                l.add("$p/grp")
            }
            for (p in listOf("main/st", "main/m")) {
                for (s in listOf("config", "dyn", "dyn/filter", "insert", "eq")) l.add("$p/$s")
                for (b in 1..6) l.add("$p/eq/$b")
                l.add("$p/mix")
                for (m in 1..6) l.add("$p/mix/${Fmt.two(m)}")
                l.add("$p/grp")
            }
            for (i in 1..8) { l.add("dca/$i"); l.add("dca/$i/config") }
            for (i in 1..8) { l.add("fx/$i"); l.add("fx/$i/source"); l.add("fx/$i/par") }
            for (i in 0..31) l.add("headamp/" + String.format(Locale.US, "%03d", i))
        }
        return l
    }

    // ------------------------------------------------------------------ respuestas /node

    private val saveReplies = HashMap<String, String>()
    private val liveNodes = HashMap<String, List<String>>()
    private var collecting = false
    private var lastReply = 0L

    /** Llamado desde el tick con el texto de cada respuesta /node. */
    fun onNode(raw: String) {
        val line = raw.replace("\u0007", "").trim()
        if (!line.startsWith("/")) return
        val toks = tokenize(line)
        if (toks.isEmpty()) return
        val path = toks[0].removePrefix("/")
        lastReply = SystemClock.uptimeMillis()
        if (collecting) saveReplies[path] = line
        liveNodes[path] = toks.drop(1)
    }

    private fun requestNode(path: String) {
        App.client?.node(path)
    }

    // ------------------------------------------------------------------ GUARDAR

    fun busy(): Boolean = collecting || loading

    fun save(name: String, notes: String) {
        if (busy()) return
        val client = App.client
        if (client == null || !client.isConnected()) {
            onStatus?.invoke("Sin conexión con la consola.")
            return
        }
        val nodes = nodeList()
        saveReplies.clear()
        collecting = true
        lastReply = SystemClock.uptimeMillis()
        var i = 0
        val started = SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                var n = 0
                while (i < nodes.size && n < 6) {
                    requestNode(nodes[i]); i++; n++
                }
                onStatus?.invoke("Leyendo consola… ${saveReplies.size} / ${nodes.size}")
                val now = SystemClock.uptimeMillis()
                val done = i >= nodes.size && (saveReplies.size >= nodes.size || now - lastReply > 1500 || now - started > 30000)
                if (!done) {
                    handler.postDelayed(this, 20)
                    return
                }
                collecting = false
                val sb = StringBuilder()
                val ver = if (App.console.isX32) "#4.0#" else "#2.1#"
                sb.append(ver).append(" \"").append(clean(name)).append("\" \"").append(clean(notes))
                    .append("\" %000000000 1 AirMix\n")
                var count = 0
                for (p in nodes) {
                    val l = saveReplies[p] ?: continue
                    sb.append(l).append('\n')
                    count++
                }
                if (count == 0) {
                    onStatus?.invoke("La consola no respondió. No se guardó nada.")
                    return
                }
                onStatus?.invoke("Escena leída: $count bloques. Elegí dónde guardarla.")
                onSaveReady?.invoke(name, sb.toString())
            }
        }
        handler.post(step)
    }

    private fun clean(s: String) = s.replace("\"", "'").replace("\n", " ").take(32)

    // ------------------------------------------------------------------ CARGAR

    class Scene(val name: String, val notes: String, val lines: List<Pair<String, List<String>>>)

    fun parse(text: String, fallbackName: String): Scene {
        var name = fallbackName
        var notes = ""
        val lines = ArrayList<Pair<String, List<String>>>()
        for (raw in text.lines()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("#")) {
                val t = tokenize(l)
                if (t.size > 1) name = t[1]
                if (t.size > 2) notes = t[2]
                continue
            }
            if (!l.startsWith("/")) continue
            val t = tokenize(l)
            if (t.isEmpty()) continue
            lines.add(t[0].removePrefix("/") to t.drop(1))
        }
        return Scene(name, notes, lines)
    }

    private var loading = false

    fun load(scene: Scene) {
        if (busy()) return
        val client = App.client
        if (client == null || !client.isConnected()) {
            onStatus?.invoke("Sin conexión con la consola.")
            return
        }
        loading = true
        liveNodes.clear()

        // 1) muestras para calibrar: hasta 3 bloques por patrón
        val samples = LinkedHashMap<String, ArrayList<String>>()
        for ((path, _) in scene.lines) {
            val key = patternKey(path)
            if (fieldsFor(key) == null) continue
            val l = samples.getOrPut(key) { ArrayList() }
            if (l.size < 3) l.add(path)
        }
        for ((key, paths) in samples) {
            val fields = fieldsFor(key) ?: continue
            for (p in paths) {
                requestNode(p)
                for (f in fields) client.query("/$p/${f.sub}")
            }
        }
        onStatus?.invoke("Verificando formato con la consola…")

        handler.postDelayed({
            // 2) verificar campo por campo
            val ok = HashMap<String, BooleanArray>()
            for ((key, paths) in samples) {
                val fields = fieldsFor(key) ?: continue
                val res = BooleanArray(fields.size)
                for (fi in fields.indices) {
                    var compared = 0
                    var good = true
                    for (p in paths) {
                        val toks = liveNodes[p] ?: continue
                        if (fi >= toks.size) continue
                        val live = App.store.get("/$p/${fields[fi].sub}") ?: continue
                        val parsed = convert(fields[fi], toks[fi])
                        if (parsed == null) {
                            good = false
                            continue
                        }
                        compared++
                        if (!same(parsed, live)) good = false
                    }
                    res[fi] = good && compared > 0
                }
                ok[key] = res
            }

            // 3) armar la lista de cambios
            val writes = ArrayList<Pair<String, Any>>()
            var skippedFields = 0
            var unknownBlocks = 0
            for ((path, toks) in scene.lines) {
                val key = patternKey(path)
                val fields = fieldsFor(key)
                val verified = ok[key]
                if (fields == null || verified == null) {
                    unknownBlocks++
                    continue
                }
                for (fi in fields.indices) {
                    if (fi >= toks.size) break
                    if (!verified[fi]) {
                        skippedFields++
                        continue
                    }
                    val v = convert(fields[fi], toks[fi]) ?: continue
                    writes.add("/$path/${fields[fi].sub}" to v)
                }
            }
            if (writes.isEmpty()) {
                loading = false
                onStatus?.invoke("No se pudo verificar el formato con esta consola. No se cargó nada.")
                return@postDelayed
            }

            // 4) enviar de a poco
            var i = 0
            val step = object : Runnable {
                override fun run() {
                    var n = 0
                    while (i < writes.size && n < 25) {
                        App.store.set(writes[i].first, writes[i].second)
                        i++; n++
                    }
                    onStatus?.invoke("Cargando… $i / ${writes.size}")
                    if (i < writes.size) {
                        handler.postDelayed(this, 16)
                    } else {
                        loading = false
                        val extra = StringBuilder()
                        if (skippedFields > 0) extra.append("\n• $skippedFields campos sin verificar quedaron como estaban.")
                        if (unknownBlocks > 0) extra.append("\n• $unknownBlocks bloques no soportados todavía (parámetros de FX, ruteo, etc.).")
                        onStatus?.invoke("✓ Escena \"${scene.name}\" cargada: ${writes.size} parámetros.$extra")
                    }
                }
            }
            handler.post(step)
        }, 2500)
    }

    private fun same(a: Any, b: Any): Boolean = when {
        a is Float && b is Number -> Math.abs(a - b.toFloat()) < 0.02f
        a is Int && b is Number -> a == b.toInt()
        a is String -> a == b.toString()
        else -> false
    }

    // ------------------------------------------------------------------ tabla de traducción

    /** Tipos: S texto, I entero, B ON/OFF, E lista, L lineal, G logarítmico, V nivel dB, P paneo, M máscara %0101. */
    class Field(val sub: String, val type: Char, val a: Float = 0f, val b: Float = 0f, val list: List<String>? = null)

    private val COLORS = listOf("OFF", "RD", "GN", "YE", "BL", "MG", "CY", "WH", "OFFi", "RDi", "GNi", "YEi", "BLi", "MGi", "CYi", "WHi")
    private val FILTER_TYPES = listOf("LC6", "LC12", "HC6", "HC12", "1.0", "2.0", "3.0", "5.0", "10.0")
    private val RATIOS = listOf("1.1", "1.3", "1.5", "2.0", "2.5", "3.0", "4.0", "5.0", "7.0", "10", "20", "100")
    private val EQ_TYPES = listOf("LCut", "LShv", "PEQ", "VEQ", "HShv", "HCut")
    val FX_ABBR = listOf(
        "HALL", "AMBI", "RPLT", "ROOM", "CHAM", "PLAT", "VREV", "VRM", "GATE", "RVRS", "DLY", "3TAP", "4TAP",
        "CRS", "FLNG", "PHAS", "DIMC", "FILT", "ROTA", "PAN", "SUB", "D/RV", "CR/R", "FL/R", "D/CR", "D/FL",
        "MODD", "GEQ2", "GEQ", "TEQ2", "TEQ", "DES2", "DES", "P1A", "P1A2", "PQ5", "PQ5S", "WAVD", "LIM",
        "CMB", "CMB2", "FAC", "FAC1M", "FAC2", "LEC", "LEC2", "ULC", "ULC2", "ENH2", "ENH", "EXC2", "EXC",
        "IMG", "EDI", "SON", "AMP2", "AMP", "DRV2", "DRV", "PIT2", "PIT"
    )

    // Tablas X Air tomadas de XAirSetScene.h (Maillot / Mitchell), el origen del formato .scn,
    // vía xair-scene-manager (stblassitude). Confirmadas contra un .scn real de XR18.
    val XAIR_GATE_MODES = listOf("GATE", "EXP2", "EXP3", "EXP4", "DUCK")
    val XAIR_KEYSRC: List<String> = listOf("SELF") + (1..16).map { "Ch" + Fmt.two(it) } + (1..6).map { "Bus$it" }
    private val XAIR_INSRC: List<String> = (1..16).map { "In" + Fmt.two(it) } + listOf("OFF")
    private val XAIR_CHRTNSRC: List<String> = (1..18).map { "U" + Fmt.two(it) }
    private val XAIR_RTNRTNSRC = listOf("U0102", "U0304", "U0506", "U0708", "U0910", "U1112", "U1314", "U1516", "U1718")
    private val XAIR_TAP = listOf("IN", "PREEQ", "POSTEQ", "PRE", "POST", "GRP")
    private val XAIR_CHFXSLOT = listOf("OFF", "Fx1A", "Fx1B", "Fx2A", "Fx2B", "Fx3A", "Fx3B", "Fx4A", "Fx4B")
    private val XAIR_LRFXSLOT = listOf("OFF", "Fx1", "Fx2", "Fx3", "Fx4")
    private val XAIR_EQMODE = listOf("PEQ", "GEQ", "TEQ")

    val X32_GATE_MODES = listOf("EXP2", "EXP3", "EXP4", "GATE", "DUCK")

    private val BASES = listOf(
        "rtn/aux", "main/st", "main/m", "ch/#", "rtn/#", "auxin/#", "fxrtn/#", "bus/#", "fxsend/#",
        "mtx/#", "dca/#", "fx/#", "headamp/#", "lr", "config"
    )
    private val INPUTS = setOf("ch/#", "rtn/#", "rtn/aux", "auxin/#", "fxrtn/#")

    fun patternKey(path: String): String =
        path.split('/').joinToString("/") { seg -> if (seg.isNotEmpty() && seg.all { it.isDigit() }) "#" else seg }

    private val EQ_BAND = listOf(
        Field("type", 'E', list = EQ_TYPES),
        Field("f", 'G', 20f, 20000f), Field("g", 'L', -15f, 15f), Field("q", 'G', 10f, 0.3f)
    )

    private fun filterFields() = listOf(Field("on", 'B'), Field("type", 'E', list = FILTER_TYPES), Field("f", 'G', 20f, 20000f))

    /** Campos de un patrón de bloque. null = no soportado (se informa al cargar). */
    fun fieldsFor(key: String): List<Field>? {
        val base = BASES.firstOrNull { key == it || key.startsWith("$it/") } ?: return null
        val rest = key.removePrefix(base).trimStart('/')
        return if (App.console.isX32) x32Fields(base, rest) else xairFields(base, rest)
    }

    private fun xairFields(base: String, rest: String): List<Field>? {
        val input = base in INPUTS
        return when (rest) {
            "config" -> when (base) {
                "ch/#" -> listOf(
                    Field("name", 'S'), Field("color", 'I'),
                    Field("insrc", 'E', list = XAIR_INSRC), Field("rtnsrc", 'E', list = XAIR_CHRTNSRC)
                )
                "rtn/#" -> listOf(Field("name", 'S'), Field("color", 'I'), Field("rtnsrc", 'E', list = XAIR_RTNRTNSRC))
                "rtn/aux" -> listOf(Field("name", 'S'), Field("color", 'I'), Field("rtnsrc", 'E', list = listOf("U1718")))
                "config" -> null
                else -> listOf(Field("name", 'S'), Field("color", 'I'))
            }
            "mute" -> if (base == "config") (1..4).map { Field("$it", 'B') } else null
            "preamp" -> if (base == "ch/#") listOf(
                Field("rtntrim", 'L', -18f, 18f), Field("rtnsw", 'B'), Field("invert", 'B'),
                Field("hpon", 'B'), Field("hpf", 'G', 20f, 400f)
            ) else listOf(Field("rtntrim", 'L', -18f, 18f), Field("rtnsw", 'B'))
            "gate" -> listOf(
                Field("on", 'B'), Field("mode", 'E', list = XAIR_GATE_MODES),
                Field("thr", 'L', -80f, 0f), Field("range", 'L', 3f, 60f), Field("attack", 'L', 0f, 120f),
                Field("hold", 'G', 0.02f, 2000f), Field("release", 'G', 5f, 4000f),
                Field("keysrc", 'E', list = XAIR_KEYSRC)
            )
            "gate/filter", "dyn/filter" -> filterFields()
            "dyn" -> {
                val l = arrayListOf(
                    Field("on", 'B'), Field("mode", 'E', list = listOf("COMP", "EXP")),
                    Field("det", 'E', list = listOf("PEAK", "RMS")), Field("env", 'E', list = listOf("LIN", "LOG")),
                    Field("thr", 'L', -60f, 0f), Field("ratio", 'E', list = RATIOS), Field("knee", 'L', 0f, 5f),
                    Field("mgain", 'L', 0f, 24f), Field("attack", 'L', 0f, 120f), Field("hold", 'G', 0.02f, 2000f),
                    Field("release", 'G', 5f, 4000f), Field("mix", 'L', 0f, 100f)
                )
                if (base != "lr") l.add(Field("keysrc", 'E', list = XAIR_KEYSRC))
                l.add(Field("auto", 'B'))
                l
            }
            "insert" -> listOf(Field("on", 'B'), Field("fxslot", 'E', list = if (base == "lr") XAIR_LRFXSLOT else XAIR_CHFXSLOT))
            "eq" -> if (input) listOf(Field("on", 'B')) else listOf(Field("on", 'B'), Field("mode", 'E', list = XAIR_EQMODE))
            "eq/#" -> EQ_BAND
            "mix" -> when {
                input || base == "bus/#" -> listOf(Field("on", 'B'), Field("fader", 'V'), Field("lr", 'B'), Field("pan", 'P'))
                base == "lr" -> listOf(Field("on", 'B'), Field("fader", 'V'), Field("pan", 'P'))
                else -> listOf(Field("on", 'B'), Field("fader", 'V'))
            }
            "mix/#" -> listOf(Field("level", 'V'), Field("grpon", 'B'), Field("tap", 'E', list = XAIR_TAP), Field("pan", 'P'))
            "grp" -> listOf(Field("dca", 'M'), Field("mute", 'M'))
            "automix" -> listOf(Field("group", 'E', list = listOf("OFF", "X", "Y")), Field("weight", 'L', -12f, 12f))
            "" -> when (base) {
                "dca/#" -> listOf(Field("on", 'B'), Field("fader", 'V'))
                "fx/#" -> listOf(Field("type", 'E', list = FX_ABBR), Field("insert", 'B'))
                "headamp/#" -> listOf(Field("gain", 'L', -12f, 60f), Field("phantom", 'B'))
                else -> null
            }
            else -> null
        }
    }

    /** X32: formato aproximado; la verificación previa descarta lo que no coincida. */
    private fun x32Fields(base: String, rest: String): List<Field>? {
        val input = base in INPUTS
        return when (rest) {
            "config" -> {
                if (base == "config") return null
                val l = arrayListOf(Field("name", 'S'), Field("icon", 'I'), Field("color", 'E', list = COLORS))
                if (base == "ch/#" || base == "auxin/#") l.add(Field("source", 'I'))
                l
            }
            "preamp" -> listOf(
                Field("trim", 'L', -18f, 18f), Field("invert", 'B'), Field("hpon", 'B'),
                Field("hpslope", 'E', list = listOf("12", "18", "24")), Field("hpf", 'G', 20f, 400f)
            )
            "gate" -> listOf(
                Field("on", 'B'), Field("mode", 'E', list = X32_GATE_MODES),
                Field("thr", 'L', -80f, 0f), Field("range", 'L', 3f, 60f), Field("attack", 'L', 0f, 120f),
                Field("hold", 'G', 0.02f, 2000f), Field("release", 'G', 5f, 4000f), Field("keysrc", 'I')
            )
            "gate/filter", "dyn/filter" -> filterFields()
            "dyn" -> listOf(
                Field("on", 'B'), Field("mode", 'E', list = listOf("COMP", "EXP")),
                Field("det", 'E', list = listOf("PEAK", "RMS")), Field("env", 'E', list = listOf("LIN", "LOG")),
                Field("thr", 'L', -60f, 0f), Field("ratio", 'E', list = RATIOS), Field("knee", 'L', 0f, 5f),
                Field("mgain", 'L', 0f, 24f), Field("attack", 'L', 0f, 120f), Field("hold", 'G', 0.02f, 2000f),
                Field("release", 'G', 5f, 4000f), Field("pos", 'E', list = listOf("PRE", "POST")),
                Field("keysrc", 'I'), Field("mix", 'L', 0f, 100f), Field("auto", 'B')
            )
            "eq" -> listOf(Field("on", 'B'))
            "eq/#" -> EQ_BAND
            "mix" -> if (input) listOf(Field("on", 'B'), Field("fader", 'V'), Field("st", 'B'), Field("pan", 'P'))
            else listOf(Field("on", 'B'), Field("fader", 'V'))
            "mix/#" -> listOf(Field("on", 'B'), Field("level", 'V'), Field("pan", 'P'))
            "grp" -> listOf(Field("dca", 'M'), Field("mute", 'M'))
            "" -> when (base) {
                "dca/#" -> listOf(Field("on", 'B'), Field("fader", 'V'))
                "fx/#" -> listOf(Field("type", 'E', list = FX_ABBR))
                "headamp/#" -> listOf(Field("gain", 'L', -12f, 60f), Field("phantom", 'B'))
                else -> null
            }
            else -> null
        }
    }

    // ------------------------------------------------------------------ conversión texto -> valor OSC

    fun convert(f: Field, tok: String): Any? {
        val t = tok.trim()
        return when (f.type) {
            'S' -> t
            'I' -> t.toIntOrNull() ?: t.toFloatOrNull()?.let { Math.round(it) }
            'B' -> when (t.uppercase(Locale.US)) { "ON" -> 1; "OFF" -> 0; else -> null }
            'E' -> {
                val l = f.list ?: return null
                val i = l.indexOf(t)
                if (i >= 0) i else l.indexOfFirst { it.equals(t, ignoreCase = true) }.takeIf { it >= 0 }
            }
            'L' -> num(t)?.let { ((it - f.a) / (f.b - f.a)).coerceIn(0f, 1f) }
            'G' -> num(t)?.let { Fmt.logInv(f.a, f.b, it).coerceIn(0f, 1f) }
            'V' -> if (t == "-oo") 0f else num(t)?.let { Fmt.dbToFader(it).coerceIn(0f, 1f) }
            'P' -> num(t)?.let { ((it + 100f) / 200f).coerceIn(0f, 1f) }
            'M' -> if (t.startsWith("%")) (if (t.length == 1) 0 else t.substring(1).toIntOrNull(2)) else null
            else -> null
        }
    }

    /** "1k20" = 1200, "+3.5" = 3.5, "20k0" = 20000. */
    private fun num(t: String): Float? {
        var s = t.removePrefix("+")
        var mult = 1f
        if (s.contains('k')) {
            s = s.replace('k', '.')
            mult = 1000f
        }
        return s.toFloatOrNull()?.times(mult)
    }

    fun tokenize(line: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        val n = line.length
        while (i < n) {
            while (i < n && line[i].isWhitespace()) i++
            if (i >= n) break
            if (line[i] == '"') {
                val end = line.indexOf('"', i + 1)
                if (end < 0) {
                    out.add(line.substring(i + 1)); break
                }
                out.add(line.substring(i + 1, end))
                i = end + 1
            } else {
                val st = i
                while (i < n && !line[i].isWhitespace()) i++
                out.add(line.substring(st, i))
            }
        }
        return out
    }
}
