import Foundation

/// Escenas .scn. Espejo exacto de Scenes.kt (mismas tablas y mismo proceso de verificación).
final class Scenes: ObservableObject {
    static let shared = Scenes()

    @Published var status = "Listo."
    @Published var busy = false
    @Published var exportText: String?
    @Published var exportName = "escena"
    @Published var libraryVersion = 0

    private var saveReplies: [String: String] = [:]
    private var liveNodes: [String: [String]] = [:]
    private var collecting = false
    private var lastReply: TimeInterval = 0

    // MARK: lista de bloques

    func nodeList() -> [String] {
        let c = Core.shared.console
        var l: [String] = []
        if !c.isX32 {
            for i in 1...c.channels {
                let p = "ch/\(Fmt.two(i))"
                for s in ["config", "preamp", "gate", "gate/filter", "dyn", "dyn/filter", "insert", "eq"] { l.append("\(p)/\(s)") }
                for b in 1...4 { l.append("\(p)/eq/\(b)") }
                l.append("\(p)/mix")
                for b in 1...c.buses { l.append("\(p)/mix/\(Fmt.two(b))") }
                for f in 7...10 { l.append("\(p)/mix/\(Fmt.two(f))") }
                l.append("\(p)/grp")
                l.append("\(p)/automix")
            }
            for i in 1...c.headamps { l.append("headamp/\(Fmt.two(i))") }
            for p in ["rtn/aux"] + (1...4).map({ "rtn/\($0)" }) {
                for s in ["config", "preamp", "eq"] { l.append("\(p)/\(s)") }
                for b in 1...4 { l.append("\(p)/eq/\(b)") }
                l.append("\(p)/mix")
                for b in 1...c.buses { l.append("\(p)/mix/\(Fmt.two(b))") }
                l.append("\(p)/grp")
            }
            for i in 1...c.buses {
                let p = "bus/\(i)"
                for s in ["config", "dyn", "dyn/filter", "insert", "eq"] { l.append("\(p)/\(s)") }
                for b in 1...6 { l.append("\(p)/eq/\(b)") }
                l.append("\(p)/mix")
                l.append("\(p)/grp")
            }
            for i in 1...4 { l.append("fxsend/\(i)/config"); l.append("fxsend/\(i)/mix"); l.append("fxsend/\(i)/grp") }
            for s in ["config", "dyn", "dyn/filter", "insert", "eq"] { l.append("lr/\(s)") }
            for b in 1...6 { l.append("lr/eq/\(b)") }
            l.append("lr/mix")
            for i in 1...c.dcas { l.append("dca/\(i)"); l.append("dca/\(i)/config") }
            for i in 1...c.fxUnits { l.append("fx/\(i)"); l.append("fx/\(i)/par") }
        } else {
            for i in 1...32 {
                let p = "ch/\(Fmt.two(i))"
                for s in ["config", "delay", "preamp", "gate", "gate/filter", "dyn", "dyn/filter", "insert", "eq"] { l.append("\(p)/\(s)") }
                for b in 1...4 { l.append("\(p)/eq/\(b)") }
                l.append("\(p)/mix")
                for b in 1...16 { l.append("\(p)/mix/\(Fmt.two(b))") }
                l.append("\(p)/grp")
                l.append("\(p)/automix")
            }
            for i in 1...8 {
                let p = "auxin/\(Fmt.two(i))"
                for s in ["config", "preamp", "eq"] { l.append("\(p)/\(s)") }
                for b in 1...4 { l.append("\(p)/eq/\(b)") }
                l.append("\(p)/mix")
                for b in 1...16 { l.append("\(p)/mix/\(Fmt.two(b))") }
                l.append("\(p)/grp")
            }
            for i in 1...8 {
                let p = "fxrtn/\(Fmt.two(i))"
                for s in ["config", "eq"] { l.append("\(p)/\(s)") }
                for b in 1...4 { l.append("\(p)/eq/\(b)") }
                l.append("\(p)/mix")
                for b in 1...16 { l.append("\(p)/mix/\(Fmt.two(b))") }
                l.append("\(p)/grp")
            }
            for i in 1...16 {
                let p = "bus/\(Fmt.two(i))"
                for s in ["config", "dyn", "dyn/filter", "insert", "eq"] { l.append("\(p)/\(s)") }
                for b in 1...6 { l.append("\(p)/eq/\(b)") }
                l.append("\(p)/mix")
                for m in 1...6 { l.append("\(p)/mix/\(Fmt.two(m))") }
                l.append("\(p)/grp")
            }
            for i in 1...6 {
                let p = "mtx/\(Fmt.two(i))"
                for s in ["config", "preamp", "dyn", "dyn/filter", "insert", "eq"] { l.append("\(p)/\(s)") }
                for b in 1...6 { l.append("\(p)/eq/\(b)") }
                l.append("\(p)/mix")
                l.append("\(p)/grp")
            }
            for p in ["main/st", "main/m"] {
                for s in ["config", "dyn", "dyn/filter", "insert", "eq"] { l.append("\(p)/\(s)") }
                for b in 1...6 { l.append("\(p)/eq/\(b)") }
                l.append("\(p)/mix")
                for m in 1...6 { l.append("\(p)/mix/\(Fmt.two(m))") }
                l.append("\(p)/grp")
            }
            for i in 1...8 { l.append("dca/\(i)"); l.append("dca/\(i)/config") }
            for i in 1...8 { l.append("fx/\(i)"); l.append("fx/\(i)/source"); l.append("fx/\(i)/par") }
            for i in 0...31 { l.append("headamp/" + String(format: "%03d", i)) }
        }
        return l
    }

