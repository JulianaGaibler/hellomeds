// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import SwiftUI
import shared

@main
struct HelloMedsApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    handleIncomingBackupFile(url: url)
                }
                .overlay {
                    if ScreenPrivacyManager.shared.shouldShowOverlay {
                        Color(UIColor.systemBackground)
                            .ignoresSafeArea()
                    }
                }
                .onChange(of: scenePhase) { _, newPhase in
                    ScreenPrivacyManager.shared.scenePhaseChanged(newPhase)
                }
        }
    }
}

/// Handle .hmbackup files opened from other apps (e.g. messenger, email, Files)
private func handleIncomingBackupFile(url: URL) {
    guard url.pathExtension == "hmbackup" else { return }

    let accessing = url.startAccessingSecurityScopedResource()
    defer { if accessing { url.stopAccessingSecurityScopedResource() } }

    guard let data = try? Data(contentsOf: url) else { return }

    // Convert to KotlinByteArray and pass to the shared import handler
    let kotlinBytes = KotlinByteArray(size: Int32(data.count))
    data.withUnsafeBytes { ptr in
        let bytes = ptr.bindMemory(to: Int8.self)
        for i in 0..<data.count {
            kotlinBytes.set(index: Int32(i), value: bytes[i])
        }
    }

    // Store for the Compose UI to pick up
    IncomingBackupHandlerKt.setPendingImportBytes(bytes: kotlinBytes)
}

/// Manages screen privacy overlay state.
/// Kotlin pushes preference changes via the bridge; SwiftUI reads the overlay flag.
@Observable
class ScreenPrivacyManager {
    static let shared = ScreenPrivacyManager()

    var isEnabled = false
    var shouldShowOverlay = false

    private init() {
        // Register the bridge callback to receive preference updates from Kotlin
        ScreenPrivacyBridgeKt.registerScreenPrivacyBridge { enabled in
            DispatchQueue.main.async {
                self.isEnabled = enabled.boolValue
            }
        }
    }

    func scenePhaseChanged(_ phase: ScenePhase) {
        switch phase {
        case .active:
            shouldShowOverlay = false
        case .inactive, .background:
            if isEnabled {
                shouldShowOverlay = true
            }
        @unknown default:
            break
        }
    }
}
