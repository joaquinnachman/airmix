import SwiftUI

/// Efectos: nombres, tipos por slot y parámetros de cada tipo. Espejo de Fx.kt.
/// FX_TABLE es texto IDÉNTICO al de Android (rangos de XAir_fxparse1.c, P.-G. Maillot).
/// Formato por parámetro: ETIQUETA:tipo:min:max:unidad  (A lineal, L log, E lista).
enum Fx {
    static let table = """
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

    /// Abreviaturas en el orden de /fx/N/type (X Air FX 1-4 y X32 FX 1-4).
    static let abbr: [String] = [
        "HALL", "AMBI", "RPLT", "ROOM", "CHAM", "PLAT", "VREV", "VRM", "GATE", "RVRS", "DLY", "3TAP", "4TAP",
        "CRS", "FLNG", "PHAS", "DIMC", "FILT", "ROTA", "PAN", "SUB", "D/RV", "CR/R", "FL/R", "D/CR", "D/FL",
        "MODD", "GEQ2", "GEQ", "TEQ2", "TEQ", "DES2", "DES", "P1A", "P1A2", "PQ5", "PQ5S", "WAVD", "LIM",
        "CMB", "CMB2", "FAC", "FAC1M", "FAC2", "LEC", "LEC2", "ULC", "ULC2", "ENH2", "ENH", "EXC2", "EXC",
        "IMG", "EDI", "SON", "AMP2", "AMP", "DRV2", "DRV", "PIT2", "PIT"
    ]

    /// X32 FX 5-8: lista reducida.
    static let x32Fx5: [String] = [
        "GEQ2", "GEQ", "TEQ2", "TEQ", "DES2", "DES", "P1A", "P1A2", "PQ5", "PQ5S", "WAVD", "LIM", "FAC", "FAC1M",
        "FAC2", "LEC", "LEC2", "ULC", "ULC2", "ENH2", "ENH", "EXC2", "EXC", "IMG", "EDI", "SON", "AMP2", "AMP",
        "DRV2", "DRV", "PHAS", "FILT", "PAN", "SUB"
    ]

    struct Par {
        let label: String
        let kind: Character
        let a: Float
        let b: Float
        let unit: String
        let options: [String]
    }

    private static let parsed: [String: [Par]] = {
        var m: [String: [Par]] = [:]
        for line in table.split(separator: "\n") {
            guard let eq = line.firstIndex(of: "=") else { continue }
            var list: [Par] = []
            for p in line[line.index(after: eq)...].split(separator: ";") {
                let f = p.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
                if f.count < 5 { continue }
                if f[1] == "E" {
                    list.append(Par(label: f[0], kind: "E", a: 0, b: 0, unit: "", options: f[2].split(separator: ",").map(String.init)))
                } else {
                    list.append(Par(label: f[0], kind: Character(f[1]), a: Float(f[2]) ?? 0, b: Float(f[3]) ?? 1, unit: f[4], options: []))
                }
            }
            m[String(line[..<eq])] = list
        }
        return m
    }()

    static func typeOptions(_ n: Int) -> [String] {
        (Core.shared.console.isX32 && n >= 5 ? x32Fx5 : abbr).map { name($0) }
    }

    static func abbrOf(_ n: Int, _ raw: Int) -> String? {
        let l = Core.shared.console.isX32 && n >= 5 ? x32Fx5 : abbr
        return raw >= 0 && raw < l.count ? l[raw] : nil
    }

    static func name(_ a: String) -> String {
        if let i = abbr.firstIndex(of: a), i < FX_TYPES.count { return FX_TYPES[i] }
        return a
    }

    static func typeName(_ n: Int, _ raw: Int) -> String { abbrOf(n, raw).map { name($0) } ?? "Efecto #\(raw)" }

    /// "FX1 · Hall Reverb"
    static func label(_ n: Int, _ raw: Int?) -> String {
        guard let r = raw else { return "FX \(n)" }
        return "FX\(n) · \(typeName(n, r))"
    }

    static func params(_ a: String?) -> [Par] {
        if let a = a, let p = parsed[a] { return p }
        return (1...12).map { Par(label: "PARÁM. \($0)", kind: "A", a: 0, b: 100, unit: "%", options: []) }
    }

    static func spec(_ n: Int, _ i: Int, _ p: Par) -> Spec {
        let addr = "/fx/\(n)/par/\(Fmt.two(i + 1))"
        if p.kind == "E" {
            let opts = p.options
            return Spec(label: p.label, addr: addr, steps: opts.count, def: 0) { v in
                let k = Int(v)
                return k >= 0 && k < opts.count ? opts[k] : "?"
            }
        }
        let bip = p.a < 0 && p.b > 0
        return Spec(label: p.label, addr: addr, def: bip ? 0.5 : 0, bipolar: bip) { format(p, $0) }
    }

    static func format(_ p: Par, _ f: Float) -> String {
        let v = p.kind == "L" ? Fmt.log(p.a, p.b, f) : Fmt.lin(p.a, p.b, f)
        switch p.unit {
        case "Hz": return Fmt.hz(v)
        case "ms": return Fmt.ms(v)
        case "s": return String(format: "%.2f s", v)
        case "dB": return Fmt.db(v)
        case "%": return String(format: "%.0f %%", v)
        case "°": return String(format: "%.0f°", v)
        case "x": return String(format: "%.2fx", v)
        default: return p.b - p.a >= 20 ? String(format: "%.0f", v) : String(format: "%.1f", v)
        }
    }

    /// Retorno de cada FX: X Air /rtn/N; X32 FX 1-4 por /fxrtn (pares L/R). FX 5-8 de X32 son insert.
    static func returnPrefix(_ n: Int) -> String? {
        Core.shared.console.isX32 ? (n <= 4 ? "/fxrtn/\(Fmt.two(2 * n - 1))" : nil) : "/rtn/\(n)"
    }
}

/// Valores de fábrica del EQ para RESET (tipo, Hz, Q). Igual que EqDefaults en Android.
enum EqDefaults {
    // Tipos: 0 LCut, 1 LShv, 2 PEQ, 3 VEQ, 4 HShv, 5 HCut
    private static let four: [(Int, Float, Float)] = [(1, 200, 2), (2, 500, 2), (2, 2000, 2), (4, 10000, 2)]
    private static let six: [(Int, Float, Float)] = [(1, 80, 2), (2, 200, 2), (2, 600, 2), (2, 2000, 2), (2, 6000, 2), (4, 12000, 2)]

    static func reset(_ p: String, bands: Int) {
        let list = bands == 4 ? four : six
        for b in 1...bands {
            let (type, hz, q) = list[b - 1]
            Store.shared.set("\(p)/eq/\(b)/type", .i(Int32(type)))
            Store.shared.set("\(p)/eq/\(b)/f", .f(Fmt.logInv(20, 20000, hz)))
            Store.shared.set("\(p)/eq/\(b)/g", .f(0.5))
            Store.shared.set("\(p)/eq/\(b)/q", .f(Fmt.logInv(10, 0.3, q)))
        }
    }
}

/// Color de scribble de un bus para toda la app.
enum BusColor {
    static func of(_ idx: Int) -> Color {
        let c = min(max(idx, 0), 15) % 8
        return c == 0 ? C.sends : C.scribble[c]
    }

    static func ink(_ idx: Int) -> Color {
        let c = min(max(idx, 0), 15) % 8
        return [1, 4, 5].contains(c) ? .white : C.ink
    }
}

extension Strip {
    /// /-stat/selidx = número de solo - 1 (el RTA de la consola sigue esta selección).
    var selIdx: Int {
        guard let n = Int(soloAddr.split(separator: "/").last ?? "") else { return -1 }
        return n - 1
    }
}
