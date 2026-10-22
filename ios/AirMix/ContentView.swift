import SwiftUI
import UniformTypeIdentifiers

enum Tab: String, CaseIterable {
    case mix = "MEZCLA", fx = "EFECTOS", meters = "MEDIDORES", scenes = "ESCENAS"
}

struct ContentView: View {
    @ObservedObject private var core = Core.shared
    @Environment(\.horizontalSizeClass) private var hsc
    @State private var tab = Tab.mix
    @State private var layerIdx = 0
    @State private var sends = SendTarget.main
    @State private var detail: Strip?
    @State private var section = Section.config
    @State private var showNotes = false
    @State private var showConnect = false
    @State private var confirmReset = false

    private var compact: Bool { hsc == .compact }

    var body: some View {
        let layers = buildLayers()
        VStack(spacing: 0) {
            topBar
            HStack(spacing: 0) {
                rail(layers)
                ZStack(alignment: .trailing) {
                    content(layers)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    if showNotes {
                        NotesPanel { showNotes = false }
                            .frame(width: compact ? 320 : 420)
                            .padding(8)
                            .transition(.move(edge: .trailing))
                    }
                }
            }
        }
        .background(C.bg.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .statusBarHidden(true)
        .sheet(isPresented: $showConnect) { ConnectSheet() }
        .onAppear { if UserDefaults.standard.string(forKey: "ip") == nil { showConnect = true } }
        .onChange(of: core.layoutVersion) { _ in
            layerIdx = 0
            detail = nil
            sends = .main
        }
    }

    // MARK: barra superior

    private var topBar: some View {
        HStack(spacing: compact ? 6 : 10) {
            // Logo 8C (curva EQ + "airmix"), mismo vector que el ícono. Generado por tools/logo/gen.py.
            Image("Logo")
                .resizable()
                .scaledToFit()
                .frame(height: compact ? 22 : 28)
                .accessibilityLabel("AirMix")
            Spacer().frame(width: compact ? 4 : 30)
            ForEach(Tab.allCases, id: \.self) { t in
                Pill(text: t.rawValue, active: tab == t, style: .tab, size: compact ? 12 : 15) { tab = t }
                    .frame(width: compact ? 84 : 120, height: 38)
            }
            Spacer(minLength: 0)
            Button { showConnect = true } label: {
                Text(core.connected ? "●  \(core.console.model)  \(core.client.host ?? "")" : "●  SIN CONEXIÓN")
                    .font(bf(compact ? 11 : 14))
                    .lineLimit(1)
                    .foregroundColor(core.connected ? C.green : C.red)
                    .padding(.horizontal, 12)
                    .frame(height: 38)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(C.line))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12)
        .frame(height: compact ? 48 : 58)
        .background(C.top)
    }

    // MARK: riel izquierdo

    private func rail(_ layers: [Layer]) -> some View {
        VStack(spacing: 8) {
            TimelineView(.periodic(from: .now, by: 1)) { _ in
                let t = ShowTimer.elapsed()
                VStack(spacing: 0) {
                    Text("SHOW").font(bf(10)).foregroundColor(C.dim)
                    Text(ShowTimer.format(t)).font(.system(size: compact ? 18 : 22, weight: .bold, design: .monospaced))
                        .foregroundColor(ShowTimer.running ? C.green : (t > 0 ? C.solo : C.dim))
                    if !compact { Text("tocá: ▶ / ❚❚").font(bf(9)).foregroundColor(C.tick) }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 12).fill(C.surface))
            }
            .onTapGesture { ShowTimer.toggle() }
            .onLongPressGesture { confirmReset = true }
            .confirmationDialog("¿Poner el cronómetro en cero?", isPresented: $confirmReset, titleVisibility: .visible) {
                Button("Reset", role: .destructive) { ShowTimer.reset() }
            }

            ScrollView(showsIndicators: false) {
                VStack(spacing: 6) {
                    ForEach(layers) { l in
                        Pill(text: l.label, active: tab == .mix && layerIdx == l.id, style: .nav, size: compact ? 12 : 14) {
                            layerIdx = l.id
                            tab = .mix
                            detail = nil
                        }
                        .frame(height: compact ? 40 : 48)
                    }
                }
            }

            Pill(text: "✎ NOTAS", active: showNotes, style: .outline, size: 14) {
                withAnimation(.easeOut(duration: 0.2)) { showNotes.toggle() }
            }
            .frame(height: 42)

            FxMuteButton().id(core.layoutVersion).frame(height: compact ? 60 : 74)

            Pill(text: "CLR SOLO", style: .outline, size: 13) {
                let n = core.console.isX32 ? 80 : 36
                for i in 1...n { Store.shared.set("/-stat/solosw/\(Fmt.two(i))", .i(0)) }
            }
            .frame(height: 40)
        }
        .padding(10)
        .frame(width: compact ? 96 : 120)
        .background(C.top)
    }

