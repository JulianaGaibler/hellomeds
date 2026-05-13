// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.MedicationType
import org.jetbrains.compose.resources.PluralStringResource

// ── Time Formatting ─────────────────────────────────────────────────────────

@Composable
expect fun formatTime(hour: Int, minute: Int): String

@Composable
expect fun formatTime(time: LocalTime): String

// ── Date Formatting ─────────────────────────────────────────────────────────

expect fun formatDate(date: LocalDate): String

expect fun formatDate(millis: Long): String

expect fun formatShortDate(date: LocalDate): String

expect fun formatShortDate(millis: Long): String

@Composable
expect fun formatDateWithRelativeWeekday(date: LocalDate): String

@Composable
expect fun formatDateWithTodayPrefix(date: LocalDate): String

// ── Number Formatting ───────────────────────────────────────────────────────

expect fun formatDecimal(value: Double): String

// ── Medication Formatting ───────────────────────────────────────────────────

@Composable
expect fun formatMedicationTypeAndStrength(medication: Medication): String

// ── Schedule Formatting ─────────────────────────────────────────────────────

@Composable
expect fun formatScheduleTitle(schedule: Schedule): String

@Composable
expect fun formatScheduleEndDate(schedule: Schedule): String?

@Composable
expect fun formatDoseText(schedule: Schedule, medicationType: MedicationType): String

// ── Log Event Formatting ────────────────────────────────────────────────────

@Composable
expect fun formatLogEventDose(event: ProjectedEvent, medicationType: MedicationType): String

@Composable
expect fun formatLogEventTime(event: ProjectedEvent): String

// ── Importance Label Formatting ─────────────────────────────────────────────

@Composable
expect fun formatImportanceLabelDescription(label: ImportanceLabel): String

// ── Dose Unit Plurals ───────────────────────────────────────────────────────

expect fun getDoseUnitPluralRes(medicationType: MedicationType): PluralStringResource

// ── Platform Utilities ──────────────────────────────────────────────────────

/**
 * Returns true if the platform uses 24-hour time format.
 */
@Composable
expect fun is24HourFormat(): Boolean
