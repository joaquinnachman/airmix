package ar.joaquin.airmix

import java.util.Locale

/**
 * Efectos: nombres, tipos por slot y parámetros de cada tipo.
 *
 * La tabla FX_TABLE es texto y es IDÉNTICA en Fx.swift (copiar y pegar si se toca).
 * Rangos tomados de XAir_fxparse1.c / XAir_fxparse.c (Patrick-Gilles Maillot), el mismo origen
 * que usan X-Air Edit y las escenas .scn. /fx/N/par/01..: floats 0..1 (o int para listas).
 *
 * Formato por parámetro: ETIQUETA:tipo:min:max:unidad
 *   A = lineal, L = logarítmico, E = lista (min = opciones separadas por coma)
 */
object Fx {
    const val FX_TABLE = """
HALL=PRE DELAY:A:0:200:ms;DECAY:L:0.2:5:s;SIZE:A:2:100:;DAMPING:L:1000:20000:Hz;DIFFUSE:A:1:30:;LEVEL:A:-12:12:dB;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;BASS MULT:L:0.5:2:x;SPREAD:A:0:50:;SHAPE:A:0:250:;MOD SPEED:A:0:100:%
AMBI=PRE DELAY:A:0:200:ms;DECAY:L:0.2:7.3:s;SIZE:A:2:100:;DAMPING:L:1000:20000:Hz;DIFFUSE:A:1:30:;LEVEL:A:-12:12:dB;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;MODULATE:A:0:100:%;TAIL GAIN:A:0:100:%
RPLT=PRE DELAY:A:0:200:ms;DECAY:L:0.3:29:s;SIZE:A:4:39:;DAMPING:L:1000:20000:Hz;DIFFUSE:A:0:100:%;LEVEL:A:-12:12:dB;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;BASS MULT:L:0.25:4:x;SPREAD:A:0:50:;ATTACK:A:0:100:%;SPIN:A:0:100:%;ECHO L:A:0:1200:ms;ECHO R:A:0:1200:ms;FEED L:A:-100:100:%;FEED R:A:-100:100:%
ROOM=PRE DELAY:A:0:200:ms;DECAY:L:0.3:29:s;SIZE:A:4:76:;DAMPING:L:1000:20000:Hz;DIFFUSE:A:0:100:%;LEVEL:A:-12:12:dB;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;BASS MULT:L:0.25:4:x;SPREAD:A:0:50:;SHAPE:A:0:250:;SPIN:A:0:100:%;ECHO L:A:0:1200:ms;ECHO R:A:0:1200:ms;FEED L:A:-100:100:%;FEED R:A:-100:100:%
CHAM=PRE DELAY:A:0:200:ms;DECAY:L:0.3:29:s;SIZE:A:4:76:;DAMPING:L:1000:20000:Hz;DIFFUSE:A:0:100:%;LEVEL:A:-12:12:dB;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;BASS MULT:L:0.25:4:x;SPREAD:A:0:50:;SHAPE:A:0:250:;SPIN:A:0:100:%;REFL L:A:0:500:ms;REFL R:A:0:500:ms;REFL GAIN L:A:0:100:%;REFL GAIN R:A:0:100:%
PLAT=PRE DELAY:A:0:200:ms;DECAY:L:0.5:10:s;SIZE:A:2:100:;DAMPING:L:1000:20000:Hz;DIFFUSE:A:1:30:;LEVEL:A:-12:12:dB;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;BASS MULT:L:0.5:2:x;XOVER:L:10:500:Hz;MOD DEPTH:A:0:50:;MOD SPEED:A:0:100:%
VREV=PRE DELAY:A:0:120:ms;DECAY:L:0.3:4.5:s;MODULATE:A:0:100:%;VINTAGE:E:OFF,ON::;POSITION:E:FRONT,REAR::;LEVEL:A:-12:12:dB;LO CUT:L:10:500:Hz;HI CUT:L:10000:20000:Hz;LO MULT:L:0.5:2:x;HI MULT:L:0.25:1:x
VRM=REV DELAY:A:0:200:ms;DECAY:L:0.1:20:s;SIZE:A:0:100:%;DENSITY:A:1:30:;ER LEVEL:A:0:100:%;LEVEL:A:-12:12:dB;LO MULT:L:0.1:10:x;HI MULT:L:0.1:10:x;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;ER LEFT:A:0:100:%;ER RIGHT:A:0:100:%;FREEZE:E:OFF,ON::
GATE=PRE DELAY:A:0:200:ms;DECAY:A:140:1000:ms;ATTACK:A:0:30:;DENSITY:A:1:50:;SPREAD:A:0:100:%;LEVEL:A:-12:12:dB;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;HI SHV GAIN:A:-30:0:dB;DIFFUSE:A:1:30:
RVRS=PRE DELAY:A:0:200:ms;DECAY:A:140:1000:ms;RISE:A:0:50:;DIFFUSE:A:1:30:;SPREAD:A:0:100:%;LEVEL:A:-12:12:dB;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;HI SHV GAIN:A:-30:0:dB
DLY=MIX:A:0:100:%;TIME:A:1:3000:ms;MODE:E:ST,X,M::;FACTOR L:E:1/4,3/8,1/2,2/3,1,4/3,3/2,2,3::;FACTOR R:E:1/4,3/8,1/2,2/3,1,4/3,3/2,2,3::;OFFSET L/R:A:-100:100:;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;FEED LO CUT:L:10:500:Hz;FEED L:A:0:100:%;FEED R:A:0:100:%;FEED HI CUT:L:200:20000:Hz
3TAP=TIME:A:1:3000:ms;GAIN:A:0:100:%;PAN:A:-100:100:;FEEDBACK:A:0:100:%;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;FACTOR A:E:1/4,3/8,1/2,2/3,1,4/3,3/2,2,3::;GAIN A:A:0:100:%;PAN A:A:-100:100:;FACTOR B:E:1/4,3/8,1/2,2/3,1,4/3,3/2,2,3::;GAIN B:A:0:100:%;PAN B:A:-100:100:;X FEED:E:OFF,ON::;MONO:E:OFF,ON::;DRY:E:OFF,ON::
4TAP=TIME:A:1:3000:ms;GAIN:A:0:100:%;FEEDBACK:A:0:100:%;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;SPREAD:A:0:6:;FACTOR A:E:1/4,3/8,1/2,2/3,1,4/3,3/2,2,3::;GAIN A:A:0:100:%;FACTOR B:E:1/4,3/8,1/2,2/3,1,4/3,3/2,2,3::;GAIN B:A:0:100:%;FACTOR C:E:1/4,3/8,1/2,2/3,1,4/3,3/2,2,3::;GAIN C:A:0:100:%;X FEED:E:OFF,ON::;MONO:E:OFF,ON::;DRY:E:OFF,ON::
CRS=SPEED:L:0.05:5:Hz;DEPTH L:A:0:100:%;DEPTH R:A:0:100:%;DELAY L:L:0.5:50:ms;DELAY R:L:0.5:50:ms;MIX:A:0:100:%;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;PHASE:A:0:180:°;WAVE:A:0:100:%;SPREAD:A:0:100:%
FLNG=SPEED:L:0.05:5:Hz;DEPTH L:A:0:100:%;DEPTH R:A:0:100:%;DELAY L:L:0.5:20:ms;DELAY R:L:0.5:20:ms;MIX:A:0:100:%;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;PHASE:A:0:180:°;FEED LO CUT:L:10:500:Hz;FEED HI CUT:L:200:20000:Hz;FEED:A:-90:90:%
PHAS=SPEED:L:0.05:5:Hz;DEPTH:A:0:100:%;RESONANCE:A:0:80:%;BASE:A:0:50:;STAGES:A:2:12:;MIX:A:0:100:%;WAVE:A:-50:50:;PHASE:A:0:180:°;ENV MOD:A:-100:100:%;ATTACK:L:10:1000:ms;HOLD:L:1:2000:ms;RELEASE:L:10:1000:ms
FILT=SPEED:L:0.05:20:Hz;DEPTH:A:0:100:%;RESONANCE:A:0:100:%;BASE:L:20:15000:Hz;MODE:E:LP,HP,BP,NO::;MIX:A:0:100:%;WAVE:E:TRI,SIN,SAW,SAW-,RMP,SQU,RND::;PHASE:A:0:180:°;ENV MOD:A:-100:100:%;ATTACK:L:10:250:ms;RELEASE:L:10:500:ms;DRIVE:A:0:100:%
ROTA=LO SPEED:L:0.1:4:Hz;HI SPEED:L:2:10:Hz;ACCEL:A:0:100:%;DISTANCE:A:0:100:%;BALANCE:A:-100:100:;MIX:A:0:100:%;MOTOR:E:RUN,STOP::;VELOCIDAD:E:SLOW,FAST::
PAN=SPEED:L:0.05:5:Hz;PHASE:A:0:180:°;WAVE:A:-50:50:;DEPTH:A:0:100:%;ENV SPEED:A:0:100:%;ENV DEPTH:A:0:100:%;ATTACK:L:10:1000:ms;HOLD:L:1:2000:ms;RELEASE:L:10:1000:ms
D/RV=TIME:A:1:3000:ms;PATTERN:E:1/4,1/3,3/8,1/2,2/3,3/4,1,1/4X,1/3X,3/8X,1/2X,2/3X,3/4X,1X::;FEED HI CUT:L:1000:20000:Hz;FEEDBACK:A:0:100:%;X FEED:A:0:100:%;BALANCE:A:-100:100:;PRE DELAY:A:0:200:ms;DECAY:L:0.1:5:s;SIZE:A:2:100:;DAMPING:L:1000:20000:Hz;LO CUT:L:10:500:Hz;MIX:A:0:100:%
CR/R=SPEED:L:0.05:4:Hz;DEPTH:A:0:100:%;DELAY:L:0.5:50:ms;PHASE:A:0:180:°;WAVE:A:0:100:%;BALANCE:A:-100:100:;PRE DELAY:A:0:200:ms;DECAY:L:0.1:5:s;SIZE:A:2:100:;DAMPING:L:1000:20000:Hz;LO CUT:L:10:500:Hz;MIX:A:0:100:%
FL/R=SPEED:L:0.05:4:Hz;DEPTH:A:0:100:%;DELAY:L:0.5:20:ms;PHASE:A:0:180:°;FEED:A:-90:90:%;BALANCE:A:-100:100:;PRE DELAY:A:0:200:ms;DECAY:L:0.1:5:s;SIZE:A:2:100:;DAMPING:L:1000:20000:Hz;LO CUT:L:10:500:Hz;MIX:A:0:100:%
D/CR=TIME:A:1:3000:ms;PATTERN:E:1/4,1/3,3/8,1/2,2/3,3/4,1,1/4X,1/3X,3/8X,1/2X,2/3X,3/4X,1X::;FEED HI CUT:L:1000:20000:Hz;FEEDBACK:A:0:100:%;X FEED:A:0:100:%;BALANCE:A:-100:100:;SPEED:L:0.05:4:Hz;DEPTH:A:0:100:%;DELAY:L:0.5:50:ms;PHASE:A:0:180:°;WAVE:A:0:100:%;MIX:A:0:100:%
D/FL=TIME:A:1:3000:ms;PATTERN:E:1/4,1/3,3/8,1/2,2/3,3/4,1,1/4X,1/3X,3/8X,1/2X,2/3X,3/4X,1X::;FEED HI CUT:L:1000:20000:Hz;FEEDBACK:A:0:100:%;X FEED:A:0:100:%;BALANCE:A:-100:100:;SPEED:L:0.05:4:Hz;DEPTH:A:0:100:%;DELAY:L:0.5:20:ms;PHASE:A:0:180:°;FEED:A:-90:90:%;MIX:A:0:100:%
MODD=TIME:A:1:3000:ms;DELAY:E:1,1/2,2/3,3/2::;FEED:A:0:100:%;LO CUT:L:10:500:Hz;HI CUT:L:200:20000:Hz;DEPTH:A:0:100:%;RATE:L:0.05:10:Hz;SETUP:E:PAR,SER::;TIPO:E:AMB,CLUB,HALL::;DECAY:A:1:10:;DAMPING:L:1000:20000:Hz;BALANCE:A:-100:100:;MIX:A:0:100:%
LIM=IN GAIN:A:0:18:dB;OUT GAIN:A:-18:0:dB;SQUEEZE:A:0:100:%;KNEE:A:0:10:;ATTACK:L:0.05:1:ms;RELEASE:L:20:2000:ms;ST LINK:E:OFF,ON::;AUTO GAIN:E:OFF,ON::
PIT=SEMITONOS:A:-12:12:;CENTS:A:-50:50:;DELAY:L:1:500:ms;LO CUT:L:10:500:Hz;HI CUT:L:2000:20000:Hz;MIX:A:0:100:%
PIT2=A SEMITONOS:A:-12:12:;A CENTS:A:-50:50:;A DELAY:L:1:500:ms;A LO CUT:L:10:500:Hz;A HI CUT:L:2000:20000:Hz;A MIX:A:0:100:%;B SEMITONOS:A:-12:12:;B CENTS:A:-50:50:;B DELAY:L:1:500:ms;B LO CUT:L:10:500:Hz;B HI CUT:L:2000:20000:Hz;B MIX:A:0:100:%
"""

