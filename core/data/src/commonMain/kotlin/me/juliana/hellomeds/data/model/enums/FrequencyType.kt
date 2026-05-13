// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

/**
 * Defines how frequently a medication should be taken.
 */
enum class FrequencyType {
    /**
     * Regular interval - every N days (e.g., every day, every 2 days, every 3 days)
     */
    INTERVAL,

    /**
     * Specific days of the week (e.g., Monday, Wednesday, Friday)
     */
    DAYS_OF_WEEK,
}
