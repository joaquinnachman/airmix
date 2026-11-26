import SwiftUI

/// Pantallas de inicio (diseño aprobado por Joaquín el 26/11/2026). Igual que StartScreen.kt (Android):
///  1) CONEXIÓN: radar buscando consolas, las encontradas en una lista, conectar a mano por IP y DEMO.
///  2) ¿CÓMO VAS A USAR AIRMIX HOY?: CONTROL TOTAL o MÚSICO (con la lista de monitores de la consola conectada).
struct StartView: View {
    @ObservedObject private var core = Core.shared
    let small: Bool
    @State private var ip = UserDefaults.standard.string(forKey: "ip") ?? ""

    var body: some View {
        Group {
            if core.startPage == 2 { modes } else { connection }
        }
        .padding(.horizontal, small ? 16 : 40)
        .padding(.vertical, small ? 8 : 22)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(C.bg.ignoresSafeArea())
        .onAppear { if core.startPage == 1 { core.client.discover() } }
    }

    private func topBar<R: View>(@ViewBuilder right: () -> R) -> some View {
        HStack(spacing: 8) {
            Image("Logo").resizable().scaledToFit().frame(height: small ? 22 : 32).accessibilityLabel("AirMix")
            Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")
                .font(bf(small ? 10 : 13)).foregroundColor(C.dim).padding(.top, small ? 6 : 10)
            Spacer()
            right()
        }
    }

    // MARK: 1 · conexión

