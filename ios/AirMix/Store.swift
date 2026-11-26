import Foundation
import AudioToolbox
import UIKit
import Combine

/// Un parámetro de la consola. Cada vista observa solo los que usa.
final class Param: ObservableObject {
    let address: String
    @Published var value: OscArg?
    var touched: TimeInterval = 0
    var wanted = false

    init(_ a: String) { address = a }

    var int: Int? { value?.intValue }
    var float: Float? { value?.floatValue }
}

/// Valores por dirección OSC. Solo se usa desde el hilo principal.
final class Store {
    static let shared = Store()
    private var params: [String: Param] = [:]

    /// Parámetro para una vista: si no tiene valor todavía, se pide a la consola.
    func param(_ a: String) -> Param {
        if let p = params[a] {
            if !p.wanted { p.wanted = true; if p.value == nil { ask(p) } }
            else if p.value == nil && Demo.on { ask(p) }
            return p
        }
        let p = Param(a)
        p.wanted = true
        params[a] = p
        ask(p)
        return p
    }

    /// Pide el valor a la consola; en MODO DEMO no hay consola: se usa un valor de ejemplo.
    private func ask(_ p: Param) {
        if Demo.on { p.value = Demo.valueFor(p.address) } else { Core.shared.client.query(p.address) }
    }

    /// MODO DEMO: completa con valores de ejemplo todo lo que está en pantalla y no tiene valor.
    func fillMissing() {
        for p in params.values where p.wanted && p.value == nil { p.value = Demo.valueFor(p.address) }
    }

    func value(_ a: String) -> OscArg? { params[a]?.value }
    func int(_ a: String, def: Int = 0) -> Int { params[a]?.value?.intValue ?? def }
    func float(_ a: String, def: Float = 0) -> Float { params[a]?.value?.floatValue ?? def }

    /// Cambio hecho por el usuario.
    func set(_ a: String, _ v: OscArg) {
        let p = params[a] ?? { let n = Param(a); params[a] = n; return n }()
        p.touched = Date().timeIntervalSince1970
        if p.value != v { p.value = v }
        Core.shared.client.send(a, v)
    }

    /// Valor que llegó de la consola (se ignora si el usuario lo está tocando).
    func incoming(_ a: String, _ v: OscArg) {
        let p = params[a] ?? { let n = Param(a); params[a] = n; return n }()
        if Date().timeIntervalSince1970 - p.touched < 0.5 { return }
        if p.value != v { p.value = v }
    }

    /// Pedido sin crear suscripción (lo usa la verificación de escenas).
    func query(_ a: String) {
        if params[a] == nil { params[a] = Param(a) }
        Core.shared.client.query(a)
    }

    /// Olvida el valor local (para pedirlo de nuevo y verificar qué tiene la consola).
    func forget(_ a: String) {
        attempts[a] = nil
        guard let p = params[a] else { return }
        p.touched = 0
        p.value = nil
    }

    /// Reintento de pedidos sin respuesta (UDP puede perder paquetes). Cada ~1,5 s; hasta 6 intentos por dirección.
    private var attempts: [String: Int] = [:]
    func requeryMissing(max: Int = 48) {
        var n = 0
        for (k, p) in params where p.wanted && p.value == nil {
            let a = attempts[k] ?? 0
            if a >= 6 { continue }
            attempts[k] = a + 1
            Core.shared.client.query(k)
            n += 1
            if n >= max { break }
        }
    }

    func requeryWanted() {
        for (k, p) in params where p.wanted { Core.shared.client.query(k) }
    }

    func resetValues() {
        attempts.removeAll()
        for p in params.values {
            p.value = nil
            p.touched = 0
        }
    }
}

/// Agrupa varios parámetros para vistas que dependen de muchos (gráficos de EQ y dinámica).
final class ParamGroup: ObservableObject {
    let params: [Param]
    private var bag = Set<AnyCancellable>()

    init(_ addrs: [String]) {
        params = addrs.map { Store.shared.param($0) }
        for p in params {
            p.objectWillChange.sink { [weak self] _ in self?.objectWillChange.send() }.store(in: &bag)
        }
    }
}

