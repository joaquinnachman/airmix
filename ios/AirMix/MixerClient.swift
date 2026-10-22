import Foundation
import Darwin

struct MixerInfo: Identifiable, Equatable {
    var id: String { ip }
    let ip: String
    let port: Int
    let name: String
    let model: String
    let firmware: String
}

/// Conexión UDP con la consola. Mismo diseño que MixerClient.kt:
/// hilo RX (medidores sin asignar memoria), hilo TX con coalescencia, hilo keepalive.
final class MixerClient {
    static let portXAir = 10024
    static let portX32 = 10023
    static let meterCount = 96
    static let rtaBins = 100

    private var fd: Int32 = -1
    private let lock = NSLock()
    private var running = false

    private var _host: String?
    private var _port = 0
    private var _lastRx: TimeInterval = 0
    private var lastKeep: TimeInterval = 0
    private var lastReopen: TimeInterval = 0

    var x32 = false

    /// RTA de la consola: 100 bandas de 20 Hz a 20 kHz en dB (X Air /meters/4, X32 /meters/15).
    let rta: UnsafeMutablePointer<Float> = {
        let p = UnsafeMutablePointer<Float>.allocate(capacity: MixerClient.rtaBins)
        p.initialize(repeating: -100, count: MixerClient.rtaBins)
        return p
    }()
    /// Solo se pide el RTA mientras hay un EQ en pantalla.
    private(set) var rtaWanted = false

    /// dB por medidor. Lo escribe el hilo RX y lo lee la UI (lectura sin bloqueo, a propósito).
    let meters: UnsafeMutablePointer<Float> = {
        let p = UnsafeMutablePointer<Float>.allocate(capacity: MixerClient.meterCount)
        p.initialize(repeating: -100, count: MixerClient.meterCount)
        return p
    }()

    private var pending: [String: Data] = [:]
    private var order: [String] = []
    private let txSignal = DispatchSemaphore(value: 0)

    private var inbox: [OscMessage] = []
    private var found: [MixerInfo] = []

    private let xremote = Osc.encode("/xremote")
    private let metersXair = Osc.encode("/meters", [.s("/meters/1")])
    private let metersX32a = Osc.encode("/meters", [.s("/meters/0")])
    private let metersX32b = Osc.encode("/meters", [.s("/meters/2")])
    private let rtaXair = Osc.encode("/meters", [.s("/meters/4")])
    private let rtaX32 = Osc.encode("/meters", [.s("/meters/15")])
    private let xinfo = Osc.encode("/xinfo")
    private let meterPrefix: [UInt8] = Array("/meters/".utf8)

    var host: String? { lock.lock(); defer { lock.unlock() }; return _host }
    var port: Int { lock.lock(); defer { lock.unlock() }; return _port }
    var lastRx: TimeInterval { lock.lock(); defer { lock.unlock() }; return _lastRx }

    func isConnected() -> Bool {
        host != nil && Date().timeIntervalSince1970 - lastRx < 4
    }

    // MARK: ciclo de vida

    func start() {
        if running { return }
        running = true
        openSocket()
        spawn("osc-rx") { [weak self] in self?.rxLoop() }
        spawn("osc-tx") { [weak self] in self?.txLoop() }
        spawn("osc-keepalive") { [weak self] in self?.keepAliveLoop() }
    }

    func connect(ip: String, knownPort: Int = 0) -> Bool {
        let parts = ip.trimmingCharacters(in: .whitespaces).split(separator: ".")
        guard parts.count == 4, parts.allSatisfy({ Int($0).map { (0...255).contains($0) } ?? false }) else { return false }
        lock.lock()
        _host = ip.trimmingCharacters(in: .whitespaces)
        _port = knownPort
        _lastRx = 0
        lastKeep = 0
        pending.removeAll()
        order.removeAll()
        lock.unlock()
        return true
    }

    func send(_ address: String, _ value: OscArg) { enqueue(address, Osc.encode(address, [value])) }
    /// Prende/apaga el RTA. Al prender se suscribe enseguida (y el keepalive lo renueva).
    func setRta(_ on: Bool) {
        rtaWanted = on
        for i in 0..<MixerClient.rtaBins { rta[i] = -100 }
        if on { enqueue("sub:rta", x32 ? rtaX32 : rtaXair) }
    }

    func query(_ address: String) { enqueue(address + "?", Osc.encode(address)) }
    /// Pide un bloque completo en texto (escenas .scn).
    func node(_ path: String) { enqueue("node:" + path, Osc.encode("/node", [.s(path)])) }

