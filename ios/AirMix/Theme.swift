import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xff) / 255, green: Double((hex >> 8) & 0xff) / 255, blue: Double(hex & 0xff) / 255)
    }
}

/// Paleta AirMix (igual que C en Android).
enum C {
    static let bg = Color(hex: 0x0F1216)
    static let top = Color(hex: 0x0B0E11)
    static let panel = Color(hex: 0x161B21)
    static let surface = Color(hex: 0x1C222A)
    static let surface2 = Color(hex: 0x252D37)
    static let line = Color(hex: 0x2E3743)
    static let graph = Color(hex: 0x0C0F13)
    static let strip = Color(hex: 0x141920)
    static let accent = Color(hex: 0xFF7A45)
    static let sends = Color(hex: 0x4CC9F0)
    static let mute = Color(hex: 0xE5484D)
    static let solo = Color(hex: 0xF5C542)
    static let text = Color(hex: 0xE8EDF2)
    static let dim = Color(hex: 0x8A96A3)
    static let ink = Color(hex: 0x0B0E11)
    static let grid = Color(hex: 0x1F262F)
    static let green = Color(hex: 0x3DD68C)
    static let yellow = Color(hex: 0xF5C542)
    static let red = Color(hex: 0xE5484D)
    static let meterBg = Color(hex: 0x0A0D10)
    static let tick = Color(hex: 0x4A5562)
    static let fill = Color(hex: 0x5B6775)
    static let cap = Color(hex: 0xE8EDF2)
    static let capShade = Color(hex: 0xB9C2CC)
    static let knobBg = Color(hex: 0x2A323C)
    static let knobFace = Color(hex: 0x1C222A)

    static let scribble: [Color] = [
        Color(hex: 0x2E3743), Color(hex: 0xE5484D), Color(hex: 0x3DD68C), Color(hex: 0xF5C542),
        Color(hex: 0x4C8DF6), Color(hex: 0xC764E8), Color(hex: 0x4CC9F0), Color(hex: 0xE8EDF2)
    ]
}

/// Acento de color por sector. Mismo mapa que Sector en Ui.kt.
enum Sector {
    static func of(_ k: Kind) -> Color {
        switch k {
        case .ch: return Color(hex: 0xFF7A45)      // coral
        case .aux: return Color(hex: 0xC764E8)     // violeta
        case .rtn: return Color(hex: 0x2EC4B6)     // turquesa
        case .bus: return Color(hex: 0x4CC9F0)     // celeste
        case .mtx: return Color(hex: 0x8C9EFF)     // lavanda
        case .fxsend: return Color(hex: 0xFFB547)  // ámbar
        case .dca: return Color(hex: 0xF06292)     // rosa
        case .lr: return Color(hex: 0xE8EDF2)      // blanco
        }
    }
}

func bf(_ size: CGFloat) -> Font { .system(size: size, weight: .bold) }

enum PillStyle { case nav, tab, light, mute, small, outline }

/// Botón base de toda la app.
struct Pill: View {
    let text: String
    var active = false
    var activeColor = C.accent
    var style: PillStyle = .light
    var size: CGFloat = 15
    /// Color del texto cuando no está activo (nil = el del estilo).
    var idleColor: Color? = nil
    let action: () -> Void

    private var fg: Color {
        if !active, let c = idleColor { return c }
        if active {
            if style == .tab { return activeColor }
            return activeColor == C.mute ? .white : C.ink
        }
        switch style {
        case .nav, .tab, .small: return C.dim
        default: return C.text
        }
    }

    private var bg: Color {
        if active { return style == .tab ? .clear : activeColor }
        switch style {
        case .light, .mute, .small: return C.surface2
        default: return .clear
        }
    }

    private var stroke: Color {
        if active { return style == .tab ? activeColor : .clear }
        switch style {
        case .mute, .outline: return C.line
        default: return .clear
        }
    }

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(bf(size))
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .minimumScaleFactor(0.6)
                .foregroundColor(fg)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(RoundedRectangle(cornerRadius: style == .tab ? 8 : 10).fill(bg))
                .overlay(RoundedRectangle(cornerRadius: style == .tab ? 8 : 10).stroke(stroke, lineWidth: 1))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Botón on/off ligado a un parámetro. invert = MUTE (activo cuando mix/on == 0).
struct ParamToggle: View {
    @ObservedObject var p: Param
    let text: String
    var invert = false
    var color = C.accent
    var style: PillStyle = .light
    var size: CGFloat = 15

    init(_ text: String, addr: String, invert: Bool = false, color: Color = C.accent, style: PillStyle = .light, size: CGFloat = 15) {
        _p = ObservedObject(wrappedValue: Store.shared.param(addr))
        self.text = text
        self.invert = invert
        self.color = color
        self.style = style
        self.size = size
    }

