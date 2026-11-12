import Foundation

/// COPIAR / PEGAR seteos entre tiras del mismo tipo (igual que Clipboard.kt).
/// Las direcciones salen de las tablas de las escenas (Scenes.nodeList + fieldsFor).
/// Nunca se copian solos: nombre, entrada física, GAIN/trim ni 48V.
final class Clipboard: ObservableObject {
    static let shared = Clipboard()

    enum What: String, CaseIterable {
        case config = "CONFIG", gate = "GATE", dyn = "COMPRESOR", eq = "EQ", sends = "ENVÍOS", all = "TODO", busSends = "ENVÍOS AL BUS"
    }

    struct Clip {
        let what: What
        let kind: Kind
        let from: Strip
        let values: [(String, OscArg)]
        var label: String { "\(what.rawValue) de \(from.title)" }
    }

    @Published private(set) var clip: Clip?

    private static let never: Set<String> = ["name", "insrc", "rtnsrc", "source", "rtntrim", "rtnsw", "trim", "icon"]

    private func inSection(_ rel: String, _ w: What) -> Bool {
        switch w {
        case .eq: return rel == "eq" || rel.hasPrefix("eq/")
        case .gate: return rel == "gate" || rel == "gate/filter"
        case .dyn: return rel == "dyn" || rel == "dyn/filter"
        case .sends: return rel.hasPrefix("mix/")
        case .config: return ["config", "preamp", "grp", "delay"].contains(rel)
        case .all: return rel != "insert" && rel != "automix"
        case .busSends: return false
        }
    }

    func leaves(_ s: Strip, _ w: What) -> [String] {
        let base = String(s.prefix.dropFirst())
        var out: [String] = []
        for node in Scenes.shared.nodeList() {
            guard node == base || node.hasPrefix(base + "/") else { continue }
            let rel = String(node.dropFirst(base.count)).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            guard inSection(rel, w), let fields = Scenes.shared.fieldsFor(Scenes.patternKey(node)) else { continue }
            for f in fields where !Clipboard.never.contains(f.sub) { out.append("/\(node)/\(f.sub)") }
        }
        return out
    }

    private func busMix(_ s: Strip) -> String? { s.kind == .bus ? Fmt.two(s.idx) : nil }

    private func busSendLeaves(_ bus: Strip) -> [(String, String)] {
        guard let nn = busMix(bus) else { return [] }
        var out: [(String, String)] = []
        for src in stripsOfKind(.ch) + stripsOfKind(.aux) + stripsOfKind(.rtn) {
            let node = String(src.prefix.dropFirst()) + "/mix/\(nn)"
            guard let fields = Scenes.shared.fieldsFor(Scenes.patternKey(node)) else { continue }
            for f in fields { out.append((src.prefix, f.sub)) }
        }
        return out
    }

    func options(_ s: Strip, _ current: Section?) -> [What] {
        var l: [What] = []
        switch current {
        case .config: l.append(.config)
        case .gate: l.append(.gate)
        case .dyn: l.append(.dyn)
        case .eq: l.append(.eq)
        case .sends: l.append(.sends)
        case nil: break
        }
        l.append(.all)
        if s.kind == .bus { l.append(.busSends) }
        return l.filter { $0 == .busSends || !leaves(s, $0).isEmpty }
    }

    func copy(_ s: Strip, _ w: What, done: @escaping (Int) -> Void) {
        let st = Store.shared
        let addrs: [String] = w == .busSends
            ? busSendLeaves(s).map { "\($0.0)/mix/\(busMix(s) ?? "")/\($0.1)" }
            : leaves(s, w)
        for a in addrs where st.value(a) == nil { Core.shared.client.query(a) }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
            var vals: [(String, OscArg)] = []
            if w == .busSends {
                for (src, sub) in self.busSendLeaves(s) {
                    if let v = st.value("\(src)/mix/\(self.busMix(s) ?? "")/\(sub)") { vals.append(("\(src)|\(sub)", v)) }
                }
            } else {
                for a in addrs { if let v = st.value(a) { vals.append((String(a.dropFirst(s.prefix.count + 1)), v)) } }
            }
            self.clip = vals.isEmpty ? nil : Clip(what: w, kind: s.kind, from: s, values: vals)
            done(vals.count)
        }
    }

    func canPaste(_ t: Strip) -> Bool {
        guard let c = clip else { return false }
        return c.kind == t.kind && c.from.idx != t.idx
    }

    func targets() -> [Strip] {
        guard let c = clip else { return [] }
        return stripsOfKind(c.kind).filter { $0.idx != c.from.idx }
    }

    @discardableResult
    func paste(_ t: Strip) -> Int {
        guard let c = clip, c.kind == t.kind else { return 0 }
        var n = 0
        if c.what == .busSends {
            guard let nn = busMix(t) else { return 0 }
            for (rel, v) in c.values {
                let parts = rel.split(separator: "|", maxSplits: 1).map(String.init)
                guard parts.count == 2 else { continue }
                Store.shared.set("\(parts[0])/mix/\(nn)/\(parts[1])", v)
                n += 1
            }
        } else {
            for (rel, v) in c.values {
                Store.shared.set("\(t.prefix)/\(rel)", v)
                n += 1
            }
        }
        return n
    }

    func clear() { clip = nil }
}
