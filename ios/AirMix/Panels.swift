import SwiftUI

/// Tira de canal (igual en MEZCLA y en el detalle).
struct StripView: View {
    let strip: Strip
    let target: SendTarget
    var compact = false
    let onOpen: (Strip) -> Void

    var body: some View {
        let send = strip.sendAddr(target)
        let faderAddr = send ?? strip.faderAddr
        VStack(spacing: compact ? 4 : 6) {
            Text(strip.title).font(bf(11)).foregroundColor(C.dim).padding(.top, 4)
            NameChip(strip: strip)
                .padding(.horizontal, 6)
                .onTapGesture { onOpen(strip) }
            if let pan = strip.panAddr {
                PanView(addr: pan).frame(height: 26)
            } else {
                Color.clear.frame(height: 26)
            }
            HStack {
                ParamToggle("S", addr: strip.soloAddr, color: C.solo, style: .small, size: 14).frame(width: 34, height: 28)
                Spacer(minLength: 0)
                DbLabel(addr: faderAddr, color: send != nil ? C.sends : C.text)
            }
            .padding(.horizontal, 6)
            HStack(spacing: 2) {
                FaderView(addr: faderAddr, fill: send != nil ? C.sends : C.fill)
                if strip.meterL >= 0 {
                    MeterView(l: strip.meterL, r: strip.meterR)
                        .frame(width: strip.meterR >= 0 ? 10 : 6)
                        .padding(.vertical, 16)
                        .padding(.trailing, 6)
                }
            }
            ParamToggle("MUTE", addr: strip.onAddr, invert: true, color: C.mute, style: .mute, size: 16)
                .frame(height: compact ? 38 : 46)
                .padding(.horizontal, 6)
                .padding(.bottom, 6)
        }
        .background(C.strip)
        .id(faderAddr)
    }
}

struct DetailPanel: View {
    let strip: Strip
    let section: Section