    private func enqueue(_ key: String, _ data: Data) {
        lock.lock()
        let isNew = pending[key] == nil
        pending[key] = data
        if isNew { order.append(key) }
        lock.unlock()
        if isNew { txSignal.signal() }
    }

    func drainInbox() -> [OscMessage] {
        lock.lock(); defer { lock.unlock() }
        let r = inbox
        inbox.removeAll(keepingCapacity: true)
        return r
    }

    func drainFound() -> [MixerInfo] {
        lock.lock(); defer { lock.unlock() }
        let r = found
        found.removeAll()
        return r
    }

    func clearMeters() {
        for i in 0..<MixerClient.meterCount { meters[i] = -100 }
    }

    // MARK: búsqueda

    /// iOS no permite broadcast sin un permiso especial de Apple: barremos la subred /24 con /xinfo.
    func discover() {
        spawn("osc-discover") { [weak self] in
            guard let self = self else { return }
            var targets: [String] = ["192.168.1.1", "192.168.0.1"]
            if let own = MixerClient.localIPv4() {
                let p = own.split(separator: ".")
                if p.count == 4 {
                    let base = "\(p[0]).\(p[1]).\(p[2])."
                    for i in 1...254 { targets.append(base + String(i)) }
                }
            }
            for t in targets {
                self.rawSend(self.xinfo, ip: t, port: MixerClient.portXAir)
                self.rawSend(self.xinfo, ip: t, port: MixerClient.portX32)
                usleep(1500)
            }
        }
    }

