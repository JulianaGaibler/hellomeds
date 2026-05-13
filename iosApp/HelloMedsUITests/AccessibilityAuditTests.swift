// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import XCTest

/// Accessibility audit tests for HelloMeds.
///
/// Uses iOS 17's `performAccessibilityAudit()` to validate contrast ratios,
/// touch target sizes, missing labels, and other accessibility issues.
///
/// Setup: Add a UI Testing Bundle target in Xcode:
///   File > New > Target > UI Testing Bundle
///   Name: HelloMedsUITests, Target Application: HelloMeds
///
/// Run: xcodebuild test -project HelloMeds.xcodeproj -scheme HelloMeds \
///   -destination 'platform=iOS Simulator,name=iPhone 16' \
///   -only-testing:HelloMedsUITests
@available(iOS 17.0, *)
final class AccessibilityAuditTests: XCTestCase {
    let app = XCUIApplication()

    override func setUpWithError() throws {
        continueAfterFailure = false
        app.launch()
    }

    // MARK: - Main Tabs

    func testTrackingScreenAccessibility() throws {
        // Tracking is the default tab on launch
        try app.performAccessibilityAudit()
    }

    func testMedicationScreenAccessibility() throws {
        // Navigate to Medication tab
        let tabBar = app.tabBars.firstMatch
        guard tabBar.exists else { throw XCTSkip("Tab bar not found") }
        tabBar.buttons.element(boundBy: 1).tap()
        try app.performAccessibilityAudit()
    }

    func testStockScreenAccessibility() throws {
        // Navigate to Stock tab
        let tabBar = app.tabBars.firstMatch
        guard tabBar.exists else { throw XCTSkip("Tab bar not found") }
        tabBar.buttons.element(boundBy: 2).tap()
        try app.performAccessibilityAudit()
    }

    // MARK: - Filtered audit (suppress known Compose/iOS mapping issues)

    func testTrackingScreenContrastAudit() throws {
        try app.performAccessibilityAudit(for: [.contrast]) { issue in
            // Return true to suppress specific known false positives
            false
        }
    }
}
