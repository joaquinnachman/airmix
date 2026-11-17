import SwiftUI
import AVFoundation

/// SALA: mapa de riesgo de acople sin conectar nada (mismo algoritmo y constantes que Room.kt).
///
/// Con ruido rosa sonando (parlante del iPad/iPhone, apoyado cerca de los micrófonos) la app recorre la fuente del RTA
/// (/-stat/rta/source) por cada micrófono elegido y promedia ~2 s. Mide dos pasadas: al nivel actual de la salida
/// y con la salida +3 dB. Donde el lazo parlante → micrófono está cerca de acoplar, esa banda crece bastante más
/// que el resto. Al terminar (o al cancelar, salir de SALA o perder la conexión) todo vuelve como estaba.
final class Room: ObservableObject {
    static let shared = Room()

    static let settle = 0.8
    static let measure = 2.2
    static let boostDb: Float = 3
    static let floorDb: Float = -75
    static let midDb: Float = 2
    static let highDb: Float = 4
    static let peakDb: Float = 10
    static let abortDb: Float = 12
    static let abortLevel: Float = -30
    static let loBin = 16
    static let hiBin = 91
    static let bins = MixerClient.rtaBins

    struct Alert: Identifiable {
        let id = UUID()
        let hz: Float
        let high: Bool
        let chName: String
        let db: Float
        let note: String
        var text: String {
            (high ? "Riesgo ALTO" : "Riesgo medio") + " en \(Fmt.hz(hz))" +
                (chName.isEmpty ? "" : " (sobre todo en \(chName))") + (note.isEmpty ? "" : " · \(note)")
        }
    }

    struct Result {
        let outBus: Int
        let outName: String
        let score: [Float]
        let spectrum: [Float]
        let alerts: [Alert]
        let aborted: Bool
    }

    /// Canales elegidos como micrófonos (índice = canal - 1). userPicked: ya no se tildan solos.
    @Published var mics = [Bool](repeating: false, count: 32)
    var userPicked = false
    /// Salida a probar: 0 = MAIN LR, 1.. = bus de monitor.
    @Published var outBus = 0
    @Published private(set) var running = false
    @Published private(set) var status = ""
    @Published private(set) var result: Result?

    private var chans: [Int] = []
    private var names: [String] = []
    private var pass = 0
    private var step = 0
    private var stepStart: TimeInterval = 0
    private var sum = [Double](repeating: 0, count: Room.bins)
    private var count = 0
    private var p0: [[Float]] = []
    private var p1: [[Float]] = []
    private var prevSrc: Int?
    private var faderAddr = ""
    private var faderOrig: Float = 0
    private var boosted = false

    func outStrip(_ bus: Int? = nil) -> Strip {
        let b = bus ?? outBus
        return b > 0 ? Strip(kind: .bus, idx: b) : Strip(kind: .lr)
    }

    func outName(_ bus: Int) -> String {
        let n = Store.shared.value(outStrip(bus).nameAddr)?.stringValue ?? ""
        return bus > 0 ? SendTarget(bus: bus).display(n) : "MAIN LR"
    }

    func chName(_ ch: Int) -> String {
        let s = Strip(kind: .ch, idx: ch)
        let n = (Store.shared.value(s.nameAddr)?.stringValue ?? "").trimmingCharacters(in: .whitespaces)
        return n.isEmpty ? s.title : n
    }

