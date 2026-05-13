// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.CycleType

/**
 * Information about where a date falls within a medication's cycle.
 * Used by both the ScheduleProjector mask and UI components (CycleProgressIndicator).
 */
data class CycleDayInfo(
    /** Zero-based day within the current cycle (0 = first active day). */
    val dayInCycle: Int,
    /** Total cycle length (active + break days). */
    val cycleLength: Int,
    /** True if this day is in the active period, false if in the break period. */
    val isActive: Boolean,
)

/**
 * Calculate where a date falls in a medication's cycle.
 * Returns null if the medication is not cyclic or has invalid cycle config.
 *
 * Uses strict modulo to handle dates before the anchor correctly.
 */
fun getCycleDay(medication: Medication, date: LocalDate): CycleDayInfo? {
    if (medication.cycleType != CycleType.CYCLIC) return null

    val daysActive = medication.cycleDaysActive ?: return null
    val daysBreak = medication.cycleDaysBreak ?: return null
    val cycleLength = daysActive + daysBreak
    if (cycleLength <= 0) return null

    val anchorDate = medication.cycleStartDate ?: return null

    val daysSinceAnchor = anchorDate.daysUntil(date)
    // Strict modulo: Kotlin's % is remainder (can be negative), so wrap correctly
    val dayInCycle = ((daysSinceAnchor % cycleLength) + cycleLength) % cycleLength

    return CycleDayInfo(
        dayInCycle = dayInCycle,
        cycleLength = cycleLength,
        isActive = dayInCycle < daysActive,
    )
}
