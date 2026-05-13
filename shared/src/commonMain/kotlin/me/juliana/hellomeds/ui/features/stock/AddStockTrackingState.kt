// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock

import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.TrackingPrecision

/**
 * State holder for the Add Stock Tracking flow.
 * Immutable state object that holds all configuration for both EXACT and ESTIMATED modes.
 */
data class AddStockTrackingState(
    // Common fields
    val trackingPrecision: TrackingPrecision? = null,

    // EXACT mode fields (formerly "discrete")
    val packagingQuantity: String = "",
    val container: MedicationContainer? = MedicationContainer.entries.firstOrNull(), // Pre-select first container
    val fullContainers: String = "0", // Pre-fill with 0
    val partialUnits: String = "0", // Pre-fill with 0
    val lowStockEnabled: Boolean = false,
    val lowStockThreshold: String = "10", // Pre-fill with 10

    // ESTIMATED mode fields
    val estimatedContainer: MedicationContainer? = null,
    val estimatedContainerCount: Int = 1,
    val estimatedDosesPerContainer: Double? = null,
    val estimatedLowStockThreshold: Int? = null,
    val depletionReminderEnabled: Boolean = false,
    val estimatedContainerStartedAt: Long? = null, // When current container was started (millis, null = today/full)
)
