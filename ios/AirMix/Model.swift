import Foundation

// Espejo de Model.kt (Android). Cualquier cambio de direcciones o índices va en los dos.

enum Family { case xair, x32 }

struct Console: Equatable {
    let family: Family
    let model: String
    let channels: Int
    let auxIns: Int
    let fxReturns: Int
    let buses: Int
    let matrices: Int
    let dcas: Int
    let headamps: Int
    let fxUnits: Int
    let muteGroups: Int

    var isX32: Bool { family == .x32 }
    var port: Int { isX32 ? 10023 : 10024 }
    /// Envíos a FX desde el canal (solo X Air: mix/07..10).
    var fxSends: Int { isX32 ? 0 : 4 }
    var gateModes: [String] { isX32 ? Scenes.x32GateModes : Scenes.xairGateModes }

    func keySourceName(_ v: Int) -> String {
        if isX32 { return v == 0 ? "SELF" : "#\(v)" }
        return v >= 0 && v < Scenes.xairKeySrc.count ? Scenes.xairKeySrc[v] : "#\(v)"
    }

    func sameShape(_ o: Console) -> Bool {
        family == o.family && channels == o.channels && buses == o.buses &&
            auxIns == o.auxIns && fxReturns == o.fxReturns && matrices == o.matrices && dcas == o.dcas
    }

    static func fromModel(_ m: String) -> Console {
        let u = m.uppercased()
        if u.contains("32") { return Console(family: .x32, model: m, channels: 32, auxIns: 8, fxReturns: 8, buses: 16, matrices: 6, dcas: 8, headamps: 32, fxUnits: 8, muteGroups: 6) }
        if u.contains("12") { return Console(family: .xair, model: m, channels: 12, auxIns: 1, fxReturns: 4, buses: 2, matrices: 0, dcas: 4, headamps: 12, fxUnits: 4, muteGroups: 4) }
        if u.contains("16") { return Console(family: .xair, model: m, channels: 16, auxIns: 1, fxReturns: 4, buses: 4, matrices: 0, dcas: 4, headamps: 16, fxUnits: 4, muteGroups: 4) }
        return Console(family: .xair, model: m, channels: 16, auxIns: 1, fxReturns: 4, buses: 6, matrices: 0, dcas: 4, headamps: 16, fxUnits: 4, muteGroups: 4)
    }
}

enum Kind { case ch, aux, rtn, bus, mtx, fxsend, lr, dca }

enum Section: String, CaseIterable {
    case config = "CONFIG", gate = "GATE", dyn = "DYN", eq = "EQ", sends = "ENVÍOS"
}

struct SendTarget: Equatable {
    var bus = 0
    var fx = 0
    static let main = SendTarget()
    var label: String { bus > 0 ? "BUS \(bus)" : fx > 0 ? "FX \(fx)" : "MAIN" }

    /// Dirección del nombre del bus / FX send (para mostrar "1-JUANCITO").
    var nameAddr: String? {
        if bus > 0 { return Strip(kind: .bus, idx: bus).nameAddr }
        if fx > 0 { return Strip(kind: .fxsend, idx: fx).nameAddr }
        return nil
    }

    /// "1-JUANCITO" / "FX1-REVERB"; si no tiene nombre, "BUS 1" / "FX 1".
    func display(_ name: String) -> String {
        let n = name.trimmingCharacters(in: .whitespaces)
        if bus > 0 { return n.isEmpty ? "BUS \(bus)" : "\(bus)-\(n)" }
        if fx > 0 { return n.isEmpty ? "FX \(fx)" : "FX\(fx)-\(n)" }
        return "MAIN"
    }
}

/// pair = true: par estéreo linkeado (idx impar + el siguiente) mostrado como una sola tira; se controla desde el impar.
struct Strip: Equatable, Hashable {
    let kind: Kind
    var idx: Int = 1
    var pair = false

    private var c: Console { Core.shared.console }