    /// Arranca la medición. Devuelve un mensaje de error (o nil si arrancó).
    func start() -> String? {
        if running { return nil }
        let core = Core.shared
        if !core.client.isConnected() { return "Conectate a la consola primero." }
        if core.console.isX32 { return "SALA por ahora es solo para X Air (XR12 / XR16 / XR18): en X32 la fuente del RTA no está verificada." }
        let list = (1...core.console.channels).filter { mics[$0 - 1] }
        if list.isEmpty { return "Elegí al menos un micrófono." }
        let out = outStrip()
        faderAddr = out.faderAddr
        guard let f = Store.shared.value(faderAddr)?.floatValue else { return "Todavía no llegó el nivel de \(outName(outBus)): probá de nuevo en un segundo." }
        if f <= 0 { return "\(outName(outBus)) está en -∞: subilo a su nivel de show." }
        if Store.shared.int(out.onAddr, def: 1) == 0 { return "\(outName(outBus)) está muteado: desmutealo a volumen moderado." }
        if Fmt.faderToDb(f) > 10 - 1 { return "\(outName(outBus)) está casi al máximo: no hay lugar para la prueba de +3 dB." }
        faderOrig = f
        boosted = false
        chans = list
        names = list.map { chName($0) }
        p0 = Array(repeating: [Float](repeating: -100, count: Room.bins), count: list.count)
        p1 = p0
        prevSrc = Store.shared.value(rtaSourceAddr)?.intValue
        pass = 0
        step = 0
        running = true
        PinkNoise.shared.start()
        core.client.setRta(true)
        beginStep(Date().timeIntervalSince1970)
        return nil
    }

    func cancel(_ why: String) {
        guard running else { return }
        restore()
        status = why
    }

    private func restore() {
        running = false
        if boosted {
            Store.shared.set(faderAddr, .f(faderOrig))
            boosted = false
        }
        if let prev = prevSrc { Store.shared.set(rtaSourceAddr, .i(Int32(prev))) }
        Core.shared.client.setRta(false)
        PinkNoise.shared.stop()
    }

    private func beginStep(_ now: TimeInterval) {
        Store.shared.set(rtaSourceAddr, .i(Int32(chans[step] - 1)))
        stepStart = now
        for i in 0..<Room.bins { sum[i] = 0 }
        count = 0
        status = (pass == 0 ? "Midiendo al nivel actual" : "Midiendo con \(outName(outBus)) +3 dB") +
            " · \(names[step]) (\(step + 1)/\(chans.count))"
    }

    /// Lo llama el tick de 30 fps de Core.
    func tick(_ now: TimeInterval) {
        guard running else { return }
        let c = Core.shared.client
        if !c.isConnected() {
            cancel("Se cortó la conexión: la medición se canceló y quedó todo como estaba.")
            return
        }
        let el = now - stepStart
        if el < Room.settle { return }
        let m = c.rta
        if m[0] <= -99.5 && m[Room.bins / 2] <= -99.5 { return } // todavía no llegó el RTA
        if pass == 1 {
            for i in Room.loBin...Room.hiBin where m[i] > Room.abortLevel && m[i] > p0[step][i] + Room.abortDb {
                hot(i)
                return
            }
        }
        for i in 0..<Room.bins { sum[i] += pow(10, Double(m[i]) / 10) }
        count += 1
        if el < Room.settle + Room.measure { return }
        var avg = [Float](repeating: -100, count: Room.bins)
        if count > 0 { for i in 0..<Room.bins { avg[i] = Float(10 * log10(sum[i] / Double(count) + 1e-12)) } }
        if pass == 0 { p0[step] = avg } else { p1[step] = avg }
        step += 1
        if step < chans.count {
            beginStep(now)
            return
        }
        if pass == 0 {
            pass = 1
            step = 0
            let db = Fmt.faderToDb(faderOrig) + Room.boostDb
            Store.shared.set(faderAddr, .f(min(max(Fmt.dbToFader(db), 0), 1)))
            boosted = true
            beginStep(now)
            return
        }
        restore()
        result = analyze(aborted: false, hotBin: -1)
        status = "Listo. Todo volvió como estaba."
    }

    /// Una banda se disparó con +3 dB: se corta enseguida (casi acopla) y se informa.
    private func hot(_ bin: Int) {
        let ch = step
        restore()
        result = analyze(aborted: true, hotBin: bin, hotCh: ch)
        status = "Medición cortada: con +3 dB casi acopla en \(Fmt.hz(Room.binHz(bin))). Todo volvió como estaba."
    }

