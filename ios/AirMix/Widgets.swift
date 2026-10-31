import SwiftUI

/// Fader vertical con escala en dB y relleno. Arrastre relativo; doble toque = 0 dB.
struct FaderView: View {
    @ObservedObject var p: Param
    let fill: Color
    @State private var start: Float?
    @State private var lastTap = Date.distantPast

    private static let marks: [(Float, String)] = [(10, "+10"), (5, "+5"), (0, "0"), (-5, "-5"), (-10, "-10"), (-20, "-20"), (-30, "-30"), (-40, "-40"), (-60, "-60")]
    private static let labelOrder = [2, 8, 0, 5, 4, 7, 1, 6, 3]

    init(addr: String, fill: Color) {
        _p = ObservedObject(wrappedValue: Store.shared.param(addr))
        self.fill = fill
    }

    var body: some View {
        GeometryReader { geo in
            let capH: CGFloat = 28
            let top = capH / 2 + 6
            let travel = max(geo.size.height - capH - 12, 1)
            let value = p.float ?? 0
            let dragging = start != nil
            Canvas { ctx, size in
                let bottom = top + travel
                let cx = size.width * 0.62
                let tw: CGFloat = 6
                let lx = cx - 16
                // Etiquetas por prioridad: si el fader es corto solo entran algunas (no se enciman).
                var drawnY: [CGFloat] = []
                for i in FaderView.labelOrder {
                    let (db, lbl) = FaderView.marks[i]
                    let y = bottom - CGFloat(Fmt.dbToFader(db)) * travel
                    ctx.fill(Path(CGRect(x: lx + 3, y: y - 0.5, width: max(cx - tw / 2 - 3 - lx - 3, 0), height: 1)), with: .color(db == 0 ? C.text : C.tick))
                    // Si la tira es muy angosta no hay lugar para los números: solo las rayitas.
                    if lx >= 22 && drawnY.allSatisfy({ abs($0 - y) >= 12 }) {
                        ctx.draw(Text(lbl).font(bf(9)).foregroundColor(db == 0 ? C.text : C.tick), at: CGPoint(x: lx, y: y), anchor: .trailing)
                        drawnY.append(y)
                    }
                }
                ctx.fill(Path(roundedRect: CGRect(x: cx - tw / 2, y: top - 4, width: tw, height: travel + 8), cornerRadius: tw / 2), with: .color(C.meterBg))
                let y = bottom - CGFloat(value) * travel
                if value > 0 {
                    ctx.fill(Path(roundedRect: CGRect(x: cx - tw / 2, y: y, width: tw, height: bottom + 4 - y), cornerRadius: tw / 2), with: .color(fill))
                }
                let hw = min(24, size.width * 0.3)
                ctx.fill(Path(roundedRect: CGRect(x: cx - hw, y: y - capH / 2 + 3, width: hw * 2, height: capH), cornerRadius: 7), with: .color(.black.opacity(0.35)))
                ctx.fill(Path(roundedRect: CGRect(x: cx - hw, y: y - capH / 2, width: hw * 2, height: capH), cornerRadius: 7), with: .color(dragging ? .white : C.cap))
                ctx.fill(Path(roundedRect: CGRect(x: cx - hw, y: y, width: hw * 2, height: capH / 2), cornerRadius: 7), with: .color(C.capShade))
                ctx.fill(Path(CGRect(x: cx - hw + 6, y: y - 1.5, width: hw * 2 - 12, height: 3)), with: .color(fill))
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { g in
                        if start == nil { start = value }
                        let nv = min(max((start ?? 0) - Float(g.translation.height / travel), 0), 1)
                        if nv != value { Store.shared.set(p.address, .f(nv)) }
                    }
                    .onEnded { g in
                        if abs(g.translation.height) < 4 {
                            if Date().timeIntervalSince(lastTap) < 0.35 {
                                Store.shared.set(p.address, .f(0.75))
                                lastTap = .distantPast
                            } else {
                                lastTap = Date()
                            }
                        }
                        start = nil
                    }
            )
        }
    }
}

/// Definición de una perilla (igual que Spec en Android).
struct Spec {
    let label: String
    let addr: String
    var steps = 0
    var def: Float = 0
    var bipolar = false
    let fmt: (Float) -> String
}

