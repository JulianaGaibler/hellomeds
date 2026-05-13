// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.test

/**
 * Centralized test tag constants for Compose UI testing.
 * Lives in commonMain so both production code and tests can reference them.
 */
object TestTags {
    // MedicationCycleStep / EditMedicationScreen
    const val TIMEZONE_TOGGLE = "timezone_toggle"
    const val CYCLE_TOGGLE = "cycle_toggle"
    const val CYCLE_CONFIG = "cycle_config"
    const val CYCLE_PROGRESS = "cycle_progress"

    // TrackingScreen
    const val OVERDUE_SECTION = "overdue_section"
    const val MISSED_DOSE_BANNER = "missed_dose_banner"
    const val SCHEDULED_SECTION = "scheduled_section"
    const val TAKEN_SECTION = "taken_section"
    const val SKIPPED_SECTION = "skipped_section"
    const val EMPTY_STATE = "empty_state"

    // Common
    const val SAVE_BUTTON = "save_button"
}