    var prefix: String {
        switch kind {
        case .ch: return "/ch/\(Fmt.two(idx))"
        case .aux: return c.isX32 ? "/auxin/\(Fmt.two(idx))" : "/rtn/aux"
        case .rtn: return c.isX32 ? "/fxrtn/\(Fmt.two(idx))" : "/rtn/\(idx)"
        case .bus: return c.isX32 ? "/bus/\(Fmt.two(idx))" : "/bus/\(idx)"
        case .mtx: return "/mtx/\(Fmt.two(idx))"
        case .fxsend: return "/fxsend/\(idx)"
        case .lr: return c.isX32 ? "/main/st" : "/lr"
        case .dca: return "/dca/\(idx)"
        }
    }

    var title: String {
        switch kind {
        case .ch: return pair ? "CH \(Fmt.two(idx))-\(Fmt.two(idx + 1))" : "CH \(Fmt.two(idx))"
        case .aux: return c.auxIns > 1 ? "AUX \(idx)" : "AUX"
        case .rtn: return c.isX32 ? "FXR \(idx)" : "FX \(idx)"
        case .bus: return "BUS \(idx)"
        case .mtx: return "MTX \(idx)"
        case .fxsend: return "FX SEND \(idx)"
        case .lr: return "MAIN LR"
        case .dca: return "DCA \(idx)"
        }
    }

    var faderAddr: String { kind == .dca ? "\(prefix)/fader" : "\(prefix)/mix/fader" }
    var onAddr: String { kind == .dca ? "\(prefix)/on" : "\(prefix)/mix/on" }
    var panAddr: String? {
        switch kind {
        case .ch, .aux, .rtn, .lr: return "\(prefix)/mix/pan"
        default: return nil
        }
    }
    var nameAddr: String { "\(prefix)/config/name" }
    var colorAddr: String { "\(prefix)/config/color" }
    var lrAssignAddr: String { c.isX32 ? "\(prefix)/mix/st" : "\(prefix)/mix/lr" }
    var sourceAddr: String { c.isX32 ? "\(prefix)/config/source" : "\(prefix)/config/insrc" }

    var meterL: Int {
        if c.isX32 {
            switch kind {
            case .ch: return idx - 1
            case .aux: return 31 + idx
            case .rtn: return 39 + idx
            case .bus: return 47 + idx
            case .mtx: return 63 + idx
            case .lr: return 70
            default: return -1
            }
        }
        switch kind {
        case .ch: return idx - 1
        case .aux: return 16
        case .rtn: return 18 + 2 * (idx - 1)
        case .bus: return 25 + idx
        case .fxsend: return 31 + idx
        case .lr: return 36
        default: return -1
        }
    }

    var meterR: Int {
        if kind == .ch && pair { return idx }
        if c.isX32 { return kind == .lr ? 71 : -1 }
        switch kind {
        case .aux: return 17
        case .rtn: return 19 + 2 * (idx - 1)
        case .lr: return 37
        default: return -1
        }
    }

    var soloAddr: String {
        let n: Int
        if c.isX32 {
            switch kind {
            case .ch: n = idx
            case .aux: n = 32 + idx
            case .rtn: n = 40 + idx
            case .bus: n = 48 + idx
            case .mtx: n = 64 + idx
            case .lr: n = 71
            case .dca: n = 72 + idx
            case .fxsend: n = 0
            }
        } else {
            switch kind {
            case .ch: n = idx
            case .aux: n = 17
            case .rtn: n = 17 + idx
            case .bus: n = 21 + idx
            case .fxsend: n = 27 + idx
            case .lr: n = 32
            case .dca: n = 32 + idx
            case .mtx: n = 0
            }
        }
        return "/-stat/solosw/" + Fmt.two(n)
    }

    func sendAddr(_ t: SendTarget) -> String? {
        if t.bus > 0 && (kind == .ch || kind == .aux || kind == .rtn) { return "\(prefix)/mix/\(Fmt.two(t.bus))/level" }
        if t.fx > 0 && !c.isX32 && (kind == .ch || kind == .aux) { return "\(prefix)/mix/\(Fmt.two(6 + t.fx))/level" }
        return nil
    }

    /// Preamp que alimenta al canal según su FUENTE (no según el número de canal). Igual que headampFor en Android:
    /// X Air insrc 0-15 -> /headamp/01..16; X32 source 1-128 -> /headamp/000..127; el resto no tiene preamp.
    func headampFor(_ src: Int) -> String? {
        guard kind == .ch else { return nil }
        if c.isX32 { return (1...128).contains(src) ? "/headamp/" + String(format: "%03d", src - 1) : nil }
        return (0...15).contains(src) ? "/headamp/\(Fmt.two(src + 1))" : nil
    }