/// Perilla tipo anillo: arrastre vertical, doble toque = valor por defecto.
struct KnobView: View {
    let spec: Spec
    @ObservedObject var p: Param
    @State private var start: Float?
    @State private var lastTap = Date.distantPast
    @State private var lastSent = -1

    init(_ spec: Spec) {
        self.spec = spec
        _p = ObservedObject(wrappedValue: Store.shared.param(spec.addr))
    }

    private var pos: Float {
        if spec.steps > 1 { return min(max(Float(p.int ?? 0) / Float(spec.steps - 1), 0), 1) }
        return min(max(p.float ?? 0, 0), 1)
    }

    private func send(_ v: Float) {
        if spec.steps > 1 {
            let i = Int((v * Float(spec.steps - 1)).rounded())
            if i != lastSent {
                lastSent = i
                Store.shared.set(spec.addr, .i(Int32(i)))
            }
        } else {
            Store.shared.set(spec.addr, .f(v))
        }
    }

    var body: some View {
        let pos = self.pos
        let text = spec.steps > 1 ? spec.fmt((pos * Float(spec.steps - 1)).rounded()) : spec.fmt(pos)
        HStack(spacing: 14) {
            Canvas { ctx, size in
                let r = min(size.width, size.height) / 2 - 4
                let c = CGPoint(x: size.width / 2, y: size.height / 2)
                var bgArc = Path()
                bgArc.addArc(center: c, radius: r, startAngle: .degrees(135), endAngle: .degrees(405), clockwise: false)
                ctx.stroke(bgArc, with: .color(C.knobBg), style: StrokeStyle(lineWidth: 5, lineCap: .round))
                var arc = Path()
                if spec.bipolar {
                    let end = 270 + Double(pos - 0.5) * 270
                    arc.addArc(center: c, radius: r, startAngle: .degrees(270), endAngle: .degrees(end), clockwise: end < 270)
                } else if pos > 0 {
                    arc.addArc(center: c, radius: r, startAngle: .degrees(135), endAngle: .degrees(135 + Double(pos) * 270), clockwise: false)
                }
                ctx.stroke(arc, with: .color(C.accent), style: StrokeStyle(lineWidth: 5, lineCap: .round))
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - r * 0.7, y: c.y - r * 0.7, width: r * 1.4, height: r * 1.4)), with: .color(C.knobFace))
                let ang = (135 + Double(pos) * 270) * .pi / 180
                let d = CGPoint(x: c.x + CGFloat(cos(ang)) * r * 0.45, y: c.y + CGFloat(sin(ang)) * r * 0.45)
                ctx.fill(Path(ellipseIn: CGRect(x: d.x - 3, y: d.y - 3, width: 6, height: 6)), with: .color(C.text))
            }
            .frame(width: 52, height: 52)
            VStack(alignment: .leading, spacing: 2) {
                Text(spec.label).font(bf(12)).foregroundColor(C.dim)
                Text(text).font(bf(16)).foregroundColor(C.text).lineLimit(1).minimumScaleFactor(0.7)
            }
            Spacer(minLength: 0)
        }
        .frame(width: 176, height: 70, alignment: .leading)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { g in
                    if start == nil { start = pos; lastSent = -1 }
                    let nv = min(max((start ?? 0) - Float(g.translation.height / 240), 0), 1)
                    send(nv)
                }
                .onEnded { g in
                    if abs(g.translation.height) < 4 {
                        if Date().timeIntervalSince(lastTap) < 0.35 {
                            lastSent = -1
                            send(spec.def)
                            lastTap = .distantPast
                        } else {
                            lastTap = Date()
                        }
                    }
                    start = nil
                }
        )
    }
}

/// Paneo: riel con punto; arrastre horizontal, doble toque = centro.
struct PanView: View {
    @ObservedObject var p: Param
    @State private var start: Float?
    @State private var lastTap = Date.distantPast

    init(addr: String) { _p = ObservedObject(wrappedValue: Store.shared.param(addr)) }