    // MARK: contenido

    @ViewBuilder
    private func content(_ layers: [Layer]) -> some View {
        switch tab {
        case .mix:
            if let d = detail { detailView(d) } else { mixView(layers) }
        case .fx: EffectsView()
        case .meters: AllMetersView()
        case .scenes: ScenesView()
        }
    }

    private func mixView(_ layers: [Layer]) -> some View {
        let layer = layerIdx < layers.count ? layers[layerIdx] : layers.first
        let strips = layer?.strips ?? []
        let master: Strip = sends.bus > 0 ? Strip(kind: .bus, idx: sends.bus) : sends.fx > 0 ? Strip(kind: .fxsend, idx: sends.fx) : Strip(kind: .lr)
        return VStack(spacing: 0) {
            HStack(spacing: 10) {
                if sends != .main {
                    SendTargetName(target: sends, prefix: "SENDS ON FADER  →  ", inkOnColor: true).font(bf(compact ? 15 : 20))
                    Spacer()
                    sendsMenu(label: "CAMBIAR ▾", dark: true)
                    Button { sends = .main } label: {
                        Text("SALIR ✕").font(bf(14)).foregroundColor(C.text).padding(.horizontal, 14).frame(height: 38)
                            .background(RoundedRectangle(cornerRadius: 10).fill(C.surface2))
                    }.buttonStyle(.plain)
                } else {
                    Text(layer?.label ?? "").font(bf(compact ? 16 : 20)).foregroundColor(C.text)
                    Spacer()
                    sendsMenu(label: "SENDS ON FADER ▾", dark: false)
                }
            }
            .padding(.horizontal, 16)
            .frame(height: compact ? 46 : 54)
            .background(SendTargetBackground(target: sends))

            HStack(spacing: 4) {
                if compact {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 4) {
                            ForEach(strips, id: \.self) { s in
                                StripView(strip: s, target: sends, compact: true) { openDetail($0) }.frame(width: 104)
                            }
                        }
                    }
                } else {
                    ForEach(0..<8, id: \.self) { i in
                        if i < strips.count {
                            StripView(strip: strips[i], target: sends) { openDetail($0) }.frame(maxWidth: .infinity)
                        } else {
                            Color.clear.frame(maxWidth: .infinity)
                        }
                    }
                }
                StripView(strip: master, target: .main, compact: compact) { openDetail($0) }
                    .frame(width: compact ? 104 : nil)
                    .frame(maxWidth: compact ? 104 : .infinity)
                    .padding(.leading, 8)
            }
            .padding(4)
        }
    }

    private func sendsMenu(label: String, dark: Bool) -> some View {
        Menu {
            Button("MAIN (normal)") { sends = .main }
            ForEach(1...max(core.console.buses, 1), id: \.self) { b in
                Button { sends = SendTarget(bus: b, fx: 0) } label: { SendTargetName(target: SendTarget(bus: b, fx: 0)) }
            }
            if core.console.fxSends > 0 {
                ForEach(1...core.console.fxSends, id: \.self) { f in
                    Button { sends = SendTarget(bus: 0, fx: f) } label: { SendTargetName(target: SendTarget(bus: 0, fx: f)) }
                }
            }
        } label: {
            Text(label).font(bf(14)).foregroundColor(dark ? C.ink : C.text)
                .padding(.horizontal, 14).frame(height: 38)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(dark ? C.ink.opacity(0.4) : C.line))
        }
    }

    private func openDetail(_ s: Strip) {
        detail = s
    }

    private func detailView(_ s: Strip) -> some View {
        let secs = sectionsFor(s)
        let sec = secs.contains(section) ? section : secs[0]
        return VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    Pill(text: "‹  VOLVER", style: .outline, size: 15) { detail = nil }.frame(width: 110, height: 40)
                    DetailTitle(strip: s).frame(width: compact ? 150 : 260, alignment: .leading).padding(.leading, 8)
                    ForEach(secs, id: \.self) { x in
                        SectionTab(strip: s, section: x, active: x == sec) { section = x }.frame(width: 100, height: 40)
                    }
                    Pill(text: "‹", style: .outline, size: 22) { step(s, -1) }.frame(width: 50, height: 40).padding(.leading, 12)
                    Pill(text: "›", style: .outline, size: 22) { step(s, 1) }.frame(width: 50, height: 40)
                }
                .padding(.horizontal, 10)
            }
            .frame(height: 56)
            .background(C.panel)

            HStack(spacing: 4) {
                StripView(strip: s, target: .main, compact: compact) { _ in }.frame(width: compact ? 104 : 130)
                DetailPanel(strip: s, section: sec)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: 14).fill(C.panel))
                    .id("\(s.prefix)-\(sec.rawValue)")
            }
            .padding(4)
        }
    }

    private func step(_ s: Strip, _ dir: Int) {
        let list = stripsOfKind(s.kind)
        guard let i = list.firstIndex(of: s), !list.isEmpty else { return }
        detail = list[(i + dir + list.count) % list.count]
    }
}

