// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.juliana.hellomeds.data.database.entities.MedicationHistory

@Dao
interface MedicationHistoryDao {
    /**
     * Insert a history row. Returns the new rowId, or -1 if the row was ignored due to
     * the unique (medicationId, scheduleId, scheduledTime) index. Callers that already
     * checked `findByCompositeKey` inside a transaction will never observe -1; the
     * IGNORE strategy is structural defense-in-depth for paths like `autoSkipMissedEvents`
     * that insert without a prior lookup.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: MedicationHistory): Long

    @Update
    suspend fun update(history: MedicationHistory)

    @Query("SELECT * FROM medication_history WHERE id = :id")
    fun getById(id: Int): Flow<MedicationHistory?>

    @Query("SELECT * FROM medication_history WHERE id = :id")
    suspend fun getByIdSync(id: Int): MedicationHistory?

    @Query(
        "SELECT * FROM medication_history WHERE medicationId = :medicationId ORDER BY COALESCE(scheduledTime, takenTime) DESC",
    )
    fun getByMedicationId(medicationId: Int): Flow<List<MedicationHistory>>

    @Query("SELECT * FROM medication_history WHERE scheduleId = :scheduleId ORDER BY scheduledTime DESC")
    fun getByScheduleId(scheduleId: Int): Flow<List<MedicationHistory>>

    /**
     * Get history records in a time range (for tracking screen + completion status).
     * Matches on scheduledTime for scheduled doses, or takenTime for as-needed doses.
     */
    @Query(
        """
    SELECT * FROM medication_history
    WHERE (scheduledTime >= :startTime AND scheduledTime < :endTime)
       OR (scheduledTime IS NULL AND takenTime >= :startTime AND takenTime < :endTime)
    ORDER BY COALESCE(scheduledTime, takenTime) ASC
    """,
    )
    fun getInTimeRange(startTime: Long, endTime: Long): Flow<List<MedicationHistory>>

    /**
     * Suspend version for one-shot queries (e.g. ScheduleProjector).
     */
    @Query(
        """
    SELECT * FROM medication_history
    WHERE (scheduledTime >= :startTime AND scheduledTime < :endTime)
       OR (scheduledTime IS NULL AND takenTime >= :startTime AND takenTime < :endTime)
    ORDER BY COALESCE(scheduledTime, takenTime) ASC
    """,
    )
    suspend fun getInTimeRangeSuspend(startTime: Long, endTime: Long): List<MedicationHistory>

    /**
     * Find history record matching a specific schedule + time slot.
     * Used by ScheduleProjector to determine if a projected event has been acted upon.
     */
    @Query(
        """
    SELECT * FROM medication_history
    WHERE scheduleId = :scheduleId
      AND scheduledTime = :scheduledTime
    LIMIT 1
    """,
    )
    suspend fun getByScheduleAndTime(scheduleId: Int, scheduledTime: Long): MedicationHistory?

    /**
     * Get all history for reactive flows (e.g. completion status calculator).
     */
    @Query("SELECT * FROM medication_history ORDER BY COALESCE(scheduledTime, takenTime) DESC")
    fun getAll(): Flow<List<MedicationHistory>>

    /**
     * Find existing history record by composite key (for duplicate prevention).
     */
    @Query(
        """
    SELECT id FROM medication_history
    WHERE medicationId = :medId AND scheduleId = :scheduleId AND scheduledTime = :scheduledTime
    LIMIT 1
    """,
    )
    suspend fun findByCompositeKey(medId: Int, scheduleId: Int, scheduledTime: Long): Int?

    /**
     * Get all history records matching a composite key (for duplicate cleanup).
     */
    @Query(
        """
    SELECT * FROM medication_history
    WHERE medicationId = :medId AND scheduleId = :scheduleId AND scheduledTime = :scheduledTime
    """,
    )
    suspend fun getAllByCompositeKey(medId: Int, scheduleId: Int, scheduledTime: Long): List<MedicationHistory>

    /**
     * Update status, dose, and taken time on an existing record (for upsert-style dedup).
     */
    @Query("UPDATE medication_history SET status = :status, actualDose = :dose, takenTime = :takenTime WHERE id = :id")
    suspend fun updateStatusAndDose(id: Int, status: String, dose: Double, takenTime: Long)

    /**
     * Delete a history record (for undo/revert operations).
     */
    @Query("DELETE FROM medication_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * Delete all history records matching a composite key (removes duplicates).
     */
    @Query(
        """
    DELETE FROM medication_history
    WHERE medicationId = :medId AND scheduleId = :scheduleId AND scheduledTime = :scheduledTime
    """,
    )
    suspend fun deleteByCompositeKey(medId: Int, scheduleId: Int, scheduledTime: Long)

    /**
     * Count TAKEN history records for a medication since a given timestamp.
     * Used for depletion reminder calculation.
     */
    @Query(
        """
    SELECT COUNT(*) FROM medication_history
    WHERE medicationId = :medicationId
      AND status = 'TAKEN'
      AND COALESCE(takenTime, scheduledTime) >= :sinceTimestamp
  """,
    )
    suspend fun countTakenSince(medicationId: Int, sinceTimestamp: Long): Int

    /**
     * Delete all history records for a medication (used by backup REPLACE import).
     */
    @Query("DELETE FROM medication_history WHERE medicationId = :medicationId")
    suspend fun deleteByMedicationId(medicationId: Int)

    /**
     * Delete history records older than the given cutoff timestamp.
     * Used by periodic cleanup tasks to keep the database size manageable.
     * Returns the number of records deleted.
     */
    @Query(
        """
    DELETE FROM medication_history
    WHERE COALESCE(scheduledTime, takenTime, createdAt) < :cutoffTimestamp
  """,
    )
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int
}
