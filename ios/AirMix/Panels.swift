import SwiftUI

/// Tira de canal (igual en MEZCLA y en el detalle).
struct StripView: View {
    let strip: Strip
    let target: SendTarget
    var compact = false
    let onOpen: (Strip) -> Void
    @ObservedObject private var busColor: Param

    init(strip: Strip, target: SendTarget, compact: Bool = false, onOpen: @escaping (Strip) -> Void) {
        self.strip = strip
        self.target = target
        self.compact = compact
        self.onOpen = onOpen
        // En "sends on fader" a un bus, el fader y los dB toman el color del bus.
        _busColor = ObservedObject(wrappedValue: target.bus > 0 ? Store.shared.param(Strip(kind: .bus, idx: target.bus).colorAddr) : Param("-"))
    }

    var body: some View {
        let send = strip.sendAddr(target)
        let faderAddr = send ?? strip.faderAddr
        let sendColor = target.bus > 0 ? BusColor.of(busColor.int ?? 0) : C.sends
        let k = compact
        VStack(spacing: k ? 3 : 6) {
            Text(strip.title).font(bf(k ? 9 : 11)).foregroundColor(Sector.of(strip.kind)).lineLimit(1).minimumScaleFactor(0.7).padding(.top, k ? 2 : 4)
            NameChip(strip: strip, height: k ? 26 : 34)
                .padding(.horizontal, k ? 4 : 6)
                .onTapGesture { onOpen(strip) }
            if let pan = strip.panAddr {
                PanView(addr: pan).frame(height: k ? 18 : 26)
            } else {
                Color.clear.frame(height: k ? 18 : 26)
            }
            HStack {
                ParamToggle("S", addr: strip.soloAddr, color: C.solo, style: .small, size: k ? 11 : 14).frame(width: k ? 26 : 34, height: k ? 22 : 28)
                Spacer(minLength: 0)
                DbLabel(addr: faderAddr, color: send != nil ? sendColor : C.text)
            }
            .padding(.horizontal, k ? 4 : 6)
            HStack(spacing: 2) {
                FaderView(addr: faderAddr, fill: send != nil ? sendColor : Sector.of(strip.kind))
                if strip.meterL >= 0 {
                    MeterView(l: strip.meterL, r: strip.meterR)
                        .frame(width: strip.meterR >= 0 ? 12 : 8)
                        .padding(.vertical, k ? 8 : 16)
                        .padding(.trailing, k ? 3 : 6)
                }
            }
            Group {
                // MAIN LR: mutear exige mantener apretado 2 s (para no mutear el PA sin querer). Desmutear: un toque.
                if strip.kind == .lr {
                    HoldMuteButton(addr: strip.onAddr, size: k ? 13 : 16)
                } else {
                    ParamToggle("MUTE", addr: strip.onAddr, invert: true, color: C.mute, style: .mute, size: k ? 13 : 16)
                }
            }
            .frame(height: k ? 32 : 46)
            .padding(.horizontal, k ? 4 : 6)
            .padding(.bottom, k ? 4 : 6)
        }
        .background(C.strip)
        .id(faderAddr)
    }
}

/// MUTE con retención: mantener 2 s para mutear (se llena una barra; si se suelta antes, se cancela). Muteado: un toque desmutea.
struct HoldMuteButton: View {
    @ObservedObject var p: Param
    let size: CGFloat
    @State private var pressing = false
    @State private var progress: CGFloat = 0
    /// Momento en que se muteó: el mismo toque, al soltar, no tiene que desmutear.
    @State private var mutedAt = Date.distantPast

    init(addr: String, size: CGFloat) {
        _p = ObservedObject(wrappedValue: Store.shared.param(addr))
        self.size = size
    }

    var body: some View {
        let muted = (p.int ?? 1) == 0
        if muted {
            Pill(text: "MUTE", active: true, activeColor: C.mute, style: .mute, size: size) {
                if Date().timeIntervalSince(mutedAt) > 0.8 { Store.shared.set(p.address, .i(1)) }
            }
        } else {
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 10).fill(C.surface2)
                GeometryReader { g in
                    RoundedRectangle(cornerRadius: 10).fill(C.mute.opacity(0.75)).frame(width: g.size.width * progress)
                }
                RoundedRectangle(cornerRadius: 10).stroke(C.line, lineWidth: 1)
                Text(pressing ? "MANTENÉ…" : "MUTE").font(bf(size)).foregroundColor(C.text).lineLimit(1).minimumScaleFactor(0.5).padding(.horizontal, 2).frame(maxWidth: .infinity)
            }
            .contentShape(Rectangle())
            .onLongPressGesture(minimumDuration: 2, maximumDistance: 40, perform: {
                mutedAt = Date()
                Store.shared.set(p.address, .i(0))
                pressing = false
                progress = 0
            }, onPressingChanged: { down in
                pressing = down
                if down {
                    withAnimation(.linear(duration: 2)) { progress = 1 }
                } else {
                    withAnimation(.easeOut(duration: 0.15)) { progress = 0 }
                }
            })
        }
    }
}

