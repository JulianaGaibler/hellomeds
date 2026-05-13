// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import kotlinx.datetime.TimeZone
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.enums.TimeZoneMode

/**
 * Resolves the effective TimeZone for a schedule based on the medication's timezone mode.
 *
 * - FIXED mode with a valid originTimeZone → uses the origin timezone (absolute timing)
 * - LOCAL mode or missing data → uses system default (wall-clock timing)
 */
object ScheduleTimeZoneResolver {

    fun resolve(medication: Medication?, schedule: Schedule): TimeZone {
        if (medication?.timeZoneMode == TimeZoneMode.FIXED) {
            // Prefer medication-level anchor, fall back to schedule-level origin
            val tzId = medication.anchorTimeZone ?: schedule.originTimeZone
            if (tzId != null) {
                return runCatching { TimeZone.of(tzId) }.getOrNull()
                    ?: TimeZone.currentSystemDefault()
            }
        }
        return TimeZone.currentSystemDefault()
    }
}
