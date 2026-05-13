// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import me.juliana.hellomeds.data.database.entities.Medication

/**
 * Abstracts platform-specific stock display formatting.
 *
 * On Android, this wraps Context + string resources (R.string, R.plurals)
 * for localized stock quantity display with proper pluralization.
 * On iOS, this would use NSLocalizedString or similar.
 *
 * This interface allows StockTrackingViewModel to live in commonMain
 * while keeping full-fidelity localized formatting on each platform.
 */
interface StockDisplayFormatter {

    /**
     * Formats a stock quantity for display based on tracking precision.
     *
     * For EXACT mode:
     * - With packaging: "10 bottles + 7 tablets"
     * - Without packaging: "207 tablets"
     *
     * For ESTIMATED mode:
     * - "3 dispensers" or "1 bottle"
     *
     * @param medication The medication (provides tracking precision, container type, etc.)
     * @param currentStock The current stock quantity
     * @return Localized, formatted stock string
     */
    fun formatStockQuantity(medication: Medication, currentStock: Double): String

    /**
     * Determines whether a low stock warning should be shown.
     *
     * @param medication The medication (provides lowStockThreshold)
     * @param currentStock The current stock quantity
     * @return true if currentStock <= lowStockThreshold
     */
    fun shouldShowLowStockWarning(medication: Medication, currentStock: Double): Boolean

    /**
     * Returns a severity string for visual indicators.
     * One of: "CRITICAL", "LOW", "MEDIUM", "GOOD"
     *
     * @param medication The medication (provides threshold data)
     * @param currentStock The current stock quantity
     * @return Severity string
     */
    fun getStockSeverity(medication: Medication, currentStock: Double): String
}
