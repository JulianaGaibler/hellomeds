// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import kotlinx.coroutines.test.runTest
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.database.entities.StockAdjustment
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupExportServiceTest {

    private lateinit var labelDao: FakeImportanceLabelDao
    private lateinit var medDao: FakeMedicationDao
    private lateinit var scheduleDao: FakeScheduleDao
    private lateinit var historyDao: FakeHistoryDao
    private lateinit var stockAdjDao: FakeStockAdjustmentDao
    private lateinit var service: BackupExportService

    @BeforeTest
    fun setup() {
        labelDao = FakeImportanceLabelDao()
        medDao = FakeMedicationDao()
        scheduleDao = FakeScheduleDao()
        historyDao = FakeHistoryDao()
        stockAdjDao = FakeStockAdjustmentDao()
        service = BackupExportService(medDao, labelDao, scheduleDao, historyDao, stockAdjDao)
    }

    private fun label(id: Int, name: String) = ImportanceLabel(
        id = id,
        name = name,
        shouldRemind = true,
        isCritical = false,
        hasFollowUps = false,
        followUpCount = 0,
        followUpIntervalMinutes = 0,
    )

    private fun med(
        id: Int,
        name: String,
        labelId: Int = 1,
        stockEnabled: Boolean = false,
        precision: TrackingPrecision? = null,
        strengthUnit: MedicationStrengthUnit? = null,
        strengthValue: Double? = null,
    ) = Medication(
        id = id, name = name, type = MedicationType.TABLET, shape = "",
        importanceLabelId = labelId, foregroundShape = "CIRCLE", backgroundShape = "CIRCLE",
        stockTrackingEnabled = stockEnabled, trackingPrecision = precision,
        strengthUnit = strengthUnit, strengthValue = strengthValue,
    )

    private fun schedule(id: Int, medId: Int) = Schedule(
        id = id,
        medicationId = medId,
        dose = 1.0,
        startDate = 1704067200000L, // 2024-01-01
        timeOfDay = "08:00",
        frequencyType = FrequencyType.INTERVAL,
        frequencyValue = 1,
    )

    private fun history(id: Int, medId: Int, scheduleId: Int? = null) = MedicationHistory(
        id = id,
        medicationId = medId,
        scheduleId = scheduleId,
        scheduledTime = 1704096000000L,
        scheduledDose = 1.0,
        status = "TAKEN",
    )

    private fun adjustment(id: Int, medId: Int, qty: Double) = StockAdjustment(
        id = id,
        medicationId = medId,
        quantityChange = qty,
        adjustmentType = "INITIAL_STOCK",
    )

    // ================================================================
    // generateBackup()
    // ================================================================

    @Test
    fun generateBackup_basicExport_hasVersionAndMedication() = runTest {
        labelDao.seed(label(1, "Silent"))
        medDao.seed(med(1, "Aspirin", labelId = 1))

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1),
            includeSchedules = false,
            includeStockSettings = false,
            includeHistory = false,
        )

        assertEquals(BackupExportService.CURRENT_VERSION, backup.version)
        assertEquals(1, backup.medications.size)
        assertEquals("Aspirin", backup.medications[0].name)
        assertEquals("Silent", backup.medications[0].importanceLabel)
    }

    @Test
    fun generateBackup_onlySelectedMedicationsExported() = runTest {
        labelDao.seed(label(1, "Silent"))
        medDao.seed(med(1, "Aspirin", labelId = 1), med(2, "Tylenol", labelId = 1))

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1),
            includeSchedules = false,
            includeStockSettings = false,
            includeHistory = false,
        )

        assertEquals(1, backup.medications.size)
        assertEquals("Aspirin", backup.medications[0].name)
    }

    @Test
    fun generateBackup_onlyUsedLabelsExported() = runTest {
        labelDao.seed(label(1, "Silent"), label(2, "Critical"), label(3, "Unused"))
        medDao.seed(
            med(1, "Aspirin", labelId = 1),
            med(2, "Tylenol", labelId = 2),
        )

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1, 2),
            includeSchedules = false,
            includeStockSettings = false,
            includeHistory = false,
        )

        val exportedLabelNames = backup.importanceLabels.map { it.name }.toSet()
        assertEquals(setOf("Silent", "Critical"), exportedLabelNames)
        assertTrue("Unused" !in exportedLabelNames)
    }

    @Test
    fun generateBackup_includeSchedules_schedulesPresent() = runTest {
        labelDao.seed(label(1, "Silent"))
        medDao.seed(med(1, "Aspirin"))
        scheduleDao.seed(schedule(10, medId = 1), schedule(20, medId = 1))

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1),
            includeSchedules = true,
            includeStockSettings = false,
            includeHistory = false,
        )

        assertEquals(2, backup.medications[0].schedules.size)
    }

    @Test
    fun generateBackup_excludeSchedules_schedulesEmpty() = runTest {
        labelDao.seed(label(1, "Silent"))
        medDao.seed(med(1, "Aspirin"))
        scheduleDao.seed(schedule(10, medId = 1))

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1),
            includeSchedules = false,
            includeStockSettings = false,
            includeHistory = false,
        )

        assertTrue(backup.medications[0].schedules.isEmpty())
    }

    @Test
    fun generateBackup_includeHistory_historyAndStockAdjustmentsPresent() = runTest {
        labelDao.seed(label(1, "Silent"))
        medDao.seed(med(1, "Aspirin"))
        historyDao.history.add(history(1, medId = 1, scheduleId = 10))
        stockAdjDao.adjustments.add(adjustment(1, medId = 1, qty = 30.0))

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1),
            includeSchedules = false,
            includeStockSettings = false,
            includeHistory = true,
        )

        assertEquals(1, backup.medications[0].history.size)
        assertEquals("TAKEN", backup.medications[0].history[0].status)
        assertEquals(1, backup.medications[0].stockAdjustments.size)
        assertEquals(30.0, backup.medications[0].stockAdjustments[0].quantityChange)
    }

    @Test
    fun generateBackup_excludeHistory_historyEmpty() = runTest {
        labelDao.seed(label(1, "Silent"))
        medDao.seed(med(1, "Aspirin"))
        historyDao.history.add(history(1, medId = 1))

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1),
            includeSchedules = false,
            includeStockSettings = false,
            includeHistory = false,
        )

        assertTrue(backup.medications[0].history.isEmpty())
        assertTrue(backup.medications[0].stockAdjustments.isEmpty())
    }

    @Test
    fun generateBackup_includeStockSettings_onlyWhenTrackingEnabled() = runTest {
        labelDao.seed(label(1, "Silent"))
        medDao.seed(
            med(1, "Tracked", stockEnabled = true, precision = TrackingPrecision.EXACT),
            med(2, "Untracked", stockEnabled = false),
        )

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1, 2),
            includeSchedules = false,
            includeStockSettings = true,
            includeHistory = false,
        )

        assertNotNull(backup.medications.find { it.name == "Tracked" }?.stock)
        assertNull(backup.medications.find { it.name == "Untracked" }?.stock)
    }

    @Test
    fun generateBackup_excludeStockSettings_stockNull() = runTest {
        labelDao.seed(label(1, "Silent"))
        medDao.seed(med(1, "Aspirin", stockEnabled = true, precision = TrackingPrecision.EXACT))

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1),
            includeSchedules = false,
            includeStockSettings = false,
            includeHistory = false,
        )

        assertNull(backup.medications[0].stock)
    }

    @Test
    fun generateBackup_medicationFieldsMapped() = runTest {
        labelDao.seed(label(1, "Silent"))
        medDao.seed(
            med(1, "Aspirin", strengthValue = 500.0, strengthUnit = MedicationStrengthUnit.MG),
        )

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1),
            includeSchedules = false,
            includeStockSettings = false,
            includeHistory = false,
        )

        val m = backup.medications[0]
        assertEquals("Aspirin", m.name)
        assertEquals("TABLET", m.type)
        assertEquals(500.0, m.strengthValue)
        assertEquals("MG", m.strengthUnit)
    }

    @Test
    fun generateBackup_missingLabel_fallsBackToSilent() = runTest {
        // Label ID 99 doesn't exist
        labelDao.seed(label(1, "Silent"))
        medDao.seed(med(1, "Aspirin", labelId = 99))

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1),
            includeSchedules = false,
            includeStockSettings = false,
            includeHistory = false,
        )

        assertEquals("Silent", backup.medications[0].importanceLabel)
    }

    @Test
    fun generateBackup_exportedAtIsIsoTimestamp() = runTest {
        labelDao.seed(label(1, "Silent"))
        medDao.seed(med(1, "Aspirin"))

        val backup = service.generateBackup(
            selectedMedicationIds = setOf(1),
            includeSchedules = false,
            includeStockSettings = false,
            includeHistory = false,
        )

        // ISO 8601 format: starts with year, contains T
        assertTrue(backup.exportedAt.contains("T"), "Expected ISO timestamp, got: ${backup.exportedAt}")
    }
}
