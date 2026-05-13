// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.util.currentTimeMillis

@Dao
interface ScheduleDao {
    @Insert
    suspend fun insert(schedule: Schedule): Long

    @Update
    suspend fun update(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)

    @Query("SELECT * FROM schedules")
    fun getAll(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE id = :id")
    fun getById(id: Int): Flow<Schedule?>

    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId")
    fun getByMedicationId(medicationId: Int): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE isArchived = 0 AND (endDate IS NULL OR endDate > :currentTime)")
    fun getActive(currentTime: Long = currentTimeMillis()): Flow<List<Schedule>>

    @Query("UPDATE schedules SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Int)

    @Query("UPDATE schedules SET isArchived = 0 WHERE id = :id")
    suspend fun unarchive(id: Int)
}
