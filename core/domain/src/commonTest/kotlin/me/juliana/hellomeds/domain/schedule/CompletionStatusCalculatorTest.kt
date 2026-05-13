// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.model.ProjectedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompletionStatusCalculatorTest {

    private val day1 = LocalDate(2025, 3, 1)
    private val day2 = LocalDate(2025, 3, 2)
    private val day3 = LocalDate(2025, 3, 3)

    private fun LocalDate.toEpochMillis(timeOfDay: String): Long =
        kotlinx.datetime.LocalDateTime(this, LocalTime.parse(timeOfDay))
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

    private fun event(scheduledTime: Long, status: String? = null) = ProjectedEvent(
        scheduleId = 1,
        medicationId = 1,
        scheduledTime = scheduledTime,
        dose = 1.0,
        historyRecord = status?.let {
            MedicationHistory(
                id = 0,
                medicationId = 1,
                scheduleId = 1,
                scheduledTime = scheduledTime,
                takenTime = null,
                scheduledDose = 1.0,
                actualDose = null,
                status = it,
            )
        },
    )

    @Test
    fun allTaken_returnsFullyCompleted() {
        val events = listOf(
            event(day1.toEpochMillis("08:00"), MedicationHistory.STATUS_TAKEN),
            event(day1.toEpochMillis("12:00"), MedicationHistory.STATUS_TAKEN),
            event(day1.toEpochMillis("20:00"), MedicationHistory.STATUS_TAKEN),
        )

        val result = CompletionStatusCalculator.calculate(events, day1, day1)

        val status = result[day1]!!
        assertEquals(3, status.completed)
        assertTrue(status.isFullyCompleted)
    }

    @Test
    fun partialTaken_returnsCorrectCounts() {
        val events = listOf(
            event(day1.toEpochMillis("08:00"), MedicationHistory.STATUS_TAKEN),
            event(day1.toEpochMillis("12:00"), MedicationHistory.STATUS_TAKEN),
            event(day1.toEpochMillis("20:00")),
        )

        val result = CompletionStatusCalculator.calculate(events, day1, day1)

        val status = result[day1]!!
        assertEquals(2, status.completed)
        assertTrue(status.completionPercentage in 0.65f..0.68f)
    }

    @Test
    fun skippedNotCountedAsCompleted() {
        val events = listOf(
            event(day1.toEpochMillis("08:00"), MedicationHistory.STATUS_TAKEN),
            event(day1.toEpochMillis("12:00"), MedicationHistory.STATUS_SKIPPED),
        )

        val result = CompletionStatusCalculator.calculate(events, day1, day1)

        val status = result[day1]!!
        assertEquals(1, status.completed)
        assertEquals(2, status.totalScheduled)
    }

    @Test
    fun dayWithNoEvents_hasZeroTotals() {
        val result = CompletionStatusCalculator.calculate(emptyList(), day1, day1)

        val status = result[day1]!!
        assertEquals(0, status.totalScheduled)
    }

    @Test
    fun multiDayRange_groupsCorrectly() {
        val events = listOf(
            event(day1.toEpochMillis("08:00"), MedicationHistory.STATUS_TAKEN),
            event(day2.toEpochMillis("08:00"), MedicationHistory.STATUS_TAKEN),
            event(day2.toEpochMillis("20:00")),
            event(day3.toEpochMillis("08:00")),
        )

        val result = CompletionStatusCalculator.calculate(events, day1, day3)

        assertEquals(3, result.size)
        assertEquals(1, result[day1]!!.completed)
        assertEquals(1, result[day1]!!.totalScheduled)
        assertEquals(1, result[day2]!!.completed)
        assertEquals(2, result[day2]!!.totalScheduled)
        assertEquals(0, result[day3]!!.completed)
        assertEquals(1, result[day3]!!.totalScheduled)
    }

    @Test
    fun emptyEvents_returnsEntriesForAllDates() {
        val start = LocalDate(2025, 3, 1)
        val end = LocalDate(2025, 3, 5)

        val result = CompletionStatusCalculator.calculate(emptyList(), start, end)

        assertEquals(5, result.size)
        result.values.forEach { status ->
            assertEquals(0, status.totalScheduled)
            assertEquals(0, status.completed)
        }
    }

    @Test
    fun eventsOutsideRange_excluded() {
        val events = listOf(
            event(day1.toEpochMillis("08:00"), MedicationHistory.STATUS_TAKEN),
            event(day2.toEpochMillis("08:00"), MedicationHistory.STATUS_TAKEN),
            event(day3.toEpochMillis("08:00"), MedicationHistory.STATUS_TAKEN),
        )

        val result = CompletionStatusCalculator.calculate(events, day2, day2)

        assertEquals(1, result.size)
        assertEquals(1, result[day2]!!.completed)
    }

    @Test
    fun completionPercentage_zeroForZeroScheduled() {
        val result = CompletionStatusCalculator.calculate(emptyList(), day1, day1)

        assertEquals(0f, result[day1]!!.completionPercentage)
    }

    @Test
    fun completionPercentage_oneForFullyCompleted() {
        val events = listOf(
            event(day1.toEpochMillis("08:00"), MedicationHistory.STATUS_TAKEN),
        )

        val result = CompletionStatusCalculator.calculate(events, day1, day1)

        assertEquals(1.0f, result[day1]!!.completionPercentage)
    }

    @Test
    fun hasScheduledMedications_falseWhenNone() {
        val result = CompletionStatusCalculator.calculate(emptyList(), day1, day1)

        assertFalse(result[day1]!!.hasScheduledMedications)
    }
}
