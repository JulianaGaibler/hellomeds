// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import me.juliana.hellomeds.data.util.currentTimeMillis

/**
 * Records past medication actions (taken, skipped, auto-skipped).
 * Future events are never stored — they are projected on-the-fly by ScheduleProjector.
 *
 * Replaces the old dual-purpose LogEvent entity.
 */
@Entity(
    tableName = "medication_history",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Schedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.SET_NULL, // History survives schedule deletion
        ),
    ],
    indices = [
        Index("medicationId"),
        Index("scheduleId"),
        Index("scheduledTime"),
        // Structural guarantee against duplicate scheduled-dose log entries. PRN
        // ("as-needed") doses with NULL scheduleId/scheduledTime remain valid:
        // SQLite treats NULL values in UNIQUE indexes as distinct, so multiple
        // PRN rows per medication are allowed.
        Index(
            value = ["medicationId", "scheduleId", "scheduledTime"],
            unique = true,
            name = "index_medication_history_dedup",
        ),
    ],
)
data class MedicationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicationId: Int,
    val scheduleId: Int? = null,
    val scheduledTime: Long?, // When was this scheduled? null for as-needed doses
    val takenTime: Long? = null, // When was it actually taken? null for skipped
    val scheduledDose: Double,
    val actualDose: Double? = null,
    val status: String, // TAKEN, SKIPPED, AUTO_SKIPPED
    val notes: String? = null,
    val createdAt: Long = currentTimeMillis(),
) {
    companion object {
        const val STATUS_TAKEN = "TAKEN"
        const val STATUS_SKIPPED = "SKIPPED"
        const val STATUS_AUTO_SKIPPED = "AUTO_SKIPPED"
    }
}
