import Foundation
import UIKit

/// Aviso de actualización (igual que Updater en Update.kt). El repo es público: se consulta el último
/// Release sin login. CFBundleVersion = número de build del CI (tag build-NN).
/// En iOS solo se avisa y se abre la página del Release: un .ipa sin firmar no se puede auto-instalar.
final class Updater: ObservableObject {
    static let shared = Updater()
    struct Release { let build: Int; let version: String; let notes: String; let page: URL }

    @Published var available: Release?
    @Published var manualResult: String?
    private var lastCheck = Date.distantPast
    private var offered = -1

    static var installed: Int { Int(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "") ?? 0 }

    /// canShow: nunca conectado a la consola ni con el candado puesto (no molestar en un show).
    func check(manual: Bool = false, canShow: @escaping () -> Bool) {
        if UserDefaults.standard.string(forKey: "debugScreen") != nil { return } // capturas de CI
        if !manual && Date().timeIntervalSince(lastCheck) < 6 * 3600 { return }
        lastCheck = Date()
        var req = URLRequest(url: URL(string: "https://api.github.com/repos/joaquinnachman/airmix/releases/latest")!, timeoutInterval: 8)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.setValue("AirMix", forHTTPHeaderField: "User-Agent")
        URLSession.shared.dataTask(with: req) { data, resp, _ in
            var rel: Release?
            if let data, (resp as? HTTPURLResponse)?.statusCode == 200,
               let j = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let tag = j["tag_name"] as? String, let build = Int(tag.replacingOccurrences(of: "build-", with: "")),
               let page = URL(string: j["html_url"] as? String ?? "") {
                let name = j["name"] as? String ?? ""
                let ver = name.range(of: #"AirMix\s+([0-9.]+)"#, options: .regularExpression)
                    .map { String(name[$0]).replacingOccurrences(of: "AirMix", with: "").trimmingCharacters(in: .whitespaces) } ?? "build \(build)"
                let notes = (j["body"] as? String ?? "").split(separator: "\n").first.map(String.init) ?? ""
                rel = Release(build: build, version: ver, notes: String(notes.prefix(300)), page: page)
            }
            DispatchQueue.main.async {
                guard let r = rel else {
                    if manual { self.manualResult = "No se pudo buscar: ¿hay internet?" }
                    return
                }
                if r.build <= Updater.installed {
                    if manual { self.manualResult = "Estás al día (build \(Updater.installed))" }
                    return
                }
                if !manual && r.build == self.offered { return }
                if !manual && !canShow() { self.lastCheck = .distantPast; return }
                self.offered = r.build
                self.available = r
            }
        }.resume()
    }
}
