// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.graph.drawing

import me.juliana.hellomeds.data.model.StockDataPoint
import me.juliana.hellomeds.ui.components.graph.models.StockLine
import kotlin.math.abs

/**
 * Handles coordinate conversions between data space and canvas space for the graph.
 *
 * @param canvasWidth Width of the drawable canvas in pixels
 * @param canvasHeight Height of the drawable canvas in pixels
 * @param lines List of stock lines to display
 * @param visibleTimeRange Time range currently visible (start to end timestamp)
 * @param scrollOffset Horizontal scroll offset in pixels
 * @param padding Padding around the graph content (left, top, right, bottom)
 */
data class GraphCoordinateSystem(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val lines: List<StockLine>,
    val visibleTimeRange: LongRange,
    val scrollOffset: Float = 0f,
    val padding: GraphPadding = GraphPadding(),
) {

    /**
     * The drawable width accounting for padding.
     */
    val drawableWidth: Float
        get() = canvasWidth - padding.left - padding.right

    /**
     * The drawable height accounting for padding.
     */
    val drawableHeight: Float
        get() = canvasHeight - padding.top - padding.bottom

    /**
     * Convert a timestamp to X coordinate on the canvas.
     */
    fun timeToX(timestamp: Long): Float {
        val timeRange = visibleTimeRange.last - visibleTimeRange.first
        if (timeRange == 0L) return padding.left

        val normalizedTime = (timestamp - visibleTimeRange.first).toFloat() / timeRange
        return padding.left + (normalizedTime * drawableWidth) + scrollOffset
    }

    /**
     * Convert an X coordinate to a timestamp.
     */
    fun xToTime(x: Float): Long {
        val timeRange = visibleTimeRange.last - visibleTimeRange.first
        val normalizedX = (x - padding.left - scrollOffset) / drawableWidth
        return visibleTimeRange.first + (normalizedX * timeRange).toLong()
    }

    /**
     * Convert a value to Y coordinate for a specific line.
     *
     * @param value The data value
     * @param lineIndex Index of the line in the lines list
     * @return Y coordinate (0 = top, canvasHeight = bottom)
     */
    fun valueToY(value: Double, lineIndex: Int): Float {
        if (lineIndex !in lines.indices) return padding.top

        val line = lines[lineIndex]
        val (min, max) = line.yAxisRange
        val valueRange = max - min
        if (valueRange == 0.0) return padding.top + drawableHeight / 2

        val normalizedValue = ((value - min) / valueRange).toFloat()
        // Invert Y axis (0 at top, max at bottom) so higher values are at top
        return padding.top + (drawableHeight * (1f - normalizedValue))
    }

    /**
     * Convert Y coordinate back to a value for a specific line.
     */
    fun yToValue(y: Float, lineIndex: Int): Double {
        if (lineIndex !in lines.indices) return 0.0

        val line = lines[lineIndex]
        val (min, max) = line.yAxisRange
        val normalizedY = 1f - ((y - padding.top) / drawableHeight)
        return min + (normalizedY * (max - min))
    }

    /**
     * Find the data point closest to the given X position.
     *
     * @param x X coordinate on the canvas
     * @param maxDistance Maximum distance in pixels to consider (default 50)
     * @return The closest data point and its line index, or null if none within range
     */
    fun findNearestPoint(x: Float, maxDistance: Float = 50f): Pair<StockDataPoint, Int>? {
        val targetTime = xToTime(x)
        var closestPoint: StockDataPoint? = null
        var closestLineIndex = -1
        var closestDistance = Float.MAX_VALUE

        lines.forEachIndexed { lineIndex, line ->
            val points = line.dataPoints
            if (points.isEmpty()) return@forEachIndexed

            // Binary search: points are sorted by timestamp (enforced by StockLine.init)
            var lo = 0
            var hi = points.lastIndex
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (points[mid].timestamp < targetTime) lo = mid + 1 else hi = mid
            }
            val candidate = if (lo == 0) {
                points[0]
            } else if (lo >= points.size) {
                points.last()
            } else {
                val left = points[lo - 1]
                val right = points[lo]
                if (abs(left.timestamp - targetTime) <= abs(right.timestamp - targetTime)) left else right
            }

            val pointX = timeToX(candidate.timestamp)
            val distance = abs(pointX - x)
            if (distance < closestDistance && distance <= maxDistance) {
                closestDistance = distance
                closestPoint = candidate
                closestLineIndex = lineIndex
            }
        }

        return if (closestPoint != null && closestLineIndex >= 0) {
            Pair(closestPoint!!, closestLineIndex)
        } else {
            null
        }
    }

    /**
     * Get all data points within the visible time range.
     */
    fun getVisiblePoints(): List<Pair<StockDataPoint, Int>> {
        val result = mutableListOf<Pair<StockDataPoint, Int>>()
        lines.forEachIndexed { lineIndex, line ->
            line.dataPoints
                .filter { it.timestamp in visibleTimeRange }
                .forEach { point -> result.add(Pair(point, lineIndex)) }
        }
        return result
    }

    /**
     * Check if a point is currently visible on the canvas.
     */
    fun isPointVisible(point: StockDataPoint): Boolean {
        val x = timeToX(point.timestamp)
        return x >= padding.left && x <= canvasWidth - padding.right
    }
}

/**
 * Padding configuration for the graph.
 */
data class GraphPadding(
    val left: Float = 60f, // Space for Y-axis labels
    val top: Float = 20f, // Space for top margin
    val right: Float = 60f, // Space for right Y-axis labels
    val bottom: Float = 40f, // Space for X-axis labels
)