    /** Abreviaturas en el orden de /fx/N/type (X Air FX 1-4 y X32 FX 1-4). */
    val ABBR = listOf(
        "HALL", "AMBI", "RPLT", "ROOM", "CHAM", "PLAT", "VREV", "VRM", "GATE", "RVRS", "DLY", "3TAP", "4TAP",
        "CRS", "FLNG", "PHAS", "DIMC", "FILT", "ROTA", "PAN", "SUB", "D/RV", "CR/R", "FL/R", "D/CR", "D/FL",
        "MODD", "GEQ2", "GEQ", "TEQ2", "TEQ", "DES2", "DES", "P1A", "P1A2", "PQ5", "PQ5S", "WAVD", "LIM",
        "CMB", "CMB2", "FAC", "FAC1M", "FAC2", "LEC", "LEC2", "ULC", "ULC2", "ENH2", "ENH", "EXC2", "EXC",
        "IMG", "EDI", "SON", "AMP2", "AMP", "DRV2", "DRV", "PIT2", "PIT"
    )

    /** X32 FX 5-8: lista reducida (sin reverbs ni delays). Índice -> abreviatura. */
    val X32_FX5 = listOf(
        "GEQ2", "GEQ", "TEQ2", "TEQ", "DES2", "DES", "P1A", "P1A2", "PQ5", "PQ5S", "WAVD", "LIM", "FAC", "FAC1M",
        "FAC2", "LEC", "LEC2", "ULC", "ULC2", "ENH2", "ENH", "EXC2", "EXC", "IMG", "EDI", "SON", "AMP2", "AMP",
        "DRV2", "DRV", "PHAS", "FILT", "PAN", "SUB"
    )

