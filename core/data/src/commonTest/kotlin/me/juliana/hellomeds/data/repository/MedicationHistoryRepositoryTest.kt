// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.repository

import kotlinx.coroutines.test.runTest
import me.juliana.hellomeds.data.backup.FakeHistoryDao
import me.juliana.hellomeds.data.backup.FakeMedicationDao
import me.juliana.hellomeds.data.backup.FakeScheduleDao
import me.juliana.hellomeds.data.backup.FakeStockAdjustmentDao
import me.juliana.hellomeds.data.backup.FakeTransactionRunner
import me.juliana.hellomeds.data.backup.NoOpDepletionChecker
import me.juliana.hellomeds.data.backup.NoOpLowStockChecker
import me.juliana.hellomeds.data.backup.NoOpReconciler
import me.juliana.hellomeds.data.createMedication
import me.juliana.hellomeds.data.createProjectedEvent
import me.juliana.hellomeds.data.createStockAdjustment
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.service.StockPredictionEngine
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MedicationHistoryRepositoryTest {

    private lateinit var historyDao: FakeHistoryDao
    private lateinit var medicationDao: FakeMedicationDao
    private lateinit var scheduleDao: FakeScheduleDao
    private lateinit var stockAdjustmentDao: FakeStockAdjustmentDao
    private lateinit var stockTrackingRepository: StockTrackingRepository
    private lateinit var repository: MedicationHistoryRepository

    @BeforeTest
    fun setup() {
        historyDao = FakeHistoryDao()
        medicationDao = FakeMedicationDao()
        scheduleDao = FakeScheduleDao()
        stockAdjustmentDao = FakeStockAdjustmentDao()

        val transactionRunner = FakeTransactionRunner()

        stockTrackingRepository = StockTrackingRepository(
            medicationDao = medicationDao,
            stockAdjustmentDao = stockAdjustmentDao,
            transactionRunner = transactionRunner,
            scheduleDao = scheduleDao,
            lowStockNotifier = NoOpLowStockChecker(),
            predictionEngine = StockPredictionEngine(),
            medicationHistoryDao = historyDao,
            depletionReminderNotifier = NoOpDepletionChecker(),
        )

        repository = MedicationHistoryRepository(
            historyDao = historyDao,
            medicationDao = medicationDao,
            scheduleDao = scheduleDao,
            transactionRunner = transactionRunner,
            projector = ScheduleProjector(scheduleDao, historyDao, medicationDao),
            stockTrackingRepository = stockTrackingRepository,
            reconciler = NoOpReconciler(),
            lowStockNotifier = NoOpLowStockChecker(),
            depletionReminderNotifier = NoOpDepletionChecker(),
        )
    }

    // ================================================================
    // Test 1: "Double Tap" — duplicate insert becomes update, stock only decremented once
    // ================================================================

    @Test
    fun markAsTaken_calledTwice_createsOnlyOneRecord() = runTest {
        val medication = createMedication(id = 1, stockTrackingEnabled = true)
        medicationDao.insert(medication)

        val event = createProjectedEvent(
            medicationId = 1,
            scheduleId = 10,
            scheduledTime = 1_000_000L,
            dose = 1.0,
        )

        // Act: mark as taken twice with the same composite key
        repository.markAsTaken(event, actualDose = 1.0, takenTime = 2_000_000L)
        repository.markAsTaken(event, actualDose = 1.0, takenTime = 3_000_000L)

        // Assert: exactly 1 history record exists
        val records = historyDao.getAllByCompositeKey(1, 10, 1_000_000L)
        assertEquals(1, records.size, "Should have exactly 1 history record, not 2")

        // Assert: exactly 1 stock adjustment (not double-decremented)
        val adjustments = stockAdjustmentDao.adjustments.filter {
            it.medicationId == 1 && it.adjustmentType == StockAdjustmentType.INTAKE.value
        }
        assertEquals(1, adjustments.size, "Should have exactly 1 stock adjustment, not 2")
    }

    // ================================================================
    // Test 2: "Self-Healing Deletion" — cleans up pre-existing duplicates
    // ================================================================

    @Test
    fun deleteHistoryRecord_removesAllDuplicatesAndReversesStock() = runTest {
        val medication = createMedication(id = 1, stockTrackingEnabled = true)
        medicationDao.insert(medication)

        // Manually insert 3 duplicate records (simulating old corrupt data)
        val id1 = historyDao.insert(
            MedicationHistory(
                medicationId = 1,
                scheduleId = 10,
                scheduledTime = 1_000_000L,
                status = "TAKEN",
                scheduledDose = 1.0,
                actualDose = 1.0,
                takenTime = 2_000_000L,
            ),
        ).toInt()
        val id2 = historyDao.insert(
            MedicationHistory(
                medicationId = 1,
                scheduleId = 10,
                scheduledTime = 1_000_000L,
                status = "TAKEN",
                scheduledDose = 1.0,
                actualDose = 1.0,
                takenTime = 2_000_000L,
            ),
        ).toInt()
        val id3 = historyDao.insert(
            MedicationHistory(
                medicationId = 1,
                scheduleId = 10,
                scheduledTime = 1_000_000L,
                status = "TAKEN",
                scheduledDose = 1.0,
                actualDose = 1.0,
                takenTime = 2_000_000L,
            ),
        ).toInt()

        // Create matching stock adjustments for each
        stockAdjustmentDao.insert(
            createStockAdjustment(medicationId = 1, historyId = id1, quantityChange = -1.0),
        )
        stockAdjustmentDao.insert(
            createStockAdjustment(medicationId = 1, historyId = id2, quantityChange = -1.0),
        )
        stockAdjustmentDao.insert(
            createStockAdjustment(medicationId = 1, historyId = id3, quantityChange = -1.0),
        )

        assertEquals(3, stockAdjustmentDao.adjustments.size, "Precondition: 3 stock adjustments")

        // Act: delete using only the first duplicate's ID
        repository.deleteHistoryRecord(id1)

        // Assert: ALL duplicates removed
        val remaining = historyDao.getAllByCompositeKey(1, 10, 1_000_000L)
        assertTrue(remaining.isEmpty(), "All 3 duplicates should be deleted")

        // Assert: ALL stock adjustments reversed/deleted
        val remainingAdj = stockAdjustmentDao.adjustments.filter { it.medicationId == 1 }
        assertTrue(
            remainingAdj.isEmpty(),
            "All 3 stock adjustments should be reversed, but ${remainingAdj.size} remain",
        )
    }

    // ================================================================
    // Test 3: "PRN Null Safety" — as-needed deletion doesn't crash or wipe siblings
    // ================================================================

    // ================================================================
    // Test 4: "Stale dialog id" — deleteHistoryRecord(event) for a scheduled event
    // must remove the row even when event.historyRecord.id no longer matches any row.
    // Guards against the silent-no-op bug seen in user testing.
    // ================================================================

    @Test
    fun deleteHistoryRecord_byEvent_removesScheduledRow_whenHistoryRecordIdIsStale() = runTest {
        val medication = createMedication(id = 1, stockTrackingEnabled = true)
        medicationDao.insert(medication)

        // Insert the actual TAKEN row; its DAO-assigned id is what would normally
        // be put on event.historyRecord.id.
        val realId = historyDao.insert(
            MedicationHistory(
                medicationId = 1,
                scheduleId = 10,
                scheduledTime = 1_000_000L,
                status = "TAKEN",
                scheduledDose = 1.0,
                actualDose = 1.0,
                takenTime = 2_000_000L,
            ),
        ).toInt()
        assertEquals(1, historyDao.history.size, "Precondition: 1 row inserted")

        // Simulate the dialog capturing a stale historyRecord whose id (99999)
        // does NOT match the row in the DB, but whose composite key DOES.
        val staleEvent = createProjectedEvent(
            medicationId = 1,
            scheduleId = 10,
            scheduledTime = 1_000_000L,
            historyRecord = MedicationHistory(
                id = 99999, // deliberately wrong
                medicationId = 1,
                scheduleId = 10,
                scheduledTime = 1_000_000L,
                status = "TAKEN",
                scheduledDose = 1.0,
                actualDose = 1.0,
                takenTime = 2_000_000L,
            ),
        )

        // Act: delete via the event-based overload
        repository.deleteHistoryRecord(staleEvent)

        // Assert: row is gone — composite-key path doesn't depend on the dialog id
        val remaining = historyDao.getAllByCompositeKey(1, 10, 1_000_000L)
        assertTrue(
            remaining.isEmpty(),
            "Row should be deleted by composite key even with stale historyRecord.id (realId=$realId)",
        )
    }

    @Test
    fun deleteHistoryRecord_prnMedication_deletesOnlyTargetRecord() = runTest {
        // Insert 2 PRN records (scheduleId=null, scheduledTime=null)
        val id1 = historyDao.insert(
            MedicationHistory(
                medicationId = 1,
                scheduleId = null,
                scheduledTime = null,
                status = "TAKEN",
                scheduledDose = 1.0,
                actualDose = 1.0,
                takenTime = 1_000_000L,
            ),
        ).toInt()
        val id2 = historyDao.insert(
            MedicationHistory(
                medicationId = 1,
                scheduleId = null,
                scheduledTime = null,
                status = "TAKEN",
                scheduledDose = 1.0,
                actualDose = 1.0,
                takenTime = 2_000_000L,
            ),
        ).toInt()

        assertEquals(2, historyDao.history.size, "Precondition: 2 PRN records")

        // Act: delete the first PRN record — should NOT crash with NPE
        repository.deleteHistoryRecord(id1)

        // Assert: only record1 deleted, record2 still exists
        assertEquals(1, historyDao.history.size, "Should have 1 record remaining")
        assertNotNull(historyDao.getByIdSync(id2), "Record 2 should still exist")
    }
}