    private var connection: some View {
        let last = UserDefaults.standard.string(forKey: "ip")
        let found = core.discovered.filter { $0.ip != last }
        return VStack(spacing: small ? 6 : 14) {
            topBar { EmptyView() }
            HStack(spacing: small ? 18 : 48) {
                VStack(spacing: small ? 8 : 18) {
                    RadarView().frame(width: small ? 140 : 300, height: small ? 140 : 300)
                    Text(core.waiting ? "Conectando…" : core.startError.isEmpty ? "BUSCANDO CONSOLAS EN LA RED…" : core.startError)
                        .font(bf(small ? 14 : 22)).foregroundColor(core.startError.isEmpty ? (core.waiting ? C.sends : C.accent) : C.red)
                        .multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.7)
                    if !small {
                        Text("Conectá el dispositivo al wifi de la consola\no al mismo router que la consola.")
                            .font(bf(15)).foregroundColor(C.dim).multilineTextAlignment(.center)
                    }
                }
                .frame(maxWidth: .infinity)
                VStack(alignment: .leading, spacing: small ? 4 : 10) {
                    Text("CONSOLAS ENCONTRADAS").font(bf(small ? 11 : 13)).kerning(1.4).foregroundColor(C.dim)
                    ScrollView(showsIndicators: false) {
                        VStack(spacing: small ? 6 : 10) {
                            ForEach(found) { d in
                                card(model: d.model, name: d.name, ip: d.ip, last: false) { core.startConnect(ip: d.ip, port: d.port, model: d.model) }
                            }
                            if let l = last, !l.isEmpty {
                                let d = core.discovered.first { $0.ip == l }
                                card(model: d?.model ?? core.console.model, name: d?.name ?? "Última usada", ip: l, last: true) {
                                    core.startConnect(ip: l, port: d?.port ?? 0, model: d?.model)
                                }
                            }
                        }
                    }
                    Rectangle().fill(C.line).frame(height: 1).padding(.vertical, small ? 4 : 10)
                    Text("¿NO APARECE? CONECTÁ A MANO").font(bf(small ? 11 : 13)).kerning(1.4).foregroundColor(C.dim)
                    if !small {
                        Text("Escribí la IP de la consola (en la XR12, de fábrica es 192.168.1.1).").font(bf(14)).foregroundColor(C.dim)
                    }
                    HStack(spacing: 10) {
                        TextField("192.168.1.1", text: $ip)
                            .keyboardType(.numbersAndPunctuation)
                            .font(bf(small ? 15 : 20)).foregroundColor(C.text)
                            .padding(.horizontal, 14).frame(height: small ? 40 : 54)
                            .background(RoundedRectangle(cornerRadius: 12).fill(C.strip))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(C.line))
                            .onSubmit { manual() }
                        Pill(text: "CONECTAR", style: .outline, size: small ? 13 : 17) { manual() }
                            .frame(width: small ? 104 : 150, height: small ? 40 : 54)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .frame(maxHeight: .infinity)
            HStack {
                Pill(text: "↻  BUSCAR DE NUEVO", style: .outline, size: small ? 12 : 16) {
                    core.discovered.removeAll()
                    core.startError = ""
                    core.client.discover()
                }
                .frame(width: small ? 150 : 220, height: small ? 38 : 52)
                Spacer()
                Text("X32 · M32 · XR12 · XR16 · XR18 · X18 · MR12 · MR18").font(bf(small ? 11 : 15)).foregroundColor(C.tick).lineLimit(1).minimumScaleFactor(0.6)
                Spacer()
                Button { core.startDemo(); core.startPage = 2 } label: {
                    Text("▶  PROBAR SIN CONSOLA (DEMO)").font(bf(small ? 12 : 16)).foregroundColor(C.accent).lineLimit(1).minimumScaleFactor(0.7)
                        .frame(width: small ? 210 : 320, height: small ? 38 : 52)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(C.accent))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func manual() {
        let t = ip.trimmingCharacters(in: .whitespaces)
        if !t.isEmpty { core.startConnect(ip: t, port: 0, model: nil) }
    }

    private func card(model: String, name: String, ip: String, last: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack(spacing: small ? 10 : 16) {
                Text(model.uppercased()).font(bf(small ? 14 : 20)).foregroundColor(C.sends)
                    .frame(width: small ? 52 : 74, height: small ? 38 : 54)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: 0x0C2A36)))
                VStack(alignment: .leading, spacing: 2) {
                    Text(name.isEmpty ? model : name).font(bf(small ? 16 : 22)).foregroundColor(C.text).lineLimit(1)
                    Text(last ? "\(ip)  ·  última usada" : ip).font(bf(small ? 11 : 14)).foregroundColor(C.dim).lineLimit(1)
                }
                Spacer(minLength: 0)
                Text("CONECTAR").font(bf(small ? 13 : 17)).foregroundColor(C.ink)
                    .frame(width: small ? 100 : 140, height: small ? 40 : 52)
                    .background(RoundedRectangle(cornerRadius: 12).fill(C.sends))
            }
            .padding(.horizontal, small ? 10 : 16)
            .frame(height: small ? 62 : 92)
            .background(RoundedRectangle(cornerRadius: 16).fill(C.panel))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(last ? C.sends : C.line, lineWidth: last ? 2 : 1))
        }
        .buttonStyle(.plain)
    }

    // MARK: 2 · elegir modo

    private var modes: some View {
        let info = core.demo ? "▶ MODO DEMO · \(core.console.model)"
            : [core.console.model, core.mixerName, core.client.host ?? ""].filter { !$0.isEmpty }.joined(separator: "  ·  ")
        let n = core.console.buses
        let cols = n <= 2 ? 1 : (n <= 6 ? 2 : 4)
        let lastBus = UserDefaults.standard.integer(forKey: "musicianBus")
        return VStack(spacing: small ? 4 : 16) {
            topBar {
                HStack(spacing: 8) {
                    Circle().fill(core.demo ? C.yellow : C.green).frame(width: small ? 8 : 10, height: small ? 8 : 10)
                    Text(info).font(bf(small ? 12 : 17)).foregroundColor(C.text).lineLimit(1)
                }
                .padding(.horizontal, 14).frame(height: small ? 30 : 44)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0x0F1F17)))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: 0x1F5A3D)))
                Pill(text: core.demo ? "SALIR DEL DEMO" : "CAMBIAR CONSOLA", style: .outline, size: small ? 11 : 15) {
                    if core.demo { core.stopDemo() } else { core.startPage = 1 }
                }
                .frame(width: small ? 130 : 190, height: small ? 30 : 44)
            }
            Text("¿CÓMO VAS A USAR AIRMIX HOY?").font(bf(small ? 18 : 32)).foregroundColor(C.text)
            HStack(spacing: small ? 12 : 24) {
                // CONTROL TOTAL
                VStack(spacing: small ? 2 : 8) {
                    ConsoleArt().frame(width: small ? 92 : 190, height: small ? 48 : 100)
                    texts("CONTROL TOTAL", "OPERADOR · SONIDISTA · TÉCNICO", "Toda la consola: la sala, los monitores, efectos, escenas y SALA. La app de siempre.",
                          C.accent, Color(hex: 0xD9B9A6), Color(hex: 0xB9A99D))
                    Spacer(minLength: 0)
                    Button { core.musicianBus = 0; core.startPage = 0 } label: {
                        Text("ENTRAR  →").font(bf(small ? 19 : 26)).foregroundColor(Color(hex: 0x2A140A))
                            .frame(maxWidth: .infinity).frame(height: small ? 50 : 76)
                            .background(RoundedRectangle(cornerRadius: 14).fill(C.accent))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, small ? 12 : 26).padding(.vertical, small ? 8 : 22)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(RoundedRectangle(cornerRadius: 20).fill(Color(hex: 0x17130F)))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(C.accent, lineWidth: 2))
                // MÚSICO
                VStack(spacing: small ? 2 : 8) {
                    HeadphonesArt().frame(width: small ? 62 : 130, height: small ? 48 : 100)
                    texts("MÚSICO", "MONITORES · ASISTENTE · SOLO MEZCLAS", "Tocá tu monitor y entrás directo. La sala no se toca.",
                          C.sends, Color(hex: 0xA9D4E6), Color(hex: 0x8FA6B2))
                    Spacer(minLength: 0)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: small ? 4 : 8), count: cols), spacing: small ? 4 : 8) {
                        ForEach(1...max(n, 1), id: \.self) { b in
                            MonitorTile(bus: b, total: n, last: b == lastBus, small: small) {
                                UserDefaults.standard.set(b, forKey: "musicianBus")
                                core.musicianBus = b
                                core.startPage = 0
                            }
                        }
                    }
                }
                .padding(.horizontal, small ? 12 : 26).padding(.vertical, small ? 8 : 22)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(RoundedRectangle(cornerRadius: 20).fill(Color(hex: 0x0F171D)))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(C.sends, lineWidth: 2))
            }
            if !small {
                Text("Aparecen los envíos que tiene la consola conectada, con el nombre y el color que tienen en la consola.")
                    .font(bf(13)).foregroundColor(C.tick)
            }
        }
    }

    private func texts(_ title: String, _ sub: String, _ text: String, _ tc: Color, _ sc: Color, _ xc: Color) -> some View {
        VStack(spacing: small ? 1 : 4) {
            Text(title).font(bf(small ? 22 : 40)).foregroundColor(tc).lineLimit(1)
            Text(sub).font(bf(small ? 11 : 17)).kerning(1).foregroundColor(sc).lineLimit(1).minimumScaleFactor(0.7)
            Text(text).font(.system(size: small ? 11 : 15, weight: .medium)).foregroundColor(xc)
                .multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.8)
        }
    }
}

