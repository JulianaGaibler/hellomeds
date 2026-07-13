// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import UIKit
import XCTest

/// CMP maps `Modifier.testTag` to iOS accessibility identifiers, so the
/// tag IDs from `ScreenshotTestTags.kt` work directly here.
/// `@MainActor` matches SnapshotHelper's isolation under strict concurrency.
@available(iOS 17.0, *)
@MainActor
final class ScreenshotUITests: XCTestCase {
    // No-arg `XCUIApplication()` falls back to the test bundle's own ID
    // under Xcode 26's runner, so name the host explicitly.
    let app = XCUIApplication(bundleIdentifier: "me.juliana.HelloMeds")

    /// Slugs derived from each medication's raw English `name` in
    /// `example.en.json` — stable across locales because the grid testTag
    /// reads `medication.nameRaw`, not the localized `displayName`.
    private enum DemoMed {
        static let ironSingleSchedule = "iron"
        static let magnesiumCyclic = "magnesium"
        static let cetirizineStock = "cetirizine"
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
        if UIDevice.current.userInterfaceIdiom == .pad {
            XCUIDevice.shared.orientation = .landscapeLeft
        }
        app.launchArguments += ["--hm-screenshot-mode"]
        setupSnapshot(app)
        app.launch()
    }

    func test01_tracking() throws {
        try waitForElement(identifier: "tracking_section_taken", timeout: 15)
        snapshot("01_tracking")
    }

    func test02_newScheduleDialogDaysOfWeek() throws {
        tapIfPresent(identifier: "nav_tab_MEDICATION")
        try tap(identifier: "medication_grid_item_\(DemoMed.ironSingleSchedule)")
        try tap(identifier: "medication_action_schedule")
        try tap(identifier: "schedule_add_button")
        try tap(identifier: "schedule_freq_days_of_week")
        try tap(identifier: "schedule_day_chip_MONDAY")
        try tap(identifier: "schedule_day_chip_THURSDAY")
        snapshot("02_schedule_days_of_week")
    }

    func test03_importanceLabels() throws {
        try tap(identifier: "overflow_menu_button")
        try tap(identifier: "overflow_menu_settings")
        try tap(identifier: "settings_importance_labels")
        snapshot("03_importance_labels")
    }

    func test04_stockDetail() throws {
        tapIfPresent(identifier: "nav_tab_STOCK")
        try tap(identifier: "stock_list_item_\(DemoMed.cetirizineStock)")
        try waitForElement(identifier: "stock_detail_screen", timeout: 10)
        snapshot("04_stock_detail")
    }

    func test05_medicationList() throws {
        tapIfPresent(identifier: "nav_tab_MEDICATION")
        try waitForElement(
            identifier: "medication_grid_item_\(DemoMed.magnesiumCyclic)",
            timeout: 10,
        )
        snapshot("05_medication_list")
    }

    func test06_logMedicationAsNeeded() throws {
        try tap(identifier: "tracking_fab_toggle")
        try tap(identifier: "tracking_fab_log_as_needed")
        snapshot("06_log_medication_as_needed")
    }

    func test07_medicationDetailCyclic() throws {
        tapIfPresent(identifier: "nav_tab_MEDICATION")
        try tap(identifier: "medication_grid_item_\(DemoMed.magnesiumCyclic)")
        snapshot("07_medication_detail_cyclic")
    }

    func test08_stockList() throws {
        tapIfPresent(identifier: "nav_tab_STOCK")
        try waitForElement(
            identifier: "stock_list_item_\(DemoMed.cetirizineStock)",
            timeout: 10,
        )
        snapshot("08_stock_list")
    }

    // MARK: - Helpers

    /// Nav tabs only exist on compact layouts — skip silently on iPad's side-by-side.
    private func tapIfPresent(identifier: String) {
        let element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        if element.exists {
            element.tap()
        }
    }

    /// Items past the initial viewport are either absent from the iOS
    /// accessibility tree or present but not hittable; both cases need scroll.
    private func tap(identifier: String, timeout: TimeInterval = 5) throws {
        let element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        var scrolled = false
        if !element.waitForExistence(timeout: timeout) {
            for _ in 0..<8 {
                (scrollContainer(for: identifier) ?? app).swipeUp()
                scrolled = true
                if element.waitForExistence(timeout: 1) { break }
            }
        }
        guard element.exists else {
            XCTFail("Element '\(identifier)' not found")
            return
        }
        for _ in 0..<8 {
            if element.isHittable { break }
            (scrollContainer(for: identifier) ?? app).swipeUp()
            scrolled = true
        }
        // swipeUp leaves the list with momentum; tapping mid-inertia lands
        // on a different row or gets eaten as part of the scroll gesture.
        if scrolled { Thread.sleep(forTimeInterval: 0.6) }
        element.tap()
    }

    private func waitForElement(identifier: String, timeout: TimeInterval) throws {
        let element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        if element.waitForExistence(timeout: timeout) { return }
        for _ in 0..<8 {
            (scrollContainer(for: identifier) ?? app).swipeUp()
            if element.waitForExistence(timeout: 1) { return }
        }
        XCTFail("Element '\(identifier)' did not appear within \(timeout)s + scroll")
    }

    /// iPad's adaptive layout shows multiple lists side-by-side; route the
    /// swipe by identifier prefix so we scroll the pane that holds the target.
    private func scrollContainer(for identifier: String) -> XCUIElement? {
        let tag: String
        switch true {
        case identifier.hasPrefix("stock_list_item_"): tag = "stock_list_scroll"
        case identifier.hasPrefix("medication_grid_item_"): tag = "medication_list_scroll"
        default: tag = "tracking_list_scroll"
        }
        let candidate = app.descendants(matching: .any).matching(identifier: tag).firstMatch
        return candidate.exists ? candidate : nil
    }
}