    static func localIPv4() -> String? {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return nil }
        defer { freeifaddrs(ifaddr) }
        var result: String?
        var ptr: UnsafeMutablePointer<ifaddrs>? = first
        while let p = ptr {
            let flags = Int32(p.pointee.ifa_flags)
            if let sa = p.pointee.ifa_addr, sa.pointee.sa_family == UInt8(AF_INET),
               (flags & IFF_UP) != 0, (flags & IFF_LOOPBACK) == 0 {
                let name = String(cString: p.pointee.ifa_name)
                var addr = sa.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr }
                var buf = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                inet_ntop(AF_INET, &addr, &buf, socklen_t(INET_ADDRSTRLEN))
                let ip = String(cString: buf)
                if name == "en0" { return ip }
                if result == nil && name.hasPrefix("en") { result = ip }
            }
            ptr = p.pointee.ifa_next
        }
        return result
    }

    // MARK: internos

    private func spawn(_ name: String, _ body: @escaping () -> Void) {
        let t = Thread(block: body)
        t.name = name
        t.start()
    }

    private func openSocket() {
        lock.lock()
        if fd >= 0 { close(fd) }
        fd = -1
        lock.unlock()
        let s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        guard s >= 0 else { return }
        var yes: Int32 = 1
        setsockopt(s, SOL_SOCKET, SO_BROADCAST, &yes, socklen_t(MemoryLayout<Int32>.size))
        var tv = timeval(tv_sec: 0, tv_usec: 500_000)
        setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = 0
        addr.sin_addr.s_addr = 0
        _ = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                bind(s, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        lock.lock()
        fd = s
        lock.unlock()
    }

    private func currentFd() -> Int32 { lock.lock(); defer { lock.unlock() }; return fd }

    private func rawSend(_ data: Data, ip: String, port: Int) {
        let s = currentFd()
        guard s >= 0 else { return }
        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = in_port_t(UInt16(port).bigEndian)
        guard inet_pton(AF_INET, ip, &addr.sin_addr) == 1 else { return }
        data.withUnsafeBytes { (buf: UnsafeRawBufferPointer) in
            withUnsafePointer(to: &addr) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { sa in
                    _ = sendto(s, buf.baseAddress, data.count, 0, sa, socklen_t(MemoryLayout<sockaddr_in>.size))
                }
            }
        }
    }

    /// Si todavía no sabemos el puerto, mandamos a los dos.
    private func sendToMixer(_ data: Data, _ ip: String) {
        let p = port
        if p != 0 {
            rawSend(data, ip: ip, port: p)
        } else {
            rawSend(data, ip: ip, port: MixerClient.portXAir)
            rawSend(data, ip: ip, port: MixerClient.portX32)
        }
    }

    private func rxLoop() {
        var buf = [UInt8](repeating: 0, count: 8192)
        while running {
            let s = currentFd()
            if s < 0 { usleep(300_000); continue }
            var from = sockaddr_in()
            var flen = socklen_t(MemoryLayout<sockaddr_in>.size)
            let n = buf.withUnsafeMutableBytes { (b: UnsafeMutableRawBufferPointer) -> Int in
                withUnsafeMutablePointer(to: &from) {
                    $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                        recvfrom(s, b.baseAddress, 8192, 0, $0, &flen)
                    }
                }
            }
            if n <= 0 { continue }
            var a = from.sin_addr
            var ipb = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            inet_ntop(AF_INET, &a, &ipb, socklen_t(INET_ADDRSTRLEN))
            let fromIP = String(cString: ipb)
            let fromPort = Int(UInt16(bigEndian: from.sin_port))

            lock.lock()
            let fromHost = (fromIP == _host)
            if fromHost {
                _lastRx = Date().timeIntervalSince1970
                if _port == 0 { _port = fromPort }
            }
            lock.unlock()

            if fromHost && parseMeters(buf, n) { continue }
            guard let msg = Osc.decode(buf, n) else { continue }
            if msg.address == "/xinfo" && msg.args.count >= 4 {
                let info = MixerInfo(ip: fromIP, port: fromPort, name: msg.args[1].stringValue,
                                     model: msg.args[2].stringValue, firmware: msg.args[3].stringValue)
                lock.lock(); found.append(info); lock.unlock()
            }
            if fromHost {
                lock.lock(); inbox.append(msg); lock.unlock()
            }
        }
    }

    /// X Air /meters/1: int16 LE (dB*256). X32 /meters/0 (70 floats) y /meters/2 (Main L/R en 22/23 -> 70/71).
    private func parseMeters(_ b: [UInt8], _ len: Int) -> Bool {
        if len < 24 { return false }
        for i in 0..<meterPrefix.count where b[i] != meterPrefix[i] { return false }
        let d0 = Int(b[8]) - 48
        let which: Int
        if b[9] == 0 {
            which = d0
        } else if b[10] == 0 && b[9] >= 48 && b[9] <= 57 {
            which = d0 * 10 + Int(b[9]) - 48
        } else {
            return false
        }
        if b[12] != 44 || b[13] != 98 { return true }
        let blobLen = Int(Osc.be32(b, 16))
        let end = min(len, 20 + blobLen)
        var p = 24
        if (!x32 && which == 4) || (x32 && which == 15) {
            // RTA: int16 LE en dB*256 (igual en X Air y X32).
            var i = 0
            while p + 1 < end && i < MixerClient.rtaBins {
                let v = Int16(bitPattern: UInt16(b[p]) | (UInt16(b[p + 1]) << 8))
                rta[i] = Float(v) / 256
                i += 1
                p += 2
            }
            return true
        }
        if !x32 {
            if which != 1 { return true }
            var i = 0
            while p + 1 < end && i < MixerClient.meterCount {
                let v = Int16(bitPattern: UInt16(b[p]) | (UInt16(b[p + 1]) << 8))
                meters[i] = Float(v) / 256
                i += 1
                p += 2
            }
        } else {
            var i = 0
            while p + 3 < end {
                let bits = UInt32(b[p]) | (UInt32(b[p + 1]) << 8) | (UInt32(b[p + 2]) << 16) | (UInt32(b[p + 3]) << 24)
                let v = Float(bitPattern: bits)
                let db: Float = v > 0.00001 ? 20 * log10f(v) : -100
                if which == 0 && i < 70 { meters[i] = db }
                if which == 2 && i == 22 { meters[70] = db }
                if which == 2 && i == 23 { meters[71] = db; break }
                i += 1
                p += 4
            }
        }
        return true
    }

    private func txLoop() {
        while running {
            _ = txSignal.wait(timeout: .now() + 0.5)
            while true {
                lock.lock()
                if order.isEmpty { lock.unlock(); break }
                let key = order.removeFirst()
                let data = pending.removeValue(forKey: key)
                let h = _host
                lock.unlock()
                guard let d = data, let ip = h else { continue }
                sendToMixer(d, ip)
                if key.hasSuffix("?") { usleep(2000) }
            }
        }
    }

    private func keepAliveLoop() {
        while running {
            let now = Date().timeIntervalSince1970
            if currentFd() < 0 { openSocket() }
            if let h = host {
                let alive = now - lastRx < 4
                if !alive && lastRx != 0 && now - lastReopen > 6 {
                    lastReopen = now
                    openSocket()
                }
                if now - lastKeep > (alive ? 8 : 1.5) {
                    lastKeep = now
                    sendToMixer(xremote, h)
                    if x32 {
                        sendToMixer(metersX32a, h)
                        sendToMixer(metersX32b, h)
                    } else {
                        sendToMixer(metersXair, h)
                    }
                    if rtaWanted { sendToMixer(x32 ? rtaX32 : rtaXair, h) }
                    if !alive { sendToMixer(xinfo, h) }
                }
            }
            usleep(250_000)
        }
    }
}
