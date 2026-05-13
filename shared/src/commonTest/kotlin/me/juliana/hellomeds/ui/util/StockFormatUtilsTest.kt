// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockFormatUtilsTest {

    // ================================================================
    // formatDisplayDouble
    // ================================================================

    @Test
    fun formatDisplayDouble_wholeNumber() {
        assertEquals("30", StockFormatUtils.formatDisplayDouble(30.0))
    }

    @Test
    fun formatDisplayDouble_oneDecimal() {
        assertEquals("30.5", StockFormatUtils.formatDisplayDouble(30.5))
    }

    @Test
    fun formatDisplayDouble_roundsToOneDecimal() {
        assertEquals("0.3", StockFormatUtils.formatDisplayDouble(0.25))
    }

    @Test
    fun formatDisplayDouble_zero() {
        assertEquals("0", StockFormatUtils.formatDisplayDouble(0.0))
    }

    @Test
    fun formatDisplayDouble_largeWholeNumber() {
        assertEquals("1000", StockFormatUtils.formatDisplayDouble(1000.0))
    }

    // ================================================================
    // formatWeightPair
    // ================================================================

    @Test
    fun formatWeightPair_metric() {
        assertEquals("50 / 200 g", StockFormatUtils.formatWeightPair(50.0, 200.0, false))
    }

    @Test
    fun formatWeightPair_imperial() {
        val result = StockFormatUtils.formatWeightPair(50.0, 200.0, true)!!
        assertTrue(result.contains("oz"), "Expected oz unit, got $result")
        assertTrue(result.contains("1.8"), "Expected ~1.8, got $result")
        assertTrue(result.contains("7.1"), "Expected ~7.1, got $result")
    }

    @Test
    fun formatWeightPair_nullEmpty() {
        assertNull(StockFormatUtils.formatWeightPair(null, 200.0, false))
    }

    @Test
    fun formatWeightPair_nullFull() {
        assertNull(StockFormatUtils.formatWeightPair(50.0, null, false))
    }

    @Test
    fun formatWeightPair_bothNull() {
        assertNull(StockFormatUtils.formatWeightPair(null, null, false))
    }

    @Test
    fun formatWeightPair_decimalValues_metric() {
        assertEquals("12.5 / 150.3 g", StockFormatUtils.formatWeightPair(12.5, 150.3, false))
    }

    // ================================================================
    // parseLocaleDouble
    // ================================================================

    @Test
    fun parseLocaleDouble_integer() {
        assertEquals(30.0, StockFormatUtils.parseLocaleDouble("30")!!)
    }

    @Test
    fun parseLocaleDouble_decimal() {
        assertEquals(30.5, StockFormatUtils.parseLocaleDouble("30.5")!!)
    }

    @Test
    fun parseLocaleDouble_emptyString() {
        assertNull(StockFormatUtils.parseLocaleDouble(""))
    }

    @Test
    fun parseLocaleDouble_nonNumeric() {
        assertNull(StockFormatUtils.parseLocaleDouble("abc"))
    }

    @Test
    fun parseLocaleDouble_negative() {
        assertEquals(-5.5, StockFormatUtils.parseLocaleDouble("-5.5")!!)
    }

    // ================================================================
    // formatInputDouble
    // ================================================================

    @Test
    fun formatInputDouble_wholeNumber() {
        assertEquals("30", StockFormatUtils.formatInputDouble(30.0))
    }

    @Test
    fun formatInputDouble_twoDecimals() {
        val result = StockFormatUtils.formatInputDouble(30.25)
        assertTrue(result.contains("30"), "Expected 30, got $result")
        assertTrue(result.contains("25"), "Expected 25, got $result")
    }

    @Test
    fun formatInputDouble_zero() {
        assertEquals("0", StockFormatUtils.formatInputDouble(0.0))
    }

    // ================================================================
    // Round-trip: formatInputDouble -> parseLocaleDouble
    // ================================================================

    @Test
    fun roundTrip_wholeNumber() {
        val formatted = StockFormatUtils.formatInputDouble(42.0)
        assertEquals(42.0, StockFormatUtils.parseLocaleDouble(formatted)!!)
    }

    @Test
    fun roundTrip_decimal() {
        val formatted = StockFormatUtils.formatInputDouble(3.14)
        assertEquals(3.14, StockFormatUtils.parseLocaleDouble(formatted)!!)
    }

    // ================================================================
    // GRAMS_PER_OZ constant
    // ================================================================

    @Test
    fun gramsPerOz_correctValue() {
        assertTrue(
            kotlin.math.abs(28.3495 - StockFormatUtils.GRAMS_PER_OZ) < 0.0001,
            "GRAMS_PER_OZ should be ~28.3495",
        )
    }
}
