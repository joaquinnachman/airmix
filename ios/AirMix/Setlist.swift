import Foundation

/// Un tema de la lista: título + nota libre.
struct Song: Identifiable, Codable, Equatable {
    var id = UUID()
    var title: String
    var note: String = ""
}

/// Lista de temas de la banda que se está operando (pestaña TEMAS). Espejo de Setlist.kt.
final class Setlist: ObservableObject {
    static let shared = Setlist()

    @Published var songs: [Song] = []
    /// Tema que está sonando (-1 = ninguno).
    @Published var current = -1
    /// Nombre del archivo del que salió la lista.
    @Published var source = ""

    private struct Saved: Codable {
        var songs: [Song]
        var current: Int
        var source: String
    }

    private init() {
        if let d = UserDefaults.standard.data(forKey: "setlist"), let s = try? JSONDecoder().decode(Saved.self, from: d) {
            songs = s.songs
            current = s.current < s.songs.count ? s.current : -1
            source = s.source
        }
    }

    func save() {
        if let d = try? JSONEncoder().encode(Saved(songs: songs, current: current, source: source)) {
            UserDefaults.standard.set(d, forKey: "setlist")
        }
    }

    func move(_ i: Int, _ dir: Int) {
        let j = i + dir
        guard songs.indices.contains(i), songs.indices.contains(j) else { return }
        songs.swapAt(i, j)
        if current == i { current = j } else if current == j { current = i }
        save()
    }

    func remove(_ i: Int) {
        guard songs.indices.contains(i) else { return }
        songs.remove(at: i)
        if current == i { current = -1 } else if current > i { current -= 1 }
        save()
    }

    func step(_ dir: Int) {
        let n = songs.count
        if n == 0 { return }
        current = current < 0 ? (dir > 0 ? 0 : n - 1) : min(max(current + dir, 0), n - 1)
        save()
    }

    /// Texto -> temas. Un tema por línea; se ignoran vacías y la numeración ("1.", "02 -", "3)", viñetas).
    /// Si la línea tiene " // " o una tabulación, lo que sigue es la nota.
    static func parse(_ text: String) -> [Song] {
        var out: [Song] = []
        let num = try! NSRegularExpression(pattern: "^\\s*(?:\\d{1,3}\\s*[.)\\-–:]\\s*|[•·*\\-–]\\s+)")
        let lines = text.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").components(separatedBy: "\n")
        for raw in lines {
            var line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }
            let ns = line as NSString
            line = num.stringByReplacingMatches(in: line, range: NSRange(location: 0, length: ns.length), withTemplate: "")
                .trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }
            let cuts = [" // ", "\t"].compactMap { sep -> String.Index? in
                guard let r = line.range(of: sep), r.lowerBound > line.startIndex else { return nil }
                return r.lowerBound
            }
            if let cut = cuts.min() {
                let title = String(line[..<cut]).trimmingCharacters(in: .whitespaces)
                let note = String(line[cut...]).trimmingCharacters(in: CharacterSet(charactersIn: "/\t ")).trimmingCharacters(in: .whitespaces)
                out.append(Song(title: title, note: note))
            } else {
                out.append(Song(title: line))
            }
        }
        return out
    }
}