/// "1-JUANCITO" (número de bus + nombre de la consola) o "FX1 · Hall Reverb" (efecto cargado).
struct SendTargetName: View {
    let target: SendTarget
    var prefix = ""
    var inkOnColor = false
    @ObservedObject var name: Param
    @ObservedObject var color: Param
    init(target: SendTarget, prefix: String = "", inkOnColor: Bool = false) {
        self.target = target
        self.prefix = prefix
        self.inkOnColor = inkOnColor
        if target.fx > 0 {
            _name = ObservedObject(wrappedValue: Store.shared.param("/fx/\(target.fx)/type"))
        } else {
            _name = ObservedObject(wrappedValue: target.nameAddr.map { Store.shared.param($0) } ?? Param("-"))
        }
        _color = ObservedObject(wrappedValue: target.bus > 0 ? Store.shared.param(Strip(kind: .bus, idx: target.bus).colorAddr) : Param("-"))
    }
    var body: some View {
        let t = target.fx > 0 ? Fx.label(target.fx, name.int) : target.display(name.value?.stringValue ?? "")
        if inkOnColor {
            Text(prefix + t).foregroundColor(target.bus > 0 ? BusColor.ink(color.int ?? 0) : C.ink)
        } else {
            Text(prefix + t)
        }
    }
}

/// Fondo del encabezado: color del bus en "sends on fader", naranja para FX, panel normal en MAIN.
struct SendTargetBackground: View {
    let target: SendTarget
    @ObservedObject var color: Param
    init(target: SendTarget) {
        self.target = target
        _color = ObservedObject(wrappedValue: target.bus > 0 ? Store.shared.param(Strip(kind: .bus, idx: target.bus).colorAddr) : Param("-"))
    }
    var body: some View {
        if target.bus > 0 { BusColor.of(color.int ?? 0) } else if target.fx > 0 { C.accent } else { C.panel }
    }
}

struct DetailTitle: View {
    let strip: Strip
    @ObservedObject var name: Param
    init(strip: Strip) {
        self.strip = strip
        _name = ObservedObject(wrappedValue: Store.shared.param(strip.nameAddr))
    }
    var body: some View {
        let n = name.value?.stringValue ?? ""
        Text(n.isEmpty ? strip.title : "\(strip.title)  ·  \(n)").font(bf(20)).foregroundColor(C.text).lineLimit(1)
    }
}

struct SectionTab: View {
    let section: Section
    let active: Bool
    let action: () -> Void
    @ObservedObject var on: Param
    let hasOn: Bool

