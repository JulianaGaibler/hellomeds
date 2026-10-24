// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import androidx.room.InvalidationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.NotificationSessionDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.AppDatabase
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.entities.NotificationSessionEntity
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.database.entities.StockAdjustment
import me.juliana.hellomeds.data.interfaces.DepletionChecker
import me.juliana.hellomeds.data.interfaces.LowStockChecker
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler

// ================================================================
// In-memory fake DAOs for backup service tests.
// Only methods used by BackupImportService / BackupExportService
// have real implementations; others throw.
// ================================================================

class FakeImportanceLabelDao : ImportanceLabelDao {
    val labels = mutableListOf<ImportanceLabel>()
    private var nextId = 1

    fun seed(vararg items: ImportanceLabel) {
        labels.addAll(items)
        nextId = (items.maxOfOrNull { it.id } ?: 0) + 1
    }

    override suspend fun insert(label: ImportanceLabel): Long {
        val id = nextId++
        labels.add(label.copy(id = id))
        return id.toLong()
    }

    override suspend fun update(label: ImportanceLabel) {
        val idx = labels.indexOfFirst { it.id == label.id }
        if (idx >= 0) labels[idx] = label
    }

    override suspend fun delete(label: ImportanceLabel) {
        labels.removeAll { it.id == label.id }
    }

    override fun getAll(): Flow<List<ImportanceLabel>> = flow { emit(labels.toList()) }

    override suspend fun getAllSuspend(): List<ImportanceLabel> = labels.toList()

    override fun getById(id: Int): Flow<ImportanceLabel?> = flow {
        emit(labels.find { it.id == id })
    }

    override suspend fun getByIdSync(id: Int) = labels.find { it.id == id }
    override suspend fun getCount() = labels.size
    override suspend fun getByDefaultType(type: String) = labels.find { it.defaultType == type }
}

class FakeMedicationDao : MedicationDao {
    val medications = mutableListOf<Medication>()
    private var nextId = 1

    fun seed(vararg items: Medication) {
        medications.addAll(items)
        nextId = (items.maxOfOrNull { it.id } ?: 0) + 1
    }

    override suspend fun insert(medication: Medication): Long {
        val id = nextId++
        medications.add(medication.copy(id = id))
        return id.toLong()
    }

    override suspend fun update(medication: Medication) {
        val idx = medications.indexOfFirst { it.id == medication.id }
        if (idx >= 0) medications[idx] = medication
    }

    override suspend fun delete(medication: Medication) {
        medications.removeAll { it.id == medication.id }
    }

    override fun getAll(): Flow<List<Medication>> = flow { emit(medications.toList()) }

    override suspend fun getAllSuspend(): List<Medication> = medications.toList()

    override fun getById(id: Int) = flow { emit(medications.find { it.id == id }) }

    override fun getActive() = flow {
        emit(medications.filter { !it.isArchived }.sortedBy { it.displayOrder })
    }

    override fun getArchived() = flow {
        emit(medications.filter { it.isArchived }.sortedBy { it.displayOrder })
    }

    override suspend fun archive(id: Int) {
        val idx = medications.indexOfFirst { it.id == id }
        if (idx >= 0) medications[idx] = medications[idx].copy(isArchived = true)
    }

    override suspend fun unarchive(id: Int) {
        val idx = medications.indexOfFirst { it.id == id }
        if (idx >= 0) medications[idx] = medications[idx].copy(isArchived = false)
    }

    override suspend fun updateDisplayOrder(id: Int, displayOrder: Int) {
        val idx = medications.indexOfFirst { it.id == id }
        if (idx >= 0) medications[idx] = medications[idx].copy(displayOrder = displayOrder)
    }

    override suspend fun updateAll(medications: List<Medication>) {
        medications.forEach { update(it) }
    }

    override suspend fun getMaxActiveDisplayOrder(): Int =
        medications.filter { !it.isArchived }.maxOfOrNull { it.displayOrder } ?: -1

