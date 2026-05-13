// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.FrequencyType

// Shared data classes used by both Android and iOS debug screens

data class TodayDoseInfo(
    val event: ProjectedEvent,
    val medicationName: String,
    val dose: Double,
    val strengthUnit: String?,
    val scheduleDescription: String,
    val isOverdue: Boolean,
)

data class TodayOverview(
    val doses: List<TodayDoseInfo> = emptyList(),
    val totalCount: Int = 0,
    val takenCount: Int = 0,
    val skippedCount: Int = 0,
    val pendingCount: Int = 0,
    val overdueCount: Int = 0,
)

data class DetailedAlarmInfo(
    val event: ProjectedEvent,
    val medication: Medication?,
    val schedule: Schedule?,
    val importanceLabel: ImportanceLabel?,
)

data class PreferencesInfo(
    val onboardingCompleted: Boolean = false,
    val useExactAlarms: Boolean = true,
    val groupingMode: String = "COMBINED",
    val schedulingWindowHours: Int = 24,
    val lastSchedulingTimestamp: Long = 0L,
    val snoozeIntervalMinutes: Int = 10,
    val lockScreenVisibility: String = "SHOW_WITH_NAMES",
    val detectionMethod: String = "HEURISTIC",
    val useDynamicColor: Boolean = true,
)

data class DatabaseHealth(
    val activeMedicationCount: Int = 0,
    val activeScheduleCount: Int = 0,
    val activeSessionCount: Int = 0,
    val encryptionKeyPresent: Boolean = false,
    val cipherVersion: String? = null,
)

fun Schedule.toHumanDescription(): String = when (frequencyType) {
    FrequencyType.INTERVAL -> if (frequencyValue == 1) "Every day" else "Every $frequencyValue days"
    FrequencyType.DAYS_OF_WEEK -> daysOfWeek?.split(",")
        ?.joinToString(", ") { it.trim().take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
        ?: "Unknown"
}
