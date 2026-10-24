package ar.joaquin.airmix

import java.util.Locale

enum class Family { XAIR, X32 }

/**
 * Perfil de consola: cuántos canales/buses tiene y qué protocolo habla.
 * X Air (XR12/XR16/XR18/X18/MR) -> puerto 10024. X32/M32 -> puerto 10023.
 */
class Console(
    val family: Family,
    val model: String,
    val channels: Int,
    val auxIns: Int,
    val fxReturns: Int,
    val buses: Int,
    val matrices: Int,
    val dcas: Int,
    val headamps: Int,
    val fxUnits: Int,
    val muteGroups: Int
) {
    val isX32: Boolean get() = family == Family.X32
    val port: Int get() = if (isX32) 10023 else 10024
    /** Envíos a FX desde el canal (solo X Air: mix/07..10). En X32 los FX se alimentan desde buses. */
    val fxSends: Int get() = if (isX32) 0 else 4

    /** Orden de modos del gate: difiere entre X Air y X32. */
    val gateModes: List<String> get() = if (isX32) Scenes.X32_GATE_MODES else Scenes.XAIR_GATE_MODES

    fun keySourceName(v: Int): String =
        if (isX32) (if (v == 0) "SELF" else "#$v") else Scenes.XAIR_KEYSRC.getOrElse(v) { "#$v" }

    fun sameShape(o: Console): Boolean =
        family == o.family && channels == o.channels && buses == o.buses &&
            auxIns == o.auxIns && fxReturns == o.fxReturns && matrices == o.matrices && dcas == o.dcas

    companion object {
        fun fromModel(m: String): Console {
            val u = m.uppercase(Locale.US)
            return when {
                u.contains("32") -> Console(Family.X32, m, 32, 8, 8, 16, 6, 8, 32, 8, 6)
                u.contains("12") -> Console(Family.XAIR, m, 12, 1, 4, 2, 0, 4, 12, 4, 4)
                u.contains("16") -> Console(Family.XAIR, m, 16, 1, 4, 4, 0, 4, 16, 4, 4)
                else -> Console(Family.XAIR, m, 16, 1, 4, 6, 0, 4, 16, 4, 4) // XR18 / X18 / MR18
            }
        }
    }
}

enum class Kind { CH, AUX, RTN, BUS, MTX, FXSEND, LR, DCA }

enum class Section(val label: String) { CONFIG("CONFIG"), GATE("GATE"), DYN("DYN"), EQ("EQ"), SENDS("ENVÍOS") }

/** Destino de "sends on fader": Main, un bus o un FX. */
data class SendTarget(val bus: Int = 0, val fx: Int = 0) {
    val label: String get() = if (bus > 0) "BUS $bus" else if (fx > 0) "FX $fx" else "MAIN"

    /** Dirección del nombre del bus / FX send (para mostrar "1-JUANCITO"). */
    val nameAddr: String?
        get() = when {
            bus > 0 -> Strip(Kind.BUS, bus).nameAddr
            fx > 0 -> Strip(Kind.FXSEND, fx).nameAddr
            else -> null
        }

    /** "1-JUANCITO" / "FX1-REVERB"; si no tiene nombre, "BUS 1" / "FX 1". */
    fun display(name: String): String {
        val n = name.trim()
        return when {
            bus > 0 -> if (n.isEmpty()) "BUS $bus" else "$bus-$n"
            fx > 0 -> if (n.isEmpty()) "FX $fx" else "FX$fx-$n"
            else -> "MAIN"
        }
    }

    companion object {
        val MAIN = SendTarget()
    }
}

/** Un canal/bus/master con todas sus direcciones OSC según la consola conectada. */
data class Strip(val kind: Kind, val idx: Int = 1) {
    private val c: Console get() = App.console

    val prefix: String
        get() = when (kind) {
            Kind.CH -> "/ch/${Fmt.two(idx)}"
            Kind.AUX -> if (c.isX32) "/auxin/${Fmt.two(idx)}" else "/rtn/aux"
            Kind.RTN -> if (c.isX32) "/fxrtn/${Fmt.two(idx)}" else "/rtn/$idx"
            Kind.BUS -> if (c.isX32) "/bus/${Fmt.two(idx)}" else "/bus/$idx"
            Kind.MTX -> "/mtx/${Fmt.two(idx)}"
            Kind.FXSEND -> "/fxsend/$idx"
            Kind.LR -> if (c.isX32) "/main/st" else "/lr"
            Kind.DCA -> "/dca/$idx"
        }

