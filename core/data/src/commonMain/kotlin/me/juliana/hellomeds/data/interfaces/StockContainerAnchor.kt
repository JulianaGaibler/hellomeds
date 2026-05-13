// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.interfaces

/**
 * Re-anchors [containerStartedAt][me.juliana.hellomeds.data.database.entities.Medication.containerStartedAt]
 * when the consumption rate changes for ESTIMATED-precision medications.
 *
 * ESTIMATED tracking estimates the current container's remaining doses from
 * `packagingQuantity - (daysSince * dailyRate)`. When a schedule mutation changes the
 * daily rate, the old `containerStartedAt` would cause the estimate to retroactively
 * use the new rate for the entire period, producing a wrong value.
 *
 * The fix: snapshot the current remaining (under the old rate) before the mutation,
 * then back-calculate a new `containerStartedAt` that produces the same remaining
 * under the new rate.
 *
 * Called by [me.juliana.hellomeds.data.repository.ScheduleRepository] around every
 * schedule mutation. Returns null from [snapshotBeforeRateChange] to skip re-anchoring
 * when the medication is not ESTIMATED or lacks the required fields.
 */
interface StockContainerAnchor {

    /**
     * Capture the current estimated remaining doses for the open container
     * using the current (soon-to-be-old) schedules.
     *
     * @return The remaining dose estimate, or null if re-anchoring is not applicable.
     */
    suspend fun snapshotBeforeRateChange(medicationId: Int): Double?

    /**
     * Re-anchor `containerStartedAt` so that the [previousRemaining] is preserved
     * under the new schedule rate.
     *
     * No-op if [previousRemaining] is null.
     */
    suspend fun reanchorAfterRateChange(medicationId: Int, previousRemaining: Double?)
}
