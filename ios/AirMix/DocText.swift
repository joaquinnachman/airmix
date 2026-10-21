import Foundation
import Compression

/// Saca el texto plano de un archivo para la lista de temas: .txt, .docx (Word / Google Docs),
/// .doc (Word 97-2003) y .rtf. Misma lógica que DocText.kt.
enum DocText {
    static func extract(_ d: Data) -> String? {
        let b = [UInt8](d)
        if b.count >= 4 && b[0] == 0x50 && b[1] == 0x4B { return docx(b) }
        if b.count >= 8 && b[0] == 0xD0 && b[1] == 0xCF && b[2] == 0x11 && b[3] == 0xE0 { return doc(b) }
        if b.count >= 5 && String(bytes: b[0..<5], encoding: .isoLatin1) == "{\\rtf" {
            return rtf(String(bytes: b, encoding: .isoLatin1) ?? "")
        }
        return plain(b)
    }

    /// UTF-8 (con o sin BOM); si no es UTF-8 válido, Windows-1252. UTF-16 con BOM también.
    private static func plain(_ b: [UInt8]) -> String {
        if b.count >= 2 && b[0] == 0xFF && b[1] == 0xFE { return String(bytes: b[2...], encoding: .utf16LittleEndian) ?? "" }
        if b.count >= 2 && b[0] == 0xFE && b[1] == 0xFF { return String(bytes: b[2...], encoding: .utf16BigEndian) ?? "" }
        let start = (b.count >= 3 && b[0] == 0xEF && b[1] == 0xBB && b[2] == 0xBF) ? 3 : 0
        if let s = String(bytes: b[start...], encoding: .utf8) { return s }
        return String(bytes: b, encoding: .windowsCP1252) ?? ""
    }

    private static func u16(_ b: [UInt8], _ o: Int) -> Int { Int(b[o]) | (Int(b[o + 1]) << 8) }
    private static func u32(_ b: [UInt8], _ o: Int) -> Int {
        let v = UInt32(b[o]) | (UInt32(b[o + 1]) << 8) | (UInt32(b[o + 2]) << 16) | (UInt32(b[o + 3]) << 24)
        return Int(Int32(bitPattern: v))
    }

    // MARK: .docx (zip + word/document.xml)

    private static func docx(_ b: [UInt8]) -> String? {
        guard let xml = zipEntry(b, "word/document.xml") else { return nil }
        return docxXmlToText(String(decoding: xml, as: UTF8.self))
    }

    /// Lee un archivo de un zip (directorio central + inflate con el framework Compression).
    private static func zipEntry(_ b: [UInt8], _ wanted: String) -> [UInt8]? {
        guard b.count >= 22 else { return nil }
        var eocd = -1
        var i = b.count - 22
        while i >= max(0, b.count - 65557) {
            if b[i] == 0x50 && b[i + 1] == 0x4B && b[i + 2] == 0x05 && b[i + 3] == 0x06 { eocd = i; break }
            i -= 1
        }
        guard eocd >= 0 else { return nil }
        let count = u16(b, eocd + 10)
        var p = u32(b, eocd + 16)
        for _ in 0..<count {
            guard p + 46 <= b.count, u32(b, p) == 0x02014b50 else { return nil }
            let method = u16(b, p + 10)
            let csize = u32(b, p + 20)
            let usize = u32(b, p + 24)
            let nlen = u16(b, p + 28), xlen = u16(b, p + 30), clen = u16(b, p + 32)
            let local = u32(b, p + 42)
            let name = String(decoding: b[(p + 46)..<min(p + 46 + nlen, b.count)], as: UTF8.self)
            p += 46 + nlen + xlen + clen
            if name != wanted { continue }
            guard local + 30 <= b.count else { return nil }
            let start = local + 30 + u16(b, local + 26) + u16(b, local + 28)
            guard start + csize <= b.count else { return nil }
            let comp = Array(b[start..<(start + csize)])
            if method == 0 { return comp }
            guard method == 8, usize > 0 else { return nil }
            var out = [UInt8](repeating: 0, count: usize)
            let n = out.withUnsafeMutableBufferPointer { dst in
                comp.withUnsafeBufferPointer { src in
                    compression_decode_buffer(dst.baseAddress!, usize, src.baseAddress!, csize, nil, COMPRESSION_ZLIB)
                }
            }
            return n > 0 ? Array(out[0..<n]) : nil
        }
        return nil
    }