    var body: some View {
        GeometryReader { geo in
            let pos = min(max(p.float ?? 0.5, 0), 1)
            let w = max(geo.size.width - 20, 1)
            Canvas { ctx, size in
                let cy = size.height / 2
                let cx = 10 + w / 2
                ctx.fill(Path(CGRect(x: 10, y: cy - 2, width: w, height: 4)), with: .color(C.meterBg))
                let x = 10 + CGFloat(pos) * w
                ctx.fill(Path(CGRect(x: min(cx, x), y: cy - 2, width: abs(x - cx), height: 4)), with: .color(C.accent))
                ctx.fill(Path(CGRect(x: cx - 1, y: cy - 6, width: 2, height: 12)), with: .color(C.tick))
                ctx.fill(Path(ellipseIn: CGRect(x: x - 7, y: cy - 7, width: 14, height: 14)), with: .color(C.text))
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { g in
                        if start == nil { start = pos }
                        let nv = min(max((start ?? 0.5) + Float(g.translation.width / w), 0), 1)
                        if nv != pos { Store.shared.set(p.address, .f(nv)) }
                    }
                    .onEnded { g in
                        if abs(g.translation.width) < 4 {
                            if Date().timeIntervalSince(lastTap) < 0.35 {
                                Store.shared.set(p.address, .f(0.5))
                                lastTap = .distantPast
                            } else {
                                lastTap = Date()
                            }
                        }
                        start = nil
                    }
            )
        }
    }
}

/// Aviso de clip (igual que Clip en Android): si un medidor llega a 0 dBFS queda marcado en rojo 3 s.
/// Lo actualiza Core.tick; los medidores solo lo consultan. signalAt sirve para LINE CHECK.
enum Clip {
    static let hold: Double = 3
    static let threshold: Float = -0.5
    static let signalDb: Float = -50
    static var at = [Double](repeating: 0, count: MixerClient.meterCount)
    static var signalAt = [Double](repeating: 0, count: MixerClient.meterCount)

    static func scan(_ m: UnsafeMutablePointer<Float>, now: Double) {
        for i in 0..<MixerClient.meterCount {
            if m[i] >= threshold { at[i] = now }
            if m[i] >= signalDb { signalAt[i] = now }
        }
    }

    static func on(_ i: Int, _ now: Double) -> Bool { i >= 0 && i < at.count && at[i] > 0 && now - at[i] < hold }
    static func signal(_ i: Int, _ now: Double) -> Bool { i >= 0 && i < signalAt.count && signalAt[i] > 0 && now - signalAt[i] < 1.5 }

    static func reset() {
        for i in 0..<at.count { at[i] = 0; signalAt[i] = 0 }
    }
}

enum MeterDraw {
    static func bar(_ ctx: GraphicsContext, _ r: CGRect, _ db: Float, clip: Bool = false) {
        if clip { ctx.fill(Path(CGRect(x: r.minX, y: r.minY, width: r.width, height: max(r.height * 0.07, 4))), with: .color(C.red)) }
        if db <= -60 { return }
        let h = r.height
        let yv = r.maxY - CGFloat(min(max((db + 60) / 60, 0), 1)) * h
        let y12 = r.maxY - (48.0 / 60.0) * h
        let y3 = r.maxY - (57.0 / 60.0) * h
        ctx.fill(Path(CGRect(x: r.minX, y: max(yv, y12), width: r.width, height: r.maxY - max(yv, y12))), with: .color(C.green))
        if yv < y12 { ctx.fill(Path(CGRect(x: r.minX, y: max(yv, y3), width: r.width, height: y12 - max(yv, y3))), with: .color(C.yellow)) }
        if yv < y3 { ctx.fill(Path(CGRect(x: r.minX, y: yv, width: r.width, height: y3 - yv)), with: .color(C.red)) }
    }
}