    static func == (a: Strip, b: Strip) -> Bool { a.kind == b.kind && a.idx == b.idx && a.pair == b.pair }
    func hash(into h: inout Hasher) { h.combine(idx); h.combine("\(kind)"); h.combine(pair) }
}

func stripsOfKind(_ k: Kind) -> [Strip] {
    let c = Core.shared.console
    let n: Int
    switch k {
    case .ch: n = c.channels
    case .aux: n = c.auxIns
    case .rtn: n = c.fxReturns
    case .bus: n = c.buses
    case .mtx: n = c.matrices
    case .fxsend: n = c.fxSends
    case .dca: n = c.dcas
    case .lr: n = 1
    }
    return n > 0 ? (1...n).map { Strip(kind: k, idx: $0) } : []
}

struct Layer: Identifiable {
    let id: Int
    let label: String
    let strips: [Strip]
    var fav = false
    var kind: Kind { strips.first?.kind ?? .ch }
}

/// Fuente del RTA (igual que Model.kt). X Air: /-stat/rta/source 0-32 = Ch01..16, Aux, Fx1..4, Bus1..6, Send1..4, LR, MON.
/// En X32 no se toca (numeración no verificada).
let rtaSourceAddr = "/-stat/rta/source"

extension Strip {
    var rtaSource: Int? {
        if Core.shared.console.isX32 { return nil }
        switch kind {
        case .ch: return idx - 1
        case .aux: return 16
        case .rtn: return 16 + idx
        case .bus: return 20 + idx
        case .fxsend: return 26 + idx
        case .lr: return 31
        default: return nil
        }
    }
}

/// Entradas físicas que se le pueden asignar a un canal (índice = valor de insrc / source). Igual que en Model.kt.
func sourceOptions() -> [String] {
    let c = Core.shared.console
    if !c.isX32 { return (1...max(1, c.channels)).map { "IN \(Fmt.two($0))" } }
    var l = ["OFF"]
    l += (1...32).map { "IN \(Fmt.two($0))" }
    l += (1...48).map { "AES50 A \(Fmt.two($0))" }
    l += (1...48).map { "AES50 B \(Fmt.two($0))" }
    l += (1...32).map { "CARD \(Fmt.two($0))" }
    l += (1...6).map { "AUX \($0)" }
    l += ["TALKBACK INT", "TALKBACK EXT"]
    return l
}

// MARK: favoritos (igual que en Model.kt)

let maxFavs = 8
let favKinds: [Kind] = [.ch, .aux, .rtn, .bus, .mtx, .fxsend, .dca]

extension Strip {
    /// "CH:1", igual que en Android.
    var favKey: String { "\(String(describing: kind).uppercased()):\(idx)" }
}

/// Favoritos guardados -> tiras. Un canal de un par linkeado se muestra como el par (una sola vez).
func favStrips(_ saved: String) -> [Strip] {
    let links = Core.shared.links
    var out: [Strip] = []
    for tok in saved.split(separator: ";") {
        let parts = tok.split(separator: ":")
        guard parts.count == 2, let idx = Int(parts[1]),
              let kind = favKinds.first(where: { String(describing: $0).uppercased() == parts[0] }),
              stripsOfKind(kind).contains(where: { $0.idx == idx }) else { continue }
        var st = Strip(kind: kind, idx: idx)
        if kind == .ch {
            if idx % 2 == 1 && links.contains(idx) { st = Strip(kind: .ch, idx: idx, pair: true) }
            else if idx % 2 == 0 && links.contains(idx - 1) { st = Strip(kind: .ch, idx: idx - 1, pair: true) }
        }
        if !out.contains(st) { out.append(st) }
    }
    return out
}

func favCandidates() -> [Strip] { favKinds.flatMap { stripsOfKind($0) } }

/// TAP tempo (igual que en Android): el tiempo va a todos los efectos cargados con parámetro TIME en ms.
enum TapTempo {
    private static var taps: [Double] = []
    static var bpm = 0

    static func slots() -> Int { Core.shared.console.isX32 ? 4 : Core.shared.console.fxUnits }

