// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.juliana.hellomeds.data.database.entities.ImportanceLabel

@Dao
interface ImportanceLabelDao {
    @Insert
    suspend fun insert(label: ImportanceLabel): Long

    @Update
    suspend fun update(label: ImportanceLabel)

    @Delete
    suspend fun delete(label: ImportanceLabel)

    @Query("SELECT * FROM importance_labels ORDER BY id ASC")
    fun getAll(): Flow<List<ImportanceLabel>>

    @Query("SELECT * FROM importance_labels WHERE id = :id")
    fun getById(id: Int): Flow<ImportanceLabel?>

    // Suspend method for notification workers (Room KMP requires suspend for non-Flow)
    @Query("SELECT * FROM importance_labels WHERE id = :id")
    suspend fun getByIdSync(id: Int): ImportanceLabel?

    @Query("SELECT COUNT(*) FROM importance_labels")
    suspend fun getCount(): Int

    @Query("SELECT * FROM importance_labels WHERE defaultType = :type LIMIT 1")
    suspend fun getByDefaultType(type: String): ImportanceLabel?
}