    init(strip: Strip, section: Section, active: Bool, action: @escaping () -> Void) {
        self.section = section
        self.active = active
        self.action = action
        let addr: String?
        switch section {
        case .gate: addr = "\(strip.prefix)/gate/on"
        case .dyn: addr = "\(strip.prefix)/dyn/on"
        case .eq: addr = "\(strip.prefix)/eq/on"
        default: addr = nil
        }
        hasOn = addr != nil
        _on = ObservedObject(wrappedValue: addr.map { Store.shared.param($0) } ?? Param("-"))
    }

    var body: some View {
        let lit = hasOn && (on.int ?? 0) != 0
        Pill(text: (lit ? "● " : "") + section.rawValue, active: active, style: .tab, size: 15, action: action)
    }
}

/// MUTE FX: mutea todos los efectos y restaura los que estaban abiertos.
struct FxMuteButton: View {
    @StateObject private var group = ParamGroup(FxMute.targets())
    var body: some View {
        let all = group.params.allSatisfy { ($0.int ?? 1) == 0 }
        Pill(text: all ? "FX\nMUTEADOS" : "MUTE\nFX", active: all, activeColor: C.mute, style: .mute, size: 16) {
            FxMute.toggle()
        }
    }
}

// MARK: notas

struct NotesPanel: View {
    let close: () -> Void
    @AppStorage("notes") private var notes = ""
    @AppStorage("notesTab") private var tab = 0
    @State private var confirmClear = false

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 6) {
                Pill(text: "NOTAS", active: tab == 0, style: .tab, size: 15) { tab = 0 }.frame(width: 80, height: 38)
                Pill(text: "TEMAS", active: tab == 1, style: .tab, size: 15) { tab = 1 }.frame(width: 80, height: 38)
                Spacer()
                if tab == 0 {
                    Pill(text: "+ MARCA", size: 13) { mark() }.frame(width: 82, height: 38)
                    Pill(text: "BORRAR", style: .outline, size: 12) { confirmClear = true }.frame(width: 70, height: 38)
                }
                Pill(text: "✕", style: .outline, size: 15, action: close).frame(width: 40, height: 38)
            }
            if tab == 0 {
                TextEditor(text: $notes)
                    .font(.system(size: 17))
                    .foregroundColor(C.text)
                    .scrollContentBackground(.hidden)
                    .padding(8)
                    .background(RoundedRectangle(cornerRadius: 12).fill(C.bg))
            } else {
                SongsView()
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 16).fill(C.surface))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(C.line))
        .shadow(color: .black.opacity(0.5), radius: 12)
        .confirmationDialog("¿Borrar todas las notas?", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Borrar", role: .destructive) { notes = "" }
        }
    }

    /// Agrega "[12:34] " con el tiempo del show (o la hora si el cronómetro está en cero).
    private func mark() {
        let t = ShowTimer.elapsed()
        let stamp: String
        if t > 0 {
            stamp = ShowTimer.format(t)
        } else {
            let f = DateFormatter()
            f.dateFormat = "HH:mm"
            stamp = f.string(from: Date())
        }
        if !notes.isEmpty && !notes.hasSuffix("\n") { notes += "\n" }
        notes += "[\(stamp)] "
    }
}

/// Pestaña TEMAS: lista de temas editable, cargable desde .txt / .docx / .doc / .rtf.
struct SongsView: View {
    @ObservedObject private var list = Setlist.shared
    @State private var importing = false
    @State private var editing: Int?        // índice a editar; -1 = nuevo
    @State private var editTitle = ""
    @State private var editNote = ""
    @State private var pending: [Song] = []
    @State private var pendingName = ""
    @State private var confirmReplace = false
    @State private var confirmEmpty = false
    @State private var error = ""

