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
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.entities.StockAdjustment
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.model.enums.UnloggedDoseAction
import me.juliana.hellomeds.data.repository.StockTrackingRepository
import me.juliana.hellomeds.data.toEpochMillis
import me.juliana.hellomeds.data.util.TimeProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class UnloggedDoseResolverTest {

    private class MutableTime(var nowMs: Long = 0L) : TimeProvider {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }

    /** Throws on the [failOnInsert]-th insert; everything else delegates to [inner]. */
    private class FailingHistoryDao(
        private val inner: FakeHistoryDao,
        private val failOnInsert: Int,
    ) : MedicationHistoryDao by inner {
        private var inserts = 0
        override suspend fun insert(history: MedicationHistory): Long {
            if (++inserts == failOnInsert) error("disk full")
            return inner.insert(history)
        }
    }

    private lateinit var historyDao: FakeHistoryDao
    private lateinit var scheduleDao: FakeScheduleDao
    private lateinit var medicationDao: FakeMedicationDao
    private lateinit var stockDao: FakeStockAdjustmentDao
    private lateinit var state: FakeResolverState
    private lateinit var reconciler: CountingReconciler
    private lateinit var time: MutableTime
    private var deviceZone: TimeZone = TimeZone.currentSystemDefault()

    @BeforeTest
    fun setup() {
        historyDao = FakeHistoryDao()
        scheduleDao = FakeScheduleDao()
        medicationDao = FakeMedicationDao().apply { seed(createMedication(id = 1)) }
        stockDao = FakeStockAdjustmentDao()
        state = FakeResolverState()
        reconciler = CountingReconciler()
        time = MutableTime()
        deviceZone = TimeZone.currentSystemDefault()
    }

    private fun resolver(history: MedicationHistoryDao = historyDao): UnloggedDoseResolver {
        val transactionRunner = SnapshotTransactionRunner(historyDao, stockDao)
        return UnloggedDoseResolver(
            projector = ScheduleProjector(scheduleDao, history, medicationDao, time),
            scheduleDao = scheduleDao,
            medicationDao = medicationDao,
            historyDao = history,
            transactionRunner = transactionRunner,
            timeProvider = time,
            state = state,
            reconciler = reconciler,
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
            deviceTimeZone = { deviceZone },
        )
    }

    private val mar1 = LocalDate(2025, 3, 1)
    private fun day(n: Int) = mar1.plus(n - 1, DateTimeUnit.DAY)

    private fun daily(
        id: Int = 1,
        start: LocalDate = mar1,
        end: LocalDate? = null,
        time: String = "08:00",
        resolveFrom: Long = 0L,
    ) = createSchedule(
        id = id,
        startDate = start.toEpochMillis(),
        endDate = end?.toEpochMillis(),
        timeOfDay = time,
        resolveFrom = resolveFrom,
    )

    private fun autoRows() = historyDao.history.filter { it.status == MedicationHistory.STATUS_AUTO_SKIPPED }

    @Test
    fun dailyDose_staysLoggableFor24h_thenResolves() = runTest {
        scheduleDao.seed(daily())
        time.nowMs = day(2).toEpochMillis("07:59")
        resolver().resolve()
        assertTrue(autoRows().none { it.scheduledTime == day(1).toEpochMillis("08:00") })

        time.nowMs = day(2).toEpochMillis("08:00")
        resolver().resolve()
        assertTrue(autoRows().any { it.scheduledTime == day(1).toEpochMillis("08:00") })
        assertEquals(0, state.awaySkipped, "Routine runs do not raise the away banner")
    }

    @Test
    fun weeklyDose_resolvesAfter24h_notOnlyAfterNextOccurrence() = runTest {
        // The next occurrence is 7 days away, but the dose still resolves after 24h.
        scheduleDao.seed(createSchedule(id = 1, startDate = mar1.toEpochMillis(), frequencyValue = 7))
        time.nowMs = day(2).toEpochMillis("09:00")
        resolver().resolve()
        assertEquals(listOf(day(1).toEpochMillis("08:00")), autoRows().map { it.scheduledTime })
    }

    @Test
    fun courseThatEndedDuringGap_resolvesThroughFinalDay() = runTest {
        scheduleDao.seed(daily(end = day(5)))
        time.nowMs = day(20).toEpochMillis("12:00")
        resolver().resolve()
        assertEquals((1..5).map { day(it).toEpochMillis("08:00") }, autoRows().map { it.scheduledTime })
    }

    @Test
    fun hundredTwentyDayGap_isFullyResolvedAcrossChunks() = runTest {
        scheduleDao.seed(daily())
        time.nowMs = day(121).toEpochMillis("12:00")
        val result = resolver().resolve()
        // Days 1..120 are past their deadline; day 121's 08:00 dose is still loggable.
        assertEquals(120, autoRows().size)
        assertEquals(120, result.skipped)
        assertEquals(day(121).toEpochMillis("08:00"), state.watermark, "Held at the oldest still-loggable dose")
        assertEquals(120, state.awaySkipped)
    }

    @Test
    fun takeMedication_logsAutoTaken_andDeductsStockOnlyAfterLatestRecount() = runTest {
        medicationDao.medications.clear()
        medicationDao.seed(
            createMedication(id = 1, stockTrackingEnabled = true, trackingPrecision = TrackingPrecision.EXACT)
                .copy(unloggedDoseAction = UnloggedDoseAction.TAKE),
        )
        // The recount on day 3 already reflects whatever was taken on days 1 to 3.
        stockDao.insert(
            StockAdjustment(
                medicationId = 1,
                quantityChange = 30.0,
                timestamp = day(3).toEpochMillis("12:00"),
                adjustmentType = StockAdjustmentType.MANUAL_CORRECTION.value,
            ),
        )
        scheduleDao.seed(daily())
        time.nowMs = day(6).toEpochMillis("12:00")

        val result = resolver().resolve()

        assertEquals(5, result.taken)
        assertTrue(
            historyDao.history.all {
                it.status == MedicationHistory.STATUS_AUTO_TAKEN && it.takenTime == it.scheduledTime
            },
        )
        val intakes = stockDao.adjustments.filter { it.adjustmentType == StockAdjustmentType.INTAKE.value }
        assertEquals(listOf(day(4).toEpochMillis("08:00"), day(5).toEpochMillis("08:00")), intakes.map { it.timestamp })
    }

    @Test
    fun failedChunk_rollsBack_andNextRunResumesWithoutGaps() = runTest {
        scheduleDao.seed(daily())
        time.nowMs = day(61).toEpochMillis("12:00")
        // First chunk (30 days) commits; the second fails on its 5th insert.
        val failing = FailingHistoryDao(historyDao, failOnInsert = 35)
        assertFailsWith<IllegalStateException> { resolver(failing).resolve() }
        assertEquals(30, autoRows().size, "Second chunk rolled back entirely")
        val watermarkAfterFailure = state.watermark

        resolver().resolve()
        assertEquals(60, autoRows().size)
        assertEquals(60, autoRows().map { it.scheduledTime }.toSet().size, "No duplicates")
        assertTrue(watermarkAfterFailure != null && watermarkAfterFailure <= state.watermark!!)
    }

    @Test
    fun secondRun_doesNotRevisitResolvedSpan() = runTest {
        scheduleDao.seed(daily())
        time.nowMs = day(10).toEpochMillis("12:00")
        resolver().resolve()
        historyDao.history.clear() // Simulate rows we must never recreate (e.g. deliberately deleted)
        resolver().resolve()
        assertTrue(autoRows().isEmpty(), "Span before the watermark is never touched again")
    }

    @Test
    fun eventsBeforeResolveFrom_areNeverResolved() = runTest {
        scheduleDao.seed(daily(resolveFrom = day(5).toEpochMillis()))
        time.nowMs = day(10).toEpochMillis("12:00")
        resolver().resolve()
        assertEquals((5..9).map { day(it).toEpochMillis("08:00") }, autoRows().map { it.scheduledTime })
    }

    @Test
    fun eventsBeforeResolveFrom_doNotPinTheWatermark() = runTest {
        time.nowMs = day(10).toEpochMillis("12:00")
        // Today's 08:00 dose is below resolveFrom (schedule edited at 10:00).
        scheduleDao.seed(daily(resolveFrom = day(10).toEpochMillis("10:00")))
        state.watermark = day(10).toEpochMillis("00:00")
        resolver().resolve()
        assertEquals(time.nowMs, state.watermark)
    }

    @Test
    fun reprojectedEventNearARealLog_isNotInvented() = runTest {
        // User logged 08:00 on day 2, then moved the schedule to 09:00. The projector now
        // shows a pending 09:00 dose on day 2 that never existed.
        scheduleDao.seed(daily(time = "09:00"))
        historyDao.insert(
            createHistory(scheduledTime = day(2).toEpochMillis("08:00"), status = MedicationHistory.STATUS_TAKEN),
        )
        time.nowMs = day(4).toEpochMillis("12:00")
        resolver().resolve()
        val resolved = autoRows().map { it.scheduledTime }
        assertTrue(day(2).toEpochMillis("09:00") !in resolved)
        assertTrue(day(3).toEpochMillis("09:00") in resolved)
    }

    @Test
    fun clockWentBack_runIsSkippedAndNothingIsDeleted() = runTest {
        scheduleDao.seed(daily())
        time.nowMs = day(10).toEpochMillis("12:00")
        resolver().resolve()
        val rows = historyDao.history.toList()
        val watermark = state.watermark

        time.nowMs = day(3).toEpochMillis("12:00")
        val result = resolver().resolve()

        assertEquals(ResolveResult.EMPTY, result)
        assertEquals(rows, historyDao.history)
        assertEquals(watermark, state.watermark)
    }

    @Test
    fun redefine_preResolvesOldDefinition_thenBumpsResolveFrom() = runTest {
        scheduleDao.seed(daily())
        time.nowMs = day(5).toEpochMillis("10:00")
        // Today's 08:00 dose is only 2h old, so it is not yet eligible on its own.
        val bumpedTo = resolver().redefine(listOf(1))

        assertEquals(time.nowMs, bumpedTo)
        assertTrue(autoRows().any { it.scheduledTime == day(5).toEpochMillis("08:00") })
        assertEquals(time.nowMs, scheduleDao.schedules.single().resolveFrom)
    }

    @Test
    fun deviceTimeZoneChange_redefinesOnlyDeviceFollowingSchedules() = runTest {
        medicationDao.seed(createMedication(id = 2, timeZoneMode = TimeZoneMode.FIXED, anchorTimeZone = "Asia/Tokyo"))
        scheduleDao.seed(daily(id = 1), createSchedule(id = 2, medicationId = 2, startDate = mar1.toEpochMillis()))
        state.timeZoneId = "Etc/GMT+12" // Different from the current device zone
        time.nowMs = day(5).toEpochMillis("12:00")

        resolver().resolve()

        val local = scheduleDao.schedules.first { it.id == 1 }
        val fixed = scheduleDao.schedules.first { it.id == 2 }
        assertEquals(time.nowMs, local.resolveFrom)
        assertEquals(0L, fixed.resolveFrom)
        assertEquals(deviceZone.id, state.timeZoneId)
    }

    @Test
    fun onScheduleCreated_lowersWatermarkToCoverEarlierDosesToday() = runTest {
        state.watermark = day(5).toEpochMillis("12:00")
        resolver().onScheduleCreated(day(5).toEpochMillis())
        assertEquals(day(5).toEpochMillis(), state.watermark)

        resolver().onScheduleCreated(day(6).toEpochMillis())
        assertEquals(day(5).toEpochMillis(), state.watermark, "Never raised")
    }

    @Test
    fun reconcile_onlyWhenRowsWereWritten() = runTest {
        time.nowMs = day(5).toEpochMillis("12:00")
        resolver().resolve()
        assertEquals(0, reconciler.count)

        scheduleDao.seed(daily())
        state.watermark = null
        resolver().resolve()
        assertEquals(1, reconciler.count)
    }
}
