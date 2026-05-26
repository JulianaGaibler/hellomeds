// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.NotificationSessionDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.entities.NotificationSessionEntity
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.database.entities.StockAdjustment

@Database(
    entities = [
        ImportanceLabel::class,
        Medication::class,
        Schedule::class,
        MedicationHistory::class,
        StockAdjustment::class,
        NotificationSessionEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun importanceLabelDao(): ImportanceLabelDao
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun medicationHistoryDao(): MedicationHistoryDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun notificationSessionDao(): NotificationSessionDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