    private static let types: [UTType] = [
        .plainText, .rtf, UTType("org.openxmlformats.wordprocessingml.document") ?? .data,
        UTType("com.microsoft.word.doc") ?? .data, .data
    ]

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                Pill(text: "CARGAR ARCHIVO…", size: 13) { importing = true }.frame(height: 40)
                Pill(text: "+ TEMA", size: 13) { startEdit(-1) }.frame(height: 40)
                Pill(text: "VACIAR", style: .outline, size: 12) { if !list.songs.isEmpty { confirmEmpty = true } }.frame(width: 76, height: 40)
            }
            Text(info).font(bf(11)).foregroundColor(error.isEmpty ? C.dim : C.red).lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            ScrollView {
                VStack(spacing: 6) {
                    ForEach(Array(list.songs.enumerated()), id: \.element.id) { i, s in
                        row(i, s)
                    }
                }
            }
            HStack(spacing: 6) {
                Pill(text: "◀  ANTERIOR", style: .outline, size: 14) { list.step(-1) }.frame(height: 46)
                Pill(text: "SIGUIENTE  ▶", active: true, size: 14) { list.step(1) }.frame(height: 46)
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: SongsView.types) { result in
            guard case .success(let url) = result else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url) else { error = "No se pudo leer el archivo."; return }
            let songs = DocText.extract(data).map { Setlist.parse($0) } ?? []
            if songs.isEmpty { error = "No encontré temas en ese archivo. Probá con un .txt o .docx, un tema por línea."; return }
            error = ""
            pending = songs
            pendingName = url.lastPathComponent
            if list.songs.isEmpty { applyPending() } else { confirmReplace = true }
        }
        .confirmationDialog("¿Reemplazar la lista actual (\(list.songs.count) temas) por los \(pending.count) de \"\(pendingName)\"?",
                            isPresented: $confirmReplace, titleVisibility: .visible) {
            Button("Reemplazar", role: .destructive) { applyPending() }
        }
        .confirmationDialog("¿Vaciar la lista de temas?", isPresented: $confirmEmpty, titleVisibility: .visible) {
            Button("Vaciar", role: .destructive) {
                list.songs = []
                list.current = -1
                list.source = ""
                list.save()
            }
        }
        .alert(editing == -1 ? "Agregar tema" : "Editar tema", isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) {
            TextField("Tema", text: $editTitle)
            TextField("Nota (tono, quién arranca, cambios…)", text: $editNote)
            Button("Guardar") { saveEdit() }
            Button("Cancelar", role: .cancel) { editing = nil }
        }
    }

    private var info: String {
        if !error.isEmpty { return error }
        let n = list.songs.count
        if n == 0 { return "Sin temas. Cargá un .txt / .doc / .docx (un tema por línea) o agregalos a mano." }
        return list.source.isEmpty ? "\(n) temas" : "\(n) temas · \(list.source)"
    }

    private func row(_ i: Int, _ s: Song) -> some View {
        let cur = i == list.current
        return HStack(spacing: 4) {
            Text("\(i + 1)").font(bf(15)).foregroundColor(cur ? C.accent : C.dim).frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(s.title).font(bf(16)).foregroundColor(C.text).lineLimit(2)
                if !s.note.isEmpty {
                    Text(s.note).font(.system(size: 13, weight: .medium)).foregroundColor(C.dim).lineLimit(3)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture {
                list.current = cur ? -1 : i
                list.save()
            }
            Pill(text: "▲", style: .small, size: 13) { list.move(i, -1) }.frame(width: 32, height: 34)
            Pill(text: "▼", style: .small, size: 13) { list.move(i, 1) }.frame(width: 32, height: 34)
            Pill(text: "✎", style: .small, size: 13) { startEdit(i) }.frame(width: 32, height: 34)
            Pill(text: "✕", style: .small, size: 13) { list.remove(i) }.frame(width: 32, height: 34)
        }
        .padding(.vertical, 4)
        .padding(.leading, 6)
        .padding(.trailing, 4)
        .background(RoundedRectangle(cornerRadius: 10).fill(cur ? C.surface2 : C.bg))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(cur ? C.accent : Color.clear))
    }

    private func startEdit(_ i: Int) {
        if i >= 0 && i < list.songs.count {
            editTitle = list.songs[i].title
            editNote = list.songs[i].note
        } else {
            editTitle = ""
            editNote = ""
        }
        editing = i
    }

    private func saveEdit() {
        let t = editTitle.trimmingCharacters(in: .whitespaces)
        defer { editing = nil }
        guard !t.isEmpty, let i = editing else { return }
        let n = editNote.trimmingCharacters(in: .whitespaces)
        if i >= 0 && i < list.songs.count {
            list.songs[i].title = t
            list.songs[i].note = n
        } else {
            list.songs.append(Song(title: t, note: n))
        }
        list.save()
    }

    private func applyPending() {
        list.songs = pending
        list.current = -1
        list.source = pendingName
        list.save()
    }
}

