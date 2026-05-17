// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.graph.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.ui.components.graph.models.ZoomLevel
import kotlin.time.Instant

/**
 * Formats timestamps for display on the X-axis of the graph.
 * Uses kotlinx-datetime for KMP-compatible date/time handling.
 */
object TimeFormatter {

    /**
     * Format a timestamp based on the current zoom level.
     *
     * @param timestamp Unix timestamp in milliseconds
     * @param zoomLevel Current zoom level determining format granularity
     * @return Formatted time string
     */
    fun format(timestamp: Long, zoomLevel: ZoomLevel): String {
        val dateTime = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        return when (zoomLevel) {
            ZoomLevel.DAY -> {
                val hour = dateTime.hour
                val minute = dateTime.minute
                val amPm = if (hour < 12) "AM" else "PM"
                val h = if (hour == 0) {
                    12
                } else if (hour > 12) {
                    hour - 12
                } else {
                    hour
                }
                "$h:${minute.toString().padStart(2, '0')} $amPm"
            }

            ZoomLevel.WEEK -> {
                dateTime.dayOfWeek.name.take(3).lowercase()
                    .replaceFirstChar { it.uppercase() }
            }

            ZoomLevel.MONTH, ZoomLevel.QUARTER -> {
                "${monthAbbr(dateTime.month.ordinal + 1)} ${dateTime.day}"
            }

            ZoomLevel.YEAR -> {
                monthAbbr(dateTime.month.ordinal + 1)
            }
        }
    }

    /**
     * Format a timestamp for detailed tooltips or markers.
     * Shows full date and time.
     */
    fun formatDetailed(timestamp: Long): String {
        val dt = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = dt.hour
        val minute = dt.minute
        val amPm = if (hour < 12) "AM" else "PM"
        val h = if (hour == 0) {
            12
        } else if (hour > 12) {
            hour - 12
        } else {
            hour
        }
        return "${monthAbbr(dt.month.ordinal + 1)} ${dt.day}, ${dt.year} at $h:${
            minute.toString().padStart(2, '0')
        } $amPm"
    }

    /**
     * Format a timestamp for short display.
     * Shows just the date.
     */
    fun formatShort(timestamp: Long): String {
        val dt = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return "${monthAbbr(dt.month.ordinal + 1)} ${dt.day}"
    }

    private fun monthAbbr(month: Int): String = when (month) {
        1 -> "Jan"
        2 -> "Feb"
        3 -> "Mar"
        4 -> "Apr"
        5 -> "May"
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Aug"
        9 -> "Sep"
        10 -> "Oct"
        11 -> "Nov"
        12 -> "Dec"
        else -> ""
    }

    fun getLabelInterval(zoomLevel: ZoomLevel): Long {
        return when (zoomLevel) {
            ZoomLevel.DAY -> 3 * 60 * 60 * 1000L // Every 3 hours
            ZoomLevel.WEEK -> 1 * 24 * 60 * 60 * 1000L // Every day
            ZoomLevel.MONTH -> 5 * 24 * 60 * 60 * 1000L // Every 5 days
            ZoomLevel.QUARTER -> 15 * 24 * 60 * 60 * 1000L // Every 15 days
            ZoomLevel.YEAR -> 30 * 24 * 60 * 60 * 1000L // Every month (~30 days)
        }
    }

    /**
     * Calculate timestamp positions for X-axis labels within the visible range.
     */
    fun calculateLabelPositions(visibleTimeRange: LongRange, zoomLevel: ZoomLevel): List<Long> {
        val interval = getLabelInterval(zoomLevel)
        val result = mutableListOf<Long>()

        // Start from the first aligned timestamp after range start
        var currentTime = ((visibleTimeRange.first / interval) + 1) * interval

        // For WEEK view: shift from midnight to noon to match data point alignment
        val offset = if (zoomLevel == ZoomLevel.WEEK) interval / 2 else 0L

        while (currentTime <= visibleTimeRange.last) {
            result.add(currentTime + offset)
            currentTime += interval
        }

        return result
    }
}