struct DetailPanel: View {
    let strip: Strip
    let section: Section
    @Environment(\.verticalSizeClass) private var vsc

    var body: some View {
        // En celular (pantalla baja) también se puede desplazar en horizontal, así nada queda cortado.
        let axes: Axis.Set = vsc == .compact ? [.vertical, .horizontal] : .vertical
        switch section {
        case .config: ScrollView(axes) { ConfigPanel(strip: strip).padding(16) }
        case .gate: ScrollView(axes) { GatePanel(prefix: strip.prefix).padding(16) }
        case .dyn: ScrollView(axes) { DynPanel(prefix: strip.prefix).padding(16) }
        case .eq: EqPanel(prefix: strip.prefix, bands: strip.kind == .ch ? 4 : 6, hpf: strip.kind == .ch, selIdx: strip.selIdx).padding(16)
        case .sends: SendsPanel(strip: strip).padding(12)
        }
    }
}

// MARK: CONFIG

struct ConfigPanel: View {
    let strip: Strip
    @ObservedObject var nameP: Param
    @ObservedObject var colorP: Param
    @State private var name = ""
    @State private var okText = "OK"
    @State private var nameFailed = false
    @FocusState private var editing: Bool

    @ObservedObject var srcP: Param

    init(strip: Strip) {
        self.strip = strip
        _nameP = ObservedObject(wrappedValue: Store.shared.param(strip.nameAddr))
        _colorP = ObservedObject(wrappedValue: Store.shared.param(strip.colorAddr))
        _srcP = ObservedObject(wrappedValue: strip.kind == .ch ? Store.shared.param(strip.sourceAddr) : Param("-"))
    }

    private var headamp: String? { srcP.int.flatMap { strip.headampFor($0) } }

    private let colorNames = ["Apagado", "Rojo", "Verde", "Amarillo", "Azul", "Magenta", "Cian", "Blanco",
                              "Apagado inv.", "Rojo inv.", "Verde inv.", "Amarillo inv.", "Azul inv.", "Magenta inv.", "Cian inv.", "Blanco inv."]

