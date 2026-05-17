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
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.importance_desc_follow_ups
import me.juliana.hellomeds.shared.importance_desc_modifier_alarm
import me.juliana.hellomeds.shared.importance_desc_modifier_alarm_after
import me.juliana.hellomeds.shared.importance_desc_modifier_critical
import me.juliana.hellomeds.shared.importance_desc_modifier_critical_after
import me.juliana.hellomeds.shared.importance_desc_no_reminders
import me.juliana.hellomeds.shared.importance_desc_remind_once
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// ── Time Formatting ─────────────────────────────────────────────────────────

@Composable
expect fun formatTime(time: LocalTime): String

// ── Date Formatting ─────────────────────────────────────────────────────────

expect fun formatDate(date: LocalDate): String

expect fun formatDate(millis: Long): String

expect fun formatShortDate(date: LocalDate): String

@Composable
expect fun formatDateWithRelativeWeekday(date: LocalDate): String

@Composable
expect fun formatDateWithTodayPrefix(date: LocalDate): String

// ── Number Formatting ───────────────────────────────────────────────────────

/**
 * Locale-aware decimal formatter for **display** contexts. Includes a thousands
 * separator (`20,000` in en, `20.000` in de) and the locale's decimal point.
 *
 * Do NOT use this to seed editable text fields — the grouping separator will
 * break `toDoubleOrNull` round-trips. Use [formatDecimalPlain] for that.
 */
expect fun formatDecimal(value: Double): String

/**
 * Plain decimal serialization without grouping separators. Always uses `.` as
 * the decimal point so the result round-trips through `toDoubleOrNull()`.
 *
 * Use this when seeding text fields with a stored number value.
 */
expect fun formatDecimalPlain(value: Double): String

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

/**
 * Render the description shown under a reminder type. Returns one complete
 * base sentence (no-reminders / reminds-once / follow-ups) plus zero or more
 * modifier sentences (critical / alarm / critical-after / alarm-after),
 * joined by a single space.
 *
 * Each translation unit is a full sentence including terminal punctuation,
 * so translators can apply locale-appropriate grammar within each piece
 * without being constrained by a joiner.
 */
@Composable
fun formatImportanceLabelDescription(label: ImportanceLabel): String {
    if (!label.shouldRemind) {
        return stringResource(Res.string.importance_desc_no_reminders)
    }

    val base = if (label.hasFollowUps) {
        pluralStringResource(
            Res.plurals.importance_desc_follow_ups,
            label.followUpCount,
            label.followUpCount,
            label.followUpIntervalMinutes,
        )
    } else {
        stringResource(Res.string.importance_desc_remind_once)
    }

    val modifiers = buildList {
        when {
            label.isAlarm ->
                add(stringResource(Res.string.importance_desc_modifier_alarm))
            label.isCritical ->
                add(stringResource(Res.string.importance_desc_modifier_critical))
        }
        // Escalation modifiers only apply to follow-up chains and only when
        // the label isn't already permanently critical/alarm.
        if (label.hasFollowUps && !label.isAlarm) {
            label.alarmAfterFollowUp?.let {
                add(stringResource(Res.string.importance_desc_modifier_alarm_after, it))
            }
        }
        if (label.hasFollowUps && !label.isCritical && !label.isAlarm) {
            label.criticalAfterFollowUp?.let {
                add(stringResource(Res.string.importance_desc_modifier_critical_after, it))
            }
        }
    }

    return (listOf(base) + modifiers).joinToString(" ")
}

// ── Dose Unit Plurals ───────────────────────────────────────────────────────

expect fun getDoseUnitPluralRes(medicationType: MedicationType): PluralStringResource

// ── Platform Utilities ──────────────────────────────────────────────────────

/**
 * Returns true if the platform uses 24-hour time format.
 */
@Composable
expect fun is24HourFormat(): Boolean
