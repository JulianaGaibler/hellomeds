// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.interfaces.StockContainerAnchor

class ScheduleRepository(
    private val scheduleDao: ScheduleDao,
    private val reconciler: ScheduleReconciler,
    private val stockAnchor: StockContainerAnchor,
) {

    companion object {
        private const val TAG = "ScheduleRepository"
    }

    fun getAll(): Flow<List<Schedule>> = scheduleDao.getAll()

    fun getActive(): Flow<List<Schedule>> = scheduleDao.getActive()

    fun getById(id: Int): Flow<Schedule?> = scheduleDao.getById(id)

    fun getByMedicationId(medicationId: Int): Flow<List<Schedule>> = scheduleDao.getByMedicationId(medicationId)

    suspend fun insert(schedule: Schedule): Long {
        val snapshot = stockAnchor.snapshotBeforeRateChange(schedule.medicationId)
        val id = scheduleDao.insert(schedule)
        stockAnchor.reanchorAfterRateChange(schedule.medicationId, snapshot)
        reconciler.reconcile()
        return id
    }

    suspend fun update(schedule: Schedule) {
        val snapshot = stockAnchor.snapshotBeforeRateChange(schedule.medicationId)
        scheduleDao.update(schedule)
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

    suspend fun unarchive(scheduleId: Int) {
        val medicationId = scheduleDao.getById(scheduleId).first()?.medicationId
        val snapshot = if (medicationId != null) stockAnchor.snapshotBeforeRateChange(medicationId) else null
        scheduleDao.unarchive(scheduleId)
        if (medicationId != null) stockAnchor.reanchorAfterRateChange(medicationId, snapshot)
        reconciler.reconcile()
    }
}
