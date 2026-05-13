// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pure formatting and parsing helpers for stock settings UI.
 * Extracted from Compose files for testability.
 *
 * Uses KMP-compatible formatting (no java.text or String.format).
 */
object StockFormatUtils {

    const val GRAMS_PER_OZ = 28.3495

    /**
     * Platform-independent decimal separator.
     * On KMP we default to '.' — locale-aware formatting is handled by
     * the expect/actual Formatters layer.
     */
    private val decimalSeparator: Char = '.'

    /**
     * Format a double for display text: "30" for whole numbers, "30.5" for one decimal place.
     */
    fun formatDisplayDouble(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            formatFixed(value, 1)
        }
    }

    /**
     * Format a weight pair (empty/full) with optional imperial conversion.
     * Returns null if either weight is null.
     */
    fun formatWeightPair(empty: Double?, full: Double?, isImperial: Boolean): String? {
        if (empty == null || full == null) return null
        val e = if (isImperial) empty / GRAMS_PER_OZ else empty
        val f = if (isImperial) full / GRAMS_PER_OZ else full
        val unit = if (isImperial) "oz" else "g"
        return "${formatDisplayDouble(e)} / ${formatDisplayDouble(f)} $unit"
    }

    /**
     * Parse locale-aware decimal text to Double.
     * Handles both '.' and ',' decimal separators.
     */
    fun parseLocaleDouble(text: String): Double? {
        return text.replace(',', '.').toDoubleOrNull()
    }

    /**
     * Format a double for dialog input fields: "30" for whole numbers,
     * "30.25" for two decimal places.
     */
    fun formatInputDouble(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            formatFixed(value, 2)
        }
    }

    // --- Slider step configuration ---

    /**
     * Configuration for range-dependent slider precision.
     * @param stepSize the increment between slider positions
     * @param decimals number of decimal places to display (0 or 1)
     */
    data class StepConfig(val stepSize: Double, val decimals: Int)

    /**
     * Returns step size and display decimals based on the slider's max value.
     * Small ranges get fine-grained steps; large ranges get coarse steps.
     */
    fun stepConfigForRange(maxValue: Double): StepConfig = when {
        maxValue <= 5.0 -> StepConfig(0.1, 1)
        maxValue <= 20.0 -> StepConfig(0.5, 1)
        else -> StepConfig(1.0, 0)
    }

    /**
     * Snaps a value to the nearest valid step, clamped to [0, maxValue].
     * Uses [round] to handle binary floating-point imprecision (e.g. 0.1).
     */
    fun snapToStep(value: Double, stepSize: Double, maxValue: Double): Double {
        if (stepSize <= 0.0 || maxValue <= 0.0) return 0.0
        return (round(value / stepSize) * stepSize).coerceIn(0.0, maxValue)
    }

    /**
     * Formats a value with a fixed number of decimal places for slider display.
     * Prevents artifacts like "3.4000001" from Float→Double conversion.
     */
    fun formatSteppedValue(value: Double, decimals: Int): String = formatFixed(value, decimals)

    /**
     * Calculates the Compose Slider `steps` parameter (intermediate steps, excluding endpoints).
     * Uses [roundToInt] to avoid floating-point truncation (e.g. 5.0/0.1 = 49.999...).
     */
    fun sliderStepsCount(maxValue: Double, stepSize: Double): Int {
        if (stepSize <= 0.0 || maxValue <= 0.0) return 0
        val totalPositions = (maxValue / stepSize).roundToInt()
        return maxOf(0, totalPositions - 1)
    }

    /**
     * KMP-compatible fixed-decimal formatting.
     */
    private fun formatFixed(value: Double, decimals: Int): String {
        if (decimals <= 0) return value.roundToLong().toString()
        val factor = 10.0.pow(decimals)
        val rounded = (value * factor).roundToLong()
        val intPart = rounded / factor.toLong()
        val fracPart = abs(rounded % factor.toLong())
        return "$intPart.${fracPart.toString().padStart(decimals, '0')}"
    }
}
