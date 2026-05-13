// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model

import me.juliana.hellomeds.data.database.entities.Medication

/**
 * Combines a ProjectedEvent with its associated Medication for display purposes.
 * Used in tracking screens to show both event details and medication information.
 */
data class ProjectedEventWithMedication(
    val event: ProjectedEvent,
    val medication: Medication,
)

/**
 * All events for a tracking day, split by status.
 * [overdue] contains pending events from the previous 24h (carry-forward),
 * shown only when viewing the current day.
 */
data class TrackingDayEvents(
    val pending: List<ProjectedEventWithMedication>,
    val taken: List<ProjectedEventWithMedication>,
    val skipped: List<ProjectedEventWithMedication>,
    val overdue: List<ProjectedEventWithMedication> = emptyList(),
)