// MARK: conexión

struct ConnectSheet: View {
    @ObservedObject private var core = Core.shared
    @Environment(\.dismiss) private var dismiss
    @State private var ip = UserDefaults.standard.string(forKey: "ip") ?? "192.168.1.1"
    @State private var error = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Conectar a la consola").font(bf(22)).foregroundColor(C.text)
            Text("Consolas encontradas en la red:").font(bf(14)).foregroundColor(C.dim)
            if core.discovered.isEmpty {
                Text("Buscando…").font(bf(15)).foregroundColor(C.dim)
            }
            ForEach(core.discovered) { d in
                Pill(text: "\(d.model)  ·  \(d.name)  ·  \(d.ip)", size: 16) {
                    UserDefaults.standard.set(d.model, forKey: "model")
                    core.applyModel(d.model)
                    _ = core.connect(ip: d.ip, port: d.port)
                    dismiss()
                }
                .frame(height: 50)
            }
            Text("O escribí la IP (XR12 / XR16 / XR18 / X32):").font(bf(14)).foregroundColor(C.dim)
            TextField("192.168.1.1", text: $ip)
                .keyboardType(.numbersAndPunctuation)
                .font(bf(18))
                .foregroundColor(C.text)
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 10).fill(C.bg))
            if !error.isEmpty { Text(error).foregroundColor(C.red).font(bf(14)) }
            HStack {
                Pill(text: "BUSCAR DE NUEVO", style: .outline, size: 15) { core.discovered.removeAll(); core.client.discover() }.frame(width: 180, height: 46)
                Spacer()
                Pill(text: "Cancelar", style: .outline, size: 15) { dismiss() }.frame(width: 120, height: 46)
                Pill(text: "Conectar", active: true, size: 15) {
                    if core.connect(ip: ip, port: 0) { dismiss() } else { error = "IP inválida" }
                }
                .frame(width: 130, height: 46)
            }
            Spacer()
        }
        .padding(24)
        .background(C.panel.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { core.client.discover() }
    }
}

// MARK: escenas

struct ScnDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data, .plainText] }
    var text: String
    init(text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws {
        let d = configuration.file.regularFileContents ?? Data()
        text = String(data: d, encoding: .isoLatin1) ?? ""
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: text.data(using: .isoLatin1, allowLossyConversion: true) ?? Data())
    }
}

struct ScenesView: View {
    @ObservedObject private var scenes = Scenes.shared
    @State private var name = "GYP " + { let f = DateFormatter(); f.dateFormat = "dd-MM"; return f.string(from: Date()) }()
    @State private var notes = ""
    @State private var importing = false
    @State private var exporting = false
    @State private var exportDoc = ScnDocument(text: "")
    @State private var exportName = "escena"
    @State private var pending: Scenes.SceneFile?
    @State private var confirmLoad = false
    @State private var toDelete: URL?

