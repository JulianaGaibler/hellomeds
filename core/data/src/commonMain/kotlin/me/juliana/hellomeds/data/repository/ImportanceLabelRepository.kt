// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.database.DefaultLabelType
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.util.AppLogger

/**
 * Repository for ImportanceLabel operations.
 *
 * NEW ARCHITECTURE: Includes change detection for follow-up settings.
 * When follow-up configuration changes, triggers alarm reconciliation to:
 * - Schedule new follow-ups if count increased
 * - Cancel excess follow-ups if count decreased (prevents zombie alarms)
 * - Update channel IDs if criticality changed
 */
class ImportanceLabelRepository(
    private val dao: ImportanceLabelDao,
    private val medicationDao: MedicationDao,
    private val reconciler: ScheduleReconciler,
) {

    companion object {
        private const val TAG = "ImportanceLabelRepo"
    }

    val allLabels: Flow<List<ImportanceLabel>> = dao.getAll()

    fun getById(id: Int): Flow<ImportanceLabel?> = dao.getById(id)

    suspend fun insert(label: ImportanceLabel): Long = dao.insert(label)

    /**
     * Updates an importance label with change detection.
     * Triggers alarm reconciliation ONLY if follow-up settings changed.
     *
     * This prevents unnecessary alarm updates when only the label name changes,
     * but ensures alarms are updated when follow-up counts, intervals, or
     * critical thresholds change.
     */
    suspend fun update(label: ImportanceLabel) {
        // Get old label before update for comparison
        val oldLabel = dao.getById(label.id).first()

        // Update the label in database
        dao.update(label)

        // Check if follow-up settings changed
        if (oldLabel != null) {
            val followUpChanged = oldLabel.followUpCount != label.followUpCount ||
                oldLabel.followUpIntervalMinutes != label.followUpIntervalMinutes ||
                oldLabel.criticalAfterFollowUp != label.criticalAfterFollowUp ||
                oldLabel.alarmAfterFollowUp != label.alarmAfterFollowUp ||
                oldLabel.isCritical != label.isCritical ||
                oldLabel.isAlarm != label.isAlarm

            if (followUpChanged) {
                AppLogger.i(TAG, "ImportanceLabel ${label.id} follow-up settings changed:")
                AppLogger.i(TAG, "  - Follow-up count: ${oldLabel.followUpCount} → ${label.followUpCount}")
                AppLogger.i(
                    TAG,
                    "  - Interval: ${oldLabel.followUpIntervalMinutes} → ${label.followUpIntervalMinutes}",
                )
                AppLogger.i(
                    TAG,
                    "  - Critical after: ${oldLabel.criticalAfterFollowUp} → ${label.criticalAfterFollowUp}",
                )
                AppLogger.i(TAG, "  - Is critical: ${oldLabel.isCritical} → ${label.isCritical}")
                AppLogger.i(TAG, "  → Triggering alarm reconciliation...")

                reconciler.reconcile()
            } else {
                AppLogger.d(
                    TAG,
                    "ImportanceLabel ${label.id} updated, but follow-up settings unchanged. No reconciliation needed.",
                )
            }
        } else {
            // Old label not found - reconcile to be safe
            AppLogger.d(
                TAG,
                "Old ImportanceLabel ${label.id} not found for comparison. Triggering reconciliation to be safe.",
            )
            reconciler.reconcile()
        }
    }

    suspend fun delete(label: ImportanceLabel) {
        if (label.isDefault) {
            AppLogger.w(TAG, "Attempted to delete default label '${label.name}' (${label.defaultType}), refusing")
            return
        }
        dao.delete(label)
        // Cascades to medications → schedules via FK — reconcile to cancel stale alarms
        reconciler.reconcile()
    }

    // Validation and deletion with archived medication reassignment
    suspend fun canDeleteLabel(labelId: Int): Boolean {
        return medicationDao.getActiveMedicationsByImportanceLabelId(labelId).first().isEmpty()
    }

    fun getActiveMedicationsUsingLabel(labelId: Int): Flow<List<Medication>> {
        return medicationDao.getActiveMedicationsByImportanceLabelId(labelId)
    }

    suspend fun deleteWithArchivedReassignment(label: ImportanceLabel, defaultLabelId: Int) {
        if (label.isDefault) {
            AppLogger.w(TAG, "Attempted to delete default label '${label.name}' (${label.defaultType}), refusing")
            return
        }
        // Reassign archived medications to default label
        medicationDao.reassignArchivedMedications(label.id, defaultLabelId)
        // Then delete the label (cascades to remaining medications → schedules)
        dao.delete(label)
        reconciler.reconcile()
    }

    suspend fun resetToDefault(label: ImportanceLabel) {
        val type = label.defaultType ?: return
        val defaultDef = DefaultLabelType.entries.find { it.defaultType == type } ?: return
        val reset = defaultDef.toLabel(existingId = label.id)
        update(reset)
    }
}
