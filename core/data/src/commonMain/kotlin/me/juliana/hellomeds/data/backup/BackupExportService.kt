// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.backup.model.BackupData
import me.juliana.hellomeds.data.backup.model.BackupHistory
import me.juliana.hellomeds.data.backup.model.BackupImportanceLabel
import me.juliana.hellomeds.data.backup.model.BackupMedication
import me.juliana.hellomeds.data.backup.model.BackupSchedule
import me.juliana.hellomeds.data.backup.model.BackupStockAdjustment
import me.juliana.hellomeds.data.backup.model.BackupStockSettings
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.database.entities.StockAdjustment
import kotlin.time.Clock
import kotlin.time.Instant

class BackupExportService(
    private val medicationDao: MedicationDao,
    private val importanceLabelDao: ImportanceLabelDao,
    private val scheduleDao: ScheduleDao,
    private val historyDao: MedicationHistoryDao,
    private val stockAdjustmentDao: StockAdjustmentDao,
) {

    suspend fun generateBackup(
        selectedMedicationIds: Set<Int>,
        includeSchedules: Boolean,
        includeStockSettings: Boolean,
        includeHistory: Boolean,
        appVersion: String = "unknown",
    ): BackupData {
        val allLabels = importanceLabelDao.getAll().first()
        val labelMap = allLabels.associateBy { it.id }

        val allMedications = medicationDao.getAll().first()
        val selectedMedications = allMedications.filter { it.id in selectedMedicationIds }

        // Collect only the labels used by selected medications
        val usedLabelIds = selectedMedications.map { it.importanceLabelId }.toSet()
        val usedLabels = allLabels.filter { it.id in usedLabelIds }

        val backupMedications = selectedMedications.map { medication ->
            val labelName = labelMap[medication.importanceLabelId]?.name ?: "Silent"

            val schedules = if (includeSchedules) {
                scheduleDao.getByMedicationId(medication.id).first().map { it.toBackup() }
            } else {
                emptyList()
            }

            val stock = if (includeStockSettings && medication.stockTrackingEnabled) {
                medication.toBackupStock()
            } else {
                null
            }

            val history = if (includeHistory) {
                historyDao.getByMedicationId(medication.id).first().map { it.toBackup() }
            } else {
                emptyList()
            }

            val stockAdjustments = if (includeHistory) {
                stockAdjustmentDao.getByMedicationId(medication.id).first().map { it.toBackup() }
            } else {
                emptyList()
            }

            medication.toBackup(
                labelName = labelName,
                schedules = schedules,
                stock = stock,
                history = history,
                stockAdjustments = stockAdjustments,
            )
        }

        return BackupData(
            version = CURRENT_VERSION,
            appVersion = appVersion,
            exportedAt = Clock.System.now().toString(),
            schema = SCHEMA_NAME,
            importanceLabels = usedLabels.map { it.toBackup() },
            medications = backupMedications,
        )
    }

    fun serialize(backup: BackupData): String {
        return backupJson.encodeToString(BackupData.serializer(), backup)
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val SCHEMA_NAME = "hellomeds-backup.schema.json"
    }
}

private fun ImportanceLabel.toBackup() = BackupImportanceLabel(
    name = name,
    shouldRemind = shouldRemind,
    isCritical = isCritical,
    isAlarm = isAlarm,
    hasFollowUps = hasFollowUps,
    followUpCount = followUpCount,
    followUpIntervalMinutes = followUpIntervalMinutes,
    criticalAfterFollowUp = criticalAfterFollowUp,
    alarmAfterFollowUp = alarmAfterFollowUp,
    defaultType = defaultType,
)

private fun Medication.toBackup(
    labelName: String,
    schedules: List<BackupSchedule>,
    stock: BackupStockSettings?,
    history: List<BackupHistory>,
    stockAdjustments: List<BackupStockAdjustment>,
) = BackupMedication(
    name = name,
    displayName = displayName,
    type = type.name,
    shape = shape,
    notes = notes,
    isArchived = isArchived,
    createdAt = millisToIso(createdAt),
    foregroundShape = foregroundShape,
    backgroundShape = backgroundShape,
    shapeColor = shapeColor,
    strengthValue = strengthValue,
    strengthUnit = strengthUnit?.name,
    importanceLabel = labelName,
    displayOrder = displayOrder,
    cycleType = cycleType.name,
    cycleDaysActive = cycleDaysActive,
    cycleDaysBreak = cycleDaysBreak,
    cycleHasPlacebos = cycleHasPlacebos,
    cycleStartDate = cycleStartDate?.toString(),
    timeZoneMode = timeZoneMode.name,
    stock = stock,
    schedules = schedules,
    history = history,
    stockAdjustments = stockAdjustments,
)

private fun Medication.toBackupStock() = BackupStockSettings(
    trackingEnabled = stockTrackingEnabled,
    trackingPrecision = trackingPrecision?.name,
    currentStockQuantity = currentStockQuantity,
    lowStockThreshold = lowStockThreshold,
    packagingQuantity = packagingQuantity,
    medicationContainer = medicationContainer?.name,
    depletionReminderEnabled = depletionReminderEnabled,
    containerStartedAt = containerStartedAt?.let { millisToIso(it) },
)

private fun Schedule.toBackup() = BackupSchedule(
    id = id,
    dose = dose,
    startDate = millisToDate(startDate),
    endDate = endDate?.let { millisToDate(it) },
    timeOfDay = timeOfDay,
    frequencyType = frequencyType.name,
    frequencyValue = frequencyValue,
    daysOfWeek = daysOfWeek,
    isArchived = isArchived,
    originTimeZone = originTimeZone,
    createdAt = millisToIso(createdAt),
)

private fun MedicationHistory.toBackup() = BackupHistory(
    scheduleId = scheduleId,
    scheduledTime = scheduledTime?.let { millisToIso(it) },
    takenTime = takenTime?.let { millisToIso(it) },
    scheduledDose = scheduledDose,
    actualDose = actualDose,
    status = status,
    notes = notes,
    createdAt = millisToIso(createdAt),
    localId = id,
)

private fun StockAdjustment.toBackup() = BackupStockAdjustment(
    quantityChange = quantityChange,
    timestamp = millisToIso(timestamp),
    adjustmentType = adjustmentType,
    notes = notes,
    historyLocalId = historyId,
)

private fun millisToIso(millis: Long): String = Instant.fromEpochMilliseconds(millis).toString()

private fun millisToDate(millis: Long): String = Instant.fromEpochMilliseconds(millis)
    .toLocalDateTime(TimeZone.currentSystemDefault()).date
    .toString()
