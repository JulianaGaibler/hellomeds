// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.util.TransactionRunner

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val transactionRunner: TransactionRunner,
    private val reconciler: ScheduleReconciler,
) {

    fun getAll(): Flow<List<Medication>> = medicationDao.getAll()

    fun getActive(): Flow<List<Medication>> = medicationDao.getActive()

    fun getArchived(): Flow<List<Medication>> = medicationDao.getArchived()

    fun getById(id: Int): Flow<Medication?> = medicationDao.getById(id)

    suspend fun insert(medication: Medication): Long {
        val nextOrder = medicationDao.getMaxActiveDisplayOrder() + 1
        return medicationDao.insert(medication.copy(displayOrder = nextOrder))
    }

    suspend fun update(medication: Medication) {
        medicationDao.update(medication)
        // Refresh alarms to pick up any changes (importance label, name, etc.)
        reconciler.reconcile()
    }

    suspend fun delete(medication: Medication) {
        medicationDao.delete(medication)
        compactDisplayOrder()
        // Cascades to schedules via FK — reconcile to cancel stale alarms
        reconciler.reconcile()
    }

    /**
     * Archives a medication and all its active schedules
     * Uses a transaction to ensure atomic operation
     */
    suspend fun archive(medicationId: Int) {
        transactionRunner.run {
            // Archive the medication
            medicationDao.archive(medicationId)

            // Get all schedules for this medication and archive them
            val schedules = scheduleDao.getByMedicationId(medicationId).first()
            schedules.forEach { schedule ->
                if (!schedule.isArchived) {
                    scheduleDao.archive(schedule.id)
                }
            }
        }
        compactDisplayOrder()
        // Refresh alarms to cancel alarms for archived schedules
        reconciler.reconcile()
    }

    /**
     * Unarchives a medication and all its archived schedules
     * Uses a transaction to ensure atomic operation
     */
    suspend fun unarchive(medicationId: Int) {
        transactionRunner.run {
            // Unarchive the medication
            medicationDao.unarchive(medicationId)

            // Get all schedules for this medication and unarchive them
            val schedules = scheduleDao.getByMedicationId(medicationId).first()
            schedules.forEach { schedule ->
                if (schedule.isArchived) {
                    scheduleDao.unarchive(schedule.id)
                }
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
}
