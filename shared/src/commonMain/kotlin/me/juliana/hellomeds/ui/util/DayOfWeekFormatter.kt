// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import kotlinx.datetime.DayOfWeek

/**
 * Locale-aware day-of-week display names.
 *
 * On Android this uses `java.time.DayOfWeek.getDisplayName()`.
 * On iOS this uses `NSDateFormatter`.
 */
expect object DayOfWeekFormatter {
    /** Full name, e.g. "Monday" */
    fun fullName(dayOfWeek: DayOfWeek): String

    /** Short name, e.g. "Mon" */
    fun shortName(dayOfWeek: DayOfWeek): String
}