    class Par(val label: String, val kind: Char, val a: Float, val b: Float, val unit: String, val options: List<String>)

    private val table: Map<String, List<Par>> by lazy {
        val m = HashMap<String, List<Par>>()
        for (line in FX_TABLE.trim().lines()) {
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val list = ArrayList<Par>()
            for (p in line.substring(eq + 1).split(';')) {
                val f = p.split(':')
                if (f.size < 5) continue
                if (f[1] == "E") list.add(Par(f[0], 'E', 0f, 0f, "", f[2].split(',')))
                else list.add(Par(f[0], f[1][0], f[2].toFloat(), f[3].toFloat(), f[4], emptyList()))
            }
            m[line.substring(0, eq)] = list
        }
        m
    }

    /** Opciones del selector de tipo para el slot n. */
    fun typeOptions(n: Int): List<String> =
        if (App.console.isX32 && n >= 5) X32_FX5.map { name(it) } else ABBR.map { name(it) }

    /** Abreviatura del efecto cargado (valor crudo de /fx/N/type). */
    fun abbr(n: Int, raw: Int): String? =
        if (App.console.isX32 && n >= 5) X32_FX5.getOrNull(raw) else ABBR.getOrNull(raw)

    fun name(abbr: String): String {
        val i = ABBR.indexOf(abbr)
        return if (i >= 0) FX_TYPES.getOrElse(i) { abbr } else abbr
    }

