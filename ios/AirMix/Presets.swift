import Foundation

/// PRESETS guardados en el dispositivo (sin internet). Igual en Presets.kt (Android).
///  - De canal: la configuración de una tira (EQ, compresor, gate, filtro…) sin nombre, entrada, GAIN, 48V ni envíos.
///    Se cargan en cualquier tira del mismo tipo.
///  - De efecto: tipo de efecto + todos sus parámetros. Se cargan en cualquier FX.
/// Los valores se guardan con su tipo ("i:3", "f:0.5", "s:texto") para escribirlos igual que vinieron.
enum Presets {
    static let max = 60
    private static let keyStrip = "presets_strip"
    private static let keyFx = "presets_fx"

    struct Preset: Identifiable {
        var id: String { kind + "|" + name }
        let name: String
        let kind: String
        let type: Int
        let values: [(String, OscArg)]
    }

    private static func enc(_ v: OscArg) -> String? {
        switch v {
        case .i(let x): return "i:\(x)"
        case .f(let x): return "f:\(x)"
        case .s(let x): return "s:\(x)"
        case .b: return nil
        }
    }

    private static func dec(_ s: String) -> OscArg? {
        if s.hasPrefix("i:"), let v = Int32(s.dropFirst(2)) { return .i(v) }
        if s.hasPrefix("f:"), let v = Float(s.dropFirst(2)) { return .f(v) }
        if s.hasPrefix("s:") { return .s(String(s.dropFirst(2))) }
        return nil
    }

    private static func load(_ key: String) -> [Preset] {
        guard let data = UserDefaults.standard.string(forKey: key)?.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return arr.compactMap { o in
            guard let n = o["n"] as? String, let vo = o["v"] as? [String: String] else { return nil }
            let vals = vo.keys.sorted().compactMap { k in dec(vo[k] ?? "").map { (k, $0) } }
            return Preset(name: n, kind: o["k"] as? String ?? "", type: o["t"] as? Int ?? -1, values: vals)
        }
    }

    private static func save(_ key: String, _ list: [Preset]) {
        let arr: [[String: Any]] = list.map { p in
            var vo: [String: String] = [:]
            for (k, v) in p.values { if let e = enc(v) { vo[k] = e } }
            return ["n": p.name, "k": p.kind, "t": p.type, "v": vo]
        }
        if let d = try? JSONSerialization.data(withJSONObject: arr), let s = String(data: d, encoding: .utf8) {
            UserDefaults.standard.set(s, forKey: key)
        }
    }

    // MARK: canal

    static func kindKey(_ s: Strip) -> String { "\(s.kind)" }

    static func stripPresets(_ s: Strip) -> [Preset] { load(keyStrip).filter { $0.kind == kindKey(s) } }

    /// Lo que guarda un preset de canal: todo menos envíos (y lo que el portapapeles nunca copia).
    private static func stripLeaves(_ s: Strip) -> [String] {
        Clipboard.shared.leaves(s, .all).filter { !String($0.dropFirst(s.prefix.count + 1)).hasPrefix("mix/") }
    }

    /// Pide a la consola lo que falte y, un momento después, guarda. done(n) = cuántos valores.
    static func saveStrip(_ s: Strip, name: String, done: @escaping (Int) -> Void) {
        let addrs = stripLeaves(s)
        for a in addrs where Store.shared.value(a) == nil { _ = Store.shared.param(a) }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
            let vals: [(String, OscArg)] = addrs.compactMap { a in Store.shared.value(a).map { (String(a.dropFirst(s.prefix.count + 1)), $0) } }
            if !vals.isEmpty {
                var list = load(keyStrip)
                list.removeAll { $0.kind == kindKey(s) && $0.name.lowercased() == name.lowercased() }
                list.insert(Preset(name: name, kind: kindKey(s), type: -1, values: vals), at: 0)
                save(keyStrip, Array(list.prefix(max)))
            }
            done(vals.count)
        }
    }

    @discardableResult
    static func applyStrip(_ p: Preset, _ s: Strip) -> Int {
        for (rel, v) in p.values { Store.shared.set("\(s.prefix)/\(rel)", v) }
        return p.values.count
    }

    static func deleteStrip(_ p: Preset) {
        var list = load(keyStrip)
        list.removeAll { $0.kind == p.kind && $0.name == p.name }
        save(keyStrip, list)
    }

    // MARK: efectos

    static func fxPresets() -> [Preset] { load(keyFx) }

    static func saveFx(_ n: Int, name: String, done: @escaping (Int) -> Void) {
        let type = Store.shared.int("/fx/\(n)/type", def: -1)
        let count = Fx.params(Fx.abbrOf(n, type)).count
        let addrs = count > 0 ? (1...count).map { "/fx/\(n)/par/\(Fmt.two($0))" } : []
        for a in addrs where Store.shared.value(a) == nil { _ = Store.shared.param(a) }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
            var vals: [(String, OscArg)] = []
            for (i, a) in addrs.enumerated() { if let v = Store.shared.value(a) { vals.append((Fmt.two(i + 1), v)) } }
            if type >= 0 && !vals.isEmpty {
                var list = load(keyFx)
                list.removeAll { $0.name.lowercased() == name.lowercased() }
                list.insert(Preset(name: name, kind: Fx.typeName(n, type), type: type, values: vals), at: 0)
                save(keyFx, Array(list.prefix(max)))
            }
            done(type < 0 ? 0 : vals.count)
        }
    }

    /// Carga: primero el tipo de efecto y, cuando la consola lo cambió, los parámetros.
    static func applyFx(_ p: Preset, _ n: Int) {
        Store.shared.set("/fx/\(n)/type", .i(Int32(p.type)))
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            for (k, v) in p.values { Store.shared.set("/fx/\(n)/par/\(k)", v) }
        }
    }

    static func deleteFx(_ p: Preset) {
        var list = load(keyFx)
        list.removeAll { $0.name == p.name }
        save(keyFx, list)
    }
}