/// Medidor de canal (mono o L/R), redibujado a 30 fps.
struct MeterView: View {
    let l: Int
    let r: Int
    var body: some View {
        TimelineView(.periodic(from: .now, by: 1.0 / 30.0)) { _ in
            Canvas { ctx, size in
                ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(C.meterBg))
                let m = Core.shared.client.meters
                guard l >= 0 && l < MixerClient.meterCount else { return }
                let now = Date().timeIntervalSince1970
                if r < 0 || r >= MixerClient.meterCount {
                    MeterDraw.bar(ctx, CGRect(origin: .zero, size: size), m[l], clip: Clip.on(l, now))
                } else {
                    let half = size.width / 2
                    MeterDraw.bar(ctx, CGRect(x: 0, y: 0, width: half - 1, height: size.height), m[l], clip: Clip.on(l, now))
                    MeterDraw.bar(ctx, CGRect(x: half + 1, y: 0, width: half - 1, height: size.height), m[r], clip: Clip.on(r, now))
                }
            }
        }
    }
}

/// Pantalla MEDIDORES.
struct AllMetersView: View {
    let labels = meterLabels()
    var body: some View {
        TimelineView(.periodic(from: .now, by: 1.0 / 30.0)) { _ in
            Canvas { ctx, size in
                let m = Core.shared.client.meters
                let n = min(labels.count, MixerClient.meterCount)
                let pad: CGFloat = 16
                let labelH: CGFloat = 26
                let slot = (size.width - 2 * pad) / CGFloat(n)
                let bw = slot * 0.6
                let bottom = size.height - pad - labelH
                let now = Date().timeIntervalSince1970
                for i in 0..<n {
                    let x = pad + slot * CGFloat(i) + (slot - bw) / 2
                    let rect = CGRect(x: x, y: pad, width: bw, height: bottom - pad)
                    ctx.fill(Path(rect), with: .color(C.meterBg))
                    MeterDraw.bar(ctx, rect, m[i], clip: Clip.on(i, now))
                    ctx.draw(Text(labels[i]).font(bf(n > 50 ? 8 : 10)).foregroundColor(C.dim), at: CGPoint(x: x + bw / 2, y: bottom + 14))
                }
            }
        }
        .background(C.bg)
    }
}

/// LINE CHECK (MEDIDORES): una ficha por entrada, verde si llega señal, gris si no, borde rojo si clipeó.
struct LineCheckView: View {
    let strips: [Strip] = stripsOfKind(.ch) + stripsOfKind(.aux)
    @StateObject private var names: ParamGroup

    init() {
        _names = StateObject(wrappedValue: ParamGroup((stripsOfKind(.ch) + stripsOfKind(.aux)).map { $0.nameAddr }))
    }

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1.0 / 10.0)) { _ in
            Canvas { ctx, size in
                let n = strips.count
                guard n > 0 else { return }
                let cols = n <= 16 ? (n + 1) / 2 : 8
                let rows = (n + cols - 1) / cols
                let pad: CGFloat = 12, gap: CGFloat = 8
                let cw = (size.width - 2 * pad - gap * CGFloat(cols - 1)) / CGFloat(cols)
                let ch = min((size.height - 2 * pad - gap * CGFloat(rows - 1)) / CGFloat(rows), cw * 1.1)
                let now = Date().timeIntervalSince1970
                for (i, s) in strips.enumerated() {
                    let x = pad + CGFloat(i % cols) * (cw + gap)
                    let y = pad + CGFloat(i / cols) * (ch + gap)
                    let rect = CGRect(x: x, y: y, width: cw, height: ch)
                    let sig = Clip.signal(s.meterL, now) || (s.meterR >= 0 && Clip.signal(s.meterR, now))
                    let shape = Path(roundedRect: rect, cornerRadius: 12)
                    ctx.fill(shape, with: .color(sig ? C.green : C.surface2))
                    if Clip.on(s.meterL, now) || (s.meterR >= 0 && Clip.on(s.meterR, now)) {
                        ctx.stroke(shape, with: .color(C.red), lineWidth: 4)
                    }
                    let name = i < names.params.count ? (names.params[i].value?.stringValue ?? "") : ""
                    ctx.draw(Text(s.title).font(bf(min(ch * 0.2, 22))).foregroundColor(sig ? C.ink : C.dim), at: CGPoint(x: x + cw / 2, y: y + ch * 0.36))
                    if !name.isEmpty {
                        ctx.draw(Text(name).font(bf(min(ch * 0.16, 16))).foregroundColor(sig ? C.ink : C.text), at: CGPoint(x: x + cw / 2, y: y + ch * 0.62))
                    }
                    ctx.draw(Text(sig ? "SEÑAL" : "SIN SEÑAL").font(bf(min(ch * 0.11, 11))).foregroundColor(sig ? C.ink : C.tick), at: CGPoint(x: x + cw / 2, y: y + ch * 0.85))
                }
            }
        }
        .background(C.bg)
    }
}

