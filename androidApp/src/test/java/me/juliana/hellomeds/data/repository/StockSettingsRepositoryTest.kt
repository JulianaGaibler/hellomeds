// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.repository

import android.util.Log
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.juliana.hellomeds.createMedication
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.service.StockPredictionEngine
import me.juliana.hellomeds.data.util.TransactionRunner
import me.juliana.hellomeds.notifications.DepletionReminderNotifier
import me.juliana.hellomeds.notifications.LowStockNotifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class StockSettingsRepositoryTest {

    @MockK
    private lateinit var medicationDao: MedicationDao

    @MockK
    private lateinit var stockAdjustmentDao: StockAdjustmentDao

    private val transactionRunner = object : TransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    @MockK
    private lateinit var scheduleDao: ScheduleDao

    @RelaxedMockK
    private lateinit var lowStockNotifier: LowStockNotifier

    @RelaxedMockK
    private lateinit var predictionEngine: StockPredictionEngine

    @MockK
    private lateinit var medicationHistoryDao: MedicationHistoryDao

    @RelaxedMockK
    private lateinit var depletionReminderNotifier: DepletionReminderNotifier

    private lateinit var repository: StockTrackingRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)

        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        coEvery { medicationDao.updateLowStockAlertSent(any(), any()) } just Runs
        coEvery { medicationDao.updateDepletionAlertSent(any(), any()) } just Runs

        repository = StockTrackingRepository(
            medicationDao = medicationDao,
            stockAdjustmentDao = stockAdjustmentDao,
            transactionRunner = transactionRunner,
            scheduleDao = scheduleDao,
            lowStockNotifier = lowStockNotifier,
            predictionEngine = predictionEngine,
            medicationHistoryDao = medicationHistoryDao,
            depletionReminderNotifier = depletionReminderNotifier,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ================================================================
    // updateLowStockThreshold
    // ================================================================

    @Test
    fun updateLowStockThreshold_setsFromNull() = runTest {
        val medication = exactMedication(lowStockThreshold = null)
        stubMedicationLookup(medication)
        val captured = captureMedicationUpdate()

        repository.updateLowStockThreshold(1, 20.0)

        assertEquals(20.0, captured.captured.lowStockThreshold)
    }

    @Test
    fun updateLowStockThreshold_clearsExisting() = runTest {
        val medication = exactMedication(lowStockThreshold = 20.0)
        stubMedicationLookup(medication)
        val captured = captureMedicationUpdate()

        repository.updateLowStockThreshold(1, null)

        assertNull(captured.captured.lowStockThreshold)
    }

    @Test
    fun updateLowStockThreshold_updatesValue() = runTest {
        val medication = exactMedication(lowStockThreshold = 10.0)
        stubMedicationLookup(medication)
        val captured = captureMedicationUpdate()

        repository.updateLowStockThreshold(1, 25.0)

        assertEquals(25.0, captured.captured.lowStockThreshold)
    }

    @Test
    fun updateLowStockThreshold_medicationNotFound_noUpdate() = runTest {
        coEvery { medicationDao.getById(1) } returns flowOf(null)

        repository.updateLowStockThreshold(1, 20.0)

        coVerify(exactly = 0) { medicationDao.update(any()) }
    }

    // ================================================================
    // updateContainerType
    // ================================================================

    @Test
    fun updateContainerType_setsType() = runTest {
        val medication = exactMedication(medicationContainer = null)
        stubMedicationLookup(medication)
        val captured = captureMedicationUpdate()

        repository.updateContainerType(1, MedicationContainer.BOTTLE)

        assertEquals(MedicationContainer.BOTTLE, captured.captured.medicationContainer)
    }

    @Test
    fun updateContainerType_clearsType() = runTest {
        val medication = exactMedication(medicationContainer = MedicationContainer.BOTTLE)
        stubMedicationLookup(medication)
        val captured = captureMedicationUpdate()

        repository.updateContainerType(1, null)

        assertNull(captured.captured.medicationContainer)
    }

    @Test
    fun updateContainerType_medicationNotFound_noUpdate() = runTest {
        coEvery { medicationDao.getById(1) } returns flowOf(null)

        repository.updateContainerType(1, MedicationContainer.TUBE)

        coVerify(exactly = 0) { medicationDao.update(any()) }
    }

    // ================================================================
    // updatePackagingQuantity
    // ================================================================

    @Test
    fun updatePackagingQuantity_updatesAndRecalculates() = runTest {
        val medication = exactMedication(packagingQuantity = 30.0)
        stubMedicationLookup(medication)
        val medSlot = captureMedicationUpdate()
        stubCachedStockRecalculation()

        repository.updatePackagingQuantity(1, 50.0)

        assertEquals(50.0, medSlot.captured.packagingQuantity)
    }

    @Test
    fun updatePackagingQuantity_medicationNotFound_noOps() = runTest {
        coEvery { medicationDao.getById(1) } returns flowOf(null)

        repository.updatePackagingQuantity(1, 50.0)

        coVerify(exactly = 0) { medicationDao.update(any()) }
    }

    // ================================================================
    // updateDepletionReminderEnabled
    // ================================================================

    @Test
    fun updateDepletionReminderEnabled_enables() = runTest {
        val medication = estimatedMedication(depletionReminderEnabled = false)
        stubMedicationLookup(medication)
        val captured = captureMedicationUpdate()

        repository.updateDepletionReminderEnabled(1, true)

        assertEquals(true, captured.captured.depletionReminderEnabled)
    }

    @Test
    fun updateDepletionReminderEnabled_disables() = runTest {
        val medication = estimatedMedication(depletionReminderEnabled = true)
        stubMedicationLookup(medication)
        val captured = captureMedicationUpdate()

        repository.updateDepletionReminderEnabled(1, false)

        assertEquals(false, captured.captured.depletionReminderEnabled)
    }

    // ================================================================
    // Helpers
    // ================================================================

    private fun exactMedication(
        packagingQuantity: Double? = 30.0,
        lowStockThreshold: Double? = null,
        medicationContainer: MedicationContainer? = null,
    ) = createMedication(
        id = 1,
        stockTrackingEnabled = true,
        trackingPrecision = TrackingPrecision.EXACT,
        packagingQuantity = packagingQuantity,
        lowStockThreshold = lowStockThreshold,
        medicationContainer = medicationContainer,
    )

    private fun estimatedMedication(depletionReminderEnabled: Boolean = false) = createMedication(
        id = 1,
        stockTrackingEnabled = true,
        trackingPrecision = TrackingPrecision.ESTIMATED,
        depletionReminderEnabled = depletionReminderEnabled,
    )

    private fun stubMedicationLookup(medication: Medication) {
        coEvery { medicationDao.getById(medication.id) } returns flowOf(medication)
    }

    private fun captureMedicationUpdate(): io.mockk.CapturingSlot<Medication> {
        val slot = slot<Medication>()
        coEvery { medicationDao.update(capture(slot)) } just Runs
        return slot
    }

    private fun stubCachedStockRecalculation() {
        coEvery { stockAdjustmentDao.getCurrentStock(1) } returns 100.0
        coEvery { medicationDao.updateCachedStockQuantity(any(), any()) } just Runs
    }
}