    fun typeName(n: Int, raw: Int): String = abbr(n, raw)?.let { name(it) } ?: "Efecto #$raw"

    /** "FX1 · Hall Reverb" */
    fun label(n: Int, raw: Int?): String = if (raw == null) "FX $n" else "FX$n · ${typeName(n, raw)}"

    /** Parámetros del tipo; si no lo conocemos, 12 genéricos en %. */
    fun params(abbr: String?): List<Par> =
        table[abbr] ?: (1..12).map { Par("PARÁM. $it", 'A', 0f, 100f, "%", emptyList()) }

    fun spec(n: Int, i: Int, p: Par): Spec {
        val addr = "/fx/$n/par/${Fmt.two(i + 1)}"
        if (p.kind == 'E') {
            return Spec(p.label, addr, steps = p.options.size, def = 0f) { p.options.getOrElse(it.toInt()) { "?" } }
        }
        val bip = p.a < 0f && p.b > 0f
        return Spec(p.label, addr, def = if (bip) 0.5f else 0f, bipolar = bip) { f -> format(p, f) }
    }

    fun format(p: Par, f: Float): String {
        val v = if (p.kind == 'L') Fmt.log(p.a, p.b, f) else Fmt.lin(p.a, p.b, f)
        return when (p.unit) {
            "Hz" -> Fmt.hz(v)
            "ms" -> Fmt.ms(v)
            "s" -> String.format(Locale.US, "%.2f s", v)
            "dB" -> Fmt.db(v)
            "%" -> String.format(Locale.US, "%.0f %%", v)
            "°" -> String.format(Locale.US, "%.0f°", v)
            "x" -> String.format(Locale.US, "%.2fx", v)
            else -> if (p.b - p.a >= 20f) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)
        }
    }

    /** Retorno de cada FX: X Air /rtn/N; X32 FX 1-4 por /fxrtn (pares L/R). FX 5-8 de X32 son insert. */
    fun returnPrefix(n: Int): String? =
        if (App.console.isX32) (if (n <= 4) "/fxrtn/${Fmt.two(2 * n - 1)}" else null) else "/rtn/$n"
}