    override suspend fun getByIdSync(id: Int) = medications.find { it.id == id }

    override fun getActiveMedicationsByImportanceLabelId(labelId: Int) = flow {
        emit(medications.filter { it.importanceLabelId == labelId && !it.isArchived })
    }

    override suspend fun getArchivedMedicationsByImportanceLabelId(labelId: Int) =
        medications.filter { it.importanceLabelId == labelId && it.isArchived }

    override suspend fun reassignArchivedMedications(oldLabelId: Int, newLabelId: Int) {
        for (i in medications.indices) {
            val m = medications[i]
            if (m.importanceLabelId == oldLabelId && m.isArchived) {
                medications[i] = m.copy(importanceLabelId = newLabelId)
            }
        }
    }

    override suspend fun updateCachedStockQuantity(medicationId: Int, newStock: Double) {
        val idx = medications.indexOfFirst { it.id == medicationId }
        if (idx >= 0) medications[idx] = medications[idx].copy(currentStockQuantity = newStock)
    }

    override suspend fun updateLowStockAlertSent(medicationId: Int, sent: Boolean) {
        val idx = medications.indexOfFirst { it.id == medicationId }
        if (idx >= 0) medications[idx] = medications[idx].copy(lowStockAlertSent = sent)
    }

    override suspend fun updateDepletionAlertSent(medicationId: Int, sent: Boolean) {
        val idx = medications.indexOfFirst { it.id == medicationId }
        if (idx >= 0) medications[idx] = medications[idx].copy(depletionAlertSent = sent)
    }

    override fun getStockTracked() = flow {
        emit(medications.filter { it.stockTrackingEnabled })
    }

    override suspend fun getByIds(ids: List<Int>) = medications.filter { it.id in ids }

    override suspend fun updateBubbleLayout(
        medicationId: Int,
        manualLayout: String?,
        flow: me.juliana.hellomeds.data.model.enums.BubbleFlowDirection,
    ) {
        val idx = medications.indexOfFirst { it.id == medicationId }
        if (idx >= 0) {
            medications[idx] = medications[idx].copy(
                bubbleManualLayout = manualLayout,
                bubbleFlowDirection = flow,
            )
        }
    }

    override fun hasActiveMedicationsWithCriticalLabel() = flow { emit(false) }

    override fun hasActiveMedicationsWithAlarmLabel() = flow { emit(false) }
}

class FakeScheduleDao : ScheduleDao {
    val schedules = mutableListOf<Schedule>()
    private var nextId = 1

    fun seed(vararg items: Schedule) {
        schedules.addAll(items)
        nextId = (items.maxOfOrNull { it.id } ?: 0) + 1
    }

    override suspend fun insert(schedule: Schedule): Long {
        val id = nextId++
        schedules.add(schedule.copy(id = id))
        return id.toLong()
    }

    override suspend fun update(schedule: Schedule) {
        val idx = schedules.indexOfFirst { it.id == schedule.id }
        if (idx >= 0) schedules[idx] = schedule
    }

    override suspend fun delete(schedule: Schedule) {
        schedules.removeAll { it.id == schedule.id }
    }

    override fun getAll() = flow { emit(schedules.toList()) }
    override fun getById(id: Int) = flow { emit(schedules.find { it.id == id }) }

    override fun getByMedicationId(medicationId: Int) = flow {
        emit(schedules.filter { it.medicationId == medicationId })
    }

    override fun getActive(startOfToday: Long) = flow {
        emit(schedules.filter { !it.isArchived && (it.endDate == null || it.endDate >= startOfToday) })
    }

    override suspend fun getNonArchived() = schedules.filter { !it.isArchived }

    override fun observeNonArchived() = flow { emit(schedules.filter { !it.isArchived }) }