    var body: some View {
        let c = Core.shared.console
        VStack(spacing: 14) {
            Card(title: "IDENTIDAD") {
                HStack(spacing: 12) {
                    TextField("Nombre", text: $name)
                        .font(bf(20))
                        .foregroundColor(C.text)
                        .focused($editing)
                        .submitLabel(.done)
                        .onSubmit { commitName() }
                        .padding(.horizontal, 14)
                        .frame(width: 260, height: 50)
                        .background(RoundedRectangle(cornerRadius: 10).fill(C.bg))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(C.line))
                    Pill(text: okText, active: okText == "✓", activeColor: C.green, size: 17) { commitName() }
                        .frame(width: 80, height: 50)
                    Spacer()
                    ParamToggle("MUTE", addr: strip.onAddr, invert: true, color: C.mute, style: .mute, size: 17).frame(width: 110, height: 50)
                    ParamToggle("SOLO", addr: strip.soloAddr, color: C.solo, size: 17).frame(width: 110, height: 50)
                }
                // LINK STEREO: une el canal con su par (1-2, 3-4…). En MEZCLA el par pasa a ser una sola tira.
                if strip.kind == .ch {
                    let odd = strip.idx % 2 == 1 ? strip.idx : strip.idx - 1
                    if odd + 1 <= c.channels {
                        HStack(spacing: 14) {
                            ParamToggle("LINK STEREO  \(odd)-\(odd + 1)", addr: chLinkAddr(odd), color: C.sends, size: 15).frame(width: 230, height: 46)
                            Text("Une los dos canales: fader, mute y EQ van juntos y en MEZCLA se ven como una sola tira.")
                                .font(bf(12)).foregroundColor(C.dim).lineLimit(2).frame(maxWidth: 320, alignment: .leading)
                        }
                        .padding(.top, 4)
                    }
                }
                // Paleta de color (la misma de la consola): fila de llenos y fila de contorno.
                Text("COLOR").font(bf(12)).kerning(1.4).foregroundColor(C.dim).padding(.top, 6)
                ForEach(0..<2, id: \.self) { rowIdx in
                    HStack(spacing: 8) {
                        ForEach(0..<8, id: \.self) { k in
                            let ci = rowIdx * 8 + k
                            ColorSwatch(index: ci, selected: (colorP.int ?? -1) == ci) {
                                Store.shared.set(strip.colorAddr, .i(Int32(ci)))
                            }
                            .accessibilityLabel(colorNames[ci])
                        }
                    }
                }
            }
            if strip.kind == .ch {
                let p = strip.prefix
                Card(title: "PREAMP") {
                    // ENTRADA FÍSICA del canal: se elige acá. GAIN y 48V siguen solos al preamp nuevo.
                    HStack(spacing: 14) {
                        ListSelector("ENTRADA", addr: strip.sourceAddr, options: sourceOptions()).frame(width: 200, height: 56)
                        SourceDupWarning(strip: strip)
                    }
                    if let ha = headamp {
                        GainInfo(addr: "\(ha)/gain", ha: ha).id(ha)
                    }
                    HStack {
                        // GAIN y 48V dependen de la fuente del canal.
                        if let ha = headamp {
                            KnobView(Spec(label: "GAIN", addr: "\(ha)/gain", def: 12 / 72) { Fmt.db(Fmt.lin(-12, 60, $0)) }).id(ha)
                        } else if srcP.value != nil {
                            Text("SIN PREAMP\n(fuente sin ganancia)").font(bf(13)).foregroundColor(C.dim).frame(width: 176, height: 70)
                        }
                        if c.isX32 {
                            KnobView(Spec(label: "TRIM", addr: "\(p)/preamp/trim", def: 0.5, bipolar: true) { Fmt.db(Fmt.lin(-18, 18, $0)) })
                        }
                        KnobView(Spec(label: "LOW CUT", addr: "\(p)/preamp/hpf", def: 0) { Fmt.hz(Fmt.log(20, 400, $0)) })
                    }
                    HStack(spacing: 10) {
                        ParamToggle("LOW CUT", addr: "\(p)/preamp/hpon", size: 16).frame(width: 110, height: 48)
                        if let ha = headamp {
                            ParamToggle("48V", addr: "\(ha)/phantom", color: C.mute, size: 16).frame(width: 90, height: 48).id(ha)
                        }
                        ParamToggle("Ø PHASE", addr: "\(p)/preamp/invert", size: 16).frame(width: 110, height: 48)
                        ParamToggle("MAIN LR", addr: strip.lrAssignAddr, size: 16).frame(width: 120, height: 48)
                    }
                }
                Card(title: "GRUPOS") {
                    HStack(spacing: 6) {
                        Text("DCA").font(bf(14)).foregroundColor(C.dim).frame(width: 50)
                        ForEach(0..<c.dcas, id: \.self) { i in
                            BitToggle("\(i + 1)", addr: "\(p)/grp/dca", bit: i).frame(width: 44, height: 44)
                        }
                    }
                    HStack(spacing: 6) {
                        Text("MUTE").font(bf(14)).foregroundColor(C.dim).frame(width: 50)
                        ForEach(0..<c.muteGroups, id: \.self) { i in
                            BitToggle("\(i + 1)", addr: "\(p)/grp/mute", bit: i, color: C.mute).frame(width: 44, height: 44)
                        }
                    }
                }
            }
        }
        .onAppear { name = nameP.value?.stringValue ?? "" }
        .onChange(of: nameP.value) { v in if !editing, let v = v { name = v.stringValue } }
        .alert("La consola no confirmó el nombre", isPresented: $nameFailed) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Revisá la conexión y tocá OK de nuevo.")
        }
    }

    /// Manda el nombre, se lo vuelve a pedir a la consola y, si no coincide, reintenta (hasta 3 veces).
    private func commitName(attempt: Int = 1) {
        editing = false
        let txt = String(name.trimmingCharacters(in: .whitespaces).prefix(12))
        let addr = strip.nameAddr
        okText = "…"
        Store.shared.set(addr, .s(txt))
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
            Store.shared.forget(addr)
            Core.shared.client.query(addr)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.7) {
            if Store.shared.value(addr)?.stringValue == txt {
                okText = "✓"
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) { okText = "OK" }
            } else if attempt < 3 {
                commitName(attempt: attempt + 1)
            } else {
                okText = "OK"
                nameFailed = true
            }
        }
    }
}

