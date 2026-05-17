// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.graph.models

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import me.juliana.hellomeds.data.model.StockDataPoint
import me.juliana.hellomeds.data.model.enums.MedicationType

/**
 * Represents a line on the stock level graph for a single medication.
 *
 * @param medicationId Database ID of the medication
 * @param medicationName Display name for the legend
 * @param dataPoints List of stock level data points over time (sorted by timestamp)
 * @param medicationType The medication type enum (for resolving unit labels)
 * @param isEstimatedTracking Whether this line uses estimated (container-based) tracking
 * @param color Color for this line and its Y-axis
 * @param yAxisRange Min and max values for Y-axis scaling (min, max)
 */
@Stable
data class StockLine(
    val medicationId: Int,
    val medicationName: String,
    val dataPoints: List<StockDataPoint>,
    val medicationType: MedicationType,
    val isEstimatedTracking: Boolean,
    val color: Color,
    val yAxisRange: Pair<Double, Double>,
    val lowerBoundPoints: List<StockDataPoint> = emptyList(),
    val upperBoundPoints: List<StockDataPoint> = emptyList(),
) {
    init {
        require(dataPoints.zipWithNext().all { (a, b) -> a.timestamp <= b.timestamp }) {
            "Data points must be sorted by timestamp"
        }
        require(yAxisRange.first < yAxisRange.second) {
            "Y-axis range min must be less than max"
        }
    }

    /** Used to decide whether two lines can share a Y-axis. */
    fun hasSameUnit(other: StockLine): Boolean {
        return medicationType == other.medicationType &&
            isEstimatedTracking == other.isEstimatedTracking
    }

    val historicalPoints: List<StockDataPoint>
        get() = dataPoints.filter { !it.isFuture }

    val futurePoints: List<StockDataPoint>
        get() = dataPoints.filter { it.isFuture }

    val timeRange: LongRange
        get() = if (dataPoints.isEmpty()) {
            0L..0L
        } else {
            dataPoints.first().timestamp..dataPoints.last().timestamp
        }
}
