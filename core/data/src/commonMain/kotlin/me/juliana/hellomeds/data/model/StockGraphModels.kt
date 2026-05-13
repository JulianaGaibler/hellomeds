// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model

import me.juliana.hellomeds.data.model.enums.MedicationContainer

/**
 * Represents a single data point on the stock level graph.
 *
 * @param timestamp Unix timestamp in milliseconds
 * @param value Stock level value (count for both modes)
 * @param isFuture Whether this is a predicted future data point
 * @param event Optional event that caused this stock change
 */
data class StockDataPoint(
    val timestamp: Long,
    val value: Double,
    val isFuture: Boolean = false,
    val event: StockEvent? = null,
)

/**
 * Types of events that can occur at a data point.
 */
sealed class StockEvent {
    /**
     * A dose was taken from this medication.
     */
    data class DoseTaken(val logEventId: Int, val quantity: Double = 0.0) : StockEvent()

    /**
     * Stock was refilled or added.
     */
    data class Refill(val quantity: Double) : StockEvent()

    /**
     * Manual stock adjustment (correction, waste, etc.).
     */
    data class Adjustment(
        val adjustmentType: String,
        val notes: String? = null,
        val quantity: Double = 0.0,
    ) : StockEvent()

    /**
     * Container switch -- active container depleted, switching to next sealed container.
     * Used for both historical transitions and predicted future transitions.
     */
    data class ContainerSwitch(val container: MedicationContainer? = null) : StockEvent()
}
