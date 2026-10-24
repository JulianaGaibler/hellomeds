// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.TimeZone
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.util.currentTimeMillis
import me.juliana.hellomeds.data.util.startOfDayMillis

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
    /**
     * Earliest `scheduledTime` that `UnloggedDoseResolver` may auto-resolve for this
     * schedule. Bumped when the past projection changes (timing, timezone or cycle edits,
     * backup import) so re-projected events are not mistaken for unlogged doses. 0 means
     * unset, and `ScheduleRepository` fills it on insert.
     */
    @ColumnInfo(defaultValue = "0")
    val resolveFrom: Long = 0,
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
        return endDate != null && endDate < startOfDayMillis()
    }

    /**
     * Returns true if this schedule was manually archived (isArchived flag set).
     */
    fun isManuallyArchived(): Boolean {
        return isArchived
    }

    /** The zone `startDate` and `endDate` were stored in as local midnight. */
    fun dateZone(fallback: TimeZone = TimeZone.currentSystemDefault()): TimeZone =
        originTimeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: fallback

    /**
     * True when changing from [old] to this alters which past instants this schedule
     * projects to. An end-date change counts: extending a past end date makes days the
     * user was never reminded about look like unlogged doses.
     */
    fun timingDiffersFrom(old: Schedule): Boolean = timeOfDay != old.timeOfDay ||
        frequencyType != old.frequencyType ||
        frequencyValue != old.frequencyValue ||
        daysOfWeek != old.daysOfWeek ||
        startDate != old.startDate ||
        endDate != old.endDate ||
        originTimeZone != old.originTimeZone
}
