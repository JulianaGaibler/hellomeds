// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.interfaces.DepletionChecker
import me.juliana.hellomeds.data.interfaces.LowStockChecker
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.ProjectedEventWithMedication
import me.juliana.hellomeds.data.model.TrackingDayEvents
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.util.TransactionRunner
import kotlin.time.Clock

/**
 * Repository for medication history (taken/skipped/auto-skipped actions).
 * Combines ScheduleProjector output with history records for the tracking UI.
 *
 * Replaces the old LogEventRepository. Future events are never stored —
 * they come from ScheduleProjector. Only past actions are persisted here.
 */
class MedicationHistoryRepository(
    private val historyDao: MedicationHistoryDao,
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val transactionRunner: TransactionRunner,
    private val projector: ScheduleProjector,
    private val stockTrackingRepository: StockTrackingRepository,
    private val reconciler: ScheduleReconciler,
    private val lowStockNotifier: LowStockChecker,
    private val depletionReminderNotifier: DepletionChecker,
    private val clock: Clock = Clock.System,
) {

    /**
     * Mark a projected event as taken. Creates a MedicationHistory record
     * and records stock consumption atomically.
     */
    suspend fun markAsTaken(
        event: ProjectedEvent,
        actualDose: Double,
        takenTime: Long = clock.now().toEpochMilliseconds(),
    ): Int {
        AppLogger.d(
            TAG,
            "markAsTaken: scheduleId=${event.scheduleId}, scheduledTime=${event.scheduledTime}, " +
                "ctx=${kotlin.coroutines.coroutineContext}",
        )

        return transactionRunner.run {
            // Check for existing record to prevent duplicates
            val existingId = historyDao.findByCompositeKey(
                event.medicationId,
                event.scheduleId,
                event.scheduledTime,
            )

            val historyId = if (existingId != null) {
                historyDao.updateStatusAndDose(
                    existingId,
                    MedicationHistory.STATUS_TAKEN,
                    actualDose,
                    takenTime,
                )
                AppLogger.i(TAG, "markAsTaken: Updated existing record id=$existingId")
                existingId
            } else {
                val history = MedicationHistory(
                    medicationId = event.medicationId,
                    scheduleId = event.scheduleId,
                    scheduledTime = event.scheduledTime,
                    takenTime = takenTime,
                    scheduledDose = event.dose,
                    actualDose = actualDose,
                    status = MedicationHistory.STATUS_TAKEN,
                )
                val id = historyDao.insert(history).toInt()
                AppLogger.i(TAG, "markAsTaken: Created history record id=$id")
                id
            }

            // Record or update stock consumption
            try {
                if (existingId != null) {
                    // Update path: adjust existing stock record if present
                    val existingAdj = stockTrackingRepository.getAdjustmentByHistoryId(historyId)
                    if (existingAdj != null) {
                        stockTrackingRepository.updateAdjustment(existingAdj.id, -actualDose)
                    } else {
                        stockTrackingRepository.recordDoseConsumption(
                            medicationId = event.medicationId,
                            historyId = historyId,
                            doseQuantity = actualDose,
                        )
                    }
                } else {
                    // Insert path: create new stock record
                    stockTrackingRepository.recordDoseConsumption(
                        medicationId = event.medicationId,
                        historyId = historyId,
                        doseQuantity = actualDose,
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to record stock adjustment", e)
            }

            historyId
        }.also {
            reconciler.reconcile()
            try {
                lowStockNotifier.checkAndNotify(event.medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
            }
            try {
                depletionReminderNotifier.checkAndNotify(event.medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
            }
        }
    }

    /**
     * Mark a projected event as skipped. Creates a MedicationHistory record.
     */
    suspend fun markAsSkipped(event: ProjectedEvent) {
        AppLogger.d(
            TAG,
            "markAsSkipped: scheduleId=${event.scheduleId}, scheduledTime=${event.scheduledTime}",
        )

        transactionRunner.run {
            val existingId = historyDao.findByCompositeKey(
                event.medicationId,
                event.scheduleId,
                event.scheduledTime,
            )

            if (existingId != null) {
                historyDao.updateStatusAndDose(
                    existingId,
                    MedicationHistory.STATUS_SKIPPED,
                    0.0,
                    0L,
                )
                // Reverse any stock adjustment from a previous TAKEN status
                try {
                    stockTrackingRepository.reverseStockAdjustment(existingId)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to reverse stock on skip update", e)
                }
                AppLogger.i(TAG, "markAsSkipped: Updated existing record id=$existingId")
            } else {
                val history = MedicationHistory(
                    medicationId = event.medicationId,
                    scheduleId = event.scheduleId,
                    scheduledTime = event.scheduledTime,
                    scheduledDose = event.dose,
                    status = MedicationHistory.STATUS_SKIPPED,
                )
                val id = historyDao.insert(history)
                AppLogger.i(TAG, "markAsSkipped: Created history record id=$id")
            }
        }
        reconciler.reconcile()
    }

    /**
     * Mark a projected event as taken using an existing history record (edit case).
     * Updates the history record and adjusts stock.
     */
    suspend fun updateTaken(historyId: Int, actualDose: Double, takenTime: Long) {
        val medicationId = transactionRunner.run {
            val existing = historyDao.getByIdSync(historyId) ?: return@run null

            val updated = existing.copy(
                status = MedicationHistory.STATUS_TAKEN,
                takenTime = takenTime,
                actualDose = actualDose,
            )
            historyDao.update(updated)

            // Update stock adjustment
            try {
                val existingAdj = stockTrackingRepository.getAdjustmentByHistoryId(historyId)
                if (existingAdj != null) {
                    stockTrackingRepository.updateAdjustment(existingAdj.id, -actualDose)
                } else {
                    stockTrackingRepository.recordDoseConsumption(
                        medicationId = existing.medicationId,
                        historyId = historyId,
                        doseQuantity = actualDose,
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update stock adjustment", e)
            }

            existing.medicationId
        }
        reconciler.reconcile()
        if (medicationId != null) {
            try {
                lowStockNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
            }
            try {
                depletionReminderNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
            }
        }
    }

    /**
     * Update an existing history record to skipped status.
     */
    suspend fun updateSkipped(historyId: Int) {
        val medicationId = transactionRunner.run {
            val existing = historyDao.getByIdSync(historyId) ?: return@run null

            val updated = existing.copy(
                status = MedicationHistory.STATUS_SKIPPED,
                takenTime = null,
                actualDose = null,
            )
            historyDao.update(updated)

            // Reverse any stock adjustment
            try {
                stockTrackingRepository.reverseStockAdjustment(historyId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to reverse stock adjustment", e)
            }

            existing.medicationId
        }
        reconciler.reconcile()
        if (medicationId != null) {
            try {
                lowStockNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
            }
            try {
                depletionReminderNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
            }
        }
    }

    /**
     * Delete a history record (revert to pending).
     * Reverses any stock adjustments.
     */
    suspend fun deleteHistoryRecord(historyId: Int) {
        val medicationId = transactionRunner.run {
            val record = historyDao.getByIdSync(historyId)
            if (record == null) {
                AppLogger.e(TAG, "deleteHistoryRecord: historyId=$historyId not found — silent no-op")
                return@run null
            }

            if (record.scheduleId != null && record.scheduledTime != null) {
                // Scheduled medication: clean up all duplicates and reverse all stock
                val allDuplicates = historyDao.getAllByCompositeKey(
                    record.medicationId,
                    record.scheduleId,
                    record.scheduledTime,
                )
                for (dup in allDuplicates) {
                    try {
                        stockTrackingRepository.reverseStockAdjustment(dup.id)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to reverse stock for duplicate id=${dup.id}", e)
                    }
                }
                historyDao.deleteByCompositeKey(
                    record.medicationId,
                    record.scheduleId,
                    record.scheduledTime,
                )
                AppLogger.i(
                    TAG,
                    "deleteHistoryRecord: Deleted ${allDuplicates.size} record(s) by composite key",
                )
            } else {
                // As-needed (PRN) medication: no composite key, delete by ID
                try {
                    stockTrackingRepository.reverseStockAdjustment(record.id)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to reverse stock adjustment on delete", e)
                }
                historyDao.deleteById(record.id)
                AppLogger.i(TAG, "deleteHistoryRecord: Deleted history id=${record.id}")
            }

            record.medicationId
        }
        reconciler.reconcile()
        if (medicationId != null) {
            try {
                lowStockNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
            }
            try {
                depletionReminderNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
            }
        }
    }

    /**
     * Delete the history record(s) for a projected event (revert to pending).
     *
     * For scheduled events, deletes by composite key `(medicationId, scheduleId,
     * scheduledTime)` — this is robust against stale `event.historyRecord?.id`
     * captured by a long-lived dialog. For PRN events (`scheduleId == 0`),
     * falls back to id-based delete.
     */
    suspend fun deleteHistoryRecord(event: ProjectedEvent) {
        if (event.isScheduled) {
            val medicationId = event.medicationId
            val scheduleId = event.scheduleId
            val scheduledTime = event.scheduledTime

            transactionRunner.run {
                val allDuplicates = historyDao.getAllByCompositeKey(
                    medicationId,
                    scheduleId,
                    scheduledTime,
                )
                if (allDuplicates.isEmpty()) {
                    AppLogger.w(
                        TAG,
                        "deleteHistoryRecord(event): no rows for med=$medicationId schedule=$scheduleId time=$scheduledTime",
                    )
                    return@run
                }
                for (dup in allDuplicates) {
                    try {
                        stockTrackingRepository.reverseStockAdjustment(dup.id)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to reverse stock for duplicate id=${dup.id}", e)
                    }
                }
                historyDao.deleteByCompositeKey(medicationId, scheduleId, scheduledTime)
                AppLogger.i(
                    TAG,
                    "deleteHistoryRecord(event): deleted ${allDuplicates.size} record(s) by composite key",
                )
            }
            reconciler.reconcile()
            try {
                lowStockNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
            }
            try {
                depletionReminderNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
            }
        } else {
            val id = event.historyRecord?.id
            if (id == null) {
                AppLogger.w(TAG, "deleteHistoryRecord(event): PRN event has no historyRecord.id — skipping")
                return
            }
            deleteHistoryRecord(id)
        }
    }

    /**
     * Create a manual dose record for "as needed" medications.
     */
    suspend fun createManualDose(
        medicationId: Int,
        dose: Double,
        takenTime: Long = clock.now().toEpochMilliseconds(),
        notes: String? = null,
    ): Long {
        return transactionRunner.run {
            val history = MedicationHistory(
                medicationId = medicationId,
                scheduleId = null,
                scheduledTime = null,
                takenTime = takenTime,
                scheduledDose = dose,
                actualDose = dose,
                status = MedicationHistory.STATUS_TAKEN,
                notes = notes,
            )

            val id = historyDao.insert(history)

            try {
                stockTrackingRepository.recordDoseConsumption(
                    medicationId = medicationId,
                    historyId = id.toInt(),
                    doseQuantity = dose,
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to record stock adjustment for manual dose", e)
            }

            AppLogger.i(TAG, "createManualDose: Created manual history id=$id")
            id
        }.also {
            reconciler.reconcile()
            try {
                lowStockNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
            }
            try {
                depletionReminderNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
            }
        }
    }

    /**
     * Auto-skip events whose next occurrence has already passed.
     */
    suspend fun autoSkipMissedEvents() {
        val now = clock.now().toEpochMilliseconds()
        // Look at events from 48 hours ago to now
        val windowStart = now - (48 * 60 * 60 * 1000L)

        val events = projector.projectEvents(windowStart, now)
        val pendingPastEvents = events.filter { it.isPending && it.scheduledTime < now }

        for (event in pendingPastEvents) {
            if (projector.shouldAutoSkip(event.scheduleId, event.scheduledTime, now)) {
                val history = MedicationHistory(
                    medicationId = event.medicationId,
                    scheduleId = event.scheduleId,
                    scheduledTime = event.scheduledTime,
                    scheduledDose = event.dose,
                    status = MedicationHistory.STATUS_AUTO_SKIPPED,
                )
                historyDao.insert(history)
                AppLogger.d(
                    TAG,
                    "Auto-skipped event: schedule=${event.scheduleId}, time=${event.scheduledTime}",
                )
            }
        }
    }

    /**
     * Get projected events for a specific date with medication information,
     * split into pending/taken/skipped/overdue lists for the tracking UI.
     *
     * When [isToday] is true, also looks back 24h for pending events that the user
     * hasn't acted on yet (cross-midnight carry-forward). These appear in the
     * [TrackingDayEvents.overdue] list so the UI can display them separately.
     */
    fun getEventsForDateWithMedication(date: LocalDate, isToday: Boolean = false): Flow<TrackingDayEvents> {
        val tz = TimeZone.currentSystemDefault()
        val startOfDay = date.atStartOfDayIn(tz).toEpochMilliseconds()
        val endOfDay = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()

        // Widen query window to include overdue events from the previous 24h
        val overdueWindowStart = if (isToday) startOfDay - (24 * 60 * 60 * 1000L) else startOfDay

        return combine(
            historyDao.getInTimeRange(overdueWindowStart, endOfDay),
            medicationDao.getActive(),
            scheduleDao.getActive(),
        ) { history, medications, schedules ->
            val medicationMap = medications.associateBy { it.id }

            // Generate projected events for the full window (including overdue lookback)
            val projected = projector.projectEventsWithHistory(
                schedules,
                overdueWindowStart,
                endOfDay,
                history,
                medicationMap,
            )

            // Split projected events: today's events vs overdue carry-forward
            val todayEvents = projected.filter { it.scheduledTime in startOfDay until endOfDay }
            val overdueEvents = if (isToday) {
                projected.filter { it.scheduledTime in overdueWindowStart until startOfDay && it.isPending }
            } else {
                emptyList()
            }

            // Also include as-needed history records (no scheduleId) for today
            val asNeededHistory = history.filter {
                it.scheduleId == null &&
                    (it.takenTime ?: it.createdAt) in startOfDay until endOfDay
            }

            val allWithMedication = mutableListOf<ProjectedEventWithMedication>()

            // Add projected events for today (scheduled + already acted upon)
            for (event in todayEvents) {
                val medication = medicationMap[event.medicationId] ?: continue
                allWithMedication.add(ProjectedEventWithMedication(event, medication))
            }

            // Add as-needed history as "events" with dummy projection
            for (record in asNeededHistory) {
                val medication = medicationMap[record.medicationId] ?: continue
                val dummyEvent = ProjectedEvent(
                    scheduleId = 0,
                    medicationId = record.medicationId,
                    scheduledTime = record.takenTime ?: record.createdAt,
                    dose = record.actualDose ?: record.scheduledDose,
                    historyRecord = record,
                )
                allWithMedication.add(ProjectedEventWithMedication(dummyEvent, medication))
            }

            // Build overdue list with medication info
            val overdueWithMedication = overdueEvents.mapNotNull { event ->
                val medication = medicationMap[event.medicationId] ?: return@mapNotNull null
                ProjectedEventWithMedication(event, medication)
            }

            // Split today's events into categories
            val pending = allWithMedication.filter { it.event.isPending }
            val taken = allWithMedication.filter { it.event.isTaken }
            val skipped = allWithMedication.filter { it.event.isSkipped }

            TrackingDayEvents(pending, taken, skipped, overdueWithMedication)
        }
    }

    /**
     * Get all history records in a time range (for completion status calculation).
     */
    fun getHistoryInTimeRange(startTime: Long, endTime: Long): Flow<List<MedicationHistory>> {
        return historyDao.getInTimeRange(startTime, endTime)
    }

    /**
     * Get all history records (reactive flow for UI triggers).
     */
    fun getAll(): Flow<List<MedicationHistory>> = historyDao.getAll()

    companion object {
        private const val TAG = "MedicationHistoryRepo"
    }
}