    static func binHz(_ i: Int) -> Float { Float(20 * pow(1000, Double(i) / Double(bins - 1))) }

    /// "2.5k" / "250" para el gráfico.
    static func shortHz(_ hz: Float) -> String { hz >= 1000 ? String(format: "%.1fk", hz / 1000) : String(format: "%.0f", hz) }

    private func analyze(aborted: Bool, hotBin: Int, hotCh: Int = -1) -> Result {
        let n = chans.count
        let bins = Room.bins
        let done = aborted ? (pass == 1 ? step : 0) : n
        var score = [Float](repeating: -100, count: bins)
        var worst = [Int](repeating: -1, count: bins)
        for k in 0..<done {
            var g: [Float] = []
            for i in Room.loBin...Room.hiBin where p0[k][i] > Room.floorDb { g.append(p1[k][i] - p0[k][i]) }
            if g.count < 10 { continue }
            g.sort()
            let med = g[g.count / 2]
            var grow = [Float](repeating: 0, count: bins)
            for i in 0..<bins where p0[k][i] > Room.floorDb { grow[i] = p1[k][i] - p0[k][i] - med }
            for i in Room.loBin...Room.hiBin {
                let e = (grow[i - 1] + grow[i] + grow[i + 1]) / 3
                if e > score[i] { score[i] = e; worst[i] = k }
            }
        }
        // Espectro promedio (potencia) de la pasada normal y picos sobre las vecinas.
        var spec = [Float](repeating: -100, count: bins)
        if n > 0 {
            for i in 0..<bins {
                var s = 0.0
                for k in 0..<n { s += pow(10, Double(p0[k][i]) / 10) }
                spec[i] = Float(10 * log10(s / Double(n) + 1e-12))
            }
        }
        var risk = [Float](repeating: 0, count: bins)
        var peakAt = [Bool](repeating: false, count: bins)
        for i in Room.loBin...Room.hiBin {
            var around: Float = 0
            for d in 3...6 { around += spec[i - d] + spec[i + d] }
            around /= 8
            let peak = spec[i] > Room.floorDb && spec[i] - around >= Room.peakDb
            peakAt[i] = peak
            risk[i] = max(score[i], peak ? Room.midDb : 0)
        }
        var alerts: [Alert] = []
        var used = [Bool](repeating: false, count: bins)
        if hotBin >= 0 {
            alerts.append(Alert(hz: Room.binHz(hotBin), high: true, chName: hotCh >= 0 ? names[hotCh] : "", db: Room.abortDb, note: "casi acopla con +3 dB"))
            for d in -3...3 where hotBin + d >= 0 && hotBin + d < bins { used[hotBin + d] = true }
        }
        while alerts.count < 5 {
            var best = -1
            for i in Room.loBin...Room.hiBin where !used[i] && risk[i] >= Room.midDb && (best < 0 || risk[i] > risk[best]) { best = i }
            if best < 0 { break }
            for d in -3...3 where best + d >= 0 && best + d < bins { used[best + d] = true }
            let high = score[best] >= Room.highDb
            let who = worst[best]
            let note = peakAt[best] && score[best] < Room.midDb ? "pico en la sala" : ""
            alerts.append(Alert(hz: Room.binHz(best), high: high, chName: who >= 0 && score[best] >= Room.midDb ? names[who] : "",
                                db: max(score[best], 0), note: note))
        }
        return Result(outBus: outBus, outName: outName(outBus), score: score, spectrum: spec, alerts: alerts, aborted: aborted)
    }

