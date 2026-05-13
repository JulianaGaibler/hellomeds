// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.Medication

/**
 * Calculates the effective stock value for low stock threshold comparison.
 *
 * Both EXACT and ESTIMATED modes use the ledger sum.
 * EXACT: total remaining doses.
 * ESTIMATED: total remaining containers.
 */
object StockThresholdCalculator {
    suspend fun calculateEffectiveStock(medication: Medication, stockAdjustmentDao: StockAdjustmentDao): Double {
        return stockAdjustmentDao.getCurrentStock(medication.id) ?: 0.0
    }

    /**
     * Get stock level severity for visual indicators.
     * Returns one of: "CRITICAL", "LOW", "MEDIUM", "GOOD"
     *
     * This is a pure calculation with no UI dependencies, suitable for KMP commonMain.
     */
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
