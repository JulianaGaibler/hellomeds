// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.graph.models

/**
 * Configuration for the stock level graph component.
 *
 * @param zoomLevel Current zoom/time scale level
 * @param showGrid Whether to show grid lines
 * @param showLegend Whether to show the medication legend
 * @param snapToPoints Whether to snap to data points when scrolling
 * @param enablePanning Whether horizontal panning is enabled
 */
data class GraphConfig(
    val zoomLevel: ZoomLevel = ZoomLevel.WEEK,
    val showGrid: Boolean = false,
    val showLegend: Boolean = true,
    val snapToPoints: Boolean = true,
    val enablePanning: Boolean = true,
    val showYAxis: Boolean = true,
)

/**
 * Zoom levels determining how many days are visible at once.
 */
enum class ZoomLevel(val daysVisible: Int, val label: String) {
    DAY(1, "Day"),
    WEEK(7, "Week"),
    MONTH(30, "Month"),
    QUARTER(90, "Quarter"),
    YEAR(365, "Year"),
    ;

    val millisVisible: Long
        get() = daysVisible * 24L * 60L * 60L * 1000L

    fun zoomIn(): ZoomLevel = when (this) {
        YEAR -> QUARTER
        QUARTER -> MONTH
        MONTH -> WEEK
        WEEK -> DAY
        DAY -> DAY
    }

    fun zoomOut(): ZoomLevel = when (this) {
        DAY -> WEEK
        WEEK -> MONTH
        MONTH -> QUARTER
        QUARTER -> YEAR
        YEAR -> YEAR
    }
}
