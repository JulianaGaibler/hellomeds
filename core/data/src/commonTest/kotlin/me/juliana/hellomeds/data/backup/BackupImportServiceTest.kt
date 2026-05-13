// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import kotlinx.coroutines.test.runTest
import me.juliana.hellomeds.data.backup.model.BackupData
import me.juliana.hellomeds.data.backup.model.BackupHistory
import me.juliana.hellomeds.data.backup.model.BackupImportanceLabel
import me.juliana.hellomeds.data.backup.model.BackupMedication
import me.juliana.hellomeds.data.backup.model.BackupSchedule
import me.juliana.hellomeds.data.backup.model.BackupStockAdjustment
import me.juliana.hellomeds.data.backup.model.BackupStockSettings
import me.juliana.hellomeds.data.backup.model.ImportDecision
import me.juliana.hellomeds.data.backup.model.ImportWarning
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.MedicationType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupImportServiceTest {

    private lateinit var labelDao: FakeImportanceLabelDao
    private lateinit var medDao: FakeMedicationDao
    private lateinit var scheduleDao: FakeScheduleDao
    private lateinit var historyDao: FakeHistoryDao
    private lateinit var stockAdjDao: FakeStockAdjustmentDao
    private lateinit var reconciler: NoOpReconciler
    private lateinit var service: BackupImportService

    @BeforeTest
    fun setup() {
        labelDao = FakeImportanceLabelDao()
        medDao = FakeMedicationDao()
        scheduleDao = FakeScheduleDao()
        historyDao = FakeHistoryDao()
        stockAdjDao = FakeStockAdjustmentDao()
        reconciler = NoOpReconciler()
        val fakeDb = FakeAppDatabase(labelDao, medDao, scheduleDao, historyDao, stockAdjDao)
        service = BackupImportService(fakeDb, medDao, labelDao, scheduleDao, historyDao, stockAdjDao, reconciler)
    }

    // --- Helper factories ---

    private fun label(
        name: String,
        shouldRemind: Boolean = true,
        isCritical: Boolean = false,
        hasFollowUps: Boolean = false,
        followUpCount: Int = 0,
        followUpIntervalMinutes: Int = 0,
        criticalAfterFollowUp: Int? = null,
    ) = BackupImportanceLabel(
        name = name,
        shouldRemind = shouldRemind,
        isCritical = isCritical,
        hasFollowUps = hasFollowUps,
        followUpCount = followUpCount,
        followUpIntervalMinutes = followUpIntervalMinutes,
        criticalAfterFollowUp = criticalAfterFollowUp,
    )

    private fun med(
        name: String,
        displayName: String? = null,
        importanceLabel: String = "Silent",
        type: String = "TABLET",
        strengthUnit: String? = null,
        stock: BackupStockSettings? = null,
        schedules: List<BackupSchedule> = emptyList(),
        history: List<BackupHistory> = emptyList(),
        stockAdjustments: List<BackupStockAdjustment> = emptyList(),
    ) = BackupMedication(
        name = name,
        displayName = displayName,
        importanceLabel = importanceLabel,
        type = type,
        strengthUnit = strengthUnit,
        stock = stock,
        schedules = schedules,
        history = history,
        stockAdjustments = stockAdjustments,
    )

    private fun schedule(id: Int, frequencyType: String = "INTERVAL") = BackupSchedule(
        id = id,
        dose = 1.0,
        startDate = "2025-01-01",
        timeOfDay = "08:00",
        frequencyType = frequencyType,
        frequencyValue = 1,
    )

    private fun historyEntry(scheduleId: Int, status: String = "TAKEN") = BackupHistory(
        scheduleId = scheduleId,
        scheduledTime = "2025-01-01T08:00:00Z",
        scheduledDose = 1.0,
        status = status,
    )

    private fun existingLabel(
        id: Int,
        name: String,
        shouldRemind: Boolean = true,
        isCritical: Boolean = false,
        hasFollowUps: Boolean = false,
        followUpCount: Int = 0,
        followUpIntervalMinutes: Int = 0,
        criticalAfterFollowUp: Int? = null,
    ) = ImportanceLabel(
        id = id,
        name = name,
        shouldRemind = shouldRemind,
        isCritical = isCritical,
        hasFollowUps = hasFollowUps,
        followUpCount = followUpCount,
        followUpIntervalMinutes = followUpIntervalMinutes,
        criticalAfterFollowUp = criticalAfterFollowUp,
    )

    private fun existingMed(id: Int, name: String, displayName: String? = null, labelId: Int = 1) = Medication(
        id = id,
        name = name,
        displayName = displayName,
        type = MedicationType.TABLET,
        shape = "",
        importanceLabelId = labelId,
        foregroundShape = "CIRCLE",
        backgroundShape = "CIRCLE",
    )

    private fun existingSchedule(id: Int, medicationId: Int) = Schedule(
        id = id,
        medicationId = medicationId,
        dose = 1.0,
        startDate = 0L,
        timeOfDay = "08:00",
        frequencyType = FrequencyType.INTERVAL,
        frequencyValue = 1,
    )

    private fun backup(
        medications: List<BackupMedication> = emptyList(),
        labels: List<BackupImportanceLabel> = emptyList(),
        version: Int = 1,
    ) = BackupData(version = version, importanceLabels = labels, medications = medications)

    // ================================================================
    // analyzeImport()
    // ================================================================

    @Test
    fun analyzeImport_exactLabelMatch_markedAsExisting() = runTest {
        val existing = existingLabel(1, "Critical", isCritical = true)
        labelDao.seed(existing)

        val analysis = service.analyzeImport(
            backup(
                labels = listOf(label("Critical", isCritical = true)),
            ),
        )

        assertEquals(1, analysis.labels.size)
        assertNotNull(analysis.labels[0].existingMatch)
        assertEquals(existing.id, analysis.labels[0].existingMatch!!.id)
    }

    @Test
    fun analyzeImport_labelConfigMismatch_noMatch() = runTest {
        labelDao.seed(existingLabel(1, "Critical", isCritical = true))

        val analysis = service.analyzeImport(
            backup(
                labels = listOf(label("Critical", isCritical = false)),
            ),
        )

        assertNull(analysis.labels[0].existingMatch)
    }

    @Test
    fun analyzeImport_detectsDuplicateMedication() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))
        medDao.seed(existingMed(1, "Ibuprofen", displayName = "My Ibuprofen"))

        val analysis = service.analyzeImport(
            backup(
                medications = listOf(med("ibuprofen", displayName = "my ibuprofen")),
            ),
        )

        assertNotNull(analysis.medications[0].duplicateOf)
        assertEquals(1, analysis.medications[0].duplicateOf!!.id)
    }

    @Test
    fun analyzeImport_blankName_markedAsError() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val analysis = service.analyzeImport(
            backup(
                medications = listOf(med("   ")),
            ),
        )

        assertTrue(analysis.medications[0].hasErrors)
        assertEquals("Medication has no name", analysis.medications[0].errorMessage)
    }

    @Test
    fun analyzeImport_unknownMedicationType_warnsWithFallback() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val analysis = service.analyzeImport(
            backup(
                medications = listOf(med("Aspirin", type = "GUMMY_BEAR")),
            ),
        )

        assertTrue(analysis.warnings.any { it is ImportWarning.UnknownMedicationType && it.type == "GUMMY_BEAR" })
    }

    @Test
    fun analyzeImport_missingLabelReference_warnsDefault() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val analysis = service.analyzeImport(
            backup(
                medications = listOf(med("Aspirin", importanceLabel = "NonExistent")),
            ),
        )

        assertTrue(analysis.warnings.any { it is ImportWarning.LabelNotFound && it.labelName == "NonExistent" })
    }

    @Test
    fun analyzeImport_newerVersion_warns() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val analysis = service.analyzeImport(backup(version = 999))

        assertTrue(analysis.warnings.any { it is ImportWarning.NewerVersion })
    }

    @Test
    fun analyzeImport_detectsHistoryPresence() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val analysis = service.analyzeImport(
            backup(
                medications = listOf(med("Aspirin", history = listOf(historyEntry(1)))),
            ),
        )

        assertTrue(analysis.hasHistory)
    }

    // ================================================================
    // executeImport()
    // ================================================================

    @Test
    fun executeImport_importAsNew_insertsNewMedication() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val result = service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin")),
            ),
            decisions = mapOf(0 to ImportDecision.IMPORT_AS_NEW),
        )

        assertEquals(1, result.medicationsImported)
        assertEquals(0, result.medicationsReplaced)
        assertEquals(1, medDao.medications.size)
        assertEquals("Aspirin", medDao.medications[0].name)
    }

    @Test
    fun executeImport_skip_doesNotInsert() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val result = service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin")),
            ),
            decisions = mapOf(0 to ImportDecision.SKIP),
        )

        assertEquals(0, result.medicationsImported)
        assertEquals(1, result.medicationsSkipped)
        assertTrue(medDao.medications.isEmpty())
    }

    @Test
    fun executeImport_replace_updatesExistingPreservingId() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))
        medDao.seed(existingMed(10, "Aspirin"))
        scheduleDao.seed(existingSchedule(100, medicationId = 10))

        val result = service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin", schedules = listOf(schedule(1)))),
            ),
            decisions = mapOf(0 to ImportDecision.REPLACE),
        )

        assertEquals(1, result.medicationsReplaced)
        // Original medication preserved with same ID
        assertEquals(1, medDao.medications.size)
        assertEquals(10, medDao.medications[0].id)
        // Old schedule deleted, new schedule inserted
        assertEquals(1, scheduleDao.schedules.size)
        assertEquals(10, scheduleDao.schedules[0].medicationId)
    }

    @Test
    fun executeImport_partialCollision_mixedDecisions() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))
        medDao.seed(
            existingMed(10, "Existing-Replace"),
            existingMed(20, "Existing-Skip"),
        )

        val result = service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(
                    med("Existing-Replace"), // index 0 → REPLACE
                    med("Existing-Skip"), // index 1 → SKIP
                    med("Brand-New"), // index 2 → IMPORT_AS_NEW
                ),
            ),
            decisions = mapOf(
                0 to ImportDecision.REPLACE,
                1 to ImportDecision.SKIP,
                2 to ImportDecision.IMPORT_AS_NEW,
            ),
        )

        assertEquals(1, result.medicationsImported)
        assertEquals(1, result.medicationsReplaced)
        assertEquals(1, result.medicationsSkipped)
        // DB: original 2 + 1 new = 3, but REPLACE updates in-place so still 3
        assertEquals(3, medDao.medications.size)
        // Replaced med keeps original ID
        assertTrue(medDao.medications.any { it.id == 10 && it.name == "Existing-Replace" })
        // Skipped med is untouched
        assertTrue(medDao.medications.any { it.id == 20 && it.name == "Existing-Skip" })
        // New med has a fresh ID
        assertTrue(medDao.medications.any { it.name == "Brand-New" && it.id != 10 && it.id != 20 })
    }

    @Test
    fun executeImport_labelNameCollision_renamedWithImportedSuffix() = runTest {
        // Existing "Critical" with isCritical=true
        labelDao.seed(existingLabel(1, "Critical", isCritical = true))

        // Backup "Critical" with different config (isCritical=false)
        val result = service.executeImport(
            backup(
                labels = listOf(label("Critical", isCritical = false)),
                medications = listOf(med("Aspirin", importanceLabel = "Critical")),
            ),
            decisions = mapOf(0 to ImportDecision.IMPORT_AS_NEW),
        )

        assertEquals(1, result.labelsCreated)
        // New label inserted with "(imported)" suffix
        val imported = labelDao.labels.find { it.name == "Critical (imported)" }
        assertNotNull(imported, "Expected label 'Critical (imported)' to exist")
        // The medication uses the imported label's ID
        assertEquals(imported.id, medDao.medications[0].importanceLabelId)
    }

    @Test
    fun executeImport_exactLabelMatch_reusesExistingId() = runTest {
        labelDao.seed(existingLabel(5, "Silent", shouldRemind = false, isCritical = false))

        val result = service.executeImport(
            backup(
                labels = listOf(label("Silent", shouldRemind = false, isCritical = false)),
                medications = listOf(med("Aspirin", importanceLabel = "Silent")),
            ),
            decisions = mapOf(0 to ImportDecision.IMPORT_AS_NEW),
        )

        assertEquals(0, result.labelsCreated)
        // Medication references existing label
        assertEquals(5, medDao.medications[0].importanceLabelId)
    }

    @Test
    fun executeImport_scheduleIdRemapping_historyReferencesNewIds() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val backupSchedules = listOf(schedule(id = 100), schedule(id = 200))
        val backupHistory = listOf(
            historyEntry(scheduleId = 100, status = "TAKEN"),
            historyEntry(scheduleId = 200, status = "SKIPPED"),
        )

        service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin", schedules = backupSchedules, history = backupHistory)),
            ),
            decisions = mapOf(0 to ImportDecision.IMPORT_AS_NEW),
        )

        // Schedules got new IDs (auto-increment, not 100/200)
        val newScheduleIds = scheduleDao.schedules.map { it.id }.toSet()
        assertTrue(100 !in newScheduleIds, "Old schedule ID 100 should not persist")
        assertTrue(200 !in newScheduleIds, "Old schedule ID 200 should not persist")

        // History entries reference the NEW schedule IDs
        val historyScheduleIds = historyDao.history.map { it.scheduleId }.toSet()
        assertEquals(newScheduleIds, historyScheduleIds, "History should reference remapped schedule IDs")
    }

    @Test
    fun executeImport_blankName_silentlySkipped() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val result = service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med(""), med("Valid")),
            ),
            decisions = mapOf(0 to ImportDecision.IMPORT_AS_NEW, 1 to ImportDecision.IMPORT_AS_NEW),
        )

        assertEquals(1, result.medicationsImported)
        assertEquals(1, result.medicationsSkipped)
        assertEquals(1, medDao.medications.size)
        assertEquals("Valid", medDao.medications[0].name)
    }

    @Test
    fun executeImport_unknownType_fallsBackToTablet() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin", type = "GUMMY_BEAR")),
            ),
            decisions = mapOf(0 to ImportDecision.IMPORT_AS_NEW),
        )

        assertEquals(MedicationType.TABLET, medDao.medications[0].type)
    }

    @Test
    fun executeImport_withStockAdjustments_allImported() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val adjustments = listOf(
            BackupStockAdjustment(quantityChange = 30.0, adjustmentType = "INITIAL_STOCK"),
            BackupStockAdjustment(quantityChange = -1.0, adjustmentType = "INTAKE"),
        )

        val result = service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin", stockAdjustments = adjustments)),
            ),
            decisions = mapOf(0 to ImportDecision.IMPORT_AS_NEW),
        )

        assertEquals(2, result.stockAdjustmentsImported)
        assertEquals(2, stockAdjDao.adjustments.size)
        // All adjustments reference the imported medication
        assertTrue(stockAdjDao.adjustments.all { it.medicationId == medDao.medications[0].id })
    }

    @Test
    fun executeImport_replaceDuplicateGone_importsAsNew() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))
        // No existing medication matching "Aspirin"

        val result = service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin")),
            ),
            decisions = mapOf(0 to ImportDecision.REPLACE),
        )

        // REPLACE but duplicate no longer exists → falls back to IMPORT_AS_NEW
        assertEquals(1, result.medicationsImported)
        assertEquals(0, result.medicationsReplaced)
    }

    @Test
    fun executeImport_replace_deletesOldStockAdjustments() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))
        medDao.seed(existingMed(10, "Aspirin"))
        stockAdjDao.adjustments.add(
            me.juliana.hellomeds.data.database.entities.StockAdjustment(
                id = 1,
                medicationId = 10,
                quantityChange = 30.0,
                adjustmentType = "INITIAL_STOCK",
            ),
        )

        val newAdjustments = listOf(
            BackupStockAdjustment(quantityChange = 60.0, adjustmentType = "INITIAL_STOCK"),
        )

        service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin", stockAdjustments = newAdjustments)),
            ),
            decisions = mapOf(0 to ImportDecision.REPLACE),
        )

        // Old adjustment (30.0) deleted, new one (60.0) inserted
        assertEquals(1, stockAdjDao.adjustments.size)
        assertEquals(60.0, stockAdjDao.adjustments[0].quantityChange)
    }

    @Test
    fun executeImport_defaultDecision_isImportAsNew() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        // No explicit decision provided → defaults to IMPORT_AS_NEW
        val result = service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin")),
            ),
            decisions = emptyMap(),
        )

        assertEquals(1, result.medicationsImported)
    }

    @Test
    fun executeImport_reconcilerCalledOnce() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("A"), med("B"), med("C")),
            ),
            decisions = emptyMap(),
        )

        assertEquals(1, reconciler.reconcileCount)
    }

    @Test
    fun executeImport_replace_deletesOldHistory() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))
        medDao.seed(existingMed(10, "Aspirin"))
        // Pre-existing history for medication 10
        historyDao.history.add(
            MedicationHistory(
                id = 1,
                medicationId = 10,
                scheduleId = null,
                scheduledTime = 1000L,
                scheduledDose = 1.0,
                status = "TAKEN",
            ),
        )

        val backupHistory = listOf(historyEntry(scheduleId = 1))

        service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(
                    med("Aspirin", schedules = listOf(schedule(1)), history = backupHistory),
                ),
            ),
            decisions = mapOf(0 to ImportDecision.REPLACE),
        )

        // Old history deleted, only imported history remains
        assertEquals(1, historyDao.history.size)
        assertEquals("TAKEN", historyDao.history[0].status)
        // Should reference the newly imported medication
        assertEquals(10, historyDao.history[0].medicationId)
    }

    @Test
    fun executeImport_replace_reconcilesCachedStock() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))
        medDao.seed(existingMed(10, "Aspirin"))

        val newAdjustments = listOf(
            BackupStockAdjustment(quantityChange = 60.0, adjustmentType = "INITIAL_STOCK"),
            BackupStockAdjustment(quantityChange = -5.0, adjustmentType = "INTAKE"),
        )

        service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin", stockAdjustments = newAdjustments)),
            ),
            decisions = mapOf(0 to ImportDecision.REPLACE),
        )

        // Cached stock should match the ledger sum (60 - 5 = 55)
        assertEquals(55.0, medDao.medications[0].currentStockQuantity)
    }

    @Test
    fun executeImport_replace_stockSettingsButNoAdjustments_preservesBackupCache() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))
        medDao.seed(existingMed(10, "Aspirin"))
        // Pre-existing stock adjustment (will be deleted by REPLACE)
        stockAdjDao.adjustments.add(
            me.juliana.hellomeds.data.database.entities.StockAdjustment(
                id = 1,
                medicationId = 10,
                quantityChange = 30.0,
                adjustmentType = "INITIAL_STOCK",
            ),
        )

        // Backup has stock settings (currentStockQuantity=50) but no adjustments
        // (exported without history). The cached value is the source of truth.
        val stockSettings = BackupStockSettings(
            trackingEnabled = true,
            trackingPrecision = "EXACT",
            currentStockQuantity = 50.0,
            lowStockThreshold = 10.0,
        )

        service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin", stock = stockSettings)),
            ),
            decisions = mapOf(0 to ImportDecision.REPLACE),
        )

        // Old adjustments deleted, but backup has no new ones.
        // Cache should preserve the backup's value, NOT be zeroed.
        assertEquals(50.0, medDao.medications[0].currentStockQuantity)
        assertTrue(stockAdjDao.adjustments.isEmpty())
    }

    @Test
    fun executeImport_historyIdRemapping_stockAdjustmentLinked() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        val backupHistory = listOf(
            BackupHistory(
                scheduleId = 1,
                scheduledTime = "2025-01-01T08:00:00Z",
                scheduledDose = 1.0,
                status = "TAKEN",
                localId = 42,
            ),
        )
        val backupAdjustments = listOf(
            BackupStockAdjustment(
                quantityChange = -1.0,
                adjustmentType = "INTAKE",
                historyLocalId = 42,
            ),
        )

        service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(
                    med(
                        "Aspirin",
                        schedules = listOf(schedule(1)),
                        history = backupHistory,
                        stockAdjustments = backupAdjustments,
                    ),
                ),
            ),
            decisions = mapOf(0 to ImportDecision.IMPORT_AS_NEW),
        )

        // The stock adjustment should reference the newly created history record's ID
        val importedHistoryId = historyDao.history[0].id
        assertEquals(importedHistoryId, stockAdjDao.adjustments[0].historyId)
    }

    @Test
    fun executeImport_oldBackupWithoutLocalId_historyIdRemainsNull() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        // Simulates an older backup that doesn't have localId/historyLocalId
        val backupHistory = listOf(historyEntry(scheduleId = 1))
        val backupAdjustments = listOf(
            BackupStockAdjustment(quantityChange = -1.0, adjustmentType = "INTAKE"),
        )

        service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(
                    med(
                        "Aspirin",
                        schedules = listOf(schedule(1)),
                        history = backupHistory,
                        stockAdjustments = backupAdjustments,
                    ),
                ),
            ),
            decisions = mapOf(0 to ImportDecision.IMPORT_AS_NEW),
        )

        // Without localId, historyId can't be remapped — remains null
        assertNull(stockAdjDao.adjustments[0].historyId)
    }

    @Test
    fun executeImport_historyWithUnmappedScheduleId_setsNull() = runTest {
        labelDao.seed(existingLabel(1, "Silent"))

        // History references schedule ID 999 which isn't in the backup's schedules
        val backupHistory = listOf(historyEntry(scheduleId = 999))

        service.executeImport(
            backup(
                labels = listOf(label("Silent")),
                medications = listOf(med("Aspirin", history = backupHistory)),
            ),
            decisions = mapOf(0 to ImportDecision.IMPORT_AS_NEW),
        )

        // Schedule 999 not in oldToNewScheduleId map → null
        assertNull(historyDao.history[0].scheduleId)
    }
}