/// Un monitor en MÚSICO: "MONITOR n" (color del bus) arriba y el nombre que tiene en la consola abajo, en grande.
struct MonitorTile: View {
    let bus: Int
    let total: Int
    let last: Bool
    let small: Bool
    let onTap: () -> Void
    @ObservedObject private var name: Param
    @ObservedObject private var color: Param

    init(bus: Int, total: Int, last: Bool, small: Bool, onTap: @escaping () -> Void) {
        self.bus = bus
        self.total = total
        self.last = last
        self.small = small
        self.onTap = onTap
        let s = Strip(kind: .bus, idx: bus)
        _name = ObservedObject(wrappedValue: Store.shared.param(s.nameAddr))
        _color = ObservedObject(wrappedValue: Store.shared.param(s.colorAddr))
    }

    var body: some View {
        let n = (name.value?.stringValue ?? "").trimmingCharacters(in: .whitespaces)
        let big = total <= 6
        let nameSize: CGFloat = total <= 2 ? (small ? 18 : 32) : big ? (small ? 15 : 26) : (small ? 11 : 17)
        let h: CGFloat = total <= 2 ? (small ? 54 : 96) : big ? (small ? 40 : 72) : (small ? 30 : 56)
        Button(action: onTap) {
            VStack(spacing: 2) {
                Text(last ? "MONITOR \(bus)  ·  ÚLTIMO" : "MONITOR \(bus)")
                    .font(bf(big ? (small ? 10 : 14) : (small ? 9 : 11))).kerning(1.5)
                    .foregroundColor(BusColor.of(color.int ?? 0)).lineLimit(1).minimumScaleFactor(0.7)
                Text(n.isEmpty ? "SIN NOMBRE" : n.uppercased())
                    .font(bf(nameSize)).foregroundColor(n.isEmpty ? C.tick : C.text).lineLimit(1).minimumScaleFactor(0.5)
            }
            .frame(maxWidth: .infinity).frame(height: h)
            .background(RoundedRectangle(cornerRadius: 14).fill(last ? Color(hex: 0x12303D) : Color(hex: 0x141C23)))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(last ? C.sends : Color(hex: 0x24313A), lineWidth: last ? 2 : 1))
        }
        .buttonStyle(.plain)
    }
}

