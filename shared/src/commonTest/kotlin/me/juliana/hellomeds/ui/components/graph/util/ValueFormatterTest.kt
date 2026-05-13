// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.graph.util

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValueFormatterTest {

    // --- format() ---

    @Test
    fun format_percentUnit_noSpace() {
        // 50.0 → getOptimalDecimals = 1 → "50.0%"
        assertEquals("50.0%", ValueFormatter.format(50.0, "%"))
    }

    @Test
    fun format_tabletUnit_hasSpace() {
        assertEquals("10.0 tablets", ValueFormatter.format(10.0, "tablets"))
    }

    @Test
    fun format_explicitDecimals() {
        assertEquals("3.14 mg", ValueFormatter.format(3.14159, "mg", decimals = 2))
    }

    @Test
    fun format_largeValue_noAutoDecimals() {
        // >= 100 → 0 decimals
        assertEquals("150 tablets", ValueFormatter.format(150.0, "tablets", decimals = 0))
    }

    // --- formatCompact() ---

    @Test
    fun formatCompact_largeValue_autoZeroDecimals() {
        // >= 100 → 0 decimals
        assertEquals("150", ValueFormatter.formatCompact(150.0))
    }

    @Test
    fun formatCompact_smallValue_threeDecimals() {
        // < 0.1 → 3 decimals → "0.050"
        assertEquals("0.050", ValueFormatter.formatCompact(0.05))
    }

    @Test
    fun formatCompact_zero() {
        assertEquals("0", ValueFormatter.formatCompact(0.0))
    }

    @Test
    fun formatCompact_explicitDecimals() {
        assertEquals("1.5", ValueFormatter.formatCompact(1.5, decimals = 1))
    }

    // --- formatPercentage() ---

    @Test
    fun formatPercentage_100() {
        assertEquals("100%", ValueFormatter.formatPercentage(100.0))
    }

    @Test
    fun formatPercentage_boundarySnap_highEnd() {
        assertEquals("100%", ValueFormatter.formatPercentage(99.6))
    }

    @Test
    fun formatPercentage_boundarySnap_lowEnd() {
        assertEquals("0%", ValueFormatter.formatPercentage(0.3))
    }

    @Test
    fun formatPercentage_midRange_noDecimals() {
        // >= 10 → 0 decimals
        assertEquals("50%", ValueFormatter.formatPercentage(50.0))
    }

    @Test
    fun formatPercentage_singleDigit_oneDecimal() {
        assertEquals("5.5%", ValueFormatter.formatPercentage(5.5))
    }

    // --- formatWeight() ---

    @Test
    fun formatWeight_grams() {
        // >= 1 → 1 decimal
        assertEquals("500.0 g", ValueFormatter.formatWeight(500.0))
    }

    @Test
    fun formatWeight_kilograms() {
        // >= 1000 → divided by 1000, 1 decimal
        assertEquals("1.5 kg", ValueFormatter.formatWeight(1500.0))
    }

    @Test
    fun formatWeight_subGram() {
        // < 1 → 2 decimals
        assertEquals("0.25 g", ValueFormatter.formatWeight(0.25))
    }

    // --- formatCount() ---

    @Test
    fun formatCount_roundsToWhole() {
        // 42.7.roundToLong() = 43
        assertEquals("43", ValueFormatter.formatCount(42.7))
    }

    @Test
    fun formatCount_exactInteger() {
        assertEquals("10", ValueFormatter.formatCount(10.0))
    }

    // --- calculateLabelValues() ---

    @Test
    fun calculateLabelValues_normalRange() {
        val labels = ValueFormatter.calculateLabelValues(0.0, 100.0)
        assertTrue(labels.isNotEmpty())
        assertTrue(labels.first() <= 0.0, "First label should be <= min")
        assertTrue(labels.last() >= 100.0, "Last label should be >= max")
    }

    @Test
    fun calculateLabelValues_sameMinMax_returnsSingleValue() {
        val labels = ValueFormatter.calculateLabelValues(50.0, 50.0)
        assertEquals(1, labels.size)
        assertEquals(50.0, labels[0])
    }

    @Test
    fun calculateLabelValues_smallRange_producesNiceIntervals() {
        val labels = ValueFormatter.calculateLabelValues(0.0, 10.0)
        assertTrue(labels.size >= 2)
        if (labels.size >= 3) {
            val interval = labels[1] - labels[0]
            val frac = interval / (10.0).pow(floor(log10(interval)))
            assertTrue(frac in listOf(1.0, 2.0, 5.0), "Interval $interval should be a nice number")
        }
    }

    // --- locale safety ---

    @Test
    fun formatCompact_decimalSeparatorIsDot() {
        val result = ValueFormatter.formatCompact(1.5, decimals = 1)
        assertTrue(result.contains("."), "Decimal separator should be dot, got: $result")
    }
}
