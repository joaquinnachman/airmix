package ar.joaquin.airmix

import java.util.Locale

enum class Kind { CH, AUX, RTN, BUS, FXSEND, LR, DCA }

enum class Section(val label: String) { CONFIG("CONFIG"), GATE("GATE"), DYN("DYN"), EQ("EQ"), SENDS("SENDS") }

/** Destino de "sends on fader": Main, un bus o un FX. */
data class SendTarget(val bus: Int = 0, val fx: Int = 0) {
    val label: String get() = if (bus > 0) "BUS $bus" else if (fx > 0) "FX $fx" else "MAIN OUT"
    val title: String get() = if (bus > 0) "Bus $bus" else if (fx > 0) "FX $fx" else "Main Out"

    companion object {
        val MAIN = SendTarget()
    }
}

/** Un canal/bus/master con todas sus direcciones OSC (protocolo X Air). */
data class Strip(val kind: Kind, val idx: Int = 0) {
    val prefix: String
        get() = when (kind) {
            Kind.CH -> "/ch/${Fmt.two(idx)}"
            Kind.AUX -> "/rtn/aux"
            Kind.RTN -> "/rtn/$idx"
            Kind.BUS -> "/bus/$idx"
            Kind.FXSEND -> "/fxsend/$idx"
            Kind.LR -> "/lr"
            Kind.DCA -> "/dca/$idx"
        }

    val title: String
        get() = when (kind) {
            Kind.CH -> "CH ${Fmt.two(idx)}"
            Kind.AUX -> "AUX"
            Kind.RTN -> "FX $idx"
            Kind.BUS -> "Bus $idx"
            Kind.FXSEND -> "FX Send $idx"
            Kind.LR -> "Main Out"
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

    /** Índices dentro de /meters/1: 16 ch, 2 aux, 8 fx rtn (L/R), 6 bus, 4 fx send, 2 LR, 2 monitor. */
    val meterL: Int
        get() = when (kind) {
            Kind.CH -> idx - 1
            Kind.AUX -> 16
            Kind.RTN -> 18 + 2 * (idx - 1)
            Kind.BUS -> 25 + idx
            Kind.FXSEND -> 31 + idx
            Kind.LR -> 36
            Kind.DCA -> -1
        }
    val meterR: Int
        get() = when (kind) {
            Kind.AUX -> 17
            Kind.RTN -> 19 + 2 * (idx - 1)
            Kind.LR -> 37
            else -> -1
        }

    val soloAddr: String
        get() = "/-stat/solosw/" + Fmt.two(
            when (kind) {
                Kind.CH -> idx
                Kind.AUX -> 17
                Kind.RTN -> 17 + idx
                Kind.BUS -> 21 + idx
                Kind.FXSEND -> 27 + idx
                Kind.LR -> 32
                Kind.DCA -> 32 + idx
            }
        )

    /** Dirección del envío cuando el fader está en modo "sends on fader". */
    fun sendAddr(t: SendTarget): String? = when {
        t.bus > 0 && (kind == Kind.CH || kind == Kind.AUX || kind == Kind.RTN) -> "$prefix/mix/${Fmt.two(t.bus)}/level"
        t.fx > 0 && (kind == Kind.CH || kind == Kind.AUX) -> "$prefix/mix/${Fmt.two(6 + t.fx)}/level"
        else -> null
    }
}

fun stripsOfKind(k: Kind): List<Strip> = when (k) {
    Kind.CH -> (1..App.channels).map { Strip(Kind.CH, it) }
    Kind.BUS -> (1..App.buses).map { Strip(Kind.BUS, it) }
    Kind.RTN -> (1..4).map { Strip(Kind.RTN, it) }
    Kind.FXSEND -> (1..4).map { Strip(Kind.FXSEND, it) }
    Kind.DCA -> (1..4).map { Strip(Kind.DCA, it) }
    Kind.AUX -> listOf(Strip(Kind.AUX))
    Kind.LR -> listOf(Strip(Kind.LR))
}

fun sectionsFor(s: Strip): List<Section> = when (s.kind) {
    Kind.CH -> listOf(Section.CONFIG, Section.GATE, Section.DYN, Section.EQ, Section.SENDS)
    Kind.BUS -> listOf(Section.CONFIG, Section.DYN, Section.EQ)
    Kind.LR -> listOf(Section.DYN, Section.EQ)
    else -> listOf(Section.CONFIG)
}

/** Conversiones de valores normalizados (0..1) de X Air a unidades reales. */
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

/** Nombres de efectos X Air por índice de /fx/N/type (a verificar contra la consola). */
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