/// Muestra de color de scribble (0-7 lleno, 8-15 contorno).
struct ColorSwatch: View {
    let index: Int
    let selected: Bool
    let action: () -> Void
    var body: some View {
        let base = C.scribble[index % 8]
        let filled = index < 8
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: 8).fill(filled ? base : Color.clear)
                RoundedRectangle(cornerRadius: 8).stroke(filled ? Color.clear : (index == 8 ? C.dim : base), lineWidth: 1.5)
                if selected {
                    Text("✓").font(bf(16)).foregroundColor(filled ? ([0, 1, 4, 5].contains(index) ? .white : C.ink) : (index == 8 ? C.text : base))
                }
            }
            .frame(width: 52, height: 38)
        }
        .buttonStyle(.plain)
    }
}

/// Diagnóstico del preamp: qué dirección se usa y si la consola contesta.
struct GainInfo: View {
    let ha: String
    @ObservedObject var p: Param
    @State private var timedOut = false
    init(addr: String, ha: String) {
        self.ha = ha
        _p = ObservedObject(wrappedValue: Store.shared.param(addr))
    }
    var body: some View {
        let bad = timedOut && p.value == nil
        Text(bad ? "Preamp: \(ha) · la consola no responde" : "Preamp: \(ha)")
            .font(bf(11)).foregroundColor(bad ? C.solo : C.dim)
            .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { timedOut = true } }
    }
}

