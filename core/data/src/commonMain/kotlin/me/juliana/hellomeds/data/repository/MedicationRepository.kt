// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.repository

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.model.enums.BubbleFlowDirection
import me.juliana.hellomeds.data.service.UnloggedDoseResolver
import me.juliana.hellomeds.data.util.TransactionRunner

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val transactionRunner: TransactionRunner,
    private val reconciler: ScheduleReconciler,
    private val resolver: UnloggedDoseResolver,
) {

    fun getAll(): Flow<List<Medication>> = medicationDao.getAll()

    fun getActive(): Flow<List<Medication>> = medicationDao.getActive()

    fun getArchived(): Flow<List<Medication>> = medicationDao.getArchived()

    fun getById(id: Int): Flow<Medication?> = medicationDao.getById(id)

    suspend fun insert(medication: Medication): Long {
        val nextOrder = medicationDao.getMaxActiveDisplayOrder() + 1
        return medicationDao.insert(medication.copy(displayOrder = nextOrder))
    }

    suspend fun update(medication: Medication) = withContext(NonCancellable) {
        val existing = medicationDao.getByIdSync(medication.id)
        if (existing != null && medication.projectionDiffersFrom(existing)) {
            // Timezone and cycle settings re-project every past event of this medication.
            // Resolve under the old settings first, then start resolving afresh.
            val scheduleIds = scheduleDao.getByMedicationId(medication.id).first().map { it.id }
            resolver.redefine(scheduleIds)
        }
        medicationDao.update(medication)
        // Refresh alarms to pick up any changes (importance label, name, etc.)
        reconciler.reconcile()
    }

    suspend fun delete(medication: Medication) {
        medicationDao.delete(medication)
        compactDisplayOrder()
        // Cascades to schedules via FK, so reconcile to cancel stale alarms.
        reconciler.reconcile()
    }

    /**
     * Archives a medication and all its active schedules
     * Uses a transaction to ensure atomic operation
     */
    suspend fun archive(medicationId: Int) {
        transactionRunner.run {
            medicationDao.archive(medicationId)
            val schedules = scheduleDao.getByMedicationId(medicationId).first()
            schedules.forEach { schedule ->
                if (!schedule.isArchived) {
                    scheduleDao.archive(schedule.id)
                }
            }
        }
        compactDisplayOrder()
        reconciler.reconcile()
    }

    /**
     * Unarchives a medication and all its archived schedules
     * Uses a transaction to ensure atomic operation
     */
    suspend fun unarchive(medicationId: Int) = withContext(NonCancellable) {
        // The archived span was never reminded about, so it must not resolve as unlogged.
        resolver.startFreshAfter {
            transactionRunner.run {
                medicationDao.unarchive(medicationId)
                val schedules = scheduleDao.getByMedicationId(medicationId).first()
                schedules.filter { it.isArchived }.onEach { scheduleDao.unarchive(it.id) }.map { it.id }
            }
        }
        // Refresh alarms to reschedule alarms for unarchived schedules
        reconciler.reconcile()
    }

    /**
     * Gets medication with all its schedules
     */
    suspend fun getMedicationWithSchedules(medicationId: Int): MedicationWithSchedules? {
        val medication = medicationDao.getById(medicationId).first() ?: return null
        val schedules = scheduleDao.getByMedicationId(medicationId).first()
        return MedicationWithSchedules(medication, schedules)
    }

    /**
     * Compacts displayOrder values to be sequential after archive/delete operations.
     */
    private suspend fun compactDisplayOrder() {
        val active = medicationDao.getActive().first()
        active.forEachIndexed { index, medication ->
            if (medication.displayOrder != index) {
                medicationDao.updateDisplayOrder(medication.id, index)
            }
        }
    }

    suspend fun reorderMedications(medications: List<Medication>) {
        transactionRunner.run {
            // Update displayOrder for each medication based on its position in the list
            medications.forEachIndexed { index, medication ->
                medicationDao.updateDisplayOrder(medication.id, index)
            }
        }
    }

    suspend fun updateBubbleLayout(medicationId: Int, manualLayout: String?, flow: BubbleFlowDirection) {
        medicationDao.updateBubbleLayout(medicationId, manualLayout, flow)
    }
}