    // MARK: respuestas /node

    func onNode(_ raw: String) {
        let line = raw.replacingOccurrences(of: "\u{07}", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard line.hasPrefix("/") else { return }
        let toks = Scenes.tokenize(line)
        guard let first = toks.first else { return }
        let path = String(first.dropFirst())
        lastReply = Date().timeIntervalSince1970
        if collecting { saveReplies[path] = line }
        liveNodes[path] = Array(toks.dropFirst())
    }

    // MARK: guardar

    func save(name: String, notes: String) {
        if busy { return }
        guard Core.shared.client.isConnected() else { status = "Sin conexión con la consola."; return }
        let nodes = nodeList()
        saveReplies.removeAll()
        collecting = true
        busy = true
        lastReply = Date().timeIntervalSince1970
        let started = Date().timeIntervalSince1970
        var i = 0
        func step() {
            var n = 0
            while i < nodes.count && n < 6 {
                Core.shared.client.node(nodes[i]); i += 1; n += 1
            }
            status = "Leyendo consola… \(saveReplies.count) / \(nodes.count)"
            let now = Date().timeIntervalSince1970
            let done = i >= nodes.count && (saveReplies.count >= nodes.count || now - lastReply > 1.5 || now - started > 30)
            if !done {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.02) { step() }
                return
            }
            collecting = false
            busy = false
            let ver = Core.shared.console.isX32 ? "#4.0#" : "#2.1#"
            var text = "\(ver) \"\(Scenes.clean(name))\" \"\(Scenes.clean(notes))\" %000000000 1 AirMix\n"
            var count = 0
            for p in nodes {
                guard let l = saveReplies[p] else { continue }
                text += l + "\n"
                count += 1
            }
            if count == 0 { status = "La consola no respondió. No se guardó nada."; return }
            let file = Scenes.libraryURL().appendingPathComponent(Scenes.safeName(name) + ".scn")
            try? text.data(using: .isoLatin1, allowLossyConversion: true)?.write(to: file)
            libraryVersion += 1
            // Queda guardada local, sin internet (el dispositivo suele estar en el WiFi de la consola).
            status = "✓ Escena leída (\(count) bloques) y guardada en el dispositivo: Archivos → En mi iPad/iPhone → AirMix → Escenas → \"\(file.lastPathComponent)\"."
        }
        step()
    }