    static func docxXmlToText(_ xml: String) -> String {
        var out = ""
        let re = try! NSRegularExpression(pattern: "<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:br/>|<w:cr/>")
        for para in xml.components(separatedBy: "</w:p>") {
            let ns = para as NSString
            for m in re.matches(in: para, range: NSRange(location: 0, length: ns.length)) {
                let whole = ns.substring(with: m.range)
                if whole.hasPrefix("<w:tab") { out += "\t" }
                else if whole.hasPrefix("<w:br") || whole.hasPrefix("<w:cr") { out += "\n" }
                else if m.range(at: 1).location != NSNotFound { out += unescape(ns.substring(with: m.range(at: 1))) }
            }
            out += "\n"
        }
        return out
    }

    private static func unescape(_ s: String) -> String {
        if !s.contains("&") { return s }
        var r = s.replacingOccurrences(of: "&lt;", with: "<").replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"").replacingOccurrences(of: "&apos;", with: "'")
        let re = try! NSRegularExpression(pattern: "&#(x?)([0-9a-fA-F]+);")
        let ns = r as NSString
        var result = ""
        var last = 0
        for m in re.matches(in: r, range: NSRange(location: 0, length: ns.length)) {
            result += ns.substring(with: NSRange(location: last, length: m.range.location - last))
            let hex = ns.substring(with: m.range(at: 1)) == "x"
            let num = ns.substring(with: m.range(at: 2))
            if let code = UInt32(num, radix: hex ? 16 : 10), let u = Unicode.Scalar(code) { result += String(Character(u)) }
            last = m.range.location + m.range.length
        }
        result += ns.substring(from: last)
        r = result
        return r.replacingOccurrences(of: "&amp;", with: "&")
    }

    // MARK: .doc (Word 97-2003, archivo OLE)

    private static func doc(_ f: [UInt8]) -> String? {
        guard f.count >= 512 else { return nil }
        let ss = 1 << u16(f, 0x1E)
        let mss = 1 << u16(f, 0x20)
        let nFat = u32(f, 0x2C)
        let dirStart = u32(f, 0x30)
        let miniCutoff = u32(f, 0x38)
        let miniFatStart = u32(f, 0x3C)
        let difatStart = u32(f, 0x44)
        func sec(_ s: Int) -> Int { (s + 1) * ss }

        var fatSecs: [Int] = []
        for i in 0..<min(max(nFat, 0), 109) { fatSecs.append(u32(f, 0x4C + 4 * i)) }
        var d = difatStart
        var guardN = 0
        while fatSecs.count < nFat && d >= 0 && sec(d) + ss <= f.count && guardN < 1000 {
            guardN += 1
            for i in 0..<(ss / 4 - 1) where fatSecs.count < nFat { fatSecs.append(u32(f, sec(d) + 4 * i)) }
            d = u32(f, sec(d) + ss - 4)
        }
        var fat = [Int](repeating: -1, count: fatSecs.count * (ss / 4))
        for (k, s) in fatSecs.enumerated() {
            for i in 0..<(ss / 4) where s >= 0 && sec(s) + 4 * i + 4 <= f.count { fat[k * (ss / 4) + i] = u32(f, sec(s) + 4 * i) }
        }
        func chain(_ start: Int) -> [UInt8] {
            var out: [UInt8] = []
            var s = start
            var n = 0
            while s >= 0 && s < fat.count && n < 100000 {
                n += 1
                let o = sec(s)
                if o + ss > f.count { break }
                out.append(contentsOf: f[o..<(o + ss)])
                s = fat[s]
            }
            return out
        }

        let dir = chain(dirStart)
        guard dir.count >= 128 else { return nil }
        var names: [String: (Int, Int)] = [:]
        for i in 0..<(dir.count / 128) {
            let o = i * 128
            let len = u16(dir, o + 0x40)
            if len < 2 { continue }
            let name = String(bytes: dir[o..<(o + len - 2)], encoding: .utf16LittleEndian) ?? ""
            names[name] = (u32(dir, o + 0x74), u32(dir, o + 0x78))
        }
        let mini = chain(u32(dir, 0x74))
        let mfb = chain(miniFatStart)
        let miniFat = (0..<(mfb.count / 4)).map { u32(mfb, $0 * 4) }

        func stream(_ name: String) -> [UInt8]? {
            guard let entry = names[name] else { return nil }
            let (start, size) = entry
            if size < miniCutoff {
                var out: [UInt8] = []
                var s = start
                var n = 0
                while s >= 0 && out.count < size && n < 100000 {
                    n += 1
                    let o = s * mss
                    if o + mss > mini.count { break }
                    out.append(contentsOf: mini[o..<(o + mss)])
                    s = s < miniFat.count ? miniFat[s] : -2
                }
                return Array(out.prefix(size))
            }
            return Array(chain(start).prefix(size))
        }

        guard let wd = stream("WordDocument"), wd.count > 0x1AA, u16(wd, 0) == 0xA5EC else { return nil }
        guard let table = stream((u16(wd, 0x0A) & 0x0200) != 0 ? "1Table" : "0Table") else { return nil }
        let ccpText = u32(wd, 0x4C)
        var p = u32(wd, 0x1A2)
        while p < table.count && table[p] == 1 { p += 3 + u16(table, p + 1) }
        guard p + 5 <= table.count, table[p] == 2 else { return nil }
        let lcb = u32(table, p + 1)
        p += 5
        let n = (lcb - 4) / 12
        var text = ""
        for i in 0..<max(n, 0) {
            guard p + 4 * (n + 1) + 8 * i + 6 <= table.count else { break }
            let cpStart = u32(table, p + 4 * i)
            let cpEnd = u32(table, p + 4 * (i + 1))
            if cpStart >= ccpText { break }
            let count = min(cpEnd, ccpText) - cpStart
            let fc = u32(table, p + 4 * (n + 1) + 8 * i + 2)
            if (fc & 0x40000000) != 0 {
                let off = (fc & 0x3FFFFFFF) / 2
                if off + count <= wd.count { text += String(bytes: wd[off..<(off + count)], encoding: .windowsCP1252) ?? "" }
            } else if fc + 2 * count <= wd.count {
                text += String(bytes: wd[fc..<(fc + 2 * count)], encoding: .utf16LittleEndian) ?? ""
            }
        }
        return cleanWord(text)
    }

    /// Caracteres de control de Word: párrafo/celda -> salto; campos: se deja solo el resultado.
    private static func cleanWord(_ s: String) -> String {
        var out = ""
        var depth = 0
        var inCode = false
        for u in s.unicodeScalars {
            switch u.value {
            case 0x13: depth += 1; inCode = true
            case 0x14: inCode = false
            case 0x15: if depth > 0 { depth -= 1 }; inCode = false
            case 0x0D, 0x07, 0x0B, 0x0C: if !inCode { out += "\n" }
            case 0x09: if !inCode { out += "\t" }
            default: if !inCode && u.value >= 0x20 { out.unicodeScalars.append(u) }
            }
        }
        return out
    }

    // MARK: .rtf

    private static let rtfSkip: Set<String> = ["fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "listtable", "listoverridetable", "rsidtbl", "generator", "xmlnstbl"]

    static func rtf(_ str: String) -> String {
        let s = Array(str.unicodeScalars)
        var out = ""
        var stack: [Bool] = []
        var skip = false
        var groupStart = false
        var uc = 1
        var pend = 0
        var i = 0
        func isLetter(_ c: Unicode.Scalar) -> Bool { (c >= "a" && c <= "z") || (c >= "A" && c <= "Z") }
        func isDigit(_ c: Unicode.Scalar) -> Bool { c >= "0" && c <= "9" }
        while i < s.count {
            let c = s[i]
            if c == "{" { stack.append(skip); groupStart = true; i += 1; continue }
            if c == "}" {
                skip = stack.popLast() ?? false
                i += 1
            } else if c == "\\" {
                if i + 1 >= s.count { break }
                let n = s[i + 1]
                if n == "*" {
                    skip = true
                    i += 2
                } else if n == "'" && i + 3 < s.count {
                    let hex = String(String.UnicodeScalarView(s[(i + 2)...(i + 3)]))
                    if pend > 0 { pend -= 1 }
                    else if !skip, let code = UInt8(hex, radix: 16) { out += String(bytes: [code], encoding: .windowsCP1252) ?? "" }
                    i += 4
                } else if isLetter(n) {
                    var j = i + 1
                    while j < s.count && isLetter(s[j]) { j += 1 }
                    let word = String(String.UnicodeScalarView(s[(i + 1)..<j]))
                    var k = j
                    if k < s.count && (s[k] == "-" || isDigit(s[k])) {
                        k += 1
                        while k < s.count && isDigit(s[k]) { k += 1 }
                    }
                    let param = String(String.UnicodeScalarView(s[j..<k]))
                    if k < s.count && s[k] == " " { k += 1 }
                    if groupStart && rtfSkip.contains(word) { skip = true }
                    if word == "uc" { uc = Int(param) ?? 1 }
                    if !skip {
                        switch word {
                        case "par", "line", "row", "cell": out += "\n"
                        case "tab": out += "\t"
                        case "u":
                            if let v = Int(param), let u = Unicode.Scalar(UInt32(v < 0 ? v + 65536 : v)) {
                                out.unicodeScalars.append(u)
                                pend = uc
                            }
                        default: break
                        }
                    }
                    i = k
                } else {
                    if !skip && n != "\n" && n != "\r" { out.unicodeScalars.append(n) }
                    i += 2
                }
            } else if c == "\r" || c == "\n" {
                i += 1
            } else {
                if pend > 0 { pend -= 1 } else if !skip { out.unicodeScalars.append(c) }
                i += 1
            }
            groupStart = false
        }
        return out
    }
}