/// Detector de acople sobre el RTA (igual que en EqGraphView de Android): una banda que sobresale
/// 12 dB de sus vecinas y se sostiene ~0,7 s. hz = 0 cuando no hay.
final class FeedbackDetector: ObservableObject {
    @Published private(set) var hz: Float = 0
    private var hits = [Int](repeating: 0, count: MixerClient.rtaBins)
    private(set) var bin = -1

    func update(_ m: UnsafeMutablePointer<Float>) {
        let n = MixerClient.rtaBins
        var best = -1
        var bestLevel: Float = -200
        for i in 0..<n {
            if i < 3 || i > n - 4 { hits[i] = 0; continue }
            let around = (m[i - 3] + m[i - 2] + m[i + 2] + m[i + 3]) / 4
            let peak = m[i] > -45 && m[i] >= m[i - 1] && m[i] >= m[i + 1] && m[i] - around >= 12
            hits[i] = peak ? min(hits[i] + 1, 90) : max(hits[i] - 2, 0)
            if hits[i] >= 20 && m[i] > bestLevel { bestLevel = m[i]; best = i }
        }
        if best != bin {
            bin = best
            let f: Float = best >= 0 ? Float(20 * pow(1000, Double(best) / Double(n - 1))) : 0
            DispatchQueue.main.async { self.hz = f }
        }
    }

    func clear() {
        for i in 0..<hits.count { hits[i] = 0 }
        bin = -1
        if hz != 0 { hz = 0 }
    }
}

/// Coeficientes biquad para dibujar el EQ (igual que Biquad en Android).
enum Biquad {
    static func design(_ type: Int, _ f0: Double, _ gainDb: Double, _ q: Double) -> [Double] {
        let w0 = 2 * Double.pi * f0 / 48000
        let cw = cos(w0), sw = sin(w0)
        let alpha = sw / (2 * q)
        let a = pow(10, gainDb / 40)
        let sa = sqrt(a)
        switch type {
        case 0: return [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha]
        case 5: return [(1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha]
        case 1:
            return [a * ((a + 1) - (a - 1) * cw + 2 * sa * alpha), 2 * a * ((a - 1) - (a + 1) * cw),
                    a * ((a + 1) - (a - 1) * cw - 2 * sa * alpha), (a + 1) + (a - 1) * cw + 2 * sa * alpha,
                    -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - 2 * sa * alpha]
        case 4:
            return [a * ((a + 1) + (a - 1) * cw + 2 * sa * alpha), -2 * a * ((a - 1) + (a + 1) * cw),
                    a * ((a + 1) + (a - 1) * cw - 2 * sa * alpha), (a + 1) - (a - 1) * cw + 2 * sa * alpha,
                    2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - 2 * sa * alpha]
        default:
            return [1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a]
        }
    }

    static func magDb(_ c: [Double], _ w: Double) -> Double {
        let cw = cos(w), c2w = cos(2 * w)
        let num = c[0] * c[0] + c[1] * c[1] + c[2] * c[2] + 2 * (c[0] * c[1] + c[1] * c[2]) * cw + 2 * c[0] * c[2] * c2w
        let den = c[3] * c[3] + c[4] * c[4] + c[5] * c[5] + 2 * (c[3] * c[4] + c[4] * c[5]) * cw + 2 * c[3] * c[5] * c2w
        if num <= 0 || den <= 0 { return -60 }
        return 10 * log10(num / den)
    }
}

/// Gráfico de EQ con puntos arrastrables. Banda 0 = LOW CUT (solo canales).
struct EqGraphView: View {
    let p: String
    let bands: Int
    let hpf: Bool
    @Binding var selected: Int
    var rtaOn = false
    var detector: FeedbackDetector?
    @StateObject private var group: ParamGroup
    @State private var dragBand = -1

