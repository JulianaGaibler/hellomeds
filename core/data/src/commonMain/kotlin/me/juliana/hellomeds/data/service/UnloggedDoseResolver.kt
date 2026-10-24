// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.data.model.enums.UnloggedDoseAction
import me.juliana.hellomeds.data.preferences.ResolverStateStore
import me.juliana.hellomeds.data.repository.StockTrackingRepository
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.interfaces.DepletionChecker
import me.juliana.hellomeds.data.interfaces.LowStockChecker
import me.juliana.hellomeds.data.util.MissedDoseDetector
import me.juliana.hellomeds.data.util.TimeProvider
import me.juliana.hellomeds.data.util.TransactionRunner

/**
 * Turns scheduled doses the user never logged into history rows, so past days are
 * complete no matter how long the app was closed.
 *
 * A dose resolves 24h after its scheduled time. A schedule has one time of day, so its
 * next occurrence is never sooner than that (about 23h across a DST change).
 *
 * A persisted watermark marks everything before it as resolved. Each run walks
 * `[watermark, now)` in [CHUNK_MS] chunks, one transaction per chunk, and advances the
 * watermark after each commit. A run that is killed resumes where it stopped, and a
 * months-long gap never becomes one huge transaction. The watermark never moves past a
 * dose that is still inside its 24h window.
 *
 * History is matched to projections by exact epoch time, so anything that re-projects the
 * past (schedule edits, device timezone changes, medication timezone or cycle edits,
 * imports) makes real doses look unlogged. Two guards prevent inventing doses:
 * - `Schedule.resolveFrom`: events before it are never touched. [redefine] bumps it.
 * - Near-match: an event is skipped if any history row of the same schedule lies within
 *   ±[NEAR_MATCH_MS]. This is safe because a schedule's events are at least about 23h apart.
 *
 * If the watermark is ahead of now, the device clock went back. The run is skipped and
 * nothing is deleted, because that case can't be told apart from an earlier forward jump.
 *
 * Inserts use the dedup index with IGNORE, so a dose the user logs concurrently wins.
 *
 * Medications set to [UnloggedDoseAction.TAKE] get `AUTO_TAKEN` rows that deduct stock.
 * Doses at or before the latest stock recount, refill or initial stock entry are not
 * deducted, because that entry already reflects them.
 */
