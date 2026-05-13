// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import me.juliana.hellomeds.data.createMedication
import kotlin.test.Test
import kotlin.test.assertEquals

class StockThresholdCalculatorTest {

    private fun severity(stock: Double, threshold: Double?) = StockThresholdCalculator.getStockSeverity(
        createMedication(lowStockThreshold = threshold),
        stock,
    )

    @Test
    fun noThreshold_alwaysGood() {
        assertEquals("GOOD", severity(0.0, null))
        assertEquals("GOOD", severity(100.0, null))
    }

    @Test
    fun zeroStock_critical() {
        assertEquals("CRITICAL", severity(0.0, 10.0))
    }

    @Test
    fun stockAtHalfThreshold_critical() {
        // Exactly 50% of threshold (5.0 / 10.0 = 0.5)
        assertEquals("CRITICAL", severity(5.0, 10.0))
    }

    @Test
    fun stockBelowHalfThreshold_critical() {
        assertEquals("CRITICAL", severity(3.0, 10.0))
    }

    @Test
    fun stockJustAboveHalfThreshold_low() {
        assertEquals("LOW", severity(5.01, 10.0))
    }

    @Test
    fun stockAtThreshold_low() {
        assertEquals("LOW", severity(10.0, 10.0))
    }

    @Test
    fun stockBetweenThresholdAndOneAndHalf_medium() {
        assertEquals("MEDIUM", severity(12.0, 10.0))
    }

    @Test
    fun stockAtExactlyOneAndHalfThreshold_medium() {
        // 15.0 / 10.0 = 1.5 → still ≤ 1.5× → MEDIUM
        assertEquals("MEDIUM", severity(15.0, 10.0))
    }

    @Test
    fun stockAboveOneAndHalfThreshold_good() {
        assertEquals("GOOD", severity(15.01, 10.0))
    }

    @Test
    fun floatingPointStock_handledCorrectly() {
        // 0.5 remaining of liquid medication with threshold 1.0
        assertEquals("CRITICAL", severity(0.5, 1.0))
    }
}
