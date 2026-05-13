// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import SwiftUI
import shared
import UIKit

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        setupKeychainBridge()  // Must be first — database encryption key needed before Koin init
        setupPassphraseBridge()  // Auto-backup passphrase — before Koin init
        setupFoundationModelBridge()
        setupEncryptionBridge()
        setupNotificationBridge()
        setupAlarmKitBridge()
        ShareBridgeKt.registerShareBridge { url in
            presentShareSheet(fileURL: url as URL)
        }
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