    /// Para las capturas: un resultado de ejemplo.
    func demo() {
        var score = (0..<Room.bins).map { (i: Int) -> Float in Float(0.4 * sin(Double(i) / 5)) }
        var spec = [Float](repeating: 0, count: Room.bins)
        for i in 0..<Room.bins {
            let d = Double(i)
            let wave: Double = 3 * sin(d / 7)
            spec[i] = Float(-38 - d * 0.12 + wave)
        }
        score[73] = 5.6; score[72] = 3.9; score[74] = 3.5
        score[55] = 2.7; score[56] = 2.1
        spec[38] += 12
        result = Result(outBus: 0, outName: "MAIN LR", score: score, spectrum: spec, alerts: [
            Alert(hz: Room.binHz(73), high: true, chName: "VOZ GARKO", db: 5.6, note: ""),
            Alert(hz: Room.binHz(55), high: false, chName: "VOZ PALMI", db: 2.7, note: ""),
            Alert(hz: Room.binHz(38), high: false, chName: "", db: 0, note: "pico en la sala"),
        ], aborted: false)
        status = "Listo. Todo volvió como estaba."
        mics[0] = true; mics[1] = true; mics[2] = true
        userPicked = true
    }
}

/// Ruido rosa por el parlante (filtro de Paul Kellet sobre ruido blanco). Igual que PinkNoise en Android.
final class PinkNoise: ObservableObject {
    static let shared = PinkNoise()
    /// Nivel de salida (0..1): moderado; el volumen final lo pone el equipo.
    static let level: Float = 0.3
    @Published private(set) var on = false

    private var engine: AVAudioEngine?
    private var b = [Float](repeating: 0, count: 7)
    private var gain: Float = 0
    private var target: Float = 0

    func start() {
        if on { return }
        on = true
        target = 1
        if engine != nil { return } // todavía se estaba apagando: vuelve a subir
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        let e = AVAudioEngine()
        let sr = e.outputNode.outputFormat(forBus: 0).sampleRate
        guard let fmt = AVAudioFormat(standardFormatWithSampleRate: sr > 0 ? sr : 48000, channels: 1) else { on = false; return }
        let step = Float(1 / ((sr > 0 ? sr : 48000) * 0.3)) // fundido de 0,3 s (sin golpes)
        let src = AVAudioSourceNode(format: fmt) { [unowned self] _, _, frames, abl -> OSStatus in
            let buffers = UnsafeMutableAudioBufferListPointer(abl)
            guard let out = buffers.first?.mData?.assumingMemoryBound(to: Float.self) else { return noErr }
            for j in 0..<Int(frames) {
                let w = Float.random(in: -1...1)
                self.b[0] = 0.99886 * self.b[0] + w * 0.0555179
                self.b[1] = 0.99332 * self.b[1] + w * 0.0750759
                self.b[2] = 0.96900 * self.b[2] + w * 0.1538520
                self.b[3] = 0.86650 * self.b[3] + w * 0.3104856
                self.b[4] = 0.55000 * self.b[4] + w * 0.5329522
                self.b[5] = -0.7616 * self.b[5] - w * 0.0168980
                let s1: Float = self.b[0] + self.b[1] + self.b[2]
                let s2: Float = self.b[3] + self.b[4] + self.b[5]
                let pink: Float = (s1 + s2 + self.b[6] + w * 0.5362) * 0.11
                self.b[6] = w * 0.115926
                self.gain = self.gain < self.target ? min(self.gain + step, self.target) : max(self.gain - step, self.target)
                out[j] = pink * self.gain * PinkNoise.level
            }
            return noErr
        }
        e.attach(src)
        e.connect(src, to: e.mainMixerNode, format: fmt)
        do {
            try e.start()
            engine = e
        } catch {
            on = false
        }
    }

    func stop() {
        guard on else { return }
        on = false
        target = 0
        // Se apaga después del fundido.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { [weak self] in
            guard let self = self, !self.on else { return }
            self.engine?.stop()
            self.engine = nil
            self.gain = 0
        }
    }
}

/// Pantalla SALA (igual que RoomScreen en Android): micrófonos, salida, ruido rosa, MEDIR y el mapa de riesgo.
struct RoomView: View {
    let small: Bool
    @ObservedObject private var room = Room.shared
    @ObservedObject private var noise = PinkNoise.shared
    @ObservedObject private var core = Core.shared
    @State private var error: String?
    @State private var cutAlert: Room.Alert?
    @State private var cutMsg: String?