/// Estado general de la app + tick a 30 fps que procesa lo que llega de la red.
final class Core: ObservableObject {
    static let shared = Core()

    let client = MixerClient()
    @Published private(set) var console = Console.fromModel(UserDefaults.standard.string(forKey: "model") ?? "XR12")
    @Published var connected = false
    @Published var firmware = ""
    @Published var discovered: [MixerInfo] = []
    @Published var layoutVersion = 0
    /// Pares estéreo linkeados (canal impar). Al cambiar se rearman las capas.
    @Published var links: Set<Int> = []
    /// Se cortó la conexión después de haber estado conectados (franja roja + vibración).
    @Published var lost = false
    /// MODO DEMO prendido (simula una XR12, no sale nada por la red).
    @Published private(set) var demo = false
    /// Nombre de la consola (/xinfo).
    @Published private(set) var mixerName = ""
    /// Modo de uso: 0 = CONTROL TOTAL; n ≥ 1 = MÚSICO en el monitor (bus) n. Igual que Mode de Android.
    @Published var musicianBus = 0
    /// Pantalla de inicio: 0 = oculta, 1 = conexión, 2 = elegir modo. Siempre arranca en 1.
    @Published var startPage = 1
    /// Se tocó CONECTAR en la pantalla de inicio y se espera la respuesta de la consola.
    @Published private(set) var waiting = false
    @Published var startError = ""
    private var waitingSince: TimeInterval = 0
    private var discoverTick = 0
    private var everConnected = false
    private var linkTick = 0
    private var watched = Set<String>()

    private var timer: Timer?
    private var requeryTick = 0

    private init() {
        client.x32 = console.isX32
    }

    func start() {
        client.start()
        if let ip = UserDefaults.standard.string(forKey: "ip") {
            _ = client.connect(ip: ip, knownPort: UserDefaults.standard.integer(forKey: "port"))
        }
        timer = Timer.scheduledTimer(withTimeInterval: 1.0 / 30.0, repeats: true) { [weak self] _ in self?.tick() }
    }

    /// Mantiene pedidos (y reintentos) del estado de link de todos los pares.
    private func watchLinks() {
        for p in chLinkPairs() {
            let a = chLinkAddr(p)
            if !watched.contains(a) { watched.insert(a); _ = Store.shared.param(a) }
        }
        // Fuente del RTA (X Air): se conoce de antemano para poder devolverla al salir del EQ.
        if !console.isX32 && !watched.contains(rtaSourceAddr) { watched.insert(rtaSourceAddr); _ = Store.shared.param(rtaSourceAddr) }
        // X32: ruteo de entradas (para saber qué preamp alimenta cada canal).
        if console.isX32 { for a in x32InRouting where !watched.contains(a) { watched.insert(a); _ = Store.shared.param(a) } }
        // Tipos de efecto cargados (para TAP tempo).
        for n in 1...max(1, TapTempo.slots()) {
            let a = "/fx/\(n)/type"
            if !watched.contains(a) { watched.insert(a); _ = Store.shared.param(a) }
        }
    }

    @discardableResult
    func applyModel(_ m: String) -> Bool {
        let n = Console.fromModel(m)
        let changed = !n.sameShape(console)
        console = n
        client.x32 = n.isX32
        if changed { layoutVersion += 1 }
        return changed
    }

    func connect(ip: String, port: Int) -> Bool {
        guard client.connect(ip: ip, knownPort: port) else { return false }
        UserDefaults.standard.set(ip.trimmingCharacters(in: .whitespaces), forKey: "ip")
        UserDefaults.standard.set(port, forKey: "port")
        Store.shared.resetValues()
        connected = false
        return true
    }

    /// Conectar desde la pantalla de inicio: cuando la consola responde, pasa a elegir modo.
    func startConnect(ip: String, port: Int, model: String?) {
        if demo { stopDemo() }
        if let m = model {
            UserDefaults.standard.set(m, forKey: "model")
            applyModel(m)
        }
        if connect(ip: ip, port: port) {
            waiting = true
            waitingSince = Date().timeIntervalSince1970
            startError = ""
        } else {
            startError = "IP inválida"
        }
    }

