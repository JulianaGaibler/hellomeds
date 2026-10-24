// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.repository

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.interfaces.StockContainerAnchor
import me.juliana.hellomeds.data.service.UnloggedDoseResolver
import me.juliana.hellomeds.data.util.TimeProvider
import me.juliana.hellomeds.data.util.startOfDayMillis

/**
 * Writes that coordinate with [UnloggedDoseResolver] run under [NonCancellable]: they may
 * wait on the resolver's lock during a long backfill, and a caller leaving the screen
 * (viewModelScope cancelled) must not drop the user's edit.
 */
class ScheduleRepository(
    private val scheduleDao: ScheduleDao,
    private val reconciler: ScheduleReconciler,
    private val stockAnchor: StockContainerAnchor,
    private val resolver: UnloggedDoseResolver,
    private val timeProvider: TimeProvider,
) {

    companion object {
        private const val TAG = "ScheduleRepository"
    }

    fun getAll(): Flow<List<Schedule>> = scheduleDao.getAll()

    fun getActive(): Flow<List<Schedule>> = scheduleDao.getActive()

    fun getById(id: Int): Flow<Schedule?> = scheduleDao.getById(id)

    fun getByMedicationId(medicationId: Int): Flow<List<Schedule>> = scheduleDao.getByMedicationId(medicationId)

    suspend fun insert(schedule: Schedule): Long = withContext(NonCancellable) {
        // From the start of the creation day, so an earlier dose today still resolves.
        val resolveFrom = startOfDayMillis(timeProvider.nowMillis())
        val snapshot = stockAnchor.snapshotBeforeRateChange(schedule.medicationId)
        val id = scheduleDao.insert(schedule.copy(resolveFrom = resolveFrom))
        resolver.onScheduleCreated(resolveFrom)
        stockAnchor.reanchorAfterRateChange(schedule.medicationId, snapshot)
        reconciler.reconcile()
        id
    }

    suspend fun update(schedule: Schedule) = withContext(NonCancellable) {
        val existing = scheduleDao.getById(schedule.id).first()
        // The edit sheet rebuilds the whole entity, so keep bookkeeping fields from the stored row.
        var toSave = if (existing != null) {
            schedule.copy(createdAt = existing.createdAt, resolveFrom = existing.resolveFrom)
        } else {
            schedule
        }
        if (existing != null && schedule.timingDiffersFrom(existing)) {
            // Resolve under the OLD definition (still in the DB) before it is replaced.
            toSave = toSave.copy(resolveFrom = resolver.redefine(listOf(schedule.id)))
        }
        val snapshot = stockAnchor.snapshotBeforeRateChange(schedule.medicationId)
        scheduleDao.update(toSave)
        stockAnchor.reanchorAfterRateChange(schedule.medicationId, snapshot)
        reconciler.reconcile()
    }

    suspend fun delete(schedule: Schedule) {
        val snapshot = stockAnchor.snapshotBeforeRateChange(schedule.medicationId)
        scheduleDao.delete(schedule)
        stockAnchor.reanchorAfterRateChange(schedule.medicationId, snapshot)
        reconciler.reconcile()
    }

    suspend fun archive(scheduleId: Int) {
        val medicationId = scheduleDao.getById(scheduleId).first()?.medicationId
        val snapshot = if (medicationId != null) stockAnchor.snapshotBeforeRateChange(medicationId) else null
        scheduleDao.archive(scheduleId)
        if (medicationId != null) stockAnchor.reanchorAfterRateChange(medicationId, snapshot)
        reconciler.reconcile()
    }

    suspend fun unarchive(scheduleId: Int) = withContext(NonCancellable) {
        val medicationId = scheduleDao.getById(scheduleId).first()?.medicationId
        val snapshot = if (medicationId != null) stockAnchor.snapshotBeforeRateChange(medicationId) else null
        scheduleDao.unarchive(scheduleId)
        // The archived span was never reminded about, so it must not resolve as unlogged.
        resolver.startFresh(listOf(scheduleId))
        if (medicationId != null) stockAnchor.reanchorAfterRateChange(medicationId, snapshot)
        reconciler.reconcile()
    }
}
