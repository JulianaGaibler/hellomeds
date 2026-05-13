// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import me.juliana.hellomeds.data.util.currentTimeMillis

@Entity(
    tableName = "stock_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MedicationHistory::class,
            parentColumns = ["id"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("historyId"),
        Index(value = ["medicationId", "timestamp"]),
    ],
)
data class StockAdjustment(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicationId: Int,
    val historyId: Int? = null,
    val quantityChange: Double,
    val timestamp: Long = currentTimeMillis(),
    val adjustmentType: String,
    val notes: String? = null,
)