    var body: some View {
        let x = p.int ?? (invert ? 1 : 0)
        let active = invert ? x == 0 : x != 0
        Pill(text: text, active: active, activeColor: color, style: style, size: size) {
            let cur = p.int ?? (invert ? 1 : 0)
            Store.shared.set(p.address, .i(cur != 0 ? 0 : 1))
        }
    }
}

/// ON/OFF para gate/dyn/eq.
struct OnOff: View {
    @ObservedObject var p: Param
    init(addr: String) { _p = ObservedObject(wrappedValue: Store.shared.param(addr)) }
    var body: some View {
        let on = (p.int ?? 0) != 0
        Pill(text: on ? "● ON" : "○ OFF", active: on, size: 17) {
            Store.shared.set(p.address, .i(on ? 0 : 1))
        }
    }
}

/// Selector segmentado.
struct Choice: View {
    @ObservedObject var p: Param
    let labels: [String]
    init(addr: String, labels: [String]) {
        _p = ObservedObject(wrappedValue: Store.shared.param(addr))
        self.labels = labels
    }
    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(labels.enumerated()), id: \.offset) { i, l in
                Pill(text: l, active: p.int == i, style: .nav, size: 14) { Store.shared.set(p.address, .i(Int32(i))) }
                    .frame(width: 84, height: 38)
            }
        }
        .padding(3)
        .background(RoundedRectangle(cornerRadius: 10).fill(C.surface2))
    }
}

/// Selector de lista (menú).
struct ListSelector: View {
    @ObservedObject var p: Param
    let title: String
    let options: [String]
    init(_ title: String, addr: String, options: [String]) {
        _p = ObservedObject(wrappedValue: Store.shared.param(addr))
        self.title = title
        self.options = options
    }
    var body: some View {
        Menu {
            ForEach(Array(options.enumerated()), id: \.offset) { i, o in
                Button(o) { Store.shared.set(p.address, .i(Int32(i))) }
            }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(title)  ▾").font(bf(12)).foregroundColor(C.dim)
                Text(p.int.flatMap { $0 >= 0 && $0 < options.count ? options[$0] : nil } ?? "—").font(bf(16)).foregroundColor(C.text)
            }
            .padding(.horizontal, 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 10).fill(C.surface2))
        }
    }
}

/// Un bit de una máscara (DCA / mute groups).
struct BitToggle: View {
    @ObservedObject var p: Param
    let text: String
    let bit: Int
    var color = C.accent
    init(_ text: String, addr: String, bit: Int, color: Color = C.accent) {
        _p = ObservedObject(wrappedValue: Store.shared.param(addr))
        self.text = text
        self.bit = bit
        self.color = color
    }
    var body: some View {
        let v = p.int ?? 0
        Pill(text: text, active: ((v >> bit) & 1) == 1, activeColor: color, size: 16) {
            Store.shared.set(p.address, .i(Int32(v ^ (1 << bit))))
        }
    }
}

/// Chip con el nombre y el color de scribble de la consola.
struct NameChip: View {
    @ObservedObject var name: Param
    @ObservedObject var color: Param
    let fallback: String
    var height: CGFloat = 34
    init(strip: Strip, height: CGFloat = 34) {
        self.height = height
        _name = ObservedObject(wrappedValue: Store.shared.param(strip.nameAddr))
        _color = ObservedObject(wrappedValue: Store.shared.param(strip.colorAddr))
        fallback = strip.title
    }
    var body: some View {
        let c = min(max(color.int ?? 0, 0), 15)
        let base = C.scribble[c % 8]
        let filled = c < 8
        let n = name.value?.stringValue ?? ""
        Text(n.isEmpty ? fallback : n)
            .font(bf(height < 30 ? 12 : 14))
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .padding(.horizontal, 4)
            .frame(maxWidth: .infinity, minHeight: height, maxHeight: height)
            .foregroundColor(filled ? ([0, 1, 4, 5].contains(c) ? .white : C.ink) : (c == 8 ? C.text : base))
            .background(RoundedRectangle(cornerRadius: 8).fill(filled ? base : Color.clear))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(filled ? Color.clear : (c == 8 ? C.dim : base), lineWidth: 1.5))
    }
}

/// dB de un fader, en texto.
struct DbLabel: View {
    @ObservedObject var p: Param
    let color: Color
    init(addr: String, color: Color) {
        _p = ObservedObject(wrappedValue: Store.shared.param(addr))
        self.color = color
    }
    var body: some View {
        Text(Fmt.faderDb(p.float ?? 0)).font(bf(13)).foregroundColor(color)
    }
}

struct Card<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(bf(12)).kerning(1.4).foregroundColor(C.dim)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(C.surface))
    }
}
