// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import UIKit
import shared

/// Helper to present UIActivityViewController safely (handles iPad popover requirement).
/// Called from Kotlin via bridge function.
func presentShareSheet(fileURL: URL) {
    let activityVC = UIActivityViewController(
        activityItems: [fileURL],
        applicationActivities: nil
    )

    guard let presenter = topViewControllerForSharing() else { return }

    // iPad requires popover configuration
    if let popover = activityVC.popoverPresentationController {
        popover.sourceView = presenter.view
        popover.sourceRect = CGRect(
            x: presenter.view.bounds.midX,
            y: presenter.view.bounds.midY,
            width: 0, height: 0
        )
        popover.permittedArrowDirections = []
    }

    presenter.present(activityVC, animated: true)
}

private func topViewControllerForSharing() -> UIViewController? {
    var vc = UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .flatMap { $0.windows }
        .first { $0.isKeyWindow }?
        .rootViewController
    while let presented = vc?.presentedViewController {
        vc = presented
    }
    return vc
}
