// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import me.juliana.hellomeds.data.backup.CountingReconciler
import me.juliana.hellomeds.data.backup.FakeHistoryDao
import me.juliana.hellomeds.data.backup.FakeMedicationDao
import me.juliana.hellomeds.data.backup.FakeResolverState
import me.juliana.hellomeds.data.backup.FakeScheduleDao
import me.juliana.hellomeds.data.backup.FakeStockAdjustmentDao
import me.juliana.hellomeds.data.backup.NoOpDepletionChecker
import me.juliana.hellomeds.data.backup.NoOpLowStockChecker
import me.juliana.hellomeds.data.backup.SnapshotTransactionRunner
import me.juliana.hellomeds.data.createHistory
import me.juliana.hellomeds.data.createMedication
import me.juliana.hellomeds.data.createSchedule
import me.juliana.hellomeds.data.database.entities.StockAdjustment
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.repository.StockTrackingRepository
import me.juliana.hellomeds.data.toEpochMillis
import me.juliana.hellomeds.data.util.TimeProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MedicationDataClearerTest {

    private val mar1 = LocalDate(2025, 3, 1)
    private fun day(n: Int) = mar1.plus(n - 1, DateTimeUnit.DAY)
    private val now = day(20).toEpochMillis("12:00")
    private val time = object : TimeProvider {
        override fun now() = Instant.fromEpochMilliseconds(now)
    }

    private lateinit var historyDao: FakeHistoryDao
    private lateinit var stockDao: FakeStockAdjustmentDao
    private lateinit var scheduleDao: FakeScheduleDao
    private lateinit var medicationDao: FakeMedicationDao
    private lateinit var state: FakeResolverState
    private lateinit var resolver: UnloggedDoseResolver
    private lateinit var clearer: MedicationDataClearer

    @BeforeTest
    fun setup() {
        historyDao = FakeHistoryDao()
        stockDao = FakeStockAdjustmentDao(historyDao)
        historyDao.cascadeTo = stockDao
        scheduleDao = FakeScheduleDao().apply {
            seed(createSchedule(id = 1, startDate = mar1.toEpochMillis(), timeOfDay = "08:00"))
        }
        medicationDao = FakeMedicationDao().apply {
            seed(createMedication(id = 1, stockTrackingEnabled = true, trackingPrecision = TrackingPrecision.EXACT))
        }
        state = FakeResolverState()
        val transactionRunner = SnapshotTransactionRunner(historyDao, stockDao)
        resolver = UnloggedDoseResolver(
            projector = ScheduleProjector(scheduleDao, historyDao, medicationDao, time),
            scheduleDao = scheduleDao,
            medicationDao = medicationDao,
            historyDao = historyDao,
            transactionRunner = transactionRunner,
            timeProvider = time,
            state = state,
            reconciler = CountingReconciler(),
            stockTracking = StockTrackingRepository(
                medicationDao = medicationDao,
                stockAdjustmentDao = stockDao,
                transactionRunner = transactionRunner,
                scheduleDao = scheduleDao,
                lowStockNotifier = NoOpLowStockChecker(),
                predictionEngine = StockPredictionEngine(),
                medicationHistoryDao = historyDao,
                depletionReminderNotifier = NoOpDepletionChecker(),
            ),
            stockAdjustmentDao = stockDao,
            lowStockNotifier = NoOpLowStockChecker(),
            depletionNotifier = NoOpDepletionChecker(),
        )
        clearer = MedicationDataClearer(
            medicationDao = medicationDao,
            scheduleDao = scheduleDao,
            historyDao = historyDao,
            stockAdjustmentDao = stockDao,
            transactionRunner = transactionRunner,
            resolver = resolver,
            reconciler = CountingReconciler(),
            timeProvider = time,
            deviceTimeZone = { TimeZone.currentSystemDefault() },
        )
    }

    private suspend fun adjust(day: Int, type: StockAdjustmentType, quantity: Double, historyId: Int? = null) {
        stockDao.insert(
            StockAdjustment(
                medicationId = 1,
                historyId = historyId,
                quantityChange = quantity,
                timestamp = day(day).toEpochMillis("09:00"),
                adjustmentType = type.value,
            ),
        )
    }

    private suspend fun logged(day: Int): Int =
        historyDao.insert(createHistory(scheduledTime = day(day).toEpochMillis("08:00"))).toInt()

    private suspend fun stock() = stockDao.getCurrentStock(1)

    private suspend fun exactBaseline() = listOf(
        StockAdjustmentType.INITIAL_STOCK,
        StockAdjustmentType.MANUAL_CORRECTION,
        StockAdjustmentType.REFILL,
    ).mapNotNull { stockDao.getLatestByTypeForMedication(1, it.value)?.timestamp }.max()

    @Test
    fun stockFold_keepsLevelBaselineAndRecentRows() = runTest {
        adjust(1, StockAdjustmentType.INITIAL_STOCK, 100.0)
        adjust(5, StockAdjustmentType.REFILL, 30.0)
        adjust(8, StockAdjustmentType.MANUAL_CORRECTION, -2.0)
        adjust(9, StockAdjustmentType.INTAKE, -1.0)
        adjust(18, StockAdjustmentType.INTAKE, -1.0)
        val levelBefore = stock()
        val baselineBefore = exactBaseline()

        clearer.clear(1, clearHistory = false, clearStock = true, range = ClearRange.OLDER_THAN_7)

        assertEquals(levelBefore, stock())
        assertEquals(baselineBefore, exactBaseline())
        assertEquals(3, stockDao.adjustments.size, "Two carried-forward rows plus the recent intake")
        assertTrue(stockDao.adjustments.any { it.timestamp == day(18).toEpochMillis("09:00") })
    }

    @Test
    fun clearingHistoryOnly_keepsStockAndLinksOfKeptDoses() = runTest {
        adjust(1, StockAdjustmentType.INITIAL_STOCK, 50.0)
        adjust(3, StockAdjustmentType.INTAKE, -1.0, historyId = logged(3))
        val recentId = logged(18)
        adjust(18, StockAdjustmentType.INTAKE, -1.0, historyId = recentId)

        clearer.clear(1, clearHistory = true, clearStock = false, range = ClearRange.OLDER_THAN_7)

        assertEquals(48.0, stock(), "Deleting history must not cascade into the ledger")
        assertEquals(listOf(recentId), historyDao.history.map { it.id })
        assertEquals(recentId, stockDao.adjustments.single { it.timestamp == day(18).toEpochMillis("09:00") }.historyId)
    }

    @Test
    fun clearingOlderThan24Hours_resolverDoesNotRefill() = runTest {
        (1..20).forEach { logged(it) }
        state.setWatermark(day(19).toEpochMillis("08:00"))

        clearer.clear(1, clearHistory = true, clearStock = false, range = ClearRange.OLDER_THAN_24_HOURS)
        resolver.resolve()

        assertEquals(listOf(day(20).toEpochMillis("08:00")), historyDao.history.map { it.scheduledTime })
    }
}
