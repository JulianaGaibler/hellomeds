// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model

import me.juliana.hellomeds.data.database.entities.MedicationHistory

/**
 * An in-memory projected medication event generated from schedule definitions.
 * Never persisted to the database. Combined with MedicationHistory records
 * to determine completion status.
 */
data class ProjectedEvent(
    val scheduleId: Int,
    val medicationId: Int,
    val scheduledTime: Long,
    val dose: Double,
    /** Non-null when the user has already acted on this event (taken/skipped). */
    val historyRecord: MedicationHistory? = null,
    /** True when this event falls on a break day with placebo pills in a cyclic schedule. */
    val isPlacebo: Boolean = false,
) {
    /** True when this event corresponds to a real Schedule row (not a PRN dummy). */
    val isScheduled: Boolean get() = scheduleId != 0

    /** True when the user has not yet acted on this event. */
    val isPending: Boolean get() = historyRecord == null

    /** True when the event was taken. */
    val isTaken: Boolean
        get() = historyRecord?.status == MedicationHistory.STATUS_TAKEN

    /** True when the event was skipped (manually or auto). */
    val isSkipped: Boolean
        get() = historyRecord?.status == MedicationHistory.STATUS_SKIPPED ||
            historyRecord?.status == MedicationHistory.STATUS_AUTO_SKIPPED

    /** Unique composite key for deduplication. */
    val compositeKey: String get() = "${medicationId}_${scheduleId}_$scheduledTime"
}
