import Foundation

/// MODO DEMO: simula una XR12 sin consola (igual que Demo.kt).
/// - Nada sale por la red mientras está prendido (MixerClient.demo descarta todo lo que se manda).
/// - Valores de ejemplo: nombres, faders, mutes, envíos, EQ y efectos; lo que no está en la tabla toma un valor neutro.
/// - Medidores (y RTA) con señal simulada desde el tick de 30 fps de Core, sin asignaciones.
/// - Para SALA, escenas y el aviso de actualización es como estar sin conexión (isConnected() sigue en false).
enum Demo {
    static var on = false
    static let model = "XR12"
    private static let ch = 12

    static let names = ["VOZ GARKO", "VOZ PALMI", "GUITARRA", "CAJON", "BAJO", "TECLADO L",
                        "TECLADO R", "PISTA L", "PISTA R", "CLICK", "PERC", "AMBIENTE"]
    private static let faders: [Float] = [0.72, 0.70, 0.64, 0.60, 0.62, 0.55, 0.55, 0.58, 0.58, 0.0, 0.50, 0.45]
    private static let pans: [Float] = [0.4, 0.6, 0.3, 0.5, 0.5, 0.0, 1.0, 0.0, 1.0, 0.5, 0.7, 0.5]
    private static let colors: [Int32] = [1, 1, 3, 3, 4, 5, 5, 6, 6, 7, 3, 2]
    /// Envío a los buses 1-2 (monitores) y a los FX.
    private static let mon1: [Float] = [0.70, 0.35, 0.55, 0.50, 0.45, 0.30, 0.30, 0.40, 0.40, 0.65, 0.30, 0]
    private static let mon2: [Float] = [0.35, 0.70, 0.50, 0.55, 0.45, 0.30, 0.30, 0.40, 0.40, 0.65, 0.30, 0]
    private static let rev: [Float] = [0.45, 0.45, 0.30, 0.15, 0, 0.20, 0.20, 0, 0, 0, 0.25, 0]
    /// Cada canal: nivel medio (dB), cuánto se mueve y fase (para que no se muevan todos juntos).
    private static let base: [Float] = [-16, -18, -20, -14, -17, -22, -22, -15, -15, -12, -24, -32]
    private static let swing: [Float] = [10, 10, 6, 9, 5, 4, 4, 3, 3, 2, 8, 3]
    private static let phase: [Double] = [0, 2.1, 0.7, 1.3, 2.9, 0.4, 0.45, 1.9, 1.95, 0, 3.4, 5.0]
    private static let fxTypes: [Int32] = [0, 10, 13, 21]

    // Direcciones precalculadas (el tick no arma strings).
    private static let faderAddr = (1...12).map { "/ch/\(Fmt.two($0))/mix/fader" }
    private static let onAddr = (1...12).map { "/ch/\(Fmt.two($0))/mix/on" }
    private static let lvl: UnsafeMutablePointer<Float> = {
        let p = UnsafeMutablePointer<Float>.allocate(capacity: 12)
        p.initialize(repeating: -90, count: 12)
        return p
    }()
    private static var seedN: UInt32 = 12345

    private static func put(_ a: String, _ v: OscArg) { Store.shared.incoming(a, v) }

    static func seed() {
        for i in 0..<12 { lvl[i] = -90 }
        for i in 0..<ch {
            let p = "/ch/\(Fmt.two(i + 1))"
            put("\(p)/config/name", .s(names[i]))
            put("\(p)/config/color", .i(colors[i]))
            put("\(p)/config/insrc", .i(Int32(i)))
            put("\(p)/mix/fader", .f(faders[i]))
            put("\(p)/mix/on", .i(i == 9 ? 0 : 1)) // el CLICK no va al PA
            put("\(p)/mix/pan", .f(pans[i]))
            put("\(p)/mix/lr", .i(i == 9 ? 0 : 1))
            put("\(p)/mix/01/level", .f(mon1[i]))
            put("\(p)/mix/02/level", .f(mon2[i]))
            put("\(p)/mix/07/level", .f(rev[i]))
            put("\(p)/mix/08/level", .f(rev[i] * 0.6))
            put("\(p)/mix/09/level", .f(i < 2 ? 0.35 : 0))
            put("\(p)/mix/10/level", .f(i == 2 ? 0.3 : 0))
            put("\(p)/dyn/on", .i(i < 5 ? 1 : 0))
            put("\(p)/gate/on", .i(0))
        }
        put("/headamp/01/phantom", .i(0))
        put("/headamp/04/phantom", .i(1))
        put("/bus/1/config/name", .s("MON GARKO"))
        put("/bus/2/config/name", .s("MON PALMI"))
        put("/bus/1/config/color", .i(4))
        put("/bus/2/config/color", .i(5))
        let fxNames = ["REVERB", "PLACA", "DELAY", "CHORUS"]
        for n in 1...4 {
            put("/fx/\(n)/type", .i(fxTypes[n - 1]))
            for k in 1...12 { put("/fx/\(n)/par/\(Fmt.two(k))", .f(Float(k * 7 % 10) / 10)) }
            put("/fxsend/\(n)/config/name", .s(fxNames[n - 1]))
            put("/rtn/\(n)/config/name", .s(fxNames[n - 1]))
            put("/rtn/\(n)/config/color", .i(6))
            put("/rtn/\(n)/mix/fader", .f(0.6))
        }
        put("/lr/config/name", .s("MAIN"))
        put("/lr/mix/fader", .f(0.75))
        put("/lr/mix/on", .i(1))
        put("/-stat/solo", .i(0))
    }