    private static let gridHz: [Float] = [20, 40, 60, 80, 100, 200, 300, 400, 500, 600, 800, 1000, 2000, 3000, 4000, 5000, 6000, 8000, 10000, 20000]
    private static let gridLbl = ["20", "40", "60", "80", "100", "200", "300", "400", "500", "600", "800", "1k", "2k", "3k", "4k", "5k", "6k", "8k", "10k", "20k"]
    private static let dotColors: [Color] = [Color(hex: 0x2F6FD6), Color(hex: 0x1AA6A6), Color(hex: 0xC9B81E), Color(hex: 0xB01EB5), Color(hex: 0xD6731E), Color(hex: 0x3FB64A), Color(hex: 0xD63A3A)]

    init(prefix: String, bands: Int, hpf: Bool, selected: Binding<Int>, rtaOn: Bool = false, detector: FeedbackDetector? = nil) {
        self.p = prefix
        self.bands = bands
        self.hpf = hpf
        self.rtaOn = rtaOn
        self.detector = detector
        _selected = selected
        var addrs = ["\(prefix)/eq/on"]
        for b in 1...bands { for k in ["type", "f", "g", "q"] { addrs.append("\(prefix)/eq/\(b)/\(k)") } }
        if hpf { addrs.append("\(prefix)/preamp/hpon"); addrs.append("\(prefix)/preamp/hpf") }
        _group = StateObject(wrappedValue: ParamGroup(addrs))
    }

    private func f(_ a: String, _ d: Float) -> Float { Store.shared.float(a, def: d) }
    private func i(_ a: String, _ d: Int) -> Int { Store.shared.int(a, def: d) }

    private func dot(_ b: Int, _ r: CGRect) -> CGPoint {
        let xHz: (Float) -> CGFloat = { hz in r.minX + CGFloat(Fmt.logInv(20, 20000, hz)) * r.width }
        let yDb: (Float) -> CGFloat = { db in r.minY + CGFloat(1 - (db + 15) / 30) * r.height }
        if b == 0 { return CGPoint(x: xHz(Fmt.log(20, 400, f("\(p)/preamp/hpf", 0))), y: yDb(0)) }
        let t = i("\(p)/eq/\(b)/type", 2)
        let x = r.minX + CGFloat(f("\(p)/eq/\(b)/f", 0.5)) * r.width
        return CGPoint(x: x, y: (t == 0 || t == 5) ? yDb(0) : yDb(-15 + 30 * f("\(p)/eq/\(b)/g", 0.5)))
    }

    private func plotRect(_ s: CGSize) -> CGRect { CGRect(x: 30, y: 10, width: max(s.width - 40, 1), height: max(s.height - 36, 1)) }