/** Valores de fábrica del EQ para el botón RESET (tipo, Hz, Q). */
object EqDefaults {
    // Tipos: 0 LCut, 1 LShv, 2 PEQ, 3 VEQ, 4 HShv, 5 HCut
    private val FOUR = listOf(Triple(1, 200f, 2f), Triple(2, 500f, 2f), Triple(2, 2000f, 2f), Triple(4, 10000f, 2f))
    private val SIX = listOf(
        Triple(1, 80f, 2f), Triple(2, 200f, 2f), Triple(2, 600f, 2f),
        Triple(2, 2000f, 2f), Triple(2, 6000f, 2f), Triple(4, 12000f, 2f)
    )

    /** Pone el EQ plano: ganancia 0 dB y tipo/frecuencia/Q de fábrica en todas las bandas. */
    fun reset(p: String, bands: Int) {
        val list = if (bands == 4) FOUR else SIX
        for (b in 1..bands) {
            val (type, hz, q) = list[b - 1]
            App.store.set("$p/eq/$b/type", type)
            App.store.set("$p/eq/$b/f", Fmt.logInv(20f, 20000f, hz))
            App.store.set("$p/eq/$b/g", 0.5f)
            App.store.set("$p/eq/$b/q", Fmt.logInv(10f, 0.3f, q))
        }
    }
}

/** Color de scribble de un bus para toda la app (envíos, selector, encabezado). */
object BusColor {
    /** Color base; si el bus no tiene color (0 / 8), el celeste de envíos. */
    fun of(colorIdx: Int): Int {
        val c = colorIdx.coerceIn(0, 15) % 8
        return if (c == 0) C.SENDS else SCRIBBLE_COLORS[c]
    }

    /** Texto legible encima del color. */
    fun ink(colorIdx: Int): Int {
        val c = colorIdx.coerceIn(0, 15) % 8
        return if (c == 1 || c == 4 || c == 5) android.graphics.Color.WHITE else C.INK
    }
}

/** Índice de canal seleccionado (/-stat/selidx) = número de solo - 1. El RTA de la consola sigue esta selección. */
fun Strip.selIdx(): Int {
    val n = soloAddr.substringAfterLast('/').toIntOrNull() ?: return -1
    return n - 1
}
