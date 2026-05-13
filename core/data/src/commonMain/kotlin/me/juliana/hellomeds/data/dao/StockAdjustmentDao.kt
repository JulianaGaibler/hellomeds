// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.juliana.hellomeds.data.database.entities.StockAdjustment

@Dao
interface StockAdjustmentDao {

    @Insert
    suspend fun insert(adjustment: StockAdjustment): Long

    @Update
    suspend fun update(adjustment: StockAdjustment)

    @Query("SELECT * FROM stock_adjustments WHERE medicationId = :medicationId ORDER BY timestamp DESC")
    fun getByMedicationId(medicationId: Int): Flow<List<StockAdjustment>>

    @Query("SELECT * FROM stock_adjustments WHERE medicationId = :medicationId ORDER BY timestamp ASC")
    fun getByMedicationIdAsc(medicationId: Int): Flow<List<StockAdjustment>>

    @Query("SELECT SUM(quantityChange) FROM stock_adjustments WHERE medicationId = :medicationId")
    suspend fun getCurrentStock(medicationId: Int): Double?

    @Query(
        """
    SELECT * FROM stock_adjustments
    WHERE medicationId = :medicationId
      AND timestamp >= :startTime
      AND timestamp < :endTime
    ORDER BY timestamp ASC
  """,
    )
    fun getInTimeRange(medicationId: Int, startTime: Long, endTime: Long): Flow<List<StockAdjustment>>

    @Query("SELECT * FROM stock_adjustments WHERE historyId = :historyId LIMIT 1")
    suspend fun getByHistoryId(historyId: Int): StockAdjustment?

    @Query("DELETE FROM stock_adjustments WHERE historyId = :historyId")
    suspend fun deleteByHistoryId(historyId: Int)

    @Query("DELETE FROM stock_adjustments WHERE medicationId = :medicationId")
    suspend fun deleteByMedicationId(medicationId: Int)

    @Query("SELECT * FROM stock_adjustments WHERE id = :id")
    suspend fun getById(id: Int): StockAdjustment?

    @Query("DELETE FROM stock_adjustments WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query(
        """
    SELECT * FROM stock_adjustments
    WHERE medicationId = :medicationId
      AND adjustmentType = :type
    ORDER BY timestamp DESC
    LIMIT 1
  """,
    )
    suspend fun getLatestByTypeForMedication(medicationId: Int, type: String): StockAdjustment?
}