    val title: String
        get() = when (kind) {
            Kind.CH -> "CH ${Fmt.two(idx)}"
            Kind.AUX -> if (c.auxIns > 1) "AUX $idx" else "AUX"
            Kind.RTN -> if (c.isX32) "FXR $idx" else "FX $idx"
            Kind.BUS -> "BUS $idx"
            Kind.MTX -> "MTX $idx"
            Kind.FXSEND -> "FX SEND $idx"
            Kind.LR -> "MAIN LR"
            Kind.DCA -> "DCA $idx"
        }

    val faderAddr: String get() = if (kind == Kind.DCA) "$prefix/fader" else "$prefix/mix/fader"
    val onAddr: String get() = if (kind == Kind.DCA) "$prefix/on" else "$prefix/mix/on"
    val panAddr: String?
        get() = when (kind) {
            Kind.CH, Kind.AUX, Kind.RTN, Kind.LR -> "$prefix/mix/pan"
            else -> null
        }
    val nameAddr: String get() = "$prefix/config/name"
    val colorAddr: String get() = "$prefix/config/color"
    val lrAssignAddr: String get() = if (c.isX32) "$prefix/mix/st" else "$prefix/mix/lr"

    /**
     * Índices en App.client.meters.
     * X Air (/meters/1): 16 ch, 2 aux, 8 fx rtn (L/R), 6 bus, 4 fx send, LR, monitor.
     * X32 (/meters/0 + /meters/2): 32 ch, 8 aux, 8 fx rtn, 16 bus, 6 mtx, y LR en 70/71.
     */
    val meterL: Int
        get() = if (c.isX32) when (kind) {
            Kind.CH -> idx - 1
            Kind.AUX -> 31 + idx
            Kind.RTN -> 39 + idx
            Kind.BUS -> 47 + idx
            Kind.MTX -> 63 + idx
            Kind.LR -> 70
            else -> -1
        } else when (kind) {
            Kind.CH -> idx - 1
            Kind.AUX -> 16
            Kind.RTN -> 18 + 2 * (idx - 1)
            Kind.BUS -> 25 + idx
            Kind.FXSEND -> 31 + idx
            Kind.LR -> 36
            else -> -1
        }

    val meterR: Int
        get() = if (c.isX32) when (kind) {
            Kind.LR -> 71
            else -> -1
        } else when (kind) {
            Kind.AUX -> 17
            Kind.RTN -> 19 + 2 * (idx - 1)
            Kind.LR -> 37
            else -> -1
        }

    val soloAddr: String
        get() {
            val n = if (c.isX32) when (kind) {
                Kind.CH -> idx
                Kind.AUX -> 32 + idx
                Kind.RTN -> 40 + idx
                Kind.BUS -> 48 + idx
                Kind.MTX -> 64 + idx
                Kind.LR -> 71
                Kind.DCA -> 72 + idx
                Kind.FXSEND -> 0
            } else when (kind) {
                Kind.CH -> idx
                Kind.AUX -> 17
                Kind.RTN -> 17 + idx
                Kind.BUS -> 21 + idx
                Kind.FXSEND -> 27 + idx
                Kind.LR -> 32
                Kind.DCA -> 32 + idx
                Kind.MTX -> 0
            }
            return "/-stat/solosw/" + Fmt.two(n)
        }

    /** Dirección del envío cuando el fader está en modo "sends on fader". */
    fun sendAddr(t: SendTarget): String? = when {
        t.bus > 0 && (kind == Kind.CH || kind == Kind.AUX || kind == Kind.RTN) -> "$prefix/mix/${Fmt.two(t.bus)}/level"
        t.fx > 0 && !c.isX32 && (kind == Kind.CH || kind == Kind.AUX) -> "$prefix/mix/${Fmt.two(6 + t.fx)}/level"
        else -> null
    }

