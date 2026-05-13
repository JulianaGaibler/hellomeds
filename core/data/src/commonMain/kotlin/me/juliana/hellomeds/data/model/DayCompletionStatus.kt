// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model

import kotlinx.datetime.LocalDate

data class DayCompletionStatus(
    val date: LocalDate,
    val totalScheduled: Int,
    val completed: Int,
) {
    val completionPercentage: Float
        get() = if (totalScheduled > 0) completed.toFloat() / totalScheduled else 0f

    val isFullyCompleted: Boolean
        get() = totalScheduled > 0 && completed == totalScheduled

    val hasScheduledMedications: Boolean
        get() = totalScheduled > 0
}