/// Aviso si otro canal usa la misma entrada física (no está mal, pero suele ser un error).
struct SourceDupWarning: View {
    let strip: Strip
    @StateObject private var group: ParamGroup
    init(strip: Strip) {
        self.strip = strip
        _group = StateObject(wrappedValue: ParamGroup(stripsOfKind(.ch).map { $0.sourceAddr }))
    }
    var body: some View {
        let chans = stripsOfKind(.ch)
        let mine = Store.shared.value(strip.sourceAddr)?.intValue
        let others = chans.filter { $0.idx != strip.idx && mine != nil && Store.shared.value($0.sourceAddr)?.intValue == mine }
        let x32Off = Core.shared.console.isX32 && mine == 0
        Text(others.isEmpty || x32Off ? "" : "⚠ La misma entrada la usa también: " + others.map { $0.title }.joined(separator: ", ") + ".")
            .font(bf(13)).foregroundColor(C.yellow).lineLimit(2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .id(group.params.map { $0.int ?? -1 })
    }
}

struct SourceLabel: View {
    @ObservedObject var p: Param
    let x32: Bool
    init(addr: String, x32: Bool) {
        _p = ObservedObject(wrappedValue: Store.shared.param(addr))
        self.x32 = x32
    }
    var body: some View {
        let x = p.int ?? 0
        let t: String = x32 ? (x == 0 ? "OFF" : x <= 32 ? "IN \(Fmt.two(x))" : "#\(x)") : "INP \(Fmt.two(x + 1))"
        Text("Fuente: \(t)").font(bf(14)).foregroundColor(C.dim)
    }
}

// MARK: GATE / DYN

struct KeySource: View {
    @ObservedObject var p: Param
    init(addr: String) { _p = ObservedObject(wrappedValue: Store.shared.param(addr)) }
    var body: some View {
        VStack(spacing: 2) {
            Text("KEY SOURCE").font(bf(12)).foregroundColor(C.dim)
            Text(Core.shared.console.keySourceName(p.int ?? 0)).font(bf(15)).foregroundColor(C.text)
        }
        .frame(width: 140, height: 54)
        .background(RoundedRectangle(cornerRadius: 10).fill(C.surface2))
    }
}

struct GatePanel: View {
    let prefix: String
    var body: some View {
        let b = "\(prefix)/gate"
        VStack(spacing: 14) {
            Card(title: "GATE") {
                HStack(spacing: 12) {
                    OnOff(addr: "\(b)/on").frame(width: 120, height: 50)
                    ListSelector("TIPO", addr: "\(b)/mode", options: Core.shared.console.gateModes).frame(width: 170, height: 50)
                }
                HStack(alignment: .top, spacing: 18) {
                    DynGraphView(prefix: prefix, gate: true).frame(width: 210, height: 180)
                    VStack(alignment: .leading) {
                        HStack {
                            KnobView(Spec(label: "THRESHOLD", addr: "\(b)/thr", def: 0) { Fmt.db(Fmt.lin(-80, 0, $0)) })
                            KnobView(Spec(label: "RANGE", addr: "\(b)/range", def: 1) { Fmt.db(Fmt.lin(3, 60, $0)) })
                        }
                        HStack {
                            KnobView(Spec(label: "ATTACK", addr: "\(b)/attack", def: 0.2) { Fmt.ms(Fmt.lin(0, 120, $0)) })
                            KnobView(Spec(label: "HOLD", addr: "\(b)/hold", def: 0.5) { Fmt.ms(Fmt.log(0.02, 2000, $0)) })
                            KnobView(Spec(label: "RELEASE", addr: "\(b)/release", def: 0.5) { Fmt.ms(Fmt.log(5, 4000, $0)) })
                        }
                    }
                }
            }
            Card(title: "SIDECHAIN") {
                HStack(spacing: 12) {
                    KnobView(Spec(label: "FILTRO", addr: "\(b)/filter/f", def: 0.5) { Fmt.hz(Fmt.log(20, 20000, $0)) })
                    ParamToggle("FILTRO", addr: "\(b)/filter/on", size: 16).frame(width: 100, height: 48)
                    KeySource(addr: "\(b)/keysrc")
                }
            }
        }
    }
}

struct DynPanel: View {
    let prefix: String
    private let ratios = ["1.1", "1.3", "1.5", "2.0", "2.5", "3.0", "4.0", "5.0", "7.0", "10", "20", "100"]
    var body: some View {
        let b = "\(prefix)/dyn"
        VStack(spacing: 14) {
            Card(title: "DINÁMICA") {
                HStack(spacing: 10) {
                    OnOff(addr: "\(b)/on").frame(width: 120, height: 50)
                    ListSelector("TIPO", addr: "\(b)/mode", options: ["COMP", "EXP"]).frame(width: 140, height: 50)
                    Spacer()
                    Choice(addr: "\(b)/auto", labels: ["MANUAL", "AUTO"])
                    Choice(addr: "\(b)/env", labels: ["LIN", "LOG"])
                    Choice(addr: "\(b)/det", labels: ["PEAK", "RMS"])
                }
                HStack(alignment: .top, spacing: 18) {
                    DynGraphView(prefix: prefix, gate: false).frame(width: 210, height: 180)
                    VStack(alignment: .leading) {
                        HStack {
                            KnobView(Spec(label: "THRESHOLD", addr: "\(b)/thr", def: 1) { Fmt.db(Fmt.lin(-60, 0, $0)) })
                            KnobView(Spec(label: "RATIO", addr: "\(b)/ratio", steps: 12, def: 3.0 / 11.0) { ratios[min(max(Int($0), 0), 11)] + " : 1" })
                            KnobView(Spec(label: "KNEE", addr: "\(b)/knee", def: 0) { Fmt.num(Fmt.lin(0, 5, $0)) })
                            KnobView(Spec(label: "GAIN", addr: "\(b)/mgain", def: 0) { Fmt.db(Fmt.lin(0, 24, $0)) })
                        }
                        HStack {
                            KnobView(Spec(label: "ATTACK", addr: "\(b)/attack", def: 0.2) { Fmt.ms(Fmt.lin(0, 120, $0)) })
                            KnobView(Spec(label: "HOLD", addr: "\(b)/hold", def: 0.5) { Fmt.ms(Fmt.log(0.02, 2000, $0)) })
                            KnobView(Spec(label: "RELEASE", addr: "\(b)/release", def: 0.5) { Fmt.ms(Fmt.log(5, 4000, $0)) })
                            KnobView(Spec(label: "MIX", addr: "\(b)/mix", def: 1) { Fmt.pct(Fmt.lin(0, 100, $0)) })
                        }
                    }
                }
            }
            Card(title: "SIDECHAIN") {
                HStack(spacing: 12) {
                    KnobView(Spec(label: "FILTRO", addr: "\(b)/filter/f", def: 0.5) { Fmt.hz(Fmt.log(20, 20000, $0)) })
                    ParamToggle("FILTRO", addr: "\(b)/filter/on", size: 16).frame(width: 100, height: 48)
                    KeySource(addr: "\(b)/keysrc")
                }
            }
        }
    }
}

// MARK: EQ

extension EqPanel {
    /// Corte de acople (igual que cutFeedback en Android): si ya hay una banda PEQ cortando cerca (±1/6 de octava)
    /// la baja 3 dB más; si no, usa la banda libre (PEQ/VEQ en 0 dB) más cercana: PEQ, -6 dB, Q 8.
    static func cutFeedback(_ p: String, bands: Int, hz: Float) -> Int {
        let st = Store.shared
        let target = min(max(Fmt.logInv(20, 20000, hz), 0), 1)
        let near: Float = 1.0 / 18.0
        for b in 1...bands {
            let type = st.int("\(p)/eq/\(b)/type", def: 2)
            let g = Fmt.lin(-15, 15, st.float("\(p)/eq/\(b)/g", def: 0.5))
            if type == 2 && g < -0.5 && abs(st.float("\(p)/eq/\(b)/f", def: 0.5) - target) < near {
                st.set("\(p)/eq/\(b)/g", .f((max(g - 3, -15) + 15) / 30))
                if st.int("\(p)/eq/on", def: 1) == 0 { st.set("\(p)/eq/on", .i(1)) }
                return b
            }
        }
        var best = -1
        var dist: Float = 2
        for b in 1...bands {
            let type = st.int("\(p)/eq/\(b)/type", def: 2)
            let g = Fmt.lin(-15, 15, st.float("\(p)/eq/\(b)/g", def: 0.5))
            guard (type == 2 || type == 3) && abs(g) < 0.5 else { continue }
            let d = abs(st.float("\(p)/eq/\(b)/f", def: 0.5) - target)
            if d < dist { dist = d; best = b }
        }
        if best < 0 { return -1 }
        st.set("\(p)/eq/\(best)/type", .i(2))
        st.set("\(p)/eq/\(best)/f", .f(target))
        st.set("\(p)/eq/\(best)/g", .f((-6 + 15) / 30))
        st.set("\(p)/eq/\(best)/q", .f(Fmt.logInv(10, 0.3, 8)))
        if st.int("\(p)/eq/on", def: 1) == 0 { st.set("\(p)/eq/on", .i(1)) }
        return best
    }
}

/// RTA prendido/apagado (se recuerda mientras la app está abierta).
var eqRtaOn = true

struct EqPanel: View {
    let prefix: String
    let bands: Int
    let hpf: Bool
    var selIdx = -1
    @State private var selected = 1
    @State private var rta = eqRtaOn
    @State private var confirmReset = false
    @State private var cutMsg: String?
    @StateObject private var detector = FeedbackDetector()
    @Environment(\.verticalSizeClass) private var vsc
    private let eqTypes = ["LCut", "LShv", "PEQ", "VEQ", "HShv", "HCut"]