    /**
     * Preamp que alimenta al canal según su FUENTE (no según el número de canal):
     * X Air: insrc 0-15 = entradas 1-16 -> /headamp/01..16 (17+ = AUX / USB, sin preamp).
     * X32: source 1-128 = local + AES50 -> /headamp/000..127 (0 = OFF, 129+ = tarjeta / aux, sin preamp).
     */
    fun headampFor(src: Int): String? {
        if (kind != Kind.CH) return null
        return if (c.isX32) {
            if (src in 1..128) "/headamp/" + String.format(Locale.US, "%03d", src - 1) else null
        } else {
            if (src in 0..15) "/headamp/${Fmt.two(src + 1)}" else null
        }
    }

    val sourceAddr: String get() = if (c.isX32) "$prefix/config/source" else "$prefix/config/insrc"
}

fun stripsOfKind(k: Kind): List<Strip> {
    val c = App.console
    val n = when (k) {
        Kind.CH -> c.channels
        Kind.AUX -> c.auxIns
        Kind.RTN -> c.fxReturns
        Kind.BUS -> c.buses
        Kind.MTX -> c.matrices
        Kind.FXSEND -> c.fxSends
        Kind.DCA -> c.dcas
        Kind.LR -> 1
    }
    return (1..n).map { Strip(k, it) }
}

/** Una "capa" del riel izquierdo: hasta 8 tiras. */
class Layer(val label: String, val strips: List<Strip>) {
    val kind: Kind get() = strips.firstOrNull()?.kind ?: Kind.CH
}

fun buildLayers(): List<Layer> {
    val c = App.console
    val out = ArrayList<Layer>()
    for (g in stripsOfKind(Kind.CH).chunked(8)) out.add(Layer("CH ${g.first().idx}-${g.last().idx}", g))
    out.add(Layer(if (c.auxIns > 1) "AUX IN" else "AUX", stripsOfKind(Kind.AUX)))
    out.add(Layer("FX RTN", stripsOfKind(Kind.RTN)))
    val buses = stripsOfKind(Kind.BUS)
    if (buses.size <= 8) out.add(Layer("BUSES", buses))
    else for (g in buses.chunked(8)) out.add(Layer("BUS ${g.first().idx}-${g.last().idx}", g))
    if (c.matrices > 0) out.add(Layer("MATRIX", stripsOfKind(Kind.MTX)))
    if (c.fxSends > 0) out.add(Layer("FX SENDS", stripsOfKind(Kind.FXSEND)))
    out.add(Layer("DCA", stripsOfKind(Kind.DCA)))
    return out
}

fun sectionsFor(s: Strip): List<Section> = when (s.kind) {
    Kind.CH -> listOf(Section.CONFIG, Section.GATE, Section.DYN, Section.EQ, Section.SENDS)
    Kind.BUS, Kind.MTX -> listOf(Section.CONFIG, Section.DYN, Section.EQ)
    Kind.LR -> listOf(Section.DYN, Section.EQ)
    else -> listOf(Section.CONFIG)
}

/** Etiquetas de la pantalla METERS según la consola. */
fun meterLabels(): List<String> {
    val c = App.console
    val l = ArrayList<String>()
    if (c.isX32) {
        for (i in 1..32) l.add("$i")
        for (i in 1..8) l.add("A$i")
        for (i in 1..8) l.add("FR$i")
        for (i in 1..16) l.add("B$i")
        for (i in 1..6) l.add("M$i")
        l.add("L"); l.add("R")
    } else {
        for (i in 1..16) l.add(if (i <= c.channels) "$i" else "·")
        l.add("AUX L"); l.add("AUX R")
        for (i in 1..4) { l.add("FX${i}L"); l.add("FX${i}R") }
        for (i in 1..6) l.add("B$i")
        for (i in 1..4) l.add("FXS$i")
        l.add("L"); l.add("R")
    }
    return l
}

/**
 * Mute de efectos de un toque (para hablar entre temas).
 * X Air: mutea los FX SEND masters (las colas de reverb se apagan naturalmente).
 * X32: mutea los FX returns.
 * Al desmutear, restaura solo los que estaban abiertos antes.
 */
object FxMute {
    private val saved = HashSet<String>()

    fun targets(): List<String> =
        if (App.console.isX32) (1..App.console.fxReturns).map { "/fxrtn/${Fmt.two(it)}/mix/on" }
        else (1..4).map { "/fxsend/$it/mix/on" }