    static func clean(_ s: String) -> String {
        String(s.replacingOccurrences(of: "\"", with: "'").replacingOccurrences(of: "\n", with: " ").prefix(32))
    }

    static func safeName(_ n: String) -> String {
        let t = n.trimmingCharacters(in: .whitespaces)
        let base = t.isEmpty ? "escena" : t
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: " _-"))
        return String(String(base.unicodeScalars.map { allowed.contains($0) ? Character($0) : "_" }).prefix(40))
    }

    static func libraryURL() -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("Escenas")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    // MARK: cargar

    struct SceneFile {
        let name: String
        let notes: String
        let lines: [(String, [String])]
    }

    static func parse(_ text: String, fallbackName: String) -> SceneFile {
        var name = fallbackName
        var notes = ""
        var lines: [(String, [String])] = []
        for raw in text.components(separatedBy: .newlines) {
            let l = raw.trimmingCharacters(in: .whitespaces)
            if l.isEmpty { continue }
            if l.hasPrefix("#") {
                let t = tokenize(l)
                if t.count > 1 { name = t[1] }
                if t.count > 2 { notes = t[2] }
                continue
            }
            guard l.hasPrefix("/") else { continue }
            let t = tokenize(l)
            guard let first = t.first else { continue }
            lines.append((String(first.dropFirst()), Array(t.dropFirst())))
        }
        return SceneFile(name: name, notes: notes, lines: lines)
    }

    func load(_ scene: SceneFile) {
        if busy { return }
        guard Core.shared.client.isConnected() else { status = "Sin conexión con la consola."; return }
        busy = true
        liveNodes.removeAll()

        // 1) muestras para calibrar
        var samples: [(String, [String])] = []
        var index: [String: Int] = [:]
        for (path, _) in scene.lines {
            let key = Scenes.patternKey(path)
            guard fieldsFor(key) != nil else { continue }
            if let i = index[key] {
                if samples[i].1.count < 3 { samples[i].1.append(path) }
            } else {
                index[key] = samples.count
                samples.append((key, [path]))
            }
        }
        for (key, paths) in samples {
            guard let fields = fieldsFor(key) else { continue }
            for p in paths {
                Core.shared.client.node(p)
                for f in fields { Store.shared.query("/\(p)/\(f.sub)") }
            }
        }
        status = "Verificando formato con la consola…"

        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { [self] in
            // 2) verificar campo por campo
            var ok: [String: [Bool]] = [:]
            for (key, paths) in samples {
                guard let fields = fieldsFor(key) else { continue }
                var res = [Bool](repeating: false, count: fields.count)
                for fi in 0..<fields.count {
                    var compared = 0
                    var good = true
                    for p in paths {
                        guard let toks = liveNodes[p], fi < toks.count else { continue }
                        guard let live = Store.shared.value("/\(p)/\(fields[fi].sub)") else { continue }
                        guard let parsed = Scenes.convert(fields[fi], toks[fi]) else { good = false; continue }
                        compared += 1
                        if !Scenes.same(parsed, live) { good = false }
                    }
                    res[fi] = good && compared > 0
                }
                ok[key] = res
            }

            // 3) cambios
            var writes: [(String, OscArg)] = []
            var skipped = 0
            var unknown = 0
            for (path, toks) in scene.lines {
                let key = Scenes.patternKey(path)
                guard let fields = fieldsFor(key), let verified = ok[key] else { unknown += 1; continue }
                for fi in 0..<fields.count {
                    if fi >= toks.count { break }
                    if !verified[fi] { skipped += 1; continue }
                    guard let v = Scenes.convert(fields[fi], toks[fi]) else { continue }
                    writes.append(("/\(path)/\(fields[fi].sub)", v))
                }
            }
            if writes.isEmpty {
                busy = false
                status = "No se pudo verificar el formato con esta consola. No se cargó nada."
                return
            }

            // 4) enviar de a poco
            var i = 0
            func step() {
                var n = 0
                while i < writes.count && n < 25 {
                    Store.shared.set(writes[i].0, writes[i].1); i += 1; n += 1
                }
                status = "Cargando… \(i) / \(writes.count)"
                if i < writes.count {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.016) { step() }
                } else {
                    busy = false
                    var extra = ""
                    if skipped > 0 { extra += "\n• \(skipped) campos sin verificar quedaron como estaban." }
                    if unknown > 0 { extra += "\n• \(unknown) bloques no soportados todavía (parámetros de FX, ruteo, etc.)." }
                    status = "✓ Escena \"\(scene.name)\" cargada: \(writes.count) parámetros.\(extra)"
                }
            }
            step()
        }
    }

    static func same(_ a: OscArg, _ b: OscArg) -> Bool {
        switch a {
        case .f(let x): return abs(x - b.floatValue) < 0.02
        case .i(let x): return Int(x) == b.intValue
        case .s(let x): return x == b.stringValue
        case .b: return false
        }
    }

    // MARK: tablas (idénticas a Scenes.kt)

    struct Field {
        let sub: String
        let type: Character
        var a: Float = 0
        var b: Float = 0
        var list: [String]? = nil
    }

    static let colors = ["OFF", "RD", "GN", "YE", "BL", "MG", "CY", "WH", "OFFi", "RDi", "GNi", "YEi", "BLi", "MGi", "CYi", "WHi"]
    static let filterTypes = ["LC6", "LC12", "HC6", "HC12", "1.0", "2.0", "3.0", "5.0", "10.0"]
    static let ratios = ["1.1", "1.3", "1.5", "2.0", "2.5", "3.0", "4.0", "5.0", "7.0", "10", "20", "100"]
    static let eqTypes = ["LCut", "LShv", "PEQ", "VEQ", "HShv", "HCut"]
    static var fxAbbr: [String] { Fx.abbr }
    static let xairGateModes = ["GATE", "EXP2", "EXP3", "EXP4", "DUCK"]
    static let x32GateModes = ["EXP2", "EXP3", "EXP4", "GATE", "DUCK"]
    static let xairKeySrc: [String] = ["SELF"] + (1...16).map { "Ch" + Fmt.two($0) } + (1...6).map { "Bus\($0)" }
    static let xairInSrc: [String] = (1...16).map { "In" + Fmt.two($0) } + ["OFF"]
    static let xairChRtnSrc: [String] = (1...18).map { "U" + Fmt.two($0) }
    static let xairRtnRtnSrc = ["U0102", "U0304", "U0506", "U0708", "U0910", "U1112", "U1314", "U1516", "U1718"]
    static let xairTap = ["IN", "PREEQ", "POSTEQ", "PRE", "POST", "GRP"]
    static let xairChFxSlot = ["OFF", "Fx1A", "Fx1B", "Fx2A", "Fx2B", "Fx3A", "Fx3B", "Fx4A", "Fx4B"]
    static let xairLrFxSlot = ["OFF", "Fx1", "Fx2", "Fx3", "Fx4"]
    static let xairEqMode = ["PEQ", "GEQ", "TEQ"]

    static let bases = ["rtn/aux", "main/st", "main/m", "ch/#", "rtn/#", "auxin/#", "fxrtn/#", "bus/#", "fxsend/#",
                        "mtx/#", "dca/#", "fx/#", "headamp/#", "lr", "config"]
    static let inputs: Set<String> = ["ch/#", "rtn/#", "rtn/aux", "auxin/#", "fxrtn/#"]

    static func patternKey(_ path: String) -> String {
        path.split(separator: "/", omittingEmptySubsequences: false)
            .map { seg in (!seg.isEmpty && seg.allSatisfy { $0.isNumber }) ? "#" : String(seg) }
            .joined(separator: "/")
    }

    static let eqBand: [Field] = [
        Field(sub: "type", type: "E", list: eqTypes), Field(sub: "f", type: "G", a: 20, b: 20000),
        Field(sub: "g", type: "L", a: -15, b: 15), Field(sub: "q", type: "G", a: 10, b: 0.3)
    ]

    static func filterFields() -> [Field] {
        [Field(sub: "on", type: "B"), Field(sub: "type", type: "E", list: filterTypes), Field(sub: "f", type: "G", a: 20, b: 20000)]
    }

    func fieldsFor(_ key: String) -> [Field]? {
        guard let base = Scenes.bases.first(where: { key == $0 || key.hasPrefix($0 + "/") }) else { return nil }
        var rest = String(key.dropFirst(base.count))
        if rest.hasPrefix("/") { rest.removeFirst() }
        return Core.shared.console.isX32 ? Scenes.x32Fields(base, rest) : Scenes.xairFields(base, rest)
    }

    static func xairFields(_ base: String, _ rest: String) -> [Field]? {
        let input = inputs.contains(base)
        switch rest {
        case "config":
            switch base {
            case "ch/#": return [Field(sub: "name", type: "S"), Field(sub: "color", type: "I"),
                                 Field(sub: "insrc", type: "E", list: xairInSrc), Field(sub: "rtnsrc", type: "E", list: xairChRtnSrc)]
            case "rtn/#": return [Field(sub: "name", type: "S"), Field(sub: "color", type: "I"), Field(sub: "rtnsrc", type: "E", list: xairRtnRtnSrc)]
            case "rtn/aux": return [Field(sub: "name", type: "S"), Field(sub: "color", type: "I"), Field(sub: "rtnsrc", type: "E", list: ["U1718"])]
            case "config": return nil
            default: return [Field(sub: "name", type: "S"), Field(sub: "color", type: "I")]
            }
        case "mute":
            return base == "config" ? (1...4).map { Field(sub: "\($0)", type: "B") } : nil
        case "preamp":
            if base == "ch/#" {
                return [Field(sub: "rtntrim", type: "L", a: -18, b: 18), Field(sub: "rtnsw", type: "B"), Field(sub: "invert", type: "B"),
                        Field(sub: "hpon", type: "B"), Field(sub: "hpf", type: "G", a: 20, b: 400)]
            }
            return [Field(sub: "rtntrim", type: "L", a: -18, b: 18), Field(sub: "rtnsw", type: "B")]
        case "gate":
            return [Field(sub: "on", type: "B"), Field(sub: "mode", type: "E", list: xairGateModes),
                    Field(sub: "thr", type: "L", a: -80, b: 0), Field(sub: "range", type: "L", a: 3, b: 60),
                    Field(sub: "attack", type: "L", a: 0, b: 120), Field(sub: "hold", type: "G", a: 0.02, b: 2000),
                    Field(sub: "release", type: "G", a: 5, b: 4000), Field(sub: "keysrc", type: "E", list: xairKeySrc)]
        case "gate/filter", "dyn/filter":
            return filterFields()
        case "dyn":
            var l: [Field] = [Field(sub: "on", type: "B"), Field(sub: "mode", type: "E", list: ["COMP", "EXP"]),
                              Field(sub: "det", type: "E", list: ["PEAK", "RMS"]), Field(sub: "env", type: "E", list: ["LIN", "LOG"]),
                              Field(sub: "thr", type: "L", a: -60, b: 0), Field(sub: "ratio", type: "E", list: ratios),
                              Field(sub: "knee", type: "L", a: 0, b: 5), Field(sub: "mgain", type: "L", a: 0, b: 24),
                              Field(sub: "attack", type: "L", a: 0, b: 120), Field(sub: "hold", type: "G", a: 0.02, b: 2000),
                              Field(sub: "release", type: "G", a: 5, b: 4000), Field(sub: "mix", type: "L", a: 0, b: 100)]
            if base != "lr" { l.append(Field(sub: "keysrc", type: "E", list: xairKeySrc)) }
            l.append(Field(sub: "auto", type: "B"))
            return l
        case "insert":
            return [Field(sub: "on", type: "B"), Field(sub: "fxslot", type: "E", list: base == "lr" ? xairLrFxSlot : xairChFxSlot)]
        case "eq":
            return input ? [Field(sub: "on", type: "B")] : [Field(sub: "on", type: "B"), Field(sub: "mode", type: "E", list: xairEqMode)]
        case "eq/#":
            return eqBand
        case "mix":
            if input || base == "bus/#" {
                return [Field(sub: "on", type: "B"), Field(sub: "fader", type: "V"), Field(sub: "lr", type: "B"), Field(sub: "pan", type: "P")]
            }
            if base == "lr" { return [Field(sub: "on", type: "B"), Field(sub: "fader", type: "V"), Field(sub: "pan", type: "P")] }
            return [Field(sub: "on", type: "B"), Field(sub: "fader", type: "V")]
        case "mix/#":
            return [Field(sub: "level", type: "V"), Field(sub: "grpon", type: "B"), Field(sub: "tap", type: "E", list: xairTap), Field(sub: "pan", type: "P")]
        case "grp":
            return [Field(sub: "dca", type: "M"), Field(sub: "mute", type: "M")]
        case "automix":
            return [Field(sub: "group", type: "E", list: ["OFF", "X", "Y"]), Field(sub: "weight", type: "L", a: -12, b: 12)]
        case "":
            switch base {
            case "dca/#": return [Field(sub: "on", type: "B"), Field(sub: "fader", type: "V")]
            case "fx/#": return [Field(sub: "type", type: "E", list: fxAbbr), Field(sub: "insert", type: "B")]
            case "headamp/#": return [Field(sub: "gain", type: "L", a: -12, b: 60), Field(sub: "phantom", type: "B")]
            default: return nil
            }
        default:
            return nil
        }
    }

    static func x32Fields(_ base: String, _ rest: String) -> [Field]? {
        let input = inputs.contains(base)
        switch rest {
        case "config":
            if base == "config" { return nil }
            var l = [Field(sub: "name", type: "S"), Field(sub: "icon", type: "I"), Field(sub: "color", type: "E", list: colors)]
            if base == "ch/#" || base == "auxin/#" { l.append(Field(sub: "source", type: "I")) }
            return l
        case "preamp":
            return [Field(sub: "trim", type: "L", a: -18, b: 18), Field(sub: "invert", type: "B"), Field(sub: "hpon", type: "B"),
                    Field(sub: "hpslope", type: "E", list: ["12", "18", "24"]), Field(sub: "hpf", type: "G", a: 20, b: 400)]
        case "gate":
            return [Field(sub: "on", type: "B"), Field(sub: "mode", type: "E", list: x32GateModes),
                    Field(sub: "thr", type: "L", a: -80, b: 0), Field(sub: "range", type: "L", a: 3, b: 60),
                    Field(sub: "attack", type: "L", a: 0, b: 120), Field(sub: "hold", type: "G", a: 0.02, b: 2000),
                    Field(sub: "release", type: "G", a: 5, b: 4000), Field(sub: "keysrc", type: "I")]
        case "gate/filter", "dyn/filter":
            return filterFields()
        case "dyn":
            return [Field(sub: "on", type: "B"), Field(sub: "mode", type: "E", list: ["COMP", "EXP"]),
                    Field(sub: "det", type: "E", list: ["PEAK", "RMS"]), Field(sub: "env", type: "E", list: ["LIN", "LOG"]),
                    Field(sub: "thr", type: "L", a: -60, b: 0), Field(sub: "ratio", type: "E", list: ratios),
                    Field(sub: "knee", type: "L", a: 0, b: 5), Field(sub: "mgain", type: "L", a: 0, b: 24),
                    Field(sub: "attack", type: "L", a: 0, b: 120), Field(sub: "hold", type: "G", a: 0.02, b: 2000),
                    Field(sub: "release", type: "G", a: 5, b: 4000), Field(sub: "pos", type: "E", list: ["PRE", "POST"]),
                    Field(sub: "keysrc", type: "I"), Field(sub: "mix", type: "L", a: 0, b: 100), Field(sub: "auto", type: "B")]
        case "eq":
            return [Field(sub: "on", type: "B")]
        case "eq/#":
            return eqBand
        case "mix":
            if input { return [Field(sub: "on", type: "B"), Field(sub: "fader", type: "V"), Field(sub: "st", type: "B"), Field(sub: "pan", type: "P")] }
            return [Field(sub: "on", type: "B"), Field(sub: "fader", type: "V")]
        case "mix/#":
            return [Field(sub: "on", type: "B"), Field(sub: "level", type: "V"), Field(sub: "pan", type: "P")]
        case "grp":
            return [Field(sub: "dca", type: "M"), Field(sub: "mute", type: "M")]
        case "":
            switch base {
            case "dca/#": return [Field(sub: "on", type: "B"), Field(sub: "fader", type: "V")]
            case "fx/#": return [Field(sub: "type", type: "E", list: fxAbbr)]
            case "headamp/#": return [Field(sub: "gain", type: "L", a: -12, b: 60), Field(sub: "phantom", type: "B")]
            default: return nil
            }
        default:
            return nil
        }
    }

    // MARK: conversión

    static func convert(_ f: Field, _ tok: String) -> OscArg? {
        let t = tok.trimmingCharacters(in: .whitespaces)
        switch f.type {
        case "S": return .s(t)
        case "I":
            if let v = Int32(t) { return .i(v) }
            if let v = Float(t) { return .i(Int32(v.rounded())) }
            return nil
        case "B":
            switch t.uppercased() { case "ON": return .i(1); case "OFF": return .i(0); default: return nil }
        case "E":
            guard let l = f.list else { return nil }
            if let i = l.firstIndex(of: t) { return .i(Int32(i)) }
            if let i = l.firstIndex(where: { $0.caseInsensitiveCompare(t) == .orderedSame }) { return .i(Int32(i)) }
            return nil
        case "L":
            guard let v = num(t) else { return nil }
            return .f(min(max((v - f.a) / (f.b - f.a), 0), 1))
        case "G":
            guard let v = num(t) else { return nil }
            return .f(min(max(Fmt.logInv(f.a, f.b, v), 0), 1))
        case "V":
            if t == "-oo" { return .f(0) }
            guard let v = num(t) else { return nil }
            return .f(min(max(Fmt.dbToFader(v), 0), 1))
        case "P":
            guard let v = num(t) else { return nil }
            return .f(min(max((v + 100) / 200, 0), 1))
        case "M":
            guard t.hasPrefix("%") else { return nil }
            let bits = t.dropFirst()
            if bits.isEmpty { return .i(0) }
            guard let v = Int32(bits, radix: 2) else { return nil }
            return .i(v)
        default:
            return nil
        }
    }

    /// "1k20" = 1200, "+3.5" = 3.5, "20k0" = 20000.
    static func num(_ t: String) -> Float? {
        var s = t.hasPrefix("+") ? String(t.dropFirst()) : t
        var mult: Float = 1
        if s.contains("k") {
            s = s.replacingOccurrences(of: "k", with: ".")
            mult = 1000
        }
        return Float(s).map { $0 * mult }
    }

    static func tokenize(_ line: String) -> [String] {
        var out: [String] = []
        let ch = Array(line)
        var i = 0
        let n = ch.count
        while i < n {
            while i < n && ch[i].isWhitespace { i += 1 }
            if i >= n { break }
            if ch[i] == "\"" {
                var j = i + 1
                while j < n && ch[j] != "\"" { j += 1 }
                out.append(String(ch[(i + 1)..<min(j, n)]))
                i = j + 1
            } else {
                let st = i
                while i < n && !ch[i].isWhitespace { i += 1 }
                out.append(String(ch[st..<i]))
            }
        }
        return out
    }
}