    var body: some View {
        let names = bands == 4 ? ["LOW", "LO MID", "HI MID", "HIGH"] : ["LOW", "LOW 2", "LO MID", "HI MID", "HIGH 2", "HIGH"]
        VStack(spacing: vsc == .compact ? 4 : 10) {
            ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                OnOff(addr: "\(prefix)/eq/on").frame(width: 110, height: 44)
                Spacer().frame(width: 12)
                if hpf {
                    Pill(text: "LOW CUT", active: selected == 0, style: .tab, size: 14) { selected = 0 }.frame(width: 92, height: 40)
                }
                ForEach(1...bands, id: \.self) { i in
                    Pill(text: names[i - 1], active: selected == i, style: .tab, size: 14) { selected = i }.frame(width: 84, height: 40)
                }
                Spacer()
                // Acople detectado en el RTA: se sugiere un corte angosto (se aplica solo si lo tocás).
                if detector.hz > 0 {
                    Pill(text: "✂ CORTAR \(Fmt.hz(detector.hz))", active: true, activeColor: C.red, size: 13) {
                        let b = EqPanel.cutFeedback(prefix, bands: bands, hz: detector.hz)
                        if b < 0 { cutMsg = "No hay una banda libre para el corte: liberá una (ganancia en 0 dB)." }
                        else { selected = b; cutMsg = "Corte en \(Fmt.hz(detector.hz)) (banda \(names[b - 1])). Si sigue, tocá de nuevo para cortar más." }
                    }.frame(width: 190, height: 40)
                }
                Pill(text: "RTA", active: rta, activeColor: C.sends, style: .outline, size: 14) {
                    rta.toggle()
                    eqRtaOn = rta
                    Core.shared.client.setRta(rta)
                    if !rta { detector.clear() }
                }.frame(width: 64, height: 40)
                Pill(text: "RESET", style: .outline, size: 14) { confirmReset = true }.frame(width: 80, height: 40)
            }
            }
            .frame(height: 46)
            EqGraphView(prefix: prefix, bands: bands, hpf: hpf, selected: $selected, rtaOn: rta, detector: detector)
                .overlay(alignment: .bottom) {
                    if let m = cutMsg {
                        Text(m).font(bf(13)).foregroundColor(C.text).padding(10)
                            .background(RoundedRectangle(cornerRadius: 10).fill(C.surface2))
                            .padding(.bottom, 30)
                            .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 3) { cutMsg = nil } }
                    }
                }
            ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 16) {
                if selected == 0 {
                    ParamToggle("LOW CUT", addr: "\(prefix)/preamp/hpon", size: 16).frame(width: 120, height: 48)
                    KnobView(Spec(label: "FREQ", addr: "\(prefix)/preamp/hpf", def: 0) { Fmt.hz(Fmt.log(20, 400, $0)) })
                } else {
                    let e = "\(prefix)/eq/\(selected)"
                    ListSelector("TIPO", addr: "\(e)/type", options: eqTypes).frame(width: 130, height: 52)
                    KnobView(Spec(label: "FREQ", addr: "\(e)/f", def: 0.5) { Fmt.hz(Fmt.log(20, 20000, $0)) })
                    KnobView(Spec(label: "GAIN", addr: "\(e)/g", def: 0.5, bipolar: true) { Fmt.db(Fmt.lin(-15, 15, $0)) })
                    KnobView(Spec(label: "Q", addr: "\(e)/q", def: 0.5) { Fmt.num(Fmt.log(10, 0.3, $0)) })
                }
            }
            .id(selected)
            .padding(.horizontal, 14)
            .frame(height: 90)
            }
            .frame(height: 90)
            .background(RoundedRectangle(cornerRadius: 14).fill(C.surface))
        }
        // El RTA de la consola muestra el canal seleccionado: lo seleccionamos y pedimos el RTA mientras el EQ esté a la vista.
        .onAppear {
            if selIdx >= 0 { Store.shared.set("/-stat/selidx", .i(Int32(selIdx))) }
            Core.shared.client.setRta(rta)
        }
        .onDisappear { Core.shared.client.setRta(false) }
        .confirmationDialog("¿Poner el EQ plano?", isPresented: $confirmReset, titleVisibility: .visible) {
            Button("Resetear", role: .destructive) { EqDefaults.reset(prefix, bands: bands) }
        } message: {
            Text("Todas las bandas vuelven a 0 dB con su tipo, frecuencia y Q de fábrica.")
        }
    }
}

