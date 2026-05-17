// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import me.juliana.hellomeds.data.backup.model.BackupData
import me.juliana.hellomeds.data.backup.model.BackupHistory
import me.juliana.hellomeds.data.backup.model.BackupImportanceLabel
import me.juliana.hellomeds.data.backup.model.BackupMedication
import me.juliana.hellomeds.data.backup.model.BackupSchedule
import me.juliana.hellomeds.data.backup.model.BackupStockAdjustment
import me.juliana.hellomeds.data.backup.model.ImportAnalysis
import me.juliana.hellomeds.data.backup.model.ImportDecision
import me.juliana.hellomeds.data.backup.model.ImportResult
import me.juliana.hellomeds.data.backup.model.ImportWarning
import me.juliana.hellomeds.data.backup.model.LabelImportInfo
import me.juliana.hellomeds.data.backup.model.MedicationImportInfo
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.AppDatabase
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.database.entities.StockAdjustment
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import kotlin.time.Instant

class BackupImportService(
    private val database: AppDatabase,
    private val medicationDao: MedicationDao,
    private val importanceLabelDao: ImportanceLabelDao,
    private val scheduleDao: ScheduleDao,
    private val historyDao: MedicationHistoryDao,
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val reconciler: ScheduleReconciler,
) {

    fun parseBackup(jsonString: String): Result<BackupData> {
        return try {
            val data = backupJson.decodeFromString(BackupData.serializer(), jsonString)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeImport(backup: BackupData): ImportAnalysis {
        val warnings = mutableListOf<ImportWarning>()
        val errors = mutableListOf<String>()

        // Check version
        if (backup.version > BackupExportService.CURRENT_VERSION) {
            warnings.add(ImportWarning.NewerVersion)
        }

        // Analyze labels
        val existingLabels = importanceLabelDao.getAll().first()
        val labelInfos = backup.importanceLabels.map { backupLabel ->
            val exactMatch = existingLabels.find { existing ->
                existing.name == backupLabel.name &&
                    existing.shouldRemind == backupLabel.shouldRemind &&
                    existing.isCritical == backupLabel.isCritical &&
                    existing.hasFollowUps == backupLabel.hasFollowUps &&
                    existing.followUpCount == backupLabel.followUpCount &&
                    existing.followUpIntervalMinutes == backupLabel.followUpIntervalMinutes &&
                    existing.criticalAfterFollowUp == backupLabel.criticalAfterFollowUp
            }
            LabelImportInfo(backupLabel = backupLabel, existingMatch = exactMatch)
        }

        // Analyze medications
        val existingMedications = medicationDao.getAll().first()
        val hasHistory = backup.medications.any { it.history.isNotEmpty() }
        val hasStockAdjustments = backup.medications.any { it.stockAdjustments.isNotEmpty() }

        val medicationInfos = backup.medications.mapIndexed { index, backupMed ->
            val medWarnings = mutableListOf<ImportWarning>()
            var hasErrors = false
            var errorMessage: String? = null

            // Check for missing required fields
            if (backupMed.name.isBlank()) {
                hasErrors = true
                errorMessage = "Medication has no name"
            }

            // Check for unknown enum values
            if (parseMedicationType(backupMed.type) == null) {
                medWarnings.add(ImportWarning.UnknownMedicationType(backupMed.type))
            }
            val strengthUnit = backupMed.strengthUnit
            if (strengthUnit != null && parseMedicationStrengthUnit(strengthUnit) == null) {
                medWarnings.add(ImportWarning.UnknownStrengthUnit(strengthUnit))
            }
            backupMed.schedules.forEachIndexed { schedIndex, schedule ->
                if (parseFrequencyType(schedule.frequencyType) == null) {
                    medWarnings.add(ImportWarning.UnknownFrequencyType(schedIndex + 1, schedule.frequencyType))
                }
            }
            val stock = backupMed.stock
            if (stock != null) {
                val precision = stock.trackingPrecision
                if (precision != null && parseTrackingPrecision(precision) == null) {
                    medWarnings.add(ImportWarning.UnknownTrackingPrecision(precision))
                }
                val container = stock.medicationContainer
                if (container != null && parseMedicationContainer(container) == null) {
                    medWarnings.add(ImportWarning.UnknownContainerType(container))
                }
            }

            // Check label reference
            val labelInBackup = backup.importanceLabels.find { it.name == backupMed.importanceLabel }
            if (labelInBackup == null) {
                // Label not in backup — check if it exists in DB or warn
                val existingLabel = existingLabels.find { it.name == backupMed.importanceLabel }
                if (existingLabel == null) {
                    medWarnings.add(ImportWarning.LabelNotFound(backupMed.importanceLabel))
                }
            }

            // Duplicate detection: match on name AND displayName (case-insensitive)
            val duplicate = existingMedications.find { existing ->
                existing.name.equals(backupMed.name, ignoreCase = true) &&
                    existing.displayName.equals(backupMed.displayName, ignoreCase = true)
            }

            if (medWarnings.isNotEmpty()) {
                warnings.addAll(medWarnings)
            }

            MedicationImportInfo(
                index = index,
                backupMedication = backupMed,
                duplicateOf = duplicate,
                validationWarnings = medWarnings,
                hasErrors = hasErrors,
                errorMessage = errorMessage,
            )
        }

        return ImportAnalysis(
            medications = medicationInfos,
            labels = labelInfos,
            warnings = warnings,
            errors = errors,
            hasHistory = hasHistory,
            hasStockAdjustments = hasStockAdjustments,
        )
    }

    suspend fun executeImport(backup: BackupData, decisions: Map<Int, ImportDecision>): ImportResult {
        var medicationsImported = 0
        var medicationsReplaced = 0
        var medicationsSkipped = 0
        var schedulesImported = 0
        var labelsCreated = 0
        var historyImported = 0
        var stockAdjustmentsImported = 0

        val existingLabels = importanceLabelDao.getAll().first()
        val labelNameToId = mutableMapOf<String, Int>()
        existingLabels.forEach { labelNameToId[it.name] = it.id }

        for (backupLabel in backup.importanceLabels) {
            val exactMatch = existingLabels.find { existing ->
                existing.name == backupLabel.name &&
                    existing.shouldRemind == backupLabel.shouldRemind &&
                    existing.isCritical == backupLabel.isCritical &&
                    existing.hasFollowUps == backupLabel.hasFollowUps &&
                    existing.followUpCount == backupLabel.followUpCount &&
                    existing.followUpIntervalMinutes == backupLabel.followUpIntervalMinutes &&
                    existing.criticalAfterFollowUp == backupLabel.criticalAfterFollowUp
            }

            if (exactMatch != null) {
                labelNameToId[backupLabel.name] = exactMatch.id
            } else {
                // Same name with different config — disambiguate to avoid clobbering existing label.
                val nameMatch = existingLabels.find { it.name == backupLabel.name }
                val labelName = if (nameMatch != null) {
                    "${backupLabel.name} (imported)"
                } else {
                    backupLabel.name
                }
                val newId = importanceLabelDao.insert(backupLabel.toEntity(labelName))
                labelNameToId[backupLabel.name] = newId.toInt()
                labelsCreated++
            }
        }

        val existingMedications = medicationDao.getAll().first()

        for (backupMed in backup.medications) {
            val index = backup.medications.indexOf(backupMed)
            val decision = decisions[index] ?: ImportDecision.IMPORT_AS_NEW

            if (decision == ImportDecision.SKIP) {
                medicationsSkipped++
                continue
            }

            if (backupMed.name.isBlank()) {
                medicationsSkipped++
                continue
            }

            // Resolve label ID
            val labelId = labelNameToId[backupMed.importanceLabel]
                ?: existingLabels.firstOrNull()?.id
                ?: 1 // Fallback to ID 1 (Silent)

            val medication = backupMed.toEntity(labelId)

            val medicationId: Int
            if (decision == ImportDecision.REPLACE) {
                val duplicate = existingMedications.find { existing ->
                    existing.name.equals(backupMed.name, ignoreCase = true) &&
                        existing.displayName.equals(backupMed.displayName, ignoreCase = true)
                }
                if (duplicate != null) {
                    // Update existing, preserving its ID
                    medicationDao.update(medication.copy(id = duplicate.id))
                    medicationId = duplicate.id
                    // Delete old schedules, history, and stock adjustments for this medication.
                    // History first: StockAdjustment FK cascades from MedicationHistory deletion.
                    scheduleDao.getByMedicationId(duplicate.id).first().forEach {
                        scheduleDao.delete(it)
                    }
                    historyDao.deleteByMedicationId(duplicate.id)
                    stockAdjustmentDao.deleteByMedicationId(duplicate.id)
                    medicationsReplaced++
                } else {
                    // Duplicate no longer exists — import as new
                    medicationId = medicationDao.insert(medication).toInt()
                    medicationsImported++
                }
            } else {
                // IMPORT_AS_NEW
                medicationId = medicationDao.insert(medication).toInt()
                medicationsImported++
            }

            // Import schedules and build oldId -> newId map
            val oldToNewScheduleId = mutableMapOf<Int, Int>()
            for (backupSchedule in backupMed.schedules) {
                val newScheduleId = scheduleDao.insert(backupSchedule.toEntity(medicationId)).toInt()
                oldToNewScheduleId[backupSchedule.id] = newScheduleId
                schedulesImported++
            }

            // Import history, linking to schedules via the ID map and building history ID map
            val oldToNewHistoryId = mutableMapOf<Int, Int>()
            for (backupHistoryEntry in backupMed.history) {
                val mappedScheduleId = backupHistoryEntry.scheduleId?.let { oldToNewScheduleId[it] }
                val newId = historyDao.insert(backupHistoryEntry.toEntity(medicationId, mappedScheduleId)).toInt()
                backupHistoryEntry.localId?.let { oldToNewHistoryId[it] = newId }
                historyImported++
            }

            // Import stock adjustments, remapping historyId via the history ID map
            for (backupAdjustment in backupMed.stockAdjustments) {
                val remappedHistoryId = backupAdjustment.historyLocalId?.let { oldToNewHistoryId[it] }
                stockAdjustmentDao.insert(backupAdjustment.toEntity(medicationId, remappedHistoryId))
                stockAdjustmentsImported++
            }

            // Reconcile cached stock with actual ledger.
            // Only when adjustments were imported — if the backup omitted history,
            // currentStockQuantity on the entity is the authoritative value.
            if (backupMed.stockAdjustments.isNotEmpty()) {
                val actualStock = stockAdjustmentDao.getCurrentStock(medicationId) ?: 0.0
                medicationDao.updateCachedStockQuantity(medicationId, actualStock)
            }
        }

        // Normalize displayOrder for all active medications to eliminate collisions
        val allActive = medicationDao.getActive().first()
        allActive.forEachIndexed { index, med ->
            if (med.displayOrder != index) {
                medicationDao.updateDisplayOrder(med.id, index)
            }
        }

        // Post-import: reconcile alarms
        reconciler.reconcile()

        return ImportResult(
            medicationsImported = medicationsImported,
            medicationsReplaced = medicationsReplaced,
            medicationsSkipped = medicationsSkipped,
            schedulesImported = schedulesImported,
            labelsCreated = labelsCreated,
            historyImported = historyImported,
            stockAdjustmentsImported = stockAdjustmentsImported,
        )
    }
}

private fun BackupImportanceLabel.toEntity(name: String = this.name) = ImportanceLabel(
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

private fun BackupMedication.toEntity(importanceLabelId: Int) = Medication(
    name = name,
    displayName = displayName,
    type = parseMedicationType(type) ?: MedicationType.TABLET,
    shape = shape,
    notes = notes,
    isArchived = isArchived,
    createdAt = parseIsoToMillis(createdAt) ?: kotlin.time.Clock.System.now().toEpochMilliseconds(),
    foregroundShape = foregroundShape,
    backgroundShape = backgroundShape,
    shapeColor = shapeColor,
    strengthValue = strengthValue,
    strengthUnit = strengthUnit?.let { parseMedicationStrengthUnit(it) },
    importanceLabelId = importanceLabelId,
    displayOrder = displayOrder,
    stockTrackingEnabled = stock?.trackingEnabled ?: false,
    trackingPrecision = stock?.trackingPrecision?.let { parseTrackingPrecision(it) },
    currentStockQuantity = stock?.currentStockQuantity,
    lowStockThreshold = stock?.lowStockThreshold,
    lowStockAlertSent = false,
    packagingQuantity = stock?.packagingQuantity,
    medicationContainer = stock?.medicationContainer?.let { parseMedicationContainer(it) },
    depletionReminderEnabled = stock?.depletionReminderEnabled ?: false,
    depletionAlertSent = false,
    containerStartedAt = stock?.containerStartedAt?.let { parseIsoToMillis(it) },
    cycleType = cycleType?.let { parseCycleType(it) } ?: CycleType.NONE,
    cycleDaysActive = cycleDaysActive,
    cycleDaysBreak = cycleDaysBreak,
    cycleHasPlacebos = cycleHasPlacebos ?: false,
    cycleStartDate = cycleStartDate?.let { parseLocalDate(it) },
    timeZoneMode = timeZoneMode?.let { parseTimeZoneMode(it) } ?: TimeZoneMode.LOCAL,
)

private fun BackupSchedule.toEntity(medicationId: Int) = Schedule(
    medicationId = medicationId,
    dose = dose,
    startDate = parseDateToMillis(startDate) ?: kotlin.time.Clock.System.now().toEpochMilliseconds(),
    endDate = endDate?.let { parseDateToMillis(it) },
    timeOfDay = timeOfDay,
    frequencyType = parseFrequencyType(frequencyType) ?: FrequencyType.INTERVAL,
    frequencyValue = frequencyValue,
    daysOfWeek = daysOfWeek,
    isArchived = isArchived,
    originTimeZone = originTimeZone,
    createdAt = parseIsoToMillis(createdAt) ?: kotlin.time.Clock.System.now().toEpochMilliseconds(),
)

private fun BackupHistory.toEntity(medicationId: Int, scheduleId: Int? = null) = MedicationHistory(
    medicationId = medicationId,
    scheduleId = scheduleId,
    scheduledTime = scheduledTime?.let { parseIsoToMillis(it) },
    takenTime = takenTime?.let { parseIsoToMillis(it) },
    scheduledDose = scheduledDose,
    actualDose = actualDose,
    status = if (status in listOf("TAKEN", "SKIPPED", "AUTO_SKIPPED")) status else "TAKEN",
    notes = notes,
    createdAt = parseIsoToMillis(createdAt) ?: kotlin.time.Clock.System.now().toEpochMilliseconds(),
)

private fun BackupStockAdjustment.toEntity(medicationId: Int, remappedHistoryId: Int? = null) = StockAdjustment(
    medicationId = medicationId,
    historyId = remappedHistoryId,
    quantityChange = quantityChange,
    timestamp = parseIsoToMillis(timestamp) ?: kotlin.time.Clock.System.now().toEpochMilliseconds(),
    adjustmentType = adjustmentType,
    notes = notes,
)

internal fun parseMedicationType(value: String): MedicationType? = try {
    MedicationType.valueOf(value)
} catch (_: Exception) {
    null
}

internal fun parseMedicationStrengthUnit(value: String): MedicationStrengthUnit? = try {
    MedicationStrengthUnit.valueOf(value)
} catch (_: Exception) {
    null
}

internal fun parseFrequencyType(value: String): FrequencyType? = try {
    FrequencyType.valueOf(value)
} catch (_: Exception) {
    null
}

internal fun parseTrackingPrecision(value: String): TrackingPrecision? = try {
    TrackingPrecision.valueOf(value)
} catch (_: Exception) {
    null
}

internal fun parseCycleType(value: String): CycleType? = try {
    CycleType.valueOf(value)
} catch (_: Exception) {
    null
}

internal fun parseTimeZoneMode(value: String): TimeZoneMode? = try {
    TimeZoneMode.valueOf(value)
} catch (_: Exception) {
    null
}

internal fun parseLocalDate(value: String): LocalDate? = try {
    LocalDate.parse(value)
} catch (_: Exception) {
    null
}

internal fun parseMedicationContainer(value: String): MedicationContainer? = try {
    MedicationContainer.valueOf(value)
} catch (_: Exception) {
    null
}

internal fun parseIsoToMillis(iso: String?): Long? {
    if (iso == null) return null
    return try {
        Instant.parse(iso).toEpochMilliseconds()
    } catch (_: Exception) {
        null
    }
}

internal fun parseDateToMillis(dateStr: String): Long? {
    return try {
        LocalDate.parse(dateStr)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    } catch (_: Exception) {
        null
    }
}