    /// Registra un toque. Devuelve cuántos efectos se actualizaron (-1 = falta otro toque).
    static func tap(_ now: Double) -> Int {
        if let last = taps.last, now - last > 2 { taps.removeAll() }
        taps.append(now)
        if taps.count > 5 { taps.removeFirst() }
        guard taps.count >= 2 else { return -1 }
        let ms = Float((taps.last! - taps.first!) * 1000 / Double(taps.count - 1))
        guard ms >= 100 else { return -1 }
        bpm = Int((60000 / ms).rounded())
        return apply(ms)
    }

    static func apply(_ ms: Float) -> Int {
        var applied = 0
        for n in 1...max(1, slots()) {
            let raw = Store.shared.int("/fx/\(n)/type", def: -1)
            guard raw >= 0, let abbr = Fx.abbrOf(n, raw) else { continue }
            let pars = Fx.params(abbr)
            guard let i = pars.firstIndex(where: { $0.label == "TIME" && $0.unit == "ms" && $0.kind == "A" }) else { continue }
            let p = pars[i]
            let v = min(max((min(max(ms, p.a), p.b) - p.a) / (p.b - p.a), 0), 1)
            Store.shared.set("/fx/\(n)/par/\(Fmt.two(i + 1))", .f(v))
            applied += 1
        }
        return applied
    }
}

/// Link estéreo de canales: dirección del par que empieza en el impar n (igual que chLinkAddr en Android).
func chLinkAddr(_ odd: Int) -> String { "/config/chlink/\(odd)-\(odd + 1)" }
func chLinkPairs() -> [Int] { Array(stride(from: 1, to: Core.shared.console.channels, by: 2)) }
func linkedPairs() -> Set<Int> { Set(chLinkPairs().filter { Store.shared.int(chLinkAddr($0), def: 0) != 0 }) }

func buildLayers() -> [Layer] {
    let c = Core.shared.console
    var out: [Layer] = []
    func add(_ l: String, _ s: [Strip]) { out.append(Layer(id: out.count, label: l, strips: s)) }
    let links = Core.shared.links
    // Capas fijas de 8 canales; dentro de cada una, los pares linkeados se juntan en una sola tira.
    for g in stripsOfKind(.ch).chunked(8) {
        var merged: [Strip] = []
        var k = 0
        while k < g.count {
            let s = g[k]
            if s.idx % 2 == 1 && links.contains(s.idx) && k + 1 < g.count {
                merged.append(Strip(kind: .ch, idx: s.idx, pair: true))
                k += 2
            } else {
                merged.append(s)
                k += 1
            }
        }
        add("CH \(g.first!.idx)-\(g.last!.idx)", merged)
    }
    add(c.auxIns > 1 ? "AUX IN" : "AUX", stripsOfKind(.aux))
    add("FX RTN", stripsOfKind(.rtn))
    let buses = stripsOfKind(.bus)
    if buses.count <= 8 { add("BUSES", buses) } else { for g in buses.chunked(8) { add("BUS \(g.first!.idx)-\(g.last!.idx)", g) } }
    if c.matrices > 0 { add("MATRIX", stripsOfKind(.mtx)) }
    if c.fxSends > 0 { add("FX SENDS", stripsOfKind(.fxsend)) }
    // DCA ya no va en el riel (pedido de Joaquín): se puede sumar a FAVS si hace falta.
    return out
}

func sectionsFor(_ s: Strip) -> [Section] {
    switch s.kind {
    case .ch: return [.config, .gate, .dyn, .eq, .sends]
    case .bus, .mtx: return [.config, .dyn, .eq]
    case .lr: return [.dyn, .eq]
    default: return [.config]
    }
}

func meterLabels() -> [String] {
    let c = Core.shared.console
    var l: [String] = []
    if c.isX32 {
        for i in 1...32 { l.append("\(i)") }
        for i in 1...8 { l.append("A\(i)") }
        for i in 1...8 { l.append("FR\(i)") }
        for i in 1...16 { l.append("B\(i)") }
        for i in 1...6 { l.append("M\(i)") }
        l.append("L"); l.append("R")
    } else {
        for i in 1...16 { l.append(i <= c.channels ? "\(i)" : "·") }
        l.append("AUX L"); l.append("AUX R")
        for i in 1...4 { l.append("FX\(i)L"); l.append("FX\(i)R") }
        for i in 1...6 { l.append("B\(i)") }
        for i in 1...4 { l.append("FXS\(i)") }
        l.append("L"); l.append("R")
    }
    return l
}

