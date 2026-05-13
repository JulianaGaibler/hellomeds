// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.util.currentTimeMillis

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("medicationId"),
        Index(value = ["isArchived", "endDate"]),
    ],
)
data class Schedule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicationId: Int,
    val dose: Double,
    val startDate: Long,
    val endDate: Long? = null,
    val timeOfDay: String,
    val frequencyType: FrequencyType,
    val frequencyValue: Int,
    val daysOfWeek: String? = null,
    val isArchived: Boolean = false,
    val originTimeZone: String? = null, // IANA timezone ID captured at creation (e.g., "America/New_York")
    val createdAt: Long = currentTimeMillis(),
) {
    /**
     * Returns true if the schedule is effectively archived.
     * This includes both manually archived schedules (isArchived = true)
     * and schedules with an end date in the past (date-archived).
     */
    fun isEffectivelyArchived(): Boolean {
        return isArchived || isDateArchived()
    }

    /**
     * Returns true if the schedule has an end date that is in the past (before today).
     */
    fun isDateArchived(): Boolean {
        return endDate != null && endDate < getTodayStartOfDayMillis()
    }

    /**
     * Returns true if this schedule was manually archived (isArchived flag set).
     */
    fun isManuallyArchived(): Boolean {
        return isArchived
    }

    private fun getTodayStartOfDayMillis(): Long {
        val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
        val today = kotlin.time.Clock.System.now().toLocalDateTime(tz).date
        return today.atStartOfDayIn(tz).toEpochMilliseconds()
    }
}
