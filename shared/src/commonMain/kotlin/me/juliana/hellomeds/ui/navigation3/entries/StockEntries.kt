// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3.entries

import androidx.compose.runtime.Composable
import me.juliana.hellomeds.ui.features.stock.StockScreen

/**
 * Entry point for the Stock screen.
 * Shows stock tracking dashboard with inventory and graph.
 */
@Composable
fun StockScreenEntry(
    onNavigateToSettings: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onNavigateToStockDetail: (medicationId: Int) -> Unit,
) {
    StockScreen(
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToSupport = onNavigateToSupport,
        onNavigateToStockDetail = onNavigateToStockDetail,
    )
}