// MARK: ENVÍOS

struct SendsPanel: View {
    let strip: Strip
    var body: some View {
        let c = Core.shared.console
        var targets: [SendTarget] = []
        for i in 0..<c.buses { targets.append(SendTarget(bus: i + 1, fx: 0)) }
        for i in 0..<c.fxSends { targets.append(SendTarget(bus: 0, fx: i + 1)) }
        return HStack(spacing: 6) {
            ForEach(0..<targets.count, id: \.self) { k in
                SendColumn(strip: strip, target: targets[k])
            }
        }
    }
}

/// Columna de un envío: nombre del bus con su color ("1-JUANCITO") o "FX1 · Hall Reverb".
struct SendColumn: View {
    let strip: Strip
    let target: SendTarget
    @ObservedObject var name: Param
    @ObservedObject var color: Param
    @ObservedObject var fxType: Param

    init(strip: Strip, target: SendTarget) {
        self.strip = strip
        self.target = target
        let isBus = target.bus > 0
        _name = ObservedObject(wrappedValue: isBus ? Store.shared.param(Strip(kind: .bus, idx: target.bus).nameAddr) : Param("-"))
        _color = ObservedObject(wrappedValue: isBus ? Store.shared.param(Strip(kind: .bus, idx: target.bus).colorAddr) : Param("-"))
        _fxType = ObservedObject(wrappedValue: isBus ? Param("-") : Store.shared.param("/fx/\(target.fx)/type"))
    }

    var body: some View {
        let mixIdx = target.bus > 0 ? target.bus : 6 + target.fx
        let addr = "\(strip.prefix)/mix/\(Fmt.two(mixIdx))/level"
        let col = target.bus > 0 ? BusColor.of(color.int ?? 0) : C.accent
        let title = target.bus > 0 ? target.display(name.value?.stringValue ?? "") : Fx.label(target.fx, fxType.int)
        VStack(spacing: 4) {
            Text(title).font(bf(13)).foregroundColor(col).lineLimit(2).multilineTextAlignment(.center)
                .minimumScaleFactor(0.7).frame(height: 36).padding(.top, 6).padding(.horizontal, 3)
            DbLabel(addr: addr, color: C.text)
            FaderView(addr: addr, fill: col).padding(.bottom, 8)
        }
        .background(RoundedRectangle(cornerRadius: 12).fill(C.surface))
    }
}

// MARK: EFECTOS

