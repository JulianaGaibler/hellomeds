// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import kotlinx.datetime.DayOfWeek
import platform.Foundation.NSDateFormatter

actual object DayOfWeekFormatter {
    private val formatter = NSDateFormatter()

    // NSDateFormatter weekday symbols: index 0 = Sunday, 1 = Monday, ..., 6 = Saturday
    // kotlinx DayOfWeek ordinal: 0 = Monday, ..., 6 = Sunday
    // Mapping: (ordinal + 1) % 7 gives the correct NSDateFormatter index
    private fun dayIndex(dayOfWeek: DayOfWeek): Int = (dayOfWeek.ordinal + 1) % 7

    actual fun fullName(dayOfWeek: DayOfWeek): String {
        @Suppress("UNCHECKED_CAST")
        val symbols = formatter.standaloneWeekdaySymbols as List<String>
        return symbols[dayIndex(dayOfWeek)]
    }

    actual fun shortName(dayOfWeek: DayOfWeek): String {
        @Suppress("UNCHECKED_CAST")
        val symbols = formatter.shortStandaloneWeekdaySymbols as List<String>
        return symbols[dayIndex(dayOfWeek)]
    }
}