    var body: some View {
        HStack(alignment: .top, spacing: small ? 8 : 14) {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: small ? 6 : 8) {
                    Text("Usalo a volumen moderado, con los micrófonos en su lugar de show. No hace falta conectar nada. Al terminar, todo vuelve como estaba.")
                        .font(bf(small ? 11 : 13)).foregroundColor(C.yellow).fixedSize(horizontal: false, vertical: true)
                    step(title: "1 · MICRÓFONOS") {
                        let n = core.console.channels
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 3), spacing: 4) {
                            ForEach(1...n, id: \.self) { ch in
                                MicChip(ch: ch, small: small)
                            }
                        }
                    }
                    step(title: "2 · SALIDA A PROBAR") {
                        HStack(spacing: 4) {
                            ForEach(0...core.console.buses, id: \.self) { bus in
                                OutChip(bus: bus, small: small)
                            }
                        }
                    }
                    step(title: "3 · RUIDO ROSA") {
                        Text("Sale por el parlante del iPad/iPhone: apoyalo cerca de los micrófonos y subí el volumen hasta que se escuche claro, sin exagerar.")
                            .font(bf(small ? 10 : 12)).foregroundColor(C.dim).fixedSize(horizontal: false, vertical: true)
                        Pill(text: "PROBAR RUIDO", active: noise.on, style: .outline, size: small ? 12 : 14) {
                            if room.running { return }
                            if noise.on { noise.stop() } else { noise.start() }
                        }.frame(height: small ? 32 : 40)
                    }
                    Pill(text: room.running ? "CANCELAR" : "MEDIR", active: !room.running, activeColor: C.green, size: small ? 15 : 18) {
                        if room.running { room.cancel("Cancelada: todo volvió como estaba.") }
                        else { error = room.start() }
                    }.frame(height: small ? 42 : 54)
                }
            }
            .frame(width: small ? 260 : 340)
            VStack(alignment: .leading, spacing: 6) {
                Text(statusText).font(bf(small ? 11 : 14)).foregroundColor(room.running ? C.sends : C.text)
                    .lineLimit(2).minimumScaleFactor(0.7).frame(height: small ? 30 : 40, alignment: .leading)
                RiskGraph(result: room.result, running: room.running)
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 4) {
                        if let res = room.result, !room.running {
                            if res.alerts.isEmpty {
                                Text("✓ Sin riesgos marcados en \(res.outName).").font(bf(small ? 12 : 14)).foregroundColor(C.green)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            ForEach(res.alerts) { a in
                                HStack(spacing: 6) {
                                    Text(a.text).font(bf(small ? 11 : 14)).foregroundColor(a.high ? C.red : C.yellow)
                                        .lineLimit(2).minimumScaleFactor(0.7).frame(maxWidth: .infinity, alignment: .leading)
                                    Pill(text: "✂ CORTAR", style: .outline, size: small ? 11 : 13) { cutAlert = a }
                                        .frame(width: small ? 84 : 110, height: small ? 30 : 38)
                                }
                                .frame(height: small ? 34 : 44)
                            }
                        }
                    }
                }
                .frame(height: small ? 96 : 170)
            }
            .padding(.horizontal, small ? 8 : 14)
            .padding(.vertical, small ? 6 : 10)
            .background(RoundedRectangle(cornerRadius: 14).fill(C.surface))
        }
        .padding(.horizontal, small ? 8 : 14)
        .padding(.vertical, small ? 6 : 10)
        .onDisappear {
            // Salir de SALA: nada queda sonando ni cambiado.
            room.cancel("Cancelada al salir de SALA: todo volvió como estaba.")
            noise.stop()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
            room.cancel("Cancelada: la app pasó a segundo plano. Todo volvió como estaba.")
            noise.stop()
        }
        .alert(item: Binding(get: { error.map { MsgItem(text: $0) } }, set: { if $0 == nil { error = nil } })) { m in
            Alert(title: Text(m.text))
        }
        .confirmationDialog(cutAlert.map { "¿Cortar \(Fmt.hz($0.hz)) en el EQ de \(room.result?.outName ?? "")?" } ?? "",
                            isPresented: Binding(get: { cutAlert != nil }, set: { if !$0 { cutAlert = nil } }), titleVisibility: .visible) {
            Button("OK") {
                if let a = cutAlert, let res = room.result {
                    let b = EqPanel.cutFeedback(room.outStrip(res.outBus).prefix, bands: 6, hz: a.hz)
                    cutMsg = b < 0 ? "No hay una banda libre en el EQ de \(res.outName): liberá una (ganancia en 0 dB)."
                        : "Corte en \(Fmt.hz(a.hz)) en \(res.outName) (banda \(b))."
                }
                cutAlert = nil
            }
            Button("Cancelar", role: .cancel) { cutAlert = nil }
        } message: {
            Text("PEQ -6 dB, Q 8 en una banda libre (si ya hay un corte cerca, lo baja 3 dB más).")
        }
        .overlay(alignment: .bottom) {
            if let m = cutMsg {
                Text(m).font(bf(13)).foregroundColor(C.text).padding(10)
                    .background(RoundedRectangle(cornerRadius: 10).fill(C.surface2))
                    .padding(.bottom, 20)
                    .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 3) { cutMsg = nil } }
            }
        }
    }

    private var statusText: String {
        if !room.status.isEmpty { return room.status }
        return room.result == nil ? "Elegí micrófonos y salida, poné el ruido rosa y tocá MEDIR (tarda ~5 s por micrófono)." : ""
    }

    private func step<Content: View>(title: String, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: small ? 4 : 6) {
            Text(title).font(bf(small ? 11 : 12)).kerning(1.4).foregroundColor(C.dim)
            content()
        }
        .padding(small ? 10 : 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(C.surface))
    }
}

