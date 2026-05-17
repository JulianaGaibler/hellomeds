// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.designsystem.testing

/**
 * Stable testTag identifiers used by the Fastlane Screengrab instrumentation
 * test (`androidApp/src/androidTestScreenshotDebug`). Tags ship in release
 * builds — the Modifier.testTag cost is negligible.
 */
object ScreenshotTestTags {
    // Bottom-nav / nav-rail items, keyed by TabDestination.name (TRACKING, MEDICATION, STOCK)
    fun navTab(destination: String) = "nav_tab_$destination"

    // Medication grid item on the medications tab, keyed by medication id
    fun medicationGridItem(medicationId: Int) = "medication_grid_item_$medicationId"

    // Per-medication stock row on the stock tab, keyed by medication id
    fun stockListItem(medicationId: Int) = "stock_list_item_$medicationId"

    // MedicationDetailScreen action buttons
    const val MEDICATION_ACTION_SCHEDULE = "medication_action_schedule"
    const val MEDICATION_ACTION_STOCK = "medication_action_stock"

    // ScheduleScreen (list of schedules behind EditScheduleRoute)
    const val SCHEDULE_ADD_BUTTON = "schedule_add_button"

    // ScheduleBottomSheet
    const val SCHEDULE_FREQ_DAYS_OF_WEEK = "schedule_freq_days_of_week"
    fun scheduleDayChip(dayName: String) = "schedule_day_chip_$dayName"

    // Settings entry
    const val SETTINGS_IMPORTANCE_LABELS = "settings_importance_labels"

    // OverflowMenu (top-bar chrome)
    const val OVERFLOW_MENU_BUTTON = "overflow_menu_button"
    const val OVERFLOW_MENU_SETTINGS = "overflow_menu_settings"

    // TrackingScreen — "Taken" section header. Used as a settle signal:
    // the section only renders once the history StateFlow has emitted past
    // events, so waiting for it avoids racing the initial load.
    const val TRACKING_SECTION_TAKEN = "tracking_section_taken"

    // TrackingScreen FAB menu — toggle and individual log-mode items.
    // Tagging by id (rather than label) makes the menu tappable in any locale.
    const val TRACKING_FAB_TOGGLE = "tracking_fab_toggle"
    const val TRACKING_FAB_LOG_SCHEDULED = "tracking_fab_log_scheduled"
    const val TRACKING_FAB_LOG_AS_NEEDED = "tracking_fab_log_as_needed"
}
