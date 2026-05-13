// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.graph.util

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * KMP-compatible fixed-decimal formatting.
 * Replaces `"%.Nf".format(value)` which is JVM-only.
 */
private fun formatFixed(value: Double, decimals: Int): String {
    if (decimals <= 0) return value.roundToLong().toString()
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToLong()
    val intPart = rounded / factor.toLong()
    val fracPart = abs(rounded % factor.toLong())
    val sign = if (value < 0 && intPart == 0L) "-" else ""
    return "$sign$intPart.${fracPart.toString().padStart(decimals, '0')}"
}

/**
 * Formats values for display on the Y-axis of the graph.
 */
object ValueFormatter {

    /**
     * Format a value with the appropriate unit.
     *
     * @param value The numeric value to format
     * @param unit The unit string (e.g., "%", "tablets", "mg")
     * @param decimals Number of decimal places (default: auto-detect)
     * @return Formatted value string
     */
    fun format(value: Double, unit: String, decimals: Int? = null): String {
        val decimalPlaces = decimals ?: getOptimalDecimals(value)
        val formatted = formatFixed(value, decimalPlaces)

        return when (unit) {
            "%" -> "$formatted%"
            else -> "$formatted $unit"
        }
    }

    /**
     * Format a value without unit (for compact display).
     */
    fun formatCompact(value: Double, decimals: Int? = null): String {
        val decimalPlaces = decimals ?: getOptimalDecimals(value)
        return formatFixed(value, decimalPlaces)
    }

    /**
     * Determine the optimal number of decimal places based on value magnitude.
     */
    private fun getOptimalDecimals(value: Double): Int {
        if (value == 0.0) return 0

        val absValue = abs(value)
        return when {
            absValue >= 100 -> 0 // 100+ -> no decimals
            absValue >= 10 -> 1 // 10-99 -> 1 decimal
            absValue >= 1 -> 1 // 1-9 -> 1 decimal
            absValue >= 0.1 -> 2 // 0.1-0.9 -> 2 decimals
            else -> 3 // < 0.1 -> 3 decimals
        }
    }

    /**
     * Calculate nice round values for Y-axis labels.
     *
     * @param min Minimum value in range
     * @param max Maximum value in range
     * @param targetCount Target number of labels (default: 5)
     * @return List of nicely spaced values
     */
    fun calculateLabelValues(min: Double, max: Double, targetCount: Int = 5): List<Double> {
        if (min >= max) return listOf(min)

        val range = max - min
        val roughInterval = range / (targetCount - 1)

        // Find nice interval (power of 10 with 1, 2, or 5 multiplier)
        val niceInterval = calculateNiceInterval(roughInterval)

        // Calculate start and end aligned to nice intervals
        val start = floor(min / niceInterval) * niceInterval
        val end = (floor(max / niceInterval) + 1) * niceInterval

        // Generate label values
        val result = mutableListOf<Double>()
        var current = start
        while (current <= end) {
            if (current >= min && current <= max) {
                result.add(current)
            }
            current += niceInterval
        }

        // Ensure we have at least min and max
        if (result.isEmpty() || result.first() > min) {
            result.add(0, min)
        }
        if (result.isEmpty() || result.last() < max) {
            result.add(max)
        }

        return result
    }

    /**
     * Calculate a "nice" interval for axis labels.
     * Returns values like 1, 2, 5, 10, 20, 50, 100, etc.
     */
    private fun calculateNiceInterval(roughInterval: Double): Double {
        if (roughInterval <= 0) return 1.0

        val exponent = floor(log10(roughInterval))
        val fraction = roughInterval / 10.0.pow(exponent)

        val niceFraction = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }

        return niceFraction * 10.0.pow(exponent)
    }

    /**
     * Format a percentage value (0-100 range).
     */
    fun formatPercentage(value: Double): String {
        return when {
            value >= 99.5 -> "100%"
            value <= 0.5 -> "0%"
            value >= 10 -> "${formatFixed(value, 0)}%"
            else -> "${formatFixed(value, 1)}%"
        }
    }

    /**
     * Format a discrete count value (whole numbers).
     */
    fun formatCount(value: Double): String {
        return formatFixed(value, 0)
    }

    /**
     * Format a weight value with appropriate precision.
     */
    fun formatWeight(value: Double, unit: String = "g"): String {
        return when {
            value >= 1000 -> "${formatFixed(value / 1000, 1)} kg"
            value >= 1 -> "${formatFixed(value, 1)} $unit"
            else -> "${formatFixed(value, 2)} $unit"
        }
    }
}