private struct MsgItem: Identifiable {
    let text: String
    var id: String { text }
}

/// Ficha de un canal para elegirlo como micrófono. Tildada sola si tiene señal o 48V (hasta que elijas a mano).
private struct MicChip: View {
    let ch: Int
    let small: Bool
    @ObservedObject private var room = Room.shared
    @ObservedObject private var name: Param
    @ObservedObject private var src: Param
    @ObservedObject private var phantom: Param

    init(ch: Int, small: Bool) {
        self.ch = ch
        self.small = small
        let s = Strip(kind: .ch, idx: ch)
        _name = ObservedObject(wrappedValue: Store.shared.param(s.nameAddr))
        _src = ObservedObject(wrappedValue: Store.shared.param(s.sourceAddr))
        let ha = s.headampFor(Store.shared.int(s.sourceAddr, def: ch - 1)) ?? "/headamp/\(Fmt.two(min(ch, 16)))"
        _phantom = ObservedObject(wrappedValue: Store.shared.param("\(ha)/phantom"))
    }

    var body: some View {
        let s = Strip(kind: .ch, idx: ch)
        let n = (name.value?.stringValue ?? "").trimmingCharacters(in: .whitespaces)
        Pill(text: n.isEmpty ? s.title : n, active: room.mics[ch - 1], activeColor: Sector.of(.ch), size: small ? 11 : 13) {
            if room.running { return }
            room.userPicked = true
            room.mics[ch - 1].toggle()
        }
        .frame(height: small ? 32 : 40)
        .onAppear { autoPick() }
        .onChange(of: phantom.int) { _ in autoPick() }
        .onChange(of: src.int) { _ in autoPick() }
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { autoPick() } }
    }

    private func autoPick() {
        if room.userPicked || room.running { return }
        let s = Strip(kind: .ch, idx: ch)
        let ha = s.headampFor(src.int ?? (ch - 1))
        let ph = ha != nil && Store.shared.int("\(ha!)/phantom", def: 0) != 0
        let v = Clip.signal(s.meterL, Date().timeIntervalSince1970) || ph
        if room.mics[ch - 1] != v { room.mics[ch - 1] = v }
    }
}