    private func files() -> [URL] {
        _ = scenes.libraryVersion
        let dir = Scenes.libraryURL()
        let list = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
        return list.filter { $0.pathExtension.lowercased() == "scn" }.sorted { a, b in
            let da = (try? a.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            let db = (try? b.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            return da > db
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            VStack(spacing: 14) {
                Card(title: "GUARDAR LA MEZCLA ACTUAL") {
                    TextField("Nombre de la escena", text: $name)
                        .font(bf(17)).foregroundColor(C.text).padding(12)
                        .background(RoundedRectangle(cornerRadius: 10).fill(C.bg))
                    TextField("Nota (opcional): lugar, fecha…", text: $notes)
                        .font(bf(15)).foregroundColor(C.text).padding(12)
                        .background(RoundedRectangle(cornerRadius: 10).fill(C.bg))
                    Pill(text: "LEER CONSOLA Y GUARDAR", active: true, size: 16) { scenes.save(name: name, notes: notes) }
                        .frame(height: 52)
                    Text("Se guarda en el dispositivo, sin internet: app Archivos → En mi iPad/iPhone → AirMix → Escenas. EXPORTAR la copia a otra carpeta. Se abre en X-Air Edit / X32-Edit.")
                        .font(bf(12)).foregroundColor(C.dim)
                }
                Card(title: "ESTADO") {
                    Text(scenes.status).font(bf(15)).foregroundColor(C.text)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)

            Card(title: "ESCENAS EN EL DISPOSITIVO") {
                HStack {
                    Spacer()
                    Pill(text: "ABRIR .scn…", style: .outline, size: 14) { importing = true }.frame(width: 140, height: 40)
                }
                ScrollView {
                    VStack(spacing: 8) {
                        let list = files()
                        if list.isEmpty {
                            Text("Todavía no hay escenas guardadas.").font(bf(15)).foregroundColor(C.dim)
                        }
                        ForEach(list, id: \.self) { url in
                            HStack(spacing: 8) {
                                Text(url.deletingPathExtension().lastPathComponent).font(bf(16)).foregroundColor(C.text).lineLimit(1)
                                Spacer()
                                Pill(text: "CARGAR", active: true, activeColor: C.sends, size: 14) {
                                    let text = (try? Data(contentsOf: url)).flatMap { String(data: $0, encoding: .isoLatin1) } ?? ""
                                    pending = Scenes.parse(text, fallbackName: url.deletingPathExtension().lastPathComponent)
                                    confirmLoad = true
                                }.frame(width: 92, height: 38)
                                Pill(text: "EXPORTAR", style: .outline, size: 12) {
                                    let text = (try? Data(contentsOf: url)).flatMap { String(data: $0, encoding: .isoLatin1) } ?? ""
                                    exportDoc = ScnDocument(text: text)
                                    exportName = url.deletingPathExtension().lastPathComponent
                                    exporting = true
                                }.frame(width: 86, height: 38)
                                Pill(text: "✕", style: .outline, size: 14) { toDelete = url }.frame(width: 40, height: 38)
                            }
                            .padding(.horizontal, 12)
                            .frame(height: 54)
                            .background(RoundedRectangle(cornerRadius: 10).fill(C.surface2))
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(14)
        .background(C.bg)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.item]) { result in
            guard case .success(let url) = result else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url), let text = String(data: data, encoding: .isoLatin1) else { return }
            let scene = Scenes.parse(text, fallbackName: url.deletingPathExtension().lastPathComponent)
            guard !scene.lines.isEmpty else { scenes.status = "Ese archivo no parece una escena .scn"; return }
            try? data.write(to: Scenes.libraryURL().appendingPathComponent(Scenes.safeName(scene.name) + ".scn"))
            scenes.libraryVersion += 1
            pending = scene
            confirmLoad = true
        }
        .fileExporter(isPresented: $exporting, document: exportDoc, contentType: .data, defaultFilename: exportName + ".scn") { _ in }
        .onChange(of: scenes.exportText) { t in
            if let t = t {
                exportDoc = ScnDocument(text: t)
                exportName = scenes.exportName
                exporting = true
                scenes.exportText = nil
            }
        }
        .confirmationDialog(
            "Cargar \"\(pending?.name ?? "")\"",
            isPresented: $confirmLoad,
            titleVisibility: .visible
        ) {
            Button("Cargar en la consola") { if let p = pending { scenes.load(p) } }
        } message: {
            Text("Esto reemplaza la mezcla actual (\(pending?.lines.count ?? 0) bloques). Antes se verifica el formato con la consola; lo que no se pueda verificar queda como está.")
        }
        .confirmationDialog("¿Borrar \(toDelete?.lastPathComponent ?? "")?", isPresented: Binding(get: { toDelete != nil }, set: { if !$0 { toDelete = nil } }), titleVisibility: .visible) {
            Button("Borrar", role: .destructive) {
                if let u = toDelete { try? FileManager.default.removeItem(at: u) }
                toDelete = nil
                scenes.libraryVersion += 1
            }
        }
    }
}
