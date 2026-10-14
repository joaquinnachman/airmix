import Foundation

/// Argumento OSC (int, float, string, blob).
enum OscArg: Equatable {
    case i(Int32)
    case f(Float)
    case s(String)
    case b(Data)

    var intValue: Int {
        switch self {
        case .i(let v): return Int(v)
        case .f(let v): return Int(v)
        case .s(let v): return Int(v) ?? 0
        case .b: return 0
        }
    }

    var floatValue: Float {
        switch self {
        case .i(let v): return Float(v)
        case .f(let v): return v
        case .s(let v): return Float(v) ?? 0
        case .b: return 0
        }
    }

    var stringValue: String {
        switch self {
        case .s(let v): return v
        case .i(let v): return String(v)
        case .f(let v): return String(v)
        case .b: return ""
        }
    }
}

struct OscMessage {
    let address: String
    let args: [OscArg]
}

/// Codec OSC mínimo. Mismo comportamiento que Osc.kt en Android.
enum Osc {
    static func encode(_ address: String, _ args: [OscArg] = []) -> Data {
        var d = Data()
        appendString(&d, address)
        var tags = ","
        for a in args {
            switch a {
            case .i: tags += "i"
            case .f: tags += "f"
            case .s: tags += "s"
            case .b: tags += "b"
            }
        }
        appendString(&d, tags)
        for a in args {
            switch a {
            case .i(let v):
                appendU32(&d, UInt32(bitPattern: v))
            case .f(let v):
                appendU32(&d, v.bitPattern)
            case .s(let v):
                appendString(&d, v)
            case .b(let v):
                appendU32(&d, UInt32(v.count))
                d.append(v)
                while d.count % 4 != 0 { d.append(0) }
            }
        }
        return d
    }

    private static func appendU32(_ d: inout Data, _ v: UInt32) {
        d.append(UInt8((v >> 24) & 0xff))
        d.append(UInt8((v >> 16) & 0xff))
        d.append(UInt8((v >> 8) & 0xff))
        d.append(UInt8(v & 0xff))
    }

    private static func appendString(_ d: inout Data, _ s: String) {
        let bytes = s.data(using: .isoLatin1, allowLossyConversion: true) ?? Data(s.utf8)
        d.append(bytes)
        d.append(0)
        while d.count % 4 != 0 { d.append(0) }
    }

    @inline(__always) static func padded(_ n: Int) -> Int { (n + 3) & ~3 }

    private static func zero(_ b: [UInt8], _ from: Int, _ len: Int) -> Int? {
        var i = from
        while i < len {
            if b[i] == 0 { return i }
            i += 1
        }
        return nil
    }

    @inline(__always) static func be32(_ b: [UInt8], _ p: Int) -> UInt32 {
        (UInt32(b[p]) << 24) | (UInt32(b[p + 1]) << 16) | (UInt32(b[p + 2]) << 8) | UInt32(b[p + 3])
    }

    static func latin1(_ b: ArraySlice<UInt8>) -> String {
        String(bytes: b, encoding: .isoLatin1) ?? ""
    }

    static func decode(_ b: [UInt8], _ len: Int) -> OscMessage? {
        guard len > 0, let ae = zero(b, 0, len), ae > 0 else { return nil }
        let address = latin1(b[0..<ae])
        var p = padded(ae + 1)
        if p >= len || b[p] != 44 { return OscMessage(address: address, args: []) }
        guard let te = zero(b, p, len) else { return nil }
        let tags = Array(b[(p + 1)..<te])
        p = padded(te + 1)
        var args: [OscArg] = []
        for t in tags {
            switch t {
            case 105: // i
                guard p + 4 <= len else { return OscMessage(address: address, args: args) }
                args.append(.i(Int32(bitPattern: be32(b, p))))
                p += 4
            case 102: // f
                guard p + 4 <= len else { return OscMessage(address: address, args: args) }
                args.append(.f(Float(bitPattern: be32(b, p))))
                p += 4
            case 115: // s (algunas respuestas /node vienen sin el 0 final)
                if let e = zero(b, p, len) {
                    args.append(.s(latin1(b[p..<e])))
                    p = padded(e + 1)
                } else {
                    if p < len { args.append(.s(latin1(b[p..<len]))) }
                    return OscMessage(address: address, args: args)
                }
            case 98: // b
                guard p + 4 <= len else { return OscMessage(address: address, args: args) }
                let n = Int(Int32(bitPattern: be32(b, p)))
                p += 4
                guard n >= 0, p + n <= len else { return OscMessage(address: address, args: args) }
                args.append(.b(Data(b[p..<(p + n)])))
                p += padded(n)
            default:
                return OscMessage(address: address, args: args)
            }
        }
        return OscMessage(address: address, args: args)
    }
}