/// Botón de salida (MAIN o un bus con su nombre).
private struct OutChip: View {
    let bus: Int
    let small: Bool
    @ObservedObject private var room = Room.shared
    @ObservedObject private var name: Param

    init(bus: Int, small: Bool) {
        self.bus = bus
        self.small = small
        _name = ObservedObject(wrappedValue: bus > 0 ? Store.shared.param(Strip(kind: .bus, idx: bus).nameAddr) : Param("-"))
    }

    var body: some View {
        Pill(text: bus == 0 ? "MAIN" : SendTarget(bus: bus).display(name.value?.stringValue ?? ""), active: room.outBus == bus,
             activeColor: bus == 0 ? Sector.of(.lr) : C.sends, size: small ? 11 : 13) {
            if !room.running { room.outBus = bus }
        }
        .frame(height: small ? 32 : 40)
    }
}

/// Mapa de riesgo: barras por banda (rojo = alto, ámbar = medio) sobre el espectro promedio de los micrófonos.
private struct RiskGraph: View {
    let result: Room.Result?
    let running: Bool

    var body: some View {
        Canvas { ctx, size in
            let l: CGFloat = 4, t: CGFloat = 4
            let w = size.width - 8
            let h = max(size.height - 22, 1)
            ctx.fill(Path(CGRect(x: l, y: t, width: w, height: h)), with: .color(C.surface2))
            for (hz, lbl) in [(Float(100), "100"), (1000, "1k"), (10000, "10k")] {
                let gx = l + CGFloat(Fmt.logInv(20, 20000, hz)) * w
                ctx.fill(Path(CGRect(x: gx - 0.5, y: t, width: 1, height: h)), with: .color(C.line))
                ctx.draw(Text(lbl).font(bf(10)).foregroundColor(C.dim), at: CGPoint(x: gx, y: t + h + 10))
            }
            guard let res = result else {
                ctx.draw(Text(running ? "Midiendo…" : "Todavía no mediste esta sala").font(bf(14)).foregroundColor(C.dim), at: CGPoint(x: l + w / 2, y: t + h / 2))
                return
            }
            let bins = Room.bins
            func x(_ i: Int) -> CGFloat { l + w * CGFloat(i) / CGFloat(bins - 1) }
            var path = Path()
            for i in 0..<bins {
                let y = t + h * (1 - CGFloat(min(max((res.spectrum[i] + 90) / 80, 0), 1)))
                if i == 0 { path.move(to: CGPoint(x: x(i), y: y)) } else { path.addLine(to: CGPoint(x: x(i), y: y)) }
            }
            ctx.stroke(path, with: .color(C.dim), lineWidth: 1.5)
            let bw = max(w / CGFloat(bins) - 1, 1)
            for i in Room.loBin...Room.hiBin {
                let s = res.score[i]
                if s <= 0.3 { continue }
                let bh = h * CGFloat(min(max(s / 8, 0), 1))
                let col = s >= Room.highDb ? C.red : (s >= Room.midDb ? C.yellow : C.line)
                ctx.fill(Path(CGRect(x: x(i) - bw / 2, y: t + h - bh, width: bw, height: bh)), with: .color(col))
            }
            for a in res.alerts {
                let ax = l + CGFloat(Fmt.logInv(20, 20000, a.hz)) * w
                let col = a.high ? C.red : C.yellow
                ctx.fill(Path(CGRect(x: ax - 1, y: t, width: 2, height: h)), with: .color(col))
                let right = ax > l + w * 0.85
                ctx.draw(Text(Room.shortHz(a.hz)).font(bf(11)).foregroundColor(col),
                         at: CGPoint(x: right ? ax - 4 : ax + 4, y: t + 10), anchor: right ? .trailing : .leading)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
