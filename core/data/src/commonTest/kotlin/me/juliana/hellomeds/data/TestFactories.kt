// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.database.entities.StockAdjustment
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.data.model.enums.TrackingPrecision

fun createSchedule(
    id: Int = 1,
    medicationId: Int = 1,
    dose: Double = 1.0,
    startDate: Long = LocalDate(2025, 1, 1)
        .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
    endDate: Long? = null,
    timeOfDay: String = "08:00",
    frequencyType: FrequencyType = FrequencyType.INTERVAL,
    frequencyValue: Int = 1,
    daysOfWeek: String? = null,
    isArchived: Boolean = false,
    originTimeZone: String? = null,
    createdAt: Long = 0L,
): Schedule = Schedule(
    id = id,
    medicationId = medicationId,
    dose = dose,
    startDate = startDate,
    endDate = endDate,
    timeOfDay = timeOfDay,
    frequencyType = frequencyType,
    frequencyValue = frequencyValue,
    daysOfWeek = daysOfWeek,
    isArchived = isArchived,
    originTimeZone = originTimeZone,
    createdAt = createdAt,
)

fun createHistory(
    id: Int = 0,
    medicationId: Int = 1,
    scheduleId: Int? = 1,
    scheduledTime: Long? = 0L,
    takenTime: Long? = null,
    scheduledDose: Double = 1.0,
    actualDose: Double? = null,
    status: String = MedicationHistory.STATUS_TAKEN,
): MedicationHistory = MedicationHistory(
    id = id,
    medicationId = medicationId,
    scheduleId = scheduleId,
    scheduledTime = scheduledTime,
    takenTime = takenTime,
    scheduledDose = scheduledDose,
    actualDose = actualDose,
    status = status,
)

fun createProjectedEvent(
    scheduleId: Int = 1,
    medicationId: Int = 1,
    scheduledTime: Long = 0L,
    dose: Double = 1.0,
    historyRecord: MedicationHistory? = null,
): ProjectedEvent = ProjectedEvent(
    scheduleId = scheduleId,
    medicationId = medicationId,
    scheduledTime = scheduledTime,
    dose = dose,
    historyRecord = historyRecord,
)

fun createMedication(
    id: Int = 1,
    name: String = "Test Medication",
    importanceLabelId: Int = 1,
    stockTrackingEnabled: Boolean = false,
    trackingPrecision: TrackingPrecision? = null,
    packagingQuantity: Double? = null,
    lowStockThreshold: Double? = null,
    medicationContainer: MedicationContainer? = null,
    currentStockQuantity: Double? = null,
    depletionReminderEnabled: Boolean = false,
    depletionAlertSent: Boolean = false,
    containerStartedAt: Long? = null,
    timeZoneMode: TimeZoneMode = TimeZoneMode.LOCAL,
    anchorTimeZone: String? = null,
    cycleType: CycleType = CycleType.NONE,
    cycleDaysActive: Int? = null,
    cycleDaysBreak: Int? = null,
    cycleHasPlacebos: Boolean = false,
    cycleStartDate: LocalDate? = null,
): Medication = Medication(
    id = id,
    name = name,
    type = MedicationType.TABLET,
    shape = "",
    importanceLabelId = importanceLabelId,
    foregroundShape = "CIRCLE",
    backgroundShape = "CIRCLE",
    stockTrackingEnabled = stockTrackingEnabled,
    trackingPrecision = trackingPrecision,
    packagingQuantity = packagingQuantity,
    lowStockThreshold = lowStockThreshold,
    medicationContainer = medicationContainer,
    currentStockQuantity = currentStockQuantity,
    depletionReminderEnabled = depletionReminderEnabled,
    depletionAlertSent = depletionAlertSent,
    containerStartedAt = containerStartedAt,
    timeZoneMode = timeZoneMode,
    anchorTimeZone = anchorTimeZone,
    cycleType = cycleType,
    cycleDaysActive = cycleDaysActive,
    cycleDaysBreak = cycleDaysBreak,
    cycleHasPlacebos = cycleHasPlacebos,
    cycleStartDate = cycleStartDate,
)

fun createImportanceLabel(
    id: Int = 1,
    name: String = "Test Label",
    isCritical: Boolean = false,
    hasFollowUps: Boolean = false,
    followUpCount: Int = 0,
    followUpIntervalMinutes: Int = 0,
    criticalAfterFollowUp: Int? = null,
): ImportanceLabel = ImportanceLabel(
    id = id,
    name = name,
    shouldRemind = true,
    isCritical = isCritical,
    hasFollowUps = hasFollowUps,
    followUpCount = followUpCount,
    followUpIntervalMinutes = followUpIntervalMinutes,
    criticalAfterFollowUp = criticalAfterFollowUp,
)

fun createStockAdjustment(
    id: Int = 0,
    medicationId: Int = 1,
    historyId: Int? = null,
    quantityChange: Double = -1.0,
    adjustmentType: StockAdjustmentType = StockAdjustmentType.INTAKE,
    timestamp: Long = 0L,
    notes: String? = null,
): StockAdjustment = StockAdjustment(
    id = id,
    medicationId = medicationId,
    historyId = historyId,
    quantityChange = quantityChange,
    timestamp = timestamp,
    adjustmentType = adjustmentType.value,
    notes = notes,
)

/** Converts a LocalDate + time-of-day string to epoch millis in the system default zone. */
fun LocalDate.toEpochMillis(timeOfDay: String = "00:00"): Long =
    kotlinx.datetime.LocalDateTime(this, LocalTime.parse(timeOfDay))
        .toInstant(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()

/** Converts a LocalDate + time-of-day string to epoch millis in a specific timezone. */
fun LocalDate.toEpochMillis(timeOfDay: String = "00:00", tz: TimeZone): Long =
    kotlinx.datetime.LocalDateTime(this, LocalTime.parse(timeOfDay))
        .toInstant(tz)
        .toEpochMilliseconds()
