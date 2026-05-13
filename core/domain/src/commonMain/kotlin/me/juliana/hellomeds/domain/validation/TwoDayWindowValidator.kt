// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.validation

import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.util.AppLogger
import kotlin.time.Clock

/**
 * Validates whether events are within the 2-day window for logging operations.
 *
 * Rules:
 * - Events <= 2 days from now: Can be logged/modified
 * - Events > 2 days from now: Blocked (cannot log in advance)
 * - As-needed logs (null scheduledTime): Always allowed
 */
object TwoDayWindowValidator {

    private const val TAG = "TwoDayWindowValidator"
    private const val TWO_DAYS_MS = 2 * 24 * 60 * 60 * 1000L

    fun isWithinWindow(scheduledTime: Long?, currentTime: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        if (scheduledTime == null) return true // Allow operations on as-needed logs

        val twoDaysFromNow = currentTime + TWO_DAYS_MS

        return scheduledTime <= twoDaysFromNow
    }

    fun validate(event: ProjectedEvent, operation: String): Boolean {
        if (!isWithinWindow(event.scheduledTime)) {
            AppLogger.d(
                TAG,
                "Blocked $operation for event schedule=${event.scheduleId}: scheduled time is >2 days in future",
            )
            return false
        }
        return true
    }
}