    override suspend fun bumpResolveFrom(ids: List<Int>, resolveFrom: Long) {
        for (i in schedules.indices) {
            val s = schedules[i]
            if (s.id in ids && s.resolveFrom < resolveFrom) schedules[i] = s.copy(resolveFrom = resolveFrom)
        }
    }
    override suspend fun archive(id: Int) {
        val idx = schedules.indexOfFirst { it.id == id }
        if (idx >= 0) schedules[idx] = schedules[idx].copy(isArchived = true)
    }

    override suspend fun unarchive(id: Int) {
        val idx = schedules.indexOfFirst { it.id == id }
        if (idx >= 0) schedules[idx] = schedules[idx].copy(isArchived = false)
    }
}

class FakeHistoryDao : MedicationHistoryDao {
    val history = mutableListOf<MedicationHistory>()
    private var nextId = 1

    /**
     * Mirrors Room's IGNORE on the unique (medicationId, scheduleId, scheduledTime) index:
     * a duplicate returns -1 and is not stored. SQLite treats NULLs as distinct, so PRN rows
     * (null scheduleId/scheduledTime) never collide.
     */
    override suspend fun insert(historyEntry: MedicationHistory): Long {
        if (historyEntry.scheduleId != null && historyEntry.scheduledTime != null &&
            history.any {
                it.medicationId == historyEntry.medicationId &&
                    it.scheduleId == historyEntry.scheduleId &&
                    it.scheduledTime == historyEntry.scheduledTime
            }
        ) {
            return -1L
        }
        return insertRaw(historyEntry)
    }

    /** Bypasses the dedup index to simulate duplicate rows written before the index existed. */
    fun insertRaw(historyEntry: MedicationHistory): Long {
        val id = nextId++
        history.add(historyEntry.copy(id = id))
        return id.toLong()
    }

    override suspend fun update(historyEntry: MedicationHistory) {
        val idx = history.indexOfFirst { it.id == historyEntry.id }
        if (idx >= 0) history[idx] = historyEntry
    }

    override fun getById(id: Int) = flow { emit(history.find { it.id == id }) }
    override suspend fun getByIdSync(id: Int) = history.find { it.id == id }

    override fun getByMedicationId(medicationId: Int) = flow {
        emit(
            history.filter { it.medicationId == medicationId }.sortedByDescending {
                it.scheduledTime ?: it.takenTime
            },
        )
    }

    override fun getByScheduleId(scheduleId: Int) = flow {
        emit(history.filter { it.scheduleId == scheduleId }.sortedByDescending { it.scheduledTime })
    }

    override fun getInTimeRange(startTime: Long, endTime: Long) = flow {
        emit(
            history.filter { h ->
                val t = h.scheduledTime ?: h.takenTime ?: return@filter false
                t in startTime until endTime
            },
        )
    }

    override suspend fun getInTimeRangeSuspend(startTime: Long, endTime: Long) = history.filter { h ->
        val t = h.scheduledTime ?: h.takenTime ?: return@filter false
        t in startTime until endTime
    }

    override suspend fun getByScheduleAndTime(scheduleId: Int, scheduledTime: Long) =
        history.find { it.scheduleId == scheduleId && it.scheduledTime == scheduledTime }

    override fun getAll() = flow { emit(history.toList()) }
    override suspend fun findByCompositeKey(medId: Int, scheduleId: Int, scheduledTime: Long): Int? = history.find {
        it.medicationId == medId && it.scheduleId == scheduleId && it.scheduledTime == scheduledTime
    }?.id

    override suspend fun getAllByCompositeKey(
        medId: Int,
        scheduleId: Int,
        scheduledTime: Long,
    ): List<MedicationHistory> = history.filter {
        it.medicationId == medId && it.scheduleId == scheduleId && it.scheduledTime == scheduledTime
    }

    override suspend fun updateStatusAndDose(id: Int, status: String, dose: Double, takenTime: Long) {
        val idx = history.indexOfFirst { it.id == id }
        if (idx >= 0) {
            history[idx] = history[idx].copy(status = status, actualDose = dose, takenTime = takenTime)
        }
    }