    /// Valor neutro para una dirección que no está en la tabla (igual que Demo.valueFor en Android).
    /// Las consolas X Air usan 0..1 para casi todo: 0,5 queda en el medio (EQ en 0 dB, paneo al centro).
    static func valueFor(_ a: String) -> OscArg {
        if a.hasSuffix("/name") { return .s("") }
        if a.hasSuffix("/on") { return .i(a.contains("/gate/") ? 0 : 1) }
        if a.hasSuffix("/mix/lr") || a.hasSuffix("/mix/st") { return .i(1) }
        if a.hasSuffix("/fader") { return .f(0.75) }
        if a.hasSuffix("/level") { return .f(0.4) }
        if a.hasPrefix("/-stat/") || a.hasPrefix("/config/") { return .i(0) }
        if let r = a.range(of: "/eq/") {
            let rest = a[r.upperBound...]
            if let band = Int(rest.split(separator: "/").first ?? "") {
                let last = (a.hasPrefix("/ch/") || a.hasPrefix("/rtn/")) ? 4 : 6
                if a.hasSuffix("/type") { return .i(band == 1 ? 1 : (band == last ? 4 : 2)) }
                if a.hasSuffix("/f") { return .f(min(max(Float(band) / Float(last + 1), 0.05), 0.95)) }
            }
        }
        return .f(0.5)
    }

    /// Señal simulada: la llama el tick de 30 fps. Sin asignaciones.
    static func tick(_ m: UnsafeMutablePointer<Float>, _ rta: UnsafeMutablePointer<Float>, rtaOn: Bool, now: TimeInterval) {
        let t = now.truncatingRemainder(dividingBy: 100_000)
        var sumL = 0.0, sumR = 0.0, m1 = 0.0, m2 = 0.0
        let st = Store.shared
        for i in 0..<ch {
            // Frases: cada tanto el canal "descansa" (voces sobre todo).
            let phr = sin(t * 0.37 + phase[i] * 1.7)
            let rest = i < 2 && phr < -0.35
            let wob = sin(t * (2.3 + Double(i) * 0.41) + phase[i]) * 0.6 + sin(t * (5.1 + Double(i) * 0.27) + phase[i] * 2) * 0.4
            var db = base[i] + swing[i] * 0.5 * Float(wob) + noise() * 2.5
            if i == 9 { db = (t * 2).truncatingRemainder(dividingBy: 1) < 0.12 ? -12 : -60 } // CLICK: golpes
            if rest { db = -55 + noise() * 3 }
            db = min(max(db, -90), -3)
            // Caída suave (como un medidor real).
            lvl[i] = db > lvl[i] ? db : max(db, lvl[i] - 1.2)
            m[i] = lvl[i]
            let f = st.float(faderAddr[i], def: 0)
            if st.int(onAddr[i], def: 1) != 0 && f > 0 {
                let p = pow(10.0, Double(lvl[i] + Fmt.faderToDb(f)) / 10)
                let pan = Double(pans[i])
                sumL += p * (1 - pan) * 2
                sumR += p * pan * 2
            }
            let pw = pow(10.0, Double(lvl[i]) / 10)
            m1 += pw * Double(mon1[i])
            m2 += pw * Double(mon2[i])
        }
        for i in ch..<16 { m[i] = -100 }
        m[16] = -100; m[17] = -100
        // FX returns (8 medidores, L/R) y FX sends: siguen a las voces.
        let voice = max(lvl[0], lvl[1])
        for k in 0..<4 {
            let v = min(max(voice - 14 - Float(k) * 4 + noise(), -90), -6)
            m[18 + 2 * k] = v
            m[19 + 2 * k] = v - 1
            m[32 + k] = v + 3
        }
        m[26] = db(m1) - 4
        m[27] = db(m2) - 4
        for i in 28...31 { m[i] = -100 }
        let lrF = st.float("/lr/mix/fader", def: 0.75)
        let lrOn = st.int("/lr/mix/on", def: 1) != 0
        let lrDb: Float = lrOn && lrF > 0 ? Fmt.faderToDb(lrF) : -200
        m[36] = min(max(db(sumL) + lrDb, -100), -2)
        m[37] = min(max(db(sumR) + lrDb, -100), -2)
        if rtaOn {
            let ph = t * 0.8
            for b in 0..<MixerClient.rtaBins {
                let d = Double(b)
                let shape = -28 - Float(b) * 0.22 + 7 * Float(sin(d / 6.5 + ph)) + 3 * Float(sin(d / 2.3 - ph * 1.7))
                rta[b] = min(max(shape + noise() * 2, -90), -6)
            }
        }
    }

    private static func db(_ p: Double) -> Float { p > 1e-12 ? Float(10 * log10(p)) : -100 }

    /// Ruido -1..1 (LCG): sin Random ni asignaciones.
    private static func noise() -> Float {
        seedN = seedN &* 1103515245 &+ 12345
        return Float((seedN >> 8) & 0xffff) / 32768 - 1
    }
}