extension Array {
    func chunked(_ n: Int) -> [[Element]] {
        stride(from: 0, to: count, by: n).map { Array(self[$0..<Swift.min($0 + n, count)]) }
    }
}

/// Mute de efectos de un toque. Igual que FxMute en Android.
enum FxMute {
    private static var saved = Set<String>()

    static func targets() -> [String] {
        let c = Core.shared.console
        if c.isX32 { return (1...c.fxReturns).map { "/fxrtn/\(Fmt.two($0))/mix/on" } }
        return (1...4).map { "/fxsend/\($0)/mix/on" }
    }

    static func allMuted() -> Bool { targets().allSatisfy { Store.shared.int($0, def: 1) == 0 } }

    static func toggle() {
        let t = targets()
        if allMuted() {
            let restore = saved.isEmpty ? t : t.filter { saved.contains($0) }
            for a in restore { Store.shared.set(a, .i(1)) }
            saved.removeAll()
        } else {
            saved.removeAll()
            for a in t where Store.shared.int(a, def: 1) != 0 { saved.insert(a) }
            for a in t { Store.shared.set(a, .i(0)) }
        }
    }
}

enum Fmt {
    static func two(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }
    static func lin(_ min: Float, _ max: Float, _ f: Float) -> Float { min + (max - min) * f }
    static func log(_ min: Float, _ max: Float, _ f: Float) -> Float { min * powf(max / min, f) }
    static func logInv(_ min: Float, _ max: Float, _ v: Float) -> Float { logf(v / min) / logf(max / min) }
    static func db(_ v: Float) -> String { String(format: "%.1f dB", v) }
    static func hz(_ v: Float) -> String { v >= 1000 ? String(format: "%.2f kHz", v / 1000) : String(format: "%.1f Hz", v) }
    static func ms(_ v: Float) -> String {
        if v >= 100 { return String(format: "%.0f ms", v) }
        if v >= 10 { return String(format: "%.1f ms", v) }
        return String(format: "%.2f ms", v)
    }
    static func pct(_ v: Float) -> String { String(format: "%.0f %%", v) }
    static func num(_ v: Float) -> String { String(format: "%.1f", v) }

    static func faderToDb(_ f: Float) -> Float {
        if f >= 0.5 { return f * 40 - 30 }
        if f >= 0.25 { return f * 80 - 50 }
        if f >= 0.0625 { return f * 160 - 70 }
        if f > 0 { return f * 480 - 90 }
        return -144
    }

    static func dbToFader(_ db: Float) -> Float {
        if db >= -10 { return (db + 30) / 40 }
        if db >= -30 { return (db + 50) / 80 }
        if db >= -60 { return (db + 70) / 160 }
        if db > -90 { return (db + 90) / 480 }
        return 0
    }

    static func faderDb(_ f: Float) -> String {
        let d = faderToDb(f)
        return d <= -89.5 ? "-∞" : String(format: "%+.1f", d)
    }
}

let FX_TYPES = [
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
]

/// Cronómetro del show (persistente, usa el reloj real).
enum ShowTimer {
    private static let d = UserDefaults.standard

    static var running: Bool { d.bool(forKey: "t_run") }

    static func elapsed() -> TimeInterval {
        let acc = d.double(forKey: "t_acc")
        return running ? acc + (Date().timeIntervalSince1970 - d.double(forKey: "t_start")) : acc
    }

    static func toggle() {
        if running {
            d.set(elapsed(), forKey: "t_acc")
            d.set(false, forKey: "t_run")
        } else {
            d.set(Date().timeIntervalSince1970, forKey: "t_start")
            d.set(true, forKey: "t_run")
        }
    }

    static func reset() {
        d.set(false, forKey: "t_run")
        d.set(0.0, forKey: "t_acc")
    }

    static func format(_ t: TimeInterval) -> String {
        let s = Int(t)
        let h = s / 3600, m = (s % 3600) / 60, sec = s % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, sec) : String(format: "%02d:%02d", m, sec)
    }
}
