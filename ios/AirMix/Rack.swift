import SwiftUI

/// EFECTOS como un rack de equipos (igual que Rack.kt): cada FX es un frente de 19" con orejas y tornillos,
/// un display con el nombre del efecto (tocarlo cambia el tipo) y todas sus perillas para editar ahí mismo.
enum RackColors {
    static let faceTop = Color(hex: 0x3A4049)
    static let faceBottom = Color(hex: 0x20252B)
    static let ear = Color(hex: 0x2B3037)
    static let edge = Color(hex: 0x4B525C)
    static let screw = Color(hex: 0x14171B)
    static let screwRing = Color(hex: 0x5A626D)
    static let lcdBg = Color(hex: 0x0B1410)
    static let lcdEdge = Color(hex: 0x22382A)
    static let lcdText = Color(hex: 0xFFB04A)
    static let skirt = Color(hex: 0x15181C)
    static let cap = Color(hex: 0x3C434C)
    static let capHi = Color(hex: 0x5B636E)
    static let tick = Color(hex: 0x8A929C)
}

/// Frente de rack: degradé metálico + orejas con tornillos.
struct RackFace: View {
    let ear: CGFloat = 26
    var body: some View {
        Canvas { ctx, size in
            let full = Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: 6)
            ctx.fill(full, with: .linearGradient(Gradient(colors: [RackColors.faceTop, RackColors.faceBottom]),
                                                 startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
            ctx.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: ear, height: size.height), cornerRadius: 6), with: .color(RackColors.ear))
            ctx.fill(Path(roundedRect: CGRect(x: size.width - ear, y: 0, width: ear, height: size.height), cornerRadius: 6), with: .color(RackColors.ear))
            for x in [ear / 2, size.width - ear / 2] {
                for y in [CGFloat(18), size.height - 18] {
                    ctx.fill(Path(ellipseIn: CGRect(x: x - 6.5, y: y - 6.5, width: 13, height: 13)), with: .color(RackColors.screwRing))
                    ctx.fill(Path(ellipseIn: CGRect(x: x - 5, y: y - 5, width: 10, height: 10)), with: .color(RackColors.screw))
                    ctx.fill(Path(CGRect(x: x - 3, y: y - 0.75, width: 6, height: 1.5)), with: .color(RackColors.screwRing))
                }
            }
            ctx.stroke(full, with: .color(RackColors.edge), lineWidth: 1)
        }
    }
}

/// Perilla de equipo: nombre arriba, perilla con marcas y puntero, valor abajo en "display".
struct RackKnob: View {
    let spec: Spec
    var accent = C.accent
    @ObservedObject var p: Param
    @State private var start: Float?
    @State private var lastTap = Date.distantPast
    @State private var lastSent = -1

    init(_ spec: Spec, accent: Color = C.accent) {
        self.spec = spec
        self.accent = accent
        _p = ObservedObject(wrappedValue: Store.shared.param(spec.addr))
    }

    private var pos: Float {
        if spec.steps > 1 { return min(max(Float(p.int ?? 0) / Float(spec.steps - 1), 0), 1) }
        return min(max(p.float ?? 0, 0), 1)
    }

    private func send(_ v: Float) {
        if spec.steps > 1 {
            let i = Int((v * Float(spec.steps - 1)).rounded())
            if i != lastSent { lastSent = i; Store.shared.set(spec.addr, .i(Int32(i))) }
        } else {
            Store.shared.set(spec.addr, .f(v))
        }
    }

