// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.BubbleFlowDirection

@Dao
interface MedicationDao {
    @Insert
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)

    @Query("SELECT * FROM medications")
    fun getAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medications")
    suspend fun getAllSuspend(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    fun getById(id: Int): Flow<Medication?>

    @Query("SELECT * FROM medications WHERE isArchived = 0 ORDER BY displayOrder ASC")
    fun getActive(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE isArchived = 1 ORDER BY displayOrder ASC")
    fun getArchived(): Flow<List<Medication>>

    @Query("UPDATE medications SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Int)

    @Query("UPDATE medications SET isArchived = 0 WHERE id = :id")
    suspend fun unarchive(id: Int)

    @Query("UPDATE medications SET displayOrder = :displayOrder WHERE id = :id")
    suspend fun updateDisplayOrder(id: Int, displayOrder: Int)

    @Query("SELECT COALESCE(MAX(displayOrder), -1) FROM medications WHERE isArchived = 0")
    suspend fun getMaxActiveDisplayOrder(): Int

    @Update
    suspend fun updateAll(medications: List<Medication>)

    // Synchronous method for notification workers
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getByIdSync(id: Int): Medication?

    // Importance label usage queries
    @Query("SELECT * FROM medications WHERE importanceLabelId = :labelId AND isArchived = 0")
    fun getActiveMedicationsByImportanceLabelId(labelId: Int): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE importanceLabelId = :labelId AND isArchived = 1")
    suspend fun getArchivedMedicationsByImportanceLabelId(labelId: Int): List<Medication>

    @Query(
        "UPDATE medications SET importanceLabelId = :newLabelId WHERE importanceLabelId = :oldLabelId AND isArchived = 1",
    )
    suspend fun reassignArchivedMedications(oldLabelId: Int, newLabelId: Int)

    @Query("UPDATE medications SET currentStockQuantity = :newStock WHERE id = :medicationId")
    suspend fun updateCachedStockQuantity(medicationId: Int, newStock: Double)

    @Query("UPDATE medications SET lowStockAlertSent = :sent WHERE id = :medicationId")
    suspend fun updateLowStockAlertSent(medicationId: Int, sent: Boolean)

    @Query("UPDATE medications SET depletionAlertSent = :sent WHERE id = :medicationId")
    suspend fun updateDepletionAlertSent(medicationId: Int, sent: Boolean)

    @Query("SELECT * FROM medications WHERE stockTrackingEnabled = 1 AND isArchived = 0")
    fun getStockTracked(): Flow<List<Medication>>

    @Query(
        "UPDATE medications SET bubbleManualLayout = :manualLayout, bubbleFlowDirection = :flow WHERE id = :medicationId",
    )
    suspend fun updateBubbleLayout(medicationId: Int, manualLayout: String?, flow: BubbleFlowDirection)

    @Query("SELECT * FROM medications WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<Medication>

    @Query(
        """
    SELECT COUNT(*) > 0 FROM medications m
    INNER JOIN importance_labels l ON m.importanceLabelId = l.id
    WHERE m.isArchived = 0
    AND (l.isCritical = 1 OR l.criticalAfterFollowUp IS NOT NULL)
  """,
    )
    fun hasActiveMedicationsWithCriticalLabel(): Flow<Boolean>

    @Query(
        """
    SELECT COUNT(*) > 0 FROM medications m
    INNER JOIN importance_labels l ON m.importanceLabelId = l.id
    WHERE m.isArchived = 0
    AND (l.isAlarm = 1 OR l.alarmAfterFollowUp IS NOT NULL)
  """,
    )
    fun hasActiveMedicationsWithAlarmLabel(): Flow<Boolean>
}
