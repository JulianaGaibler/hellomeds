// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * IMPORTANT: When adding fields to any data class in this file,
 * also update docs/hellomeds-backup.schema.json
 */

@Serializable
data class BackupData(
    val version: Int = 1,
    val appVersion: String = "",
    val exportedAt: String = "",
    @SerialName("\$schema")
    val schema: String? = null,
    val importanceLabels: List<BackupImportanceLabel> = emptyList(),
    val medications: List<BackupMedication> = emptyList(),
)

@Serializable
data class BackupMedication(
    val name: String,
    val displayName: String? = null,
    val type: String = "TABLET",
    val shape: String = "",
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: String? = null,
    val foregroundShape: String = "CAPSULE_PILL",
    val backgroundShape: String = "CIRCLE",
    val shapeColor: String? = null,
    val strengthValue: Double? = null,
    val strengthUnit: String? = null,
    val importanceLabel: String,
    val displayOrder: Int = 0,
    // Cycle / blister pack
    val cycleType: String? = null,
    val cycleDaysActive: Int? = null,
    val cycleDaysBreak: Int? = null,
    val cycleHasPlacebos: Boolean? = null,
    val cycleStartDate: String? = null,
    // Timezone handling
    val timeZoneMode: String? = null, // "LOCAL" or "FIXED"; null treated as LOCAL
    val stock: BackupStockSettings? = null,
    val schedules: List<BackupSchedule> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
    val stockAdjustments: List<BackupStockAdjustment> = emptyList(),
)

@Serializable
data class BackupSchedule(
    val id: Int,
    val dose: Double = 1.0,
    val startDate: String,
    val endDate: String? = null,
    val timeOfDay: String = "08:00",
    val frequencyType: String = "INTERVAL",
    val frequencyValue: Int = 1,
    val daysOfWeek: String? = null,
    val isArchived: Boolean = false,
    val originTimeZone: String? = null, // IANA timezone ID
    val createdAt: String? = null,
)

@Serializable
data class BackupImportanceLabel(
    val name: String,
    val shouldRemind: Boolean = true,
    val isCritical: Boolean = false,
    val isAlarm: Boolean = false,
    val hasFollowUps: Boolean = false,
    val followUpCount: Int = 0,
    val followUpIntervalMinutes: Int = 0,
    val criticalAfterFollowUp: Int? = null,
    val alarmAfterFollowUp: Int? = null,
    val defaultType: String? = null,
)

@Serializable
data class BackupHistory(
    val scheduleId: Int? = null,
    val scheduledTime: String? = null,
    val takenTime: String? = null,
    val scheduledDose: Double = 1.0,
    val actualDose: Double? = null,
    val status: String = "TAKEN",
    val notes: String? = null,
    val createdAt: String? = null,
    val localId: Int? = null,
)

@Serializable
data class BackupStockSettings(
    val trackingEnabled: Boolean = true,
    val trackingPrecision: String? = null,
    val currentStockQuantity: Double? = null,
    val lowStockThreshold: Double? = null,
    val packagingQuantity: Double? = null,
    val medicationContainer: String? = null,
    val depletionReminderEnabled: Boolean = false,
    val containerStartedAt: String? = null,
)

@Serializable
data class BackupStockAdjustment(
    val quantityChange: Double,
    val timestamp: String? = null,
    val adjustmentType: String = "MANUAL_CORRECTION",
    val notes: String? = null,
    val historyLocalId: Int? = null,
)
