// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import me.juliana.hellomeds.data.createMedication
import me.juliana.hellomeds.data.model.enums.CycleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleDayInfoTest {

    // Standard 21/7 cycle (e.g., birth control: 21 active + 7 break)
    private val anchor = LocalDate(2025, 3, 1)

    private fun cyclic2107(daysActive: Int = 21, daysBreak: Int = 7, cycleStartDate: LocalDate = anchor) =
        createMedication(
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = daysActive,
            cycleDaysBreak = daysBreak,
            cycleStartDate = cycleStartDate,
        )

    // --- Dates before anchor (negative modulo) ---

    @Test
    fun getCycleDay_dateBeforeAnchor_correctPosition() {
        // Consequence of failure: MISSED DOSE. Negative modulo produces wrong cycle
        // position, suppressing active-day events for historical queries.
        val med = cyclic2107()

        // 7 days before anchor → daysSinceAnchor = -7
        // ((-7 % 28) + 28) % 28 = ((-7) + 28) % 28 = 21 % 28 = 21
        val result7Before = getCycleDay(med, LocalDate(2025, 2, 22))
        assertNotNull(result7Before)
        assertEquals(21, result7Before.dayInCycle, "-7 days: should be day 21 (first break day)")
        assertFalse(result7Before.isActive, "Day 21 is in break period")

        // 8 days before anchor → dayInCycle = 20 (last active day)
        val result8Before = getCycleDay(med, LocalDate(2025, 2, 21))
        assertNotNull(result8Before)
        assertEquals(20, result8Before.dayInCycle, "-8 days: should be day 20 (last active day)")
        assertTrue(result8Before.isActive, "Day 20 is in active period")

        // Exactly 1 full cycle back (28 days) → dayInCycle = 0
        val result28Before = getCycleDay(med, LocalDate(2025, 2, 1))
        assertNotNull(result28Before)
        assertEquals(0, result28Before.dayInCycle, "-28 days: should wrap to day 0")
        assertTrue(result28Before.isActive, "Day 0 is active")

        // Exactly 2 full cycles back (56 days) → dayInCycle = 0
        val result56Before = getCycleDay(med, LocalDate(2025, 1, 4))
        assertNotNull(result56Before)
        assertEquals(0, result56Before.dayInCycle, "-56 days: should wrap to day 0")
        assertTrue(result56Before.isActive, "Day 0 is active")
    }

    @Test
    fun getCycleDay_dateBeforeAnchor_multiCyclesBack() {
        // Consequence of failure: Long backward calculation wraps incorrectly.
        val med = cyclic2107(cycleStartDate = LocalDate(2025, 6, 1))
        val testDate = LocalDate(2025, 1, 1) // 151 days before anchor

        val result = getCycleDay(med, testDate)

        assertNotNull(result)
        // -151 % 28 = -151 + (6 * 28) = -151 + 168 = 17
        // ((-151 % 28) + 28) % 28 = ((-11) + 28) % 28 = 17
        assertEquals(17, result.dayInCycle, "-151 days: should be day 17")
        assertTrue(result.isActive, "Day 17 < 21, so active")
    }

    // --- Long forward periods ---

    @Test
    fun getCycleDay_365DaysAfterAnchor_correctPosition() {
        // Consequence of failure: Long forward calculation drifts after many months.
        val med = cyclic2107(cycleStartDate = LocalDate(2025, 1, 1))
        val testDate = LocalDate(2026, 1, 1) // 365 days later

        val result = getCycleDay(med, testDate)

        assertNotNull(result)
        // 365 % 28 = 365 - (13 * 28) = 365 - 364 = 1
        assertEquals(1, result.dayInCycle, "365 days: should be day 1")
        assertTrue(result.isActive, "Day 1 < 21, so active")
    }

    // --- Zero-break cycle ---

    @Test
    fun getCycleDay_zeroBreakCycle_alwaysActive() {
        // Consequence of failure: Zero-break cycle falsely marks some days as break.
        // MISSED DOSE for continuous medication (e.g., continuous birth control).
        val med = cyclic2107(daysActive = 28, daysBreak = 0)

        for (offset in 0..27) {
            val date = anchor.plus(offset, DateTimeUnit.DAY)
            val result = getCycleDay(med, date)
            assertNotNull(result, "Day $offset should return non-null")
            assertTrue(result.isActive, "Day $offset: 28/0 cycle should always be active")
            assertEquals(offset, result.dayInCycle, "Day $offset: dayInCycle should match offset")
        }
    }

    // --- Determinism ---

    @Test
    fun getCycleDay_deterministic_sameInputSameOutput() {
        // Consequence of failure: Non-deterministic results cause UI to flicker
        // between active/break display.
        val med = cyclic2107()
        val date = LocalDate(2025, 3, 15) // Day 14 in cycle

        val results = (1..100).map { getCycleDay(med, date) }

        val first = results.first()
        assertNotNull(first)
        results.forEach { result ->
            assertNotNull(result)
            assertEquals(first.dayInCycle, result.dayInCycle, "dayInCycle must be deterministic")
            assertEquals(first.isActive, result.isActive, "isActive must be deterministic")
            assertEquals(first.cycleLength, result.cycleLength, "cycleLength must be deterministic")
        }
    }

    // --- Degenerate configurations ---

    @Test
    fun getCycleDay_cycleLengthZero_returnsNull() {
        // Consequence of failure: Division by zero crash.
        val med = createMedication(
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 0,
            cycleDaysBreak = 0,
            cycleStartDate = anchor,
        )

        val result = getCycleDay(med, anchor)

        assertNull(result, "cycleLength=0 should return null to avoid division by zero")
    }

    @Test
    fun getCycleDay_nonCyclic_returnsNull() {
        // Consequence of failure: Non-cyclic medication gets cycle mask applied.
        val med = createMedication(cycleType = CycleType.NONE)

        val result = getCycleDay(med, anchor)

        assertNull(result, "Non-cyclic medication should return null")
    }

    @Test
    fun getCycleDay_nullAnchorDate_returnsNull() {
        // Consequence of failure: NPE from missing anchor date.
        val med = createMedication(
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 21,
            cycleDaysBreak = 7,
            cycleStartDate = null,
        )

        val result = getCycleDay(med, anchor)

        assertNull(result, "Null cycleStartDate should return null")
    }

    @Test
    fun getCycleDay_zeroDaysActive_allBreak() {
        // Consequence of failure: App crash or nonsensical behavior with degenerate config.
        val med = createMedication(
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 0,
            cycleDaysBreak = 7,
            cycleStartDate = anchor,
        )

        for (offset in 0..6) {
            val date = anchor.plus(offset, DateTimeUnit.DAY)
            val result = getCycleDay(med, date)
            assertNotNull(result, "Day $offset should return non-null (cycleLength=7 > 0)")
            assertFalse(result.isActive, "Day $offset: daysActive=0 means all days are break")
        }
    }
}