/// FX que se está editando (se recuerda al cambiar de pestaña).
var fxSelected = 1

struct FxTypeLabel: View {
    let n: Int
    @ObservedObject var p: Param
    init(n: Int) {
        self.n = n
        _p = ObservedObject(wrappedValue: Store.shared.param("/fx/\(n)/type"))
    }
    var body: some View {
        Text(p.int.map { Fx.typeName(n, $0) } ?? "—")
            .font(bf(16)).foregroundColor(C.text).lineLimit(1).minimumScaleFactor(0.6)
    }
}

/// Vista vieja de EFECTOS (lista + editor). La pantalla ahora es RackView (Rack.swift).
struct EffectsListView: View {
    @State private var sel = fxSelected
    var body: some View {
        let c = Core.shared.console
        HStack(alignment: .top, spacing: 10) {
            ScrollView {
                VStack(spacing: 8) {
                    ForEach(1...max(c.fxUnits, 1), id: \.self) { n in
                        Button {
                            sel = n
                            fxSelected = n
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("FX \(n)").font(bf(13)).foregroundColor(sel == n ? C.ink : C.accent)
                                FxTypeLabel(n: n).colorMultiply(sel == n ? C.ink : .white)
                            }
                            .padding(.horizontal, 14)
                            .frame(maxWidth: .infinity, minHeight: c.fxUnits > 4 ? 56 : 68, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: 10).fill(sel == n ? C.accent : C.surface2))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(width: 220)
            FxEditor(n: sel).id(sel)
        }
        .padding(10)
        .background(C.bg)
        .onAppear { if sel > c.fxUnits { sel = 1 } }
    }
}

struct FxEditor: View {
    let n: Int
    @ObservedObject var type: Param

    @Environment(\.verticalSizeClass) private var vsc

    init(n: Int) {
        self.n = n
        _type = ObservedObject(wrappedValue: Store.shared.param("/fx/\(n)/type"))
    }

    var body: some View {
        let c = Core.shared.console
        let rtn = Fx.returnPrefix(n)
        let abbr = type.int.flatMap { Fx.abbrOf(n, $0) }
        let pars = Fx.params(abbr)
        ScrollView(vsc == .compact ? [.vertical, .horizontal] : .vertical) {
            VStack(spacing: 14) {
                Card(title: "FX \(n)") {
                    HStack(spacing: 14) {
                        Menu {
                            ForEach(Array(Fx.typeOptions(n).enumerated()), id: \.offset) { i, name in
                                Button(name) { setType(i) }
                            }
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("EFECTO  ▾").font(bf(12)).foregroundColor(C.dim)
                                Text(type.int.map { Fx.typeName(n, $0) } ?? "—").font(bf(18)).foregroundColor(C.text).lineLimit(1)
                            }
                            .padding(.horizontal, 16)
                            .frame(width: 250, height: 58, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: 10).fill(C.surface2))
                        }
                        if let r = rtn {
                            KnobView(Spec(label: "RETORNO", addr: "\(r)/mix/fader", def: 0.75) { Fmt.faderDb($0) + " dB" })
                            if !c.isX32 {
                                KnobView(Spec(label: "SEND MASTER", addr: "/fxsend/\(n)/mix/fader", def: 0.75) { Fmt.faderDb($0) + " dB" })
                            }
                            Spacer()
                            ParamToggle("MUTE", addr: "\(r)/mix/on", invert: true, color: C.mute, style: .mute, size: 16).frame(width: 96, height: 50)
                        } else {
                            Text("insert").font(bf(14)).foregroundColor(C.dim)
                            Spacer()
                        }
                    }
                }
                Card(title: "PARÁMETROS") {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 176), spacing: 8)], alignment: .leading, spacing: 4) {
                        ForEach(0..<pars.count, id: \.self) { i in
                            KnobView(Fx.spec(n, i, pars[i]))
                        }
                    }
                    .id(abbr ?? "?")
                }
                Text("Doble toque en una perilla = valor inicial. Los cambios van directo a la consola.")
                    .font(bf(12)).foregroundColor(C.dim).frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    /// Cambia el tipo y vuelve a pedir los parámetros (cambian con el tipo).
    private func setType(_ i: Int) {
        Store.shared.set("/fx/\(n)/type", .i(Int32(i)))
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            for k in 1...64 { Store.shared.forget("/fx/\(n)/par/\(Fmt.two(k))") }
            for k in 1...16 { Core.shared.client.query("/fx/\(n)/par/\(Fmt.two(k))") }
        }
    }
}
