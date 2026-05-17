// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.Medication

/**
 * Effective stock for low-stock threshold comparisons. Both EXACT and ESTIMATED modes use the
 * ledger sum — EXACT = remaining doses, ESTIMATED = remaining containers.
 */
object StockThresholdCalculator {
    suspend fun calculateEffectiveStock(medication: Medication, stockAdjustmentDao: StockAdjustmentDao): Double {
        return stockAdjustmentDao.getCurrentStock(medication.id) ?: 0.0
    }

    /** One of: "CRITICAL", "LOW", "MEDIUM", "GOOD". */
    fun getStockSeverity(medication: Medication, currentStock: Double): String {
        val threshold = medication.lowStockThreshold ?: return "GOOD"

        return when {
            currentStock <= 0 -> "CRITICAL"
            currentStock <= threshold * 0.5 -> "CRITICAL"
            currentStock <= threshold -> "LOW"
            currentStock <= threshold * 1.5 -> "MEDIUM"
            else -> "GOOD"
        }
    }
}
