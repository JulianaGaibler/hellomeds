// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

/**
 * Severity of a scheduled-dose event relative to the current clock.
 *
 * The full taxonomy includes `PENDING` (future) for completeness — that case is
 * the responsibility of the Tracking screen, not the missed-dose detector.
 * The detector emits only past-due events ([OVERDUE] / [MISSED]), since the
 * "Did you take these?" recovery prompt is past-only by definition.
 */
enum class DoseSeverity {
    /** Future event. Not emitted by [MissedDoseDetector]. */
    PENDING,

    /** Past, within the 1-hour grace period — still reasonable to take. */
    OVERDUE,

    /** Past, beyond the grace period. Surface for awareness; discourage retroactive logging. */
    MISSED,
}

/**
 * A single scheduled dose that should have fired but never did.
 * Emitted by [MissedDoseDetector] in chronological order.
 */
data class MissedDose(
    val medicationId: Int,
    val medicationName: String,
    val scheduleId: Int,
    val scheduledTime: Long,
    val dose: Double,
    val severity: DoseSeverity,
)
