// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.designsystem.testing

/**
 * testTag identifiers shared between the Android Compose instrumentation test
 * (`androidApp/src/androidTestScreenshotDebug`) and the iOS XCUITest
 * (`iosApp/HelloMedsUITests/ScreenshotUITests.swift`). Tags ship in release
 * builds — the Modifier.testTag cost is negligible.
 *
 * Per-medication factories key on the raw English `name` so the tag stays
 * stable when the user-visible string is localized via `displayName`.
 */
object ScreenshotTestTags {
    fun navTab(destination: String) = "nav_tab_$destination"

    fun medicationGridItem(name: String) = "medication_grid_item_${slug(name)}"

    fun stockListItem(name: String) = "stock_list_item_${slug(name)}"

    private fun slug(name: String) = name.lowercase().replace(' ', '_')

    const val MEDICATION_ACTION_SCHEDULE = "medication_action_schedule"
    const val MEDICATION_ACTION_STOCK = "medication_action_stock"

    const val SCHEDULE_ADD_BUTTON = "schedule_add_button"
    const val SCHEDULE_FREQ_DAYS_OF_WEEK = "schedule_freq_days_of_week"
    fun scheduleDayChip(dayName: String) = "schedule_day_chip_$dayName"

    const val SETTINGS_IMPORTANCE_LABELS = "settings_importance_labels"

    const val OVERFLOW_MENU_BUTTON = "overflow_menu_button"
    const val OVERFLOW_MENU_SETTINGS = "overflow_menu_settings"

    // Settle signal — the section only renders after the history StateFlow
    // emits, so waiting on it avoids racing the initial load.
    const val TRACKING_SECTION_TAKEN = "tracking_section_taken"

    const val TRACKING_FAB_TOGGLE = "tracking_fab_toggle"
    const val TRACKING_FAB_LOG_SCHEDULED = "tracking_fab_log_scheduled"
    const val TRACKING_FAB_LOG_AS_NEEDED = "tracking_fab_log_as_needed"

    // CMP renders to a single UIView on iOS, so a screen-center swipe misses
    // the scroll region in complex layouts; tagging lets XCUITest swipe it.
    const val TRACKING_LIST_SCROLL = "tracking_list_scroll"
    const val MEDICATION_LIST_SCROLL = "medication_list_scroll"
    const val STOCK_LIST_SCROLL = "stock_list_scroll"

    // Settle signal — waited on after navigating into the detail screen so
    // XCUITest doesn't snap mid-transition and capture the source list.
    const val STOCK_DETAIL_SCREEN = "stock_detail_screen"
}
