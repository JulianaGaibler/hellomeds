// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model

import kotlinx.datetime.LocalDate

/**
 * Unified stock status for UI display.
 */
data class StockStatus(
    val totalQuantity: Double,
    val sealedContainerCount: Int,
    val currentContainerRemaining: Double? = null,
    val daysRemaining: Int?,
    val severity: String,
    val runOutDate: LocalDate? = null,
    val isEstimated: Boolean = false,
    val earlyRunOutDate: LocalDate? = null,
    val lateRunOutDate: LocalDate? = null,
    val rationale: StockRationale? = null,
)

/**
 * Structured rationale data for stock prediction display.
 * Formatted in the UI layer using string resources for localization.
 */
data class StockRationale(
    val totalDoses: Int,
    val dosesPerContainer: Int?,
    val dailyRate: Double,
    val isEstimated: Boolean,
)