    var body: some View {
        GeometryReader { geo in
            let r = plotRect(geo.size)
            TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: !rtaOn)) { _ in
            Canvas { ctx, _ in
                _ = group.params.count
                let xHz: (Float) -> CGFloat = { hz in r.minX + CGFloat(Fmt.logInv(20, 20000, hz)) * r.width }
                let yDb: (Float) -> CGFloat = { db in r.minY + CGFloat(1 - (db + 15) / 30) * r.height }
                ctx.fill(Path(r), with: .color(C.graph))
                // RTA: 100 bandas log de 20 Hz a 20 kHz, -80..0 dB de abajo hacia arriba.
                if rtaOn {
                    let m = Core.shared.client.rta
                    let n = MixerClient.rtaBins
                    var sp = Path()
                    sp.move(to: CGPoint(x: r.minX, y: r.maxY))
                    for k in 0..<n {
                        let x = r.minX + r.width * CGFloat(k) / CGFloat(n - 1)
                        let y = r.minY + r.height * CGFloat(1 - min(max((m[k] + 80) / 80, 0), 1))
                        sp.addLine(to: CGPoint(x: x, y: y))
                    }
                    sp.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
                    sp.closeSubpath()
                    var rc = ctx
                    rc.clip(to: Path(r))
                    rc.fill(sp, with: .color(C.sends.opacity(0.24)))
                    rc.stroke(sp, with: .color(C.sends.opacity(0.6)), lineWidth: 1)
                    if let det = detector {
                        det.update(m)
                        if det.bin >= 0 {
                            let x = r.minX + r.width * CGFloat(det.bin) / CGFloat(n - 1)
                            rc.fill(Path(CGRect(x: x - 1, y: r.minY, width: 2, height: r.height)), with: .color(C.red))
                            let hz = Float(20 * pow(1000, Double(det.bin) / Double(n - 1)))
                            let lbl = hz >= 1000 ? String(format: "ACOPLE %.1fk", hz / 1000) : String(format: "ACOPLE %.0f", hz)
                            let right = x > r.minX + r.width * 0.8
                            ctx.draw(Text(lbl).font(bf(12)).foregroundColor(C.red), at: CGPoint(x: right ? x - 5 : x + 5, y: r.minY + 10), anchor: right ? .trailing : .leading)
                        }
                    }
                }
                for (k, hz) in EqGraphView.gridHz.enumerated() {
                    let x = xHz(hz)
                    ctx.fill(Path(CGRect(x: x, y: r.minY, width: 1, height: r.height)), with: .color(C.grid))
                    ctx.draw(Text(EqGraphView.gridLbl[k]).font(bf(10)).foregroundColor(C.dim), at: CGPoint(x: x, y: r.maxY + 14))
                }
                for db in stride(from: -15, through: 15, by: 5) {
                    let y = yDb(Float(db))
                    ctx.fill(Path(CGRect(x: r.minX, y: y, width: r.width, height: 1)), with: .color(C.grid))
                    ctx.draw(Text("\(db)").font(bf(10)).foregroundColor(C.dim), at: CGPoint(x: r.minX - 5, y: y), anchor: .trailing)
                }
                // curva
                let eqOn = i("\(p)/eq/on", 0) != 0
                var coefs: [[Double]] = []
                if eqOn {
                    for b in 1...bands {
                        let t = i("\(p)/eq/\(b)/type", 2)
                        let fr = Double(Fmt.log(20, 20000, f("\(p)/eq/\(b)/f", 0.5)))
                        let g = Double(-15 + 30 * f("\(p)/eq/\(b)/g", 0.5))
                        let q = Double(Fmt.log(10, 0.3, f("\(p)/eq/\(b)/q", 0.5)))
                        coefs.append(Biquad.design(t, fr, g, q))
                    }
                }
                if hpf && i("\(p)/preamp/hpon", 0) != 0 {
                    coefs.append(Biquad.design(0, Double(Fmt.log(20, 400, f("\(p)/preamp/hpf", 0))), 0, 0.707))
                }
                var path = Path()
                let n = 160
                for k in 0..<n {
                    let fr = 20 * pow(1000, Double(k) / Double(n - 1))
                    let w = 2 * Double.pi * fr / 48000
                    var sum = 0.0
                    for c in coefs { sum += Biquad.magDb(c, w) }
                    let x = r.minX + r.width * CGFloat(k) / CGFloat(n - 1)
                    let y = yDb(Float(min(max(sum, -18), 18)))
                    if k == 0 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
                }
                var clipped = ctx
                clipped.clip(to: Path(r))
                clipped.stroke(path, with: .color(eqOn ? .white : C.tick), lineWidth: 2.5)
                // puntos
                for b in (hpf ? 0 : 1)...bands {
                    let d = dot(b, r)
                    let rad: CGFloat = b == selected ? 19 : 15
                    ctx.fill(Path(ellipseIn: CGRect(x: d.x - rad, y: d.y - rad, width: rad * 2, height: rad * 2)),
                             with: .color(EqGraphView.dotColors[b % EqGraphView.dotColors.count].opacity(b == selected ? 0.92 : 0.66)))
                    if b == selected {
                        ctx.stroke(Path(ellipseIn: CGRect(x: d.x - rad, y: d.y - rad, width: rad * 2, height: rad * 2)), with: .color(.white), lineWidth: 2)
                    }
                }
            }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { g in
                        if dragBand < 0 {
                            var best = -1
                            var bestD: CGFloat = 44 * 44
                            for b in (hpf ? 0 : 1)...bands {
                                let d = dot(b, r)
                                let dist = (d.x - g.startLocation.x) * (d.x - g.startLocation.x) + (d.y - g.startLocation.y) * (d.y - g.startLocation.y)
                                if dist < bestD { bestD = dist; best = b }
                            }
                            if best < 0 { dragBand = -2; return }
                            dragBand = best
                            selected = best
                        }
                        if dragBand < 0 { return }
                        let fx = Float(min(max((g.location.x - r.minX) / r.width, 0), 1))
                        if dragBand == 0 {
                            let hz = min(max(Fmt.log(20, 20000, fx), 20), 400)
                            Store.shared.set("\(p)/preamp/hpf", .f(Fmt.logInv(20, 400, hz)))
                        } else {
                            Store.shared.set("\(p)/eq/\(dragBand)/f", .f(fx))
                            let t = i("\(p)/eq/\(dragBand)/type", 2)
                            if (1...4).contains(t) {
                                let gy = Float(min(max(1 - (g.location.y - r.minY) / r.height, 0), 1))
                                Store.shared.set("\(p)/eq/\(dragBand)/g", .f(gy))
                            }
                        }
                    }
                    .onEnded { _ in dragBand = -1 }
            )
        }
    }
}