    override suspend fun deleteById(id: Int) {
        history.removeAll { it.id == id }
    }

    override suspend fun deleteByCompositeKey(medId: Int, scheduleId: Int, scheduledTime: Long) {
        history.removeAll {
            it.medicationId == medId && it.scheduleId == scheduleId && it.scheduledTime == scheduledTime
        }
    }

    override suspend fun deleteByMedicationId(medicationId: Int) {
        history.removeAll { it.medicationId == medicationId }
    }

    override suspend fun countTakenSince(medicationId: Int, sinceTimestamp: Long) = history.count {
        it.medicationId == medicationId && it.status == "TAKEN" &&
            (it.takenTime ?: it.scheduledTime ?: 0) >= sinceTimestamp
    }

    override suspend fun deleteOlderThan(cutoffTimestamp: Long): Int {
        val before = history.size
        history.removeAll { (it.scheduledTime ?: it.takenTime ?: it.createdAt) < cutoffTimestamp }
        return before - history.size
    }
}

class FakeStockAdjustmentDao : StockAdjustmentDao {
    val adjustments = mutableListOf<StockAdjustment>()
    private var nextId = 1

    override suspend fun insert(adjustment: StockAdjustment): Long {
        val id = nextId++
        adjustments.add(adjustment.copy(id = id))
        return id.toLong()
    }

    override suspend fun update(adjustment: StockAdjustment) {
        val idx = adjustments.indexOfFirst { it.id == adjustment.id }
        if (idx >= 0) adjustments[idx] = adjustment
    }

    override fun getByMedicationId(medicationId: Int) = flow {
        emit(adjustments.filter { it.medicationId == medicationId }.sortedByDescending { it.timestamp })
    }

    override fun getByMedicationIdAsc(medicationId: Int) = flow {
        emit(adjustments.filter { it.medicationId == medicationId }.sortedBy { it.timestamp })
    }

    override suspend fun getCurrentStock(medicationId: Int) =
        adjustments.filter { it.medicationId == medicationId }.sumOf { it.quantityChange }

    override fun getInTimeRange(medicationId: Int, startTime: Long, endTime: Long) = flow {
        emit(
            adjustments.filter {
                it.medicationId == medicationId && it.timestamp in startTime until endTime
            },
        )
    }

    override suspend fun getByHistoryId(historyId: Int) = adjustments.find { it.historyId == historyId }

    override suspend fun deleteByHistoryId(historyId: Int) {
        adjustments.removeAll { it.historyId == historyId }
    }

    override suspend fun deleteByMedicationId(medicationId: Int) {
        adjustments.removeAll { it.medicationId == medicationId }
    }

    override suspend fun getById(id: Int) = adjustments.find { it.id == id }
    override suspend fun deleteById(id: Int) {
        adjustments.removeAll { it.id == id }
    }

    override suspend fun getLatestByTypeForMedication(medicationId: Int, type: String) =
        adjustments.filter { it.medicationId == medicationId && it.adjustmentType == type }
            .maxByOrNull { it.timestamp }
}

class NoOpReconciler : ScheduleReconciler {
    var reconcileCount = 0
    override suspend fun reconcile() {
        reconcileCount++
    }
}

class NoOpLowStockChecker : LowStockChecker {
    override suspend fun checkAndNotify(medicationId: Int) {}
}

class NoOpDepletionChecker : DepletionChecker {
    override suspend fun checkAndNotify(medicationId: Int) {}
}

