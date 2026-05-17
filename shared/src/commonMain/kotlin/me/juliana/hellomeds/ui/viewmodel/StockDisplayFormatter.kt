// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import me.juliana.hellomeds.data.database.entities.Medication

/**
 * Platform-specific stock display formatting.
 *
 * Lets StockTrackingViewModel live in commonMain while each platform plugs in localized resources
 * with proper pluralization (Android `R.string` / `R.plurals`, iOS `NSLocalizedString`).
 */
interface StockDisplayFormatter {

    /**
     * EXACT mode: "10 bottles + 7 tablets" (with packaging) or "207 tablets" (without).
     * ESTIMATED mode: "3 dispensers" or "1 bottle".
     */
    fun formatStockQuantity(medication: Medication, currentStock: Double): String

    fun shouldShowLowStockWarning(medication: Medication, currentStock: Double): Boolean

    /** One of: "CRITICAL", "LOW", "MEDIUM", "GOOD". */
    fun getStockSeverity(medication: Medication, currentStock: Double): String
}
