// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwoDayWindowValidatorTest {

    private val twoDaysMs = 2 * 24 * 60 * 60 * 1000L

    // Fixed reference: 2025-03-15T12:00:00Z = 1742040000000L
    private val fixedNow = 1742040000000L

    @Test
    fun nullScheduledTime_returnsTrue() {
        assertTrue(TwoDayWindowValidator.isWithinWindow(null, currentTime = fixedNow))
    }

    @Test
    fun pastTime_returnsTrue() {
        val pastTime = fixedNow - 3_600_000L // 1 hour ago
        assertTrue(TwoDayWindowValidator.isWithinWindow(pastTime, currentTime = fixedNow))
    }

    @Test
    fun timeWithinTwoDays_returnsTrue() {
        val futureTime = fixedNow + 24 * 60 * 60 * 1000L // 1 day from now
        assertTrue(TwoDayWindowValidator.isWithinWindow(futureTime, currentTime = fixedNow))
    }

    @Test
    fun timeAtExactlyTwoDays_returnsTrue() {
        val exactlyTwoDays = fixedNow + twoDaysMs
        assertTrue(TwoDayWindowValidator.isWithinWindow(exactlyTwoDays, currentTime = fixedNow))
    }

    @Test
    fun timeBeyondTwoDays_returnsFalse() {
        val beyondTwoDays = fixedNow + twoDaysMs + 60_000L // 2 days + 1 minute
        assertFalse(TwoDayWindowValidator.isWithinWindow(beyondTwoDays, currentTime = fixedNow))
    }

    @Test
    fun farFuture_returnsFalse() {
        val farFuture = fixedNow + 30 * 24 * 60 * 60 * 1000L // 30 days
        assertFalse(TwoDayWindowValidator.isWithinWindow(farFuture, currentTime = fixedNow))
    }

    @Test
    fun timeAtExactlyTwoDays_plusOneMillisecond_returnsFalse() {
        // Consequence of failure: Off-by-one allows logging a dose 2 days + 1ms
        // in the future, which could create premature dose records.
        val justBeyond = fixedNow + twoDaysMs + 1L
        assertFalse(
            TwoDayWindowValidator.isWithinWindow(justBeyond, currentTime = fixedNow),
            "2 days + 1ms should be outside the window",
        )
    }
}