class FakeNotificationSessionDao : NotificationSessionDao() {
    override suspend fun insert(session: NotificationSessionEntity) {}
    override suspend fun insertAll(sessions: List<NotificationSessionEntity>) {}
    override suspend fun getByKey(key: String) = null
    override suspend fun getAll() = emptyList<NotificationSessionEntity>()
    override suspend fun deleteByKey(key: String) {}
    override suspend fun getByParent(parentKey: String) = emptyList<NotificationSessionEntity>()
    override suspend fun updateFollowUpFired(
        timeSlotKey: String,
        currentTimeMs: Long,
        criticalChannelId: String,
        alarmChannelId: String,
        criticalEscalatedNotificationId: Int,
        alarmEscalatedNotificationId: Int,
    ) {}
    override suspend fun snoozeSessionAndSiblings(targetKey: String, snoozeUntil: Long) {}
    override suspend fun getDueFollowUps(now: Long) = emptyList<NotificationSessionEntity>()
    override suspend fun getDueSnoozes(now: Long) = emptyList<NotificationSessionEntity>()
    override suspend fun clearSnooze(key: String) {}
    override suspend fun deleteStale(cutoff: Long) {}
    override suspend fun countStale(cutoff: Long) = 0
}

/**
 * Test [TransactionRunner] that just executes the block, with no atomicity and no
 * thread confinement. Works because the [Fake*Dao] implementations are all
 * pure in-memory operations and tests run on a single coroutine via runTest.
 */
class FakeTransactionRunner : me.juliana.hellomeds.data.util.TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = block()
}

/**
 * Transaction runner with real rollback for the fake DAOs: snapshots their contents and
 * restores them if the block throws, so tests can observe all-or-nothing chunk commits.
 */
class SnapshotTransactionRunner(
    private val historyDao: FakeHistoryDao,
    private val stockAdjustmentDao: FakeStockAdjustmentDao? = null,
) : me.juliana.hellomeds.data.util.TransactionRunner {
    private var depth = 0

    override suspend fun <R> run(block: suspend () -> R): R {
        if (depth > 0) return block() // Nested: the outermost transaction owns rollback.
        val history = historyDao.history.toList()
        val adjustments = stockAdjustmentDao?.adjustments?.toList()
        depth++
        try {
            return block()
        } catch (e: Throwable) {
            historyDao.history.clear()
            historyDao.history.addAll(history)
            if (adjustments != null) {
                stockAdjustmentDao.adjustments.clear()
                stockAdjustmentDao.adjustments.addAll(adjustments)
            }
            throw e
        } finally {
            depth--
        }
    }
}

/** In-memory [me.juliana.hellomeds.data.preferences.ResolverStateStore]. */
class FakeResolverState : me.juliana.hellomeds.data.preferences.ResolverStateStore {
    var watermark: Long? = null
    var timeZoneId: String? = null

    override suspend fun getWatermark() = watermark
    override suspend fun setWatermark(value: Long) {
        watermark = value
    }

    override suspend fun getLastSeenTimeZoneId() = timeZoneId
    override suspend fun setLastSeenTimeZoneId(id: String) {
        timeZoneId = id
    }
}

/** Counts reconcile() calls. */
class CountingReconciler : ScheduleReconciler {
    var count = 0
    override suspend fun reconcile() {
        count++
    }
}

class FakeAppDatabase(
    private val fakeLabelDao: FakeImportanceLabelDao = FakeImportanceLabelDao(),
    private val fakeMedDao: FakeMedicationDao = FakeMedicationDao(),
    private val fakeScheduleDao: FakeScheduleDao = FakeScheduleDao(),
    private val fakeHistoryDao: FakeHistoryDao = FakeHistoryDao(),
    private val fakeStockAdjDao: FakeStockAdjustmentDao = FakeStockAdjustmentDao(),
) : AppDatabase() {
    override fun importanceLabelDao(): ImportanceLabelDao = fakeLabelDao
    override fun medicationDao(): MedicationDao = fakeMedDao
    override fun scheduleDao(): ScheduleDao = fakeScheduleDao
    override fun medicationHistoryDao(): MedicationHistoryDao = fakeHistoryDao
    override fun stockAdjustmentDao(): StockAdjustmentDao = fakeStockAdjDao
    override fun notificationSessionDao(): NotificationSessionDao = FakeNotificationSessionDao()
    override fun createInvalidationTracker(): InvalidationTracker = InvalidationTracker(this, emptyMap(), emptyMap())
}