    var body: some View {
        let pos = self.pos
        let text = spec.steps > 1 ? spec.fmt((pos * Float(spec.steps - 1)).rounded()) : spec.fmt(pos)
        VStack(spacing: 2) {
            Text(spec.label).font(bf(10)).foregroundColor(C.dim).lineLimit(1).minimumScaleFactor(0.6)
            Canvas { ctx, size in
                let c = CGPoint(x: size.width / 2, y: size.height / 2)
                let r = min(size.width, size.height) / 2 - 2
                for k in 0...10 {
                    let a = (135 + Double(k) * 27) * .pi / 180
                    var t = Path()
                    t.move(to: CGPoint(x: c.x + CGFloat(cos(a)) * r * 0.86, y: c.y + CGFloat(sin(a)) * r * 0.86))
                    t.addLine(to: CGPoint(x: c.x + CGFloat(cos(a)) * r, y: c.y + CGFloat(sin(a)) * r))
                    ctx.stroke(t, with: .color(Float(k) / 10 <= pos + 0.001 ? accent : RackColors.tick), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                }
                func circle(_ rr: CGFloat, _ col: Color, dx: CGFloat = 0, dy: CGFloat = 0) {
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x + dx - rr, y: c.y + dy - rr, width: rr * 2, height: rr * 2)), with: .color(col))
                }
                circle(r * 0.74, RackColors.skirt)
                circle(r * 0.6, RackColors.cap)
                circle(r * 0.3, RackColors.capHi, dx: -r * 0.12, dy: -r * 0.14)
                circle(r * 0.42, RackColors.cap)
                let a = (135 + Double(pos) * 270) * .pi / 180
                var ptr = Path()
                ptr.move(to: CGPoint(x: c.x + CGFloat(cos(a)) * r * 0.2, y: c.y + CGFloat(sin(a)) * r * 0.2))
                ptr.addLine(to: CGPoint(x: c.x + CGFloat(cos(a)) * r * 0.66, y: c.y + CGFloat(sin(a)) * r * 0.66))
                ctx.stroke(ptr, with: .color(.white), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }
            Text(text).font(.system(size: 11, weight: .bold, design: .monospaced)).foregroundColor(RackColors.lcdText)
                .lineLimit(1).minimumScaleFactor(0.6)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { g in
                    if start == nil { start = pos; lastSent = -1 }
                    send(min(max((start ?? 0) - Float(g.translation.height / 220), 0), 1))
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

/// Pantalla EFECTOS: un frente de rack por FX, uno abajo del otro.
struct RackView: View {
    @Environment(\.verticalSizeClass) private var vsc
    var body: some View {
        let small = vsc == .compact
        ScrollView {
            VStack(spacing: 10) {
                ForEach(1...max(Core.shared.console.fxUnits, 1), id: \.self) { n in
                    RackUnit(n: n, small: small).frame(height: small ? 128 : 150)
                }
                Text("Tocá el display para cambiar el efecto. Arrastrá una perilla hacia arriba o abajo; doble toque = valor inicial.")
                    .font(bf(small ? 11 : 13)).foregroundColor(C.dim).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(10)
        }
        .background(C.bg)
    }
}

struct RackUnit: View {
    let n: Int
    let small: Bool
    @ObservedObject var type: Param

    init(n: Int, small: Bool) {
        self.n = n
        self.small = small
        _type = ObservedObject(wrappedValue: Store.shared.param("/fx/\(n)/type"))
    }

    var body: some View {
        let abbr = type.int.flatMap { Fx.abbrOf(n, $0) }
        let pars = Fx.params(abbr)
        let rtn = Fx.returnPrefix(n)
        let kw: CGFloat = small ? 66 : 78
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("FX \(n)").font(bf(small ? 11 : 13)).foregroundColor(C.accent)
                Menu {
                    ForEach(Array(Fx.typeOptions(n).enumerated()), id: \.offset) { i, name in
                        Button(name) { setType(i) }
                    }
                } label: {
                    Text((type.int.map { Fx.typeName(n, $0) } ?? "—").uppercased() + "  ▾")
                        .font(.system(size: small ? 13 : 16, weight: .bold, design: .monospaced))
                        .foregroundColor(RackColors.lcdText)
                        .lineLimit(2).minimumScaleFactor(0.6)
                        .padding(.horizontal, 12)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 6).fill(RackColors.lcdBg))
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(RackColors.lcdEdge))
                }
            }
            .frame(width: small ? 150 : 200)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    ForEach(0..<pars.count, id: \.self) { i in
                        RackKnob(Fx.spec(n, i, pars[i])).frame(width: kw)
                    }
                }
                .id(abbr ?? "?")
            }
            if let r = rtn {
                RackKnob(Spec(label: "RETORNO", addr: "\(r)/mix/fader", def: 0.75) { Fmt.faderDb($0) + " dB" }, accent: C.sends).frame(width: kw)
                ParamToggle("MUTE", addr: "\(r)/mix/on", invert: true, color: C.mute, style: .mute, size: small ? 12 : 15)
                    .frame(width: small ? 64 : 78, height: small ? 38 : 46)
            } else {
                Text("INSERT").font(bf(14)).foregroundColor(C.dim).frame(width: kw)
            }
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 26 + 12)
        .background(RackFace())
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