    var body: some View {
        switch section {
        case .config: ScrollView { ConfigPanel(strip: strip).padding(16) }
        case .gate: ScrollView { GatePanel(prefix: strip.prefix).padding(16) }
        case .dyn: ScrollView { DynPanel(prefix: strip.prefix).padding(16) }
        case .eq: EqPanel(prefix: strip.prefix, bands: strip.kind == .ch ? 4 : 6, hpf: strip.kind == .ch).padding(16)
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
    @FocusState private var editing: Bool

    init(strip: Strip) {
        self.strip = strip
        _nameP = ObservedObject(wrappedValue: Store.shared.param(strip.nameAddr))
        _colorP = ObservedObject(wrappedValue: Store.shared.param(strip.colorAddr))
    }

    private let colorNames = ["Apagado", "Rojo", "Verde", "Amarillo", "Azul", "Magenta", "Cian", "Blanco",
                              "Apagado inv.", "Rojo inv.", "Verde inv.", "Amarillo inv.", "Azul inv.", "Magenta inv.", "Cian inv.", "Blanco inv."]

    var body: some View {
        let c = Core.shared.console
        VStack(spacing: 14) {
            Card(title: "IDENTIDAD") {
                HStack(spacing: 12) {
                    Menu {
                        ForEach(0..<16, id: \.self) { i in
                            Button(colorNames[i]) { Store.shared.set(strip.colorAddr, .i(Int32(i))) }
                        }
                    } label: {
                        RoundedRectangle(cornerRadius: 10).fill(C.scribble[(colorP.int ?? 0) % 8]).frame(width: 52, height: 50)
                    }
                    TextField("Nombre", text: $name)
                        .font(bf(20))
                        .foregroundColor(C.text)
                        .focused($editing)
                        .submitLabel(.done)
                        .onSubmit { Store.shared.set(strip.nameAddr, .s(String(name.prefix(12)))) }
                        .padding(.horizontal, 14)
                        .frame(width: 260, height: 50)
                        .background(RoundedRectangle(cornerRadius: 10).fill(C.bg))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(C.line))
                    Spacer()
                    ParamToggle("MUTE", addr: strip.onAddr, invert: true, color: C.mute, style: .mute, size: 17).frame(width: 110, height: 50)
                    ParamToggle("SOLO", addr: strip.soloAddr, color: C.solo, size: 17).frame(width: 110, height: 50)
                }
            }
            if strip.kind == .ch {
                let p = strip.prefix
                Card(title: "PREAMP") {
                    SourceLabel(addr: strip.sourceAddr, x32: c.isX32)
                    HStack {
                        if let ha = strip.headampAddr {
                            KnobView(Spec(label: "GAIN", addr: "\(ha)/gain", def: 12 / 72) { Fmt.db(Fmt.lin(-12, 60, $0)) })
                        }
                        if c.isX32 {
                            KnobView(Spec(label: "TRIM", addr: "\(p)/preamp/trim", def: 0.5, bipolar: true) { Fmt.db(Fmt.lin(-18, 18, $0)) })
                        }
                        KnobView(Spec(label: "LOW CUT", addr: "\(p)/preamp/hpf", def: 0) { Fmt.hz(Fmt.log(20, 400, $0)) })
                    }
                    HStack(spacing: 10) {
                        ParamToggle("LOW CUT", addr: "\(p)/preamp/hpon", size: 16).frame(width: 110, height: 48)
                        if let ha = strip.headampAddr {
                            ParamToggle("48V", addr: "\(ha)/phantom", color: C.mute, size: 16).frame(width: 90, height: 48)
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
        .onChange(of: nameP.value) { v in if !editing { name = v?.stringValue ?? "" } }
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

struct EqPanel: View {
    let prefix: String
    let bands: Int
    let hpf: Bool
    @State private var selected = 1
    private let eqTypes = ["LCut", "LShv", "PEQ", "VEQ", "HShv", "HCut"]

    var body: some View {
        let names = bands == 4 ? ["LOW", "LO MID", "HI MID", "HIGH"] : ["LOW", "LOW 2", "LO MID", "HI MID", "HIGH 2", "HIGH"]
        VStack(spacing: 10) {
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
                Pill(text: "RESET", style: .outline, size: 14) {
                    for i in 1...bands { Store.shared.set("\(prefix)/eq/\(i)/g", .f(0.5)) }
                }.frame(width: 80, height: 40)
            }
            EqGraphView(prefix: prefix, bands: bands, hpf: hpf, selected: $selected)
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
                Spacer()
            }
            .id(selected)
            .padding(.horizontal, 14)
            .frame(height: 90)
            .background(RoundedRectangle(cornerRadius: 14).fill(C.surface))
        }
    }
}

// MARK: ENVÍOS

struct SendsPanel: View {
    let strip: Strip
    var body: some View {
        let c = Core.shared.console
        var targets: [(String, Int, Int)] = []
        for i in 0..<c.buses { targets.append(("BUS", i + 1, i + 1)) }
        for i in 0..<c.fxSends { targets.append(("FX", i + 1, 7 + i)) }
        return HStack(spacing: 6) {
            ForEach(0..<targets.count, id: \.self) { k in
                let t = targets[k]
                let addr = "\(strip.prefix)/mix/\(Fmt.two(t.2))/level"
                let color = t.0 == "FX" ? C.accent : C.sends
                VStack(spacing: 4) {
                    Text("\(t.0) \(t.1)").font(bf(14)).foregroundColor(color).padding(.top, 6)
                    DbLabel(addr: addr, color: C.text)
                    FaderView(addr: addr, fill: color).padding(.bottom, 8)
                }
                .background(RoundedRectangle(cornerRadius: 12).fill(C.surface))
            }
        }
    }
}

// MARK: EFECTOS

struct FxTypeLabel: View {
    @ObservedObject var p: Param
    init(n: Int) { _p = ObservedObject(wrappedValue: Store.shared.param("/fx/\(n)/type")) }
    var body: some View {
        let i = p.int ?? -1
        Text(i >= 0 && i < FX_TYPES.count ? FX_TYPES[i] : (i >= 0 ? "Efecto #\(i)" : "—"))
            .font(bf(20)).foregroundColor(C.text).lineLimit(1).minimumScaleFactor(0.6)
    }
}

struct EffectsView: View {
    var body: some View {
        let c = Core.shared.console
        VStack(spacing: 10) {
            ForEach(Array(stride(from: 1, through: c.fxUnits, by: 2)), id: \.self) { n in
                HStack(spacing: 10) {
                    FxTile(n: n)
                    if n + 1 <= c.fxUnits { FxTile(n: n + 1) } else { Color.clear }
                }
            }
            Text("Editar parámetros y cambiar el tipo de efecto: próxima versión.").font(bf(13)).foregroundColor(C.dim)
        }
        .padding(10)
        .background(C.bg)
    }
}

struct FxTile: View {
    let n: Int
    var body: some View {
        let c = Core.shared.console
        let rtn: String? = c.isX32 ? (n <= 4 ? "/fxrtn/\(Fmt.two(2 * n - 1))" : nil) : "/rtn/\(n)"
        HStack(spacing: 12) {
            Text("FX \(n)").font(bf(16)).foregroundColor(C.accent).frame(width: 50)
            FxTypeLabel(n: n).frame(maxWidth: .infinity, alignment: .leading)
            if let r = rtn {
                VStack {
                    KnobView(Spec(label: "RETORNO", addr: "\(r)/mix/fader", def: 0.75) { Fmt.faderDb($0) + " dB" })
                    if !c.isX32 {
                        KnobView(Spec(label: "SEND MASTER", addr: "/fxsend/\(n)/mix/fader", def: 0.75) { Fmt.faderDb($0) + " dB" })
                    }
                }
                ParamToggle("MUTE", addr: "\(r)/mix/on", invert: true, color: C.mute, style: .mute, size: 15).frame(width: 84, height: 46)
            } else {
                Text("insert").font(bf(14)).foregroundColor(C.dim).frame(width: 84)
            }
        }
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 14).fill(C.surface))
    }
}