    /// MODO DEMO: borra lo que había de la consola real y carga la XR12 de ejemplo.
    func startDemo() {
        everConnected = false
        lost = false
        Demo.on = true
        client.demo = true
        demo = true
        musicianBus = 0
        Store.shared.resetValues()
        applyModel(Demo.model)
        layoutVersion += 1
        Demo.seed()
        Store.shared.fillMissing()
    }

    /// Sale del MODO DEMO: se olvidan los valores de ejemplo y se vuelve a pedir todo a la consola (si hay).
    func stopDemo() {
        Demo.on = false
        client.demo = false
        demo = false
        client.clearMeters()
        for i in 0..<MixerClient.rtaBins { client.rta[i] = -100 }
        Clip.reset()
        Store.shared.resetValues()
        applyModel(UserDefaults.standard.string(forKey: "model") ?? "XR12")
        layoutVersion += 1
        connected = false
        Store.shared.requeryWanted()
        musicianBus = 0
        startPage = 1
    }

    private func tick() {
        for m in client.drainInbox() {
            if m.address == "node" || m.address == "/node" {
                Scenes.shared.onNode(m.args.first?.stringValue ?? "")
                continue
            }
            if m.address == "/xinfo" {
                // La calidad del wifi pide /xinfo cada segundo: si no cambió nada, no se toca nada (ni se redibuja).
                if m.args.count >= 2 && m.args[1].stringValue != mixerName { mixerName = m.args[1].stringValue }
                if m.args.count >= 4 && (m.args[3].stringValue != firmware || m.args[2].stringValue != console.model) {
                    firmware = m.args[3].stringValue
                    UserDefaults.standard.set(m.args[2].stringValue, forKey: "model")
                    applyModel(m.args[2].stringValue)
                }
                continue
            }
            if let a = m.args.first { Store.shared.incoming(m.address, a) }
        }
        let f = client.drainFound()
        if !f.isEmpty {
            for i in f where !discovered.contains(where: { $0.ip == i.ip }) { discovered.append(i) }
        }
        linkTick += 1
        if linkTick >= 15 {
            linkTick = 0
            watchLinks()
            let now = linkedPairs()
            if now != links { links = now }
        }
        if Room.shared.running { Room.shared.tick(Date().timeIntervalSince1970) }
        let c = client.isConnected()
        // Pantalla de inicio: busca consolas cada 3 s y, si se tocó CONECTAR, espera la respuesta (6 s como mucho).
        if startPage == 1 {
            discoverTick += 1
            if discoverTick >= 90 { discoverTick = 0; client.discover() }
        }
        if waiting {
            if c {
                waiting = false
                if startPage == 1 { startPage = 2 }
            } else if Date().timeIntervalSince1970 - waitingSince > 6 {
                waiting = false
                startError = "No responde. ¿El dispositivo está en el wifi de la consola?"
            }
        }
        if demo {
            // MODO DEMO: señal simulada en los medidores (y en el RTA si hay un EQ abierto).
            let now = Date().timeIntervalSince1970
            Demo.tick(client.meters, client.rta, rtaOn: client.rtaWanted, now: now)
            Clip.scan(client.meters, now: now)
        } else if c { Clip.scan(client.meters, now: Date().timeIntervalSince1970) }
        if c {
            requeryTick += 1
            if requeryTick >= 45 { requeryTick = 0; Store.shared.requeryMissing() }
        }
        if c != connected {
            connected = c
            if c {
                if client.port != 0 { UserDefaults.standard.set(client.port, forKey: "port") }
                Store.shared.requeryWanted()
            } else {
                client.clearMeters()
                Clip.reset()
            }
            let shouldShow = !c && everConnected && !demo
            if shouldShow && !lost {
                // Vibra (en iPhone; el iPad no tiene motor de vibración) y avisa.
                UINotificationFeedbackGenerator().notificationOccurred(.error)
                AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
            }
            lost = shouldShow
            if c { everConnected = true }
        }
    }
}