/// Curva de transferencia de gate o compresor.
struct DynGraphView: View {
    let base: String
    let gate: Bool
    @StateObject private var group: ParamGroup

    init(prefix: String, gate: Bool) {
        let b = gate ? "\(prefix)/gate" : "\(prefix)/dyn"
        base = b
        self.gate = gate
        var addrs = ["\(b)/on", "\(b)/thr", "\(b)/mode"]
        addrs.append(gate ? "\(b)/range" : "\(b)/ratio")
        _group = StateObject(wrappedValue: ParamGroup(addrs))
    }

    private func transfer(_ x: Float) -> Float {
        let mode = Store.shared.int("\(base)/mode", def: 0)
        if gate {
            let thr = Fmt.lin(-80, 0, Store.shared.float("\(base)/thr", def: 0))
            let range = Fmt.lin(3, 60, Store.shared.float("\(base)/range", def: 1))
            let modes = Core.shared.console.gateModes
            let name = mode >= 0 && mode < modes.count ? modes[mode] : "GATE"
            if x >= thr { return x }
            if name.hasPrefix("EXP"), let r = Float(String(name.last!)) { return max(thr + (x - thr) * r, x - range) }
            if name == "GATE" { return x - range }
            return x
        }
        let thr = Fmt.lin(-60, 0, Store.shared.float("\(base)/thr", def: 1))
        let ratios: [Float] = [1.1, 1.3, 1.5, 2, 2.5, 3, 4, 5, 7, 10, 20, 100]
        let ratio = ratios[min(max(Store.shared.int("\(base)/ratio", def: 0), 0), ratios.count - 1)]
        if mode == 0 { return x > thr ? thr + (x - thr) / ratio : x }
        return x < thr ? thr + (x - thr) * ratio : x
    }

    var body: some View {
        Canvas { ctx, size in
            _ = group.params.count
            let r = CGRect(origin: .zero, size: size).insetBy(dx: 4, dy: 4)
            ctx.fill(Path(r), with: .color(C.graph))
            for k in 1...7 {
                ctx.fill(Path(CGRect(x: r.minX + r.width * CGFloat(k) / 8, y: r.minY, width: 1, height: r.height)), with: .color(C.grid))
                ctx.fill(Path(CGRect(x: r.minX, y: r.minY + r.height * CGFloat(k) / 8, width: r.width, height: 1)), with: .color(C.grid))
            }
            let minDb: Float = gate ? -80 : -60
            var path = Path()
            for k in 0...80 {
                let x = minDb + (-minDb) * Float(k) / 80
                let y = transfer(x)
                let px = r.minX + CGFloat((x - minDb) / -minDb) * r.width
                let py = min(max(r.minY + CGFloat(1 - (y - minDb) / -minDb) * r.height, r.minY), r.maxY)
                if k == 0 { path.move(to: CGPoint(x: px, y: py)) } else { path.addLine(to: CGPoint(x: px, y: py)) }
            }
            let on = Store.shared.int("\(base)/on", def: 0) != 0
            ctx.stroke(path, with: .color(on ? .white : C.tick), lineWidth: 3)
        }
    }
}
