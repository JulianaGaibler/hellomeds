// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import kotlinx.datetime.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

actual object DayOfWeekFormatter {
    actual fun fullName(dayOfWeek: DayOfWeek): String {
        return java.time.DayOfWeek.valueOf(dayOfWeek.name)
            .getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

    actual fun shortName(dayOfWeek: DayOfWeek): String {
        return java.time.DayOfWeek.valueOf(dayOfWeek.name)
            .getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}