/// Radar de búsqueda: un barrido que gira.
struct RadarView: View {
    var body: some View {
        TimelineView(.animation) { tl in
            Canvas { ctx, size in
                let t = tl.date.timeIntervalSinceReferenceDate
                let c = CGPoint(x: size.width / 2, y: size.height / 2)
                let r = min(size.width, size.height) / 2 - 3
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(Color(hex: 0x121519)))
                for k in [0.66, 0.33] {
                    let rr = r * k
                    ctx.stroke(Path(ellipseIn: CGRect(x: c.x - rr, y: c.y - rr, width: 2 * rr, height: 2 * rr)), with: .color(Color(hex: 0x23272F)), lineWidth: 1)
                }
                var cross = Path()
                cross.move(to: CGPoint(x: c.x - r, y: c.y)); cross.addLine(to: CGPoint(x: c.x + r, y: c.y))
                cross.move(to: CGPoint(x: c.x, y: c.y - r)); cross.addLine(to: CGPoint(x: c.x, y: c.y + r))
                ctx.stroke(cross, with: .color(Color(hex: 0x23272F)), lineWidth: 1)
                let ang = (t.truncatingRemainder(dividingBy: 3.2) / 3.2) * 360 - 90
                for k in 0..<12 {
                    var wedge = Path()
                    wedge.move(to: c)
                    wedge.addArc(center: c, radius: r, startAngle: .degrees(ang - Double(k + 1) * 6), endAngle: .degrees(ang - Double(k) * 6), clockwise: false)
                    wedge.closeSubpath()
                    ctx.fill(wedge, with: .color(C.accent.opacity(max(0, 0.35 - Double(k) * 0.028))))
                }
                var line = Path()
                line.move(to: c)
                line.addLine(to: CGPoint(x: c.x + r * cos(ang * .pi / 180), y: c.y + r * sin(ang * .pi / 180)))
                ctx.stroke(line, with: .color(C.accent), lineWidth: 2)
                let pr = t.truncatingRemainder(dividingBy: 2.4) / 2.4
                let wr = r * (0.35 + 0.65 * pr)
                ctx.stroke(Path(ellipseIn: CGRect(x: c.x - wr, y: c.y - wr, width: 2 * wr, height: 2 * wr)), with: .color(C.accent.opacity(0.8 * (1 - pr))), lineWidth: 2)
                ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(Color(hex: 0x2A2F38)), lineWidth: 2)
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 5, y: c.y - 5, width: 10, height: 10)), with: .color(C.text))
            }
        }
    }
}

/// Dibujo de CONTROL TOTAL: una consola con faders.
struct ConsoleArt: View {
    private let pos: [CGFloat] = [0.35, 0.2, 0.55, 0.28, 0.45, 0.12]
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            let body = Path(roundedRect: CGRect(x: 2, y: 2, width: w - 4, height: h - 4), cornerRadius: 10)
            ctx.fill(body, with: .color(Color(hex: 0x211C17)))
            ctx.stroke(body, with: .color(Color(hex: 0x3A332B)), lineWidth: 1.5)
            let n = CGFloat(pos.count)
            let top = h * 0.14, bot = h * 0.86
            let capW = w / n * 0.5, capH = h * 0.12
            for (i, p) in pos.enumerated() {
                let x = w * (CGFloat(i) + 0.5) / n
                var rail = Path()
                rail.move(to: CGPoint(x: x, y: top)); rail.addLine(to: CGPoint(x: x, y: bot))
                ctx.stroke(rail, with: .color(Color(hex: 0x463D33)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                let y = top + (bot - top) * p
                ctx.fill(Path(roundedRect: CGRect(x: x - capW / 2, y: y, width: capW, height: capH), cornerRadius: 3), with: .color(i == pos.count - 1 ? .white : C.cap))
                if i != pos.count - 1 {
                    ctx.fill(Path(CGRect(x: x - capW / 2 + 3, y: y + capH / 2 - 1, width: capW - 6, height: 2)), with: .color(C.accent))
                }
            }
        }
    }
}

/// Dibujo de MÚSICO: auriculares in-ear y una nota.
struct HeadphonesArt: View {
    var body: some View {
        Canvas { ctx, size in
            let s = min(size.width / 104, size.height / 80)
            let ox = (size.width - 104 * s) / 2, oy = (size.height - 80 * s) / 2
            var arc = Path()
            arc.addArc(center: CGPoint(x: ox + 46 * s, y: oy + 46 * s), radius: 34 * s, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
            ctx.stroke(arc, with: .color(C.text), style: StrokeStyle(lineWidth: 5 * s, lineCap: .round))
            ctx.fill(Path(roundedRect: CGRect(x: ox + 4 * s, y: oy + 40 * s, width: 18 * s, height: 30 * s), cornerRadius: 8 * s), with: .color(C.sends))
            ctx.fill(Path(roundedRect: CGRect(x: ox + 70 * s, y: oy + 40 * s, width: 18 * s, height: 30 * s), cornerRadius: 8 * s), with: .color(C.sends))
            let note = Color(hex: 0x8FD3EF)
            ctx.fill(Path(ellipseIn: CGRect(x: ox + 88 * s, y: oy + 30 * s, width: 8 * s, height: 8 * s)), with: .color(note))
            ctx.fill(Path(CGRect(x: ox + 94.5 * s, y: oy + 16 * s, width: 2 * s, height: 18 * s)), with: .color(note))
            ctx.fill(Path(CGRect(x: ox + 94.5 * s, y: oy + 16 * s, width: 8.5 * s, height: 3 * s)), with: .color(note))
        }
    }
}
