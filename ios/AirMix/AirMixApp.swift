import SwiftUI

@main
struct AirMixApp: App {
    init() {
        Core.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .persistentSystemOverlays(.hidden)
                .onAppear { UIApplication.shared.isIdleTimerDisabled = true } // pantalla siempre encendida en el show
        }
    }
}
