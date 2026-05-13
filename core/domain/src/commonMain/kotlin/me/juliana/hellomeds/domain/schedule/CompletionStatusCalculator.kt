// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.model.DayCompletionStatus
import me.juliana.hellomeds.data.model.ProjectedEvent
import kotlin.time.Instant

/**
 * Calculates completion status for dates in the tracking calendar.
 * Groups projected events by date and determines pending/taken/skipped counts.
 */
object CompletionStatusCalculator {

    /**
     * Calculates completion status map for a date range from projected events.
     *
     * @param events All projected events in the date range (with history merged)
     * @param startDate Start of range (inclusive)
     * @param endDate End of range (inclusive)
     * @return Map of dates to their completion status
     */
    fun calculate(
        events: List<ProjectedEvent>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Map<LocalDate, DayCompletionStatus> {
        val map = mutableMapOf<LocalDate, DayCompletionStatus>()

        // Group events by date
        val tz = TimeZone.currentSystemDefault()
        val eventsByDate = events.groupBy { event ->
            Instant.fromEpochMilliseconds(event.scheduledTime)
                .toLocalDateTime(tz)
                .date
        }

        // Calculate completion status for each date in range
        var currentDate = startDate
        while (currentDate <= endDate) {
            val eventsForDate = eventsByDate[currentDate] ?: emptyList()

            val taken = eventsForDate.count { it.isTaken }
            val total = eventsForDate.size

            map[currentDate] = DayCompletionStatus(
                date = currentDate,
                totalScheduled = total,
                completed = taken, // Only taken counts as completed, not skipped
            )

            currentDate = currentDate.plus(1, DateTimeUnit.DAY)
        }

        return map
    }
}