class UnloggedDoseResolver(
    private val projector: ScheduleProjector,
    private val scheduleDao: ScheduleDao,
    private val medicationDao: MedicationDao,
    private val historyDao: MedicationHistoryDao,
    private val transactionRunner: TransactionRunner,
    private val timeProvider: TimeProvider,
    private val state: ResolverStateStore,
    private val reconciler: ScheduleReconciler,
    private val stockTracking: StockTrackingRepository,
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val lowStockNotifier: LowStockChecker,
    private val depletionNotifier: DepletionChecker,
    private val deviceTimeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    companion object {
        private const val TAG = "UnloggedDoseResolver"

        /** A dose stays loggable for this long before it is auto-resolved. */
        const val RESOLVE_AFTER_MS: Long = MissedDoseDetector.MAX_LOOKBACK_MS
        const val NEAR_MATCH_MS: Long = 12 * 60 * 60 * 1000L
        const val CHUNK_MS: Long = 30L * 24 * 60 * 60 * 1000L

        /** Tolerated clock disagreement before a run is treated as a backwards clock jump. */
        const val CLOCK_SKEW_MS: Long = 5 * 60 * 1000L

        /** Runs covering less than this are routine and do not raise the "while you were away" banner. */
        const val AWAY_SUMMARY_MIN_SPAN_MS: Long = 3L * 24 * 60 * 60 * 1000L
    }

    private val mutex = Mutex()

    /** Resolve every eligible dose since the watermark. Safe to call from any trigger. */
    suspend fun resolve(): ResolveResult = mutex.withLock {
        withContext(Dispatchers.Default) { resolveLocked(timeProvider.nowMillis()) }
    }

    /**
     * Call before a change that re-projects these schedules' past (timing edit, medication
     * timezone or cycle edit). Resolves the old definition's still-pending doses from the
     * last 24h, which could no longer be matched afterwards, then bumps `resolveFrom` to
     * now. Returns the bump instant. Callers that write the Schedule entity themselves must
     * carry it, or their update overwrites the bump.
     */
    suspend fun redefine(scheduleIds: Collection<Int>): Long = mutex.withLock {
        withContext(Dispatchers.Default) {
            val now = timeProvider.nowMillis()
            resolveLocked(now)
            redefineLocked(scheduleIds.toSet(), now)
            now
        }
    }

    /**
     * Start resolving these schedules from now, without pre-resolving anything. For changes
     * that make past events appear that the user was never reminded about (unarchiving).
     */
    suspend fun startFresh(scheduleIds: Collection<Int>): Long = mutex.withLock {
        val now = timeProvider.nowMillis()
        if (scheduleIds.isNotEmpty()) scheduleDao.bumpResolveFrom(scheduleIds.toList(), now)
        now
    }

    /**
     * A new schedule may resolve from the start of its creation day, which can be before
     * the watermark. Lower the watermark so that span is visited. Re-visiting already
     * resolved spans is a no-op.
     */
    suspend fun onScheduleCreated(resolveFrom: Long) = mutex.withLock {
        val watermark = state.getWatermark() ?: return@withLock
        if (resolveFrom < watermark) state.setWatermark(resolveFrom)
    }

    private suspend fun resolveLocked(now: Long): ResolveResult {
        val storedWatermark = state.getWatermark()
        if (storedWatermark != null && storedWatermark > now + CLOCK_SKEW_MS) {
            AppLogger.w(TAG, "Watermark $storedWatermark is ahead of now $now — clock went back, skipping run")
            return ResolveResult.EMPTY
        }

        var counts = Counts()
        counts += handleDeviceTimeZoneChange(now)

        val schedules = scheduleDao.getNonArchived()
        // First run: from the earliest instant any schedule may resolve. resolveFrom can be
        // 0 for rows written outside ScheduleRepository, and nothing projects before startDate.
        val start = storedWatermark
            ?: schedules.minOfOrNull { maxOf(it.resolveFrom, it.startDate) }
            ?: return finish(counts, spanMs = 0)
        val schedulesById = schedules.associateBy { it.id }
        val medications = medicationDao.getAllSuspend().associateBy { it.id }

        var cursor = start
        while (cursor < now) {
            val chunkEnd = minOf(cursor + CHUNK_MS, now)
            val history = historyDao.getInTimeRangeSuspend(cursor - NEAR_MATCH_MS, chunkEnd + NEAR_MATCH_MS)
            val events = projector.projectEventsWithHistory(schedules, cursor, chunkEnd, history, medications)
            val nearIndex = NearMatchIndex(history)

            var oldestHeld: Long? = null
            val toResolve = mutableListOf<ProjectedEvent>()
            for (event in events) {
                if (!event.isPending) continue
                val schedule = schedulesById[event.scheduleId] ?: continue
                if (event.scheduledTime < schedule.resolveFrom) continue
                if (event.scheduledTime + RESOLVE_AFTER_MS > now) {
                    if (oldestHeld == null) oldestHeld = event.scheduledTime
                    continue
                }
                if (nearIndex.hasNear(event)) continue
                toResolve += event
            }

            counts += write(toResolve, medications)

            if (oldestHeld != null) {
                state.setWatermark(oldestHeld)
                break
            }
            state.setWatermark(chunkEnd)
            cursor = chunkEnd
        }

        return finish(counts, spanMs = now - start)
    }

    private suspend fun redefineLocked(scheduleIds: Set<Int>, now: Long): Counts {
        if (scheduleIds.isEmpty()) return Counts()
        val schedules = scheduleDao.getNonArchived().filter { it.id in scheduleIds }
        val medications = medicationDao.getAllSuspend().associateBy { it.id }
        val windowStart = now - RESOLVE_AFTER_MS
        val history = historyDao.getInTimeRangeSuspend(windowStart - NEAR_MATCH_MS, now + NEAR_MATCH_MS)
        val nearIndex = NearMatchIndex(history)
        val schedulesById = schedules.associateBy { it.id }

        val toResolve = projector.projectEventsWithHistory(schedules, windowStart, now, history, medications)
            .filter { event ->
                event.isPending &&
                    event.scheduledTime >= (schedulesById[event.scheduleId]?.resolveFrom ?: Long.MAX_VALUE) &&
                    !nearIndex.hasNear(event)
            }
        val counts = write(toResolve, medications)
        scheduleDao.bumpResolveFrom(scheduleIds.toList(), now)
        if (counts.written > 0) reconciler.reconcile()
        checkStock(counts)
        AppLogger.i(TAG, "Redefined schedules $scheduleIds at $now, pre-resolved ${counts.written}")
        return counts
    }

    /**
     * Schedules that follow the device timezone re-project their past when it changes.
     * Treat that as a redefinition of exactly those schedules.
     */
    private suspend fun handleDeviceTimeZoneChange(now: Long): Counts {
        val currentId = deviceTimeZone().id
        val lastId = state.getLastSeenTimeZoneId()
        if (lastId == currentId) return Counts()
        state.setLastSeenTimeZoneId(currentId)
        if (lastId == null) return Counts() // First run: nothing to compare against.

        val medications = medicationDao.getAllSuspend().associateBy { it.id }
        val followingDevice = scheduleDao.getNonArchived()
            .filter { followsDeviceTimeZone(medications[it.medicationId], it) }
            .map { it.id }
            .toSet()
        AppLogger.i(TAG, "Device timezone $lastId → $currentId, redefining ${followingDevice.size} schedules")
        return redefineLocked(followingDevice, now)
    }

    /** Mirrors [ScheduleTimeZoneResolver]: everything except FIXED mode with a usable zone. */
    private fun followsDeviceTimeZone(medication: Medication?, schedule: Schedule): Boolean {
        if (medication?.timeZoneMode != TimeZoneMode.FIXED) return true
        val zoneId = medication.anchorTimeZone ?: schedule.originTimeZone ?: return true
        return runCatching { TimeZone.of(zoneId) }.isFailure
    }

    private suspend fun write(events: List<ProjectedEvent>, medications: Map<Int, Medication>): Counts {
        if (events.isEmpty()) return Counts()
        val stockCutoffs = mutableMapOf<Int, Long>()
        return transactionRunner.run {
            var counts = Counts()
            for (event in events) {
                val medication = medications[event.medicationId] ?: continue
                val take = medication.unloggedDoseAction == UnloggedDoseAction.TAKE
                val id = historyDao.insert(
                    MedicationHistory(
                        medicationId = event.medicationId,
                        scheduleId = event.scheduleId,
                        scheduledTime = event.scheduledTime,
                        takenTime = if (take) event.scheduledTime else null,
                        scheduledDose = event.dose,
                        actualDose = if (take) event.dose else null,
                        status = if (take) MedicationHistory.STATUS_AUTO_TAKEN else MedicationHistory.STATUS_AUTO_SKIPPED,
                    ),
                )
                if (id == -1L) continue
                counts = if (take) {
                    val cutoff = stockCutoffs.getOrPut(event.medicationId) { latestStockBaseline(event.medicationId) }
                    if (event.scheduledTime > cutoff) deductStock(event, id.toInt())
                    counts.copy(taken = counts.taken + 1, medicationIds = counts.medicationIds + event.medicationId)
                        .withTaken(event.medicationId)
                } else {
                    counts.copy(skipped = counts.skipped + 1, medicationIds = counts.medicationIds + event.medicationId)
                }
            }
            counts
        }
    }

    /** Timestamp of the newest stock entry that sets the level directly, or 0 if there is none. */
    private suspend fun latestStockBaseline(medicationId: Int): Long = listOf(
        StockAdjustmentType.INITIAL_STOCK,
        StockAdjustmentType.MANUAL_CORRECTION,
        StockAdjustmentType.REFILL,
    ).maxOf { type -> stockAdjustmentDao.getLatestByTypeForMedication(medicationId, type.value)?.timestamp ?: 0L }

    private suspend fun deductStock(event: ProjectedEvent, historyId: Int) {
        try {
            stockTracking.recordDoseConsumption(event.medicationId, historyId, event.dose, event.scheduledTime)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to deduct stock for history $historyId", e)
        }
    }

    /** Low-stock and depletion checks are best effort and run once per medication per run. */
    private suspend fun checkStock(counts: Counts) {
        for (medicationId in counts.takenMedicationIds) {
            try {
                lowStockNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
            }
            try {
                depletionNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
            }
        }
    }

    private suspend fun finish(counts: Counts, spanMs: Long): ResolveResult {
        if (counts.written > 0) {
            AppLogger.i(TAG, "Resolved ${counts.skipped} skipped / ${counts.taken} taken over ${spanMs}ms")
            reconciler.reconcile()
        }
        if (spanMs >= AWAY_SUMMARY_MIN_SPAN_MS && counts.written > 0) {
            state.addAwaySummary(counts.skipped, counts.taken)
        }
        checkStock(counts)
        return ResolveResult(counts.skipped, counts.taken, spanMs, counts.medicationIds)
    }

    private data class Counts(
        val skipped: Int = 0,
        val taken: Int = 0,
        val medicationIds: Set<Int> = emptySet(),
        val takenMedicationIds: Set<Int> = emptySet(),
    ) {
        val written: Int get() = skipped + taken

        fun withTaken(medicationId: Int) = copy(takenMedicationIds = takenMedicationIds + medicationId)

        operator fun plus(other: Counts) = Counts(
            skipped + other.skipped,
            taken + other.taken,
            medicationIds + other.medicationIds,
            takenMedicationIds + other.takenMedicationIds,
        )
    }

    /** History rows per schedule, sorted by scheduledTime, for the ±12h guard. */
    private class NearMatchIndex(history: List<MedicationHistory>) {
        private val bySchedule: Map<Int, List<Long>> = history
            .filter { it.scheduleId != null && it.scheduledTime != null }
            .groupBy({ it.scheduleId!! }, { it.scheduledTime!! })
            .mapValues { (_, times) -> times.sorted() }

        fun hasNear(event: ProjectedEvent): Boolean {
            val times = bySchedule[event.scheduleId] ?: return false
            return times.any { kotlin.math.abs(it - event.scheduledTime) < NEAR_MATCH_MS }
        }
    }
}

/** Outcome of one resolver run, for logging and the "while you were away" summary. */
data class ResolveResult(
    val skipped: Int,
    val taken: Int,
    /** Span of time this run covered (now - starting watermark). */
    val spanMs: Long,
    val affectedMedicationIds: Set<Int>,
) {
    /** True when history rows were written; the resolver has then already reconciled. */
    val wroteRows: Boolean get() = skipped + taken > 0

    companion object {
        val EMPTY = ResolveResult(0, 0, 0, emptySet())
    }
}
