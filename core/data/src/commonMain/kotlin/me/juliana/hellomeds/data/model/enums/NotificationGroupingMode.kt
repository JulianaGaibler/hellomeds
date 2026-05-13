// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

/**
 * Notification grouping strategy for medication reminders
 */
enum class NotificationGroupingMode(val value: String) {
    /**
     * Combine all medications scheduled at the same time into a single notification
     * with "All Taken" and "All Skipped" actions
     */
    COMBINED("COMBINED"),

    /**
     * Show separate notifications for each medication with individual
     * "Taken" and "Skipped" actions
     */
    GROUPED("GROUPED"),
    ;

    companion object {
        /**
         * Get grouping mode from string value
         * Defaults to COMBINED for backward compatibility
         */
        fun fromValue(value: String): NotificationGroupingMode {
            return values().firstOrNull { it.value == value } ?: COMBINED
        }
    }
}