    fun allMuted(): Boolean = targets().all { App.store.int(it, 1) == 0 }

    fun toggle() {
        val t = targets()
        if (allMuted()) {
            val restore = if (saved.isEmpty()) t else t.filter { it in saved }
            for (a in restore) App.store.set(a, 1)
            saved.clear()
        } else {
            saved.clear()
            for (a in t) if (App.store.int(a, 1) != 0) saved.add(a)
            for (a in t) App.store.set(a, 0)
        }
    }
}

/** Conversiones de valores normalizados (0..1) a unidades reales. */
object Fmt {
    fun two(n: Int): String = if (n < 10) "0$n" else n.toString()

    fun lin(min: Float, max: Float, f: Float): Float = min + (max - min) * f
    fun log(min: Float, max: Float, f: Float): Float =
        (min * Math.pow((max / min).toDouble(), f.toDouble())).toFloat()
    fun logInv(min: Float, max: Float, v: Float): Float =
        (Math.log((v / min).toDouble()) / Math.log((max / min).toDouble())).toFloat()

    fun db(v: Float): String = String.format(Locale.US, "%.1f dB", v)
    fun hz(v: Float): String =
        if (v >= 1000f) String.format(Locale.US, "%.2f kHz", v / 1000f) else String.format(Locale.US, "%.1f Hz", v)
    fun ms(v: Float): String = when {
        v >= 100f -> String.format(Locale.US, "%.0f ms", v)
        v >= 10f -> String.format(Locale.US, "%.1f ms", v)
        else -> String.format(Locale.US, "%.2f ms", v)
    }
    fun pct(v: Float): String = String.format(Locale.US, "%.0f %%", v)
    fun num(v: Float): String = String.format(Locale.US, "%.1f", v)

    /** Ley de fader X32/X Air. */
    fun faderToDb(f: Float): Float = when {
        f >= 0.5f -> f * 40f - 30f
        f >= 0.25f -> f * 80f - 50f
        f >= 0.0625f -> f * 160f - 70f
        f > 0f -> f * 480f - 90f
        else -> -144f
    }

    fun dbToFader(db: Float): Float = when {
        db >= -10f -> (db + 30f) / 40f
        db >= -30f -> (db + 50f) / 80f
        db >= -60f -> (db + 70f) / 160f
        db > -90f -> (db + 90f) / 480f
        else -> 0f
    }

    fun faderDb(f: Float): String {
        val d = faderToDb(f)
        return if (d <= -89.5f) "-∞" else String.format(Locale.US, "%+.1f", d)
    }
}

/** Nombres de efectos (X Air y X32 comparten la lista base; a verificar contra la consola). */
val FX_TYPES = listOf(
    "Hall Reverb", "Ambience", "Rich Plate", "Room Reverb", "Chamber", "Plate Reverb",
    "Vintage Reverb", "Vintage Room", "Gated Reverb", "Reverse Reverb", "Stereo Delay",
    "3-Tap Delay", "4-Tap Delay", "Stereo Chorus", "Stereo Flanger", "Stereo Phaser",
    "Dimensional Chorus", "Mood Filter", "Rotary Speaker", "Tremolo/Panner", "Sub Octaver",
    "Delay/Chamber", "Chorus/Chamber", "Flanger/Chamber", "Delay/Chorus", "Delay/Flanger",
    "Modulation Delay", "Dual GEQ", "Stereo GEQ", "Dual TruEQ", "Stereo TruEQ",
    "Dual DeEsser", "Stereo DeEsser", "Stereo Xtec EQ1", "Dual Xtec EQ1", "Stereo Xtec EQ5",
    "Dual Xtec EQ5", "Wave Designer", "Precision Limiter", "Combinator", "Dual Combinator",
    "Fair Compressor", "Fair Comp M/S", "Dual Fair Comp", "Leisure Comp", "Dual Leisure Comp",
    "Ultimo Comp", "Dual Ultimo Comp", "Dual Enhancer", "Stereo Enhancer", "Dual Exciter",
    "Stereo Exciter", "Stereo Imager", "Edison EX1", "Sound Maxer", "Dual Guitar Amp",
    "Stereo Guitar Amp", "Dual Tube Stage", "Stereo Tube Stage", "Dual Pitch", "Stereo Pitch"
)
