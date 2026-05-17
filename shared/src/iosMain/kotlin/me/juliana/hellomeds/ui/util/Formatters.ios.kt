// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.date_format_weekday_date
import me.juliana.hellomeds.shared.date_picker_today_with_date
import me.juliana.hellomeds.shared.date_today
import me.juliana.hellomeds.shared.date_tomorrow
import me.juliana.hellomeds.shared.date_yesterday
import me.juliana.hellomeds.shared.dose_unit_capsule
import me.juliana.hellomeds.shared.dose_unit_cream
import me.juliana.hellomeds.shared.dose_unit_device
import me.juliana.hellomeds.shared.dose_unit_drops
import me.juliana.hellomeds.shared.dose_unit_foam
import me.juliana.hellomeds.shared.dose_unit_gel
import me.juliana.hellomeds.shared.dose_unit_inhaler
import me.juliana.hellomeds.shared.dose_unit_injection
import me.juliana.hellomeds.shared.dose_unit_liquid
import me.juliana.hellomeds.shared.dose_unit_lotion
import me.juliana.hellomeds.shared.dose_unit_ointment
import me.juliana.hellomeds.shared.dose_unit_patch
import me.juliana.hellomeds.shared.dose_unit_powder
import me.juliana.hellomeds.shared.dose_unit_spray
import me.juliana.hellomeds.shared.dose_unit_suppository
import me.juliana.hellomeds.shared.dose_unit_tablet
import me.juliana.hellomeds.shared.dose_unit_topical
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import kotlin.time.Clock
import kotlin.time.Instant

// ── Time Formatting ─────────────────────────────────────────────────────────

@Composable
actual fun formatTime(hour: Int, minute: Int): String {
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

@Composable
actual fun formatTime(time: LocalTime): String {
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

// ── Date Formatting ─────────────────────────────────────────────────────────

private fun localDateToNSDate(date: LocalDate): platform.Foundation.NSDate? {
    val components = NSDateComponents()
    components.year = date.year.toLong()
    components.month = (date.month.ordinal + 1).toLong()
    components.day = date.day.toLong()
    return NSCalendar.currentCalendar.dateFromComponents(components)
}

actual fun formatDate(date: LocalDate): String {
    val nsDate = localDateToNSDate(date) ?: return date.toString()
    val formatter = NSDateFormatter()
    formatter.dateStyle = NSDateFormatterMediumStyle
    formatter.timeStyle = NSDateFormatterNoStyle
    return formatter.stringFromDate(nsDate)
}

actual fun formatDate(millis: Long): String {
    val localDate = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return formatDate(localDate)
}

actual fun formatShortDate(date: LocalDate): String {
    val nsDate = localDateToNSDate(date) ?: return date.toString()
    val formatter = NSDateFormatter()
    formatter.dateStyle = NSDateFormatterShortStyle
    formatter.timeStyle = NSDateFormatterNoStyle
    return formatter.stringFromDate(nsDate)
}

actual fun formatShortDate(millis: Long): String {
    val localDate = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return formatShortDate(localDate)
}

@Composable
actual fun formatDateWithRelativeWeekday(date: LocalDate): String {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val daysDifference = today.daysUntil(date)

    val weekdayOrRelative = when (daysDifference) {
        0 -> stringResource(Res.string.date_today)
        -1 -> stringResource(Res.string.date_yesterday)
        1 -> stringResource(Res.string.date_tomorrow)
        else -> {
            val nsDate = localDateToNSDate(date)
            if (nsDate != null) {
                val formatter = NSDateFormatter()
                formatter.setDateFormat("EEEE")
                formatter.stringFromDate(nsDate)
            } else {
                ""
            }
        }
    }

    val dateString = formatDate(date)
    return stringResource(Res.string.date_format_weekday_date, weekdayOrRelative, dateString)
}

@Composable
actual fun formatDateWithTodayPrefix(date: LocalDate): String {
    val isToday = date == Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val dateString = formatDate(date)
    return if (isToday) {
        stringResource(Res.string.date_picker_today_with_date, dateString)
    } else {
        dateString
    }
}

// ── Number Formatting ───────────────────────────────────────────────────────

actual fun formatDecimal(value: Double): String {
    // Locale-aware grouping for display. NSNumberFormatterDecimalStyle enables
    // usesGroupingSeparator by default and picks the locale's group + decimal
    // separators ("20,000" in en, "20.000" in de).
    val formatter = platform.Foundation.NSNumberFormatter()
    formatter.numberStyle = platform.Foundation.NSNumberFormatterDecimalStyle
    formatter.maximumFractionDigits = 10u
    formatter.minimumFractionDigits = 0u
    return formatter.stringFromNumber(platform.Foundation.NSNumber(double = value))
        ?: value.toString()
}

actual fun formatDecimalPlain(value: Double): String {
    // Round-trip safe via toDoubleOrNull(); used to seed editable text fields.
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    val formatter = platform.Foundation.NSNumberFormatter()
    formatter.numberStyle = platform.Foundation.NSNumberFormatterDecimalStyle
    formatter.maximumFractionDigits = 10u
    formatter.minimumFractionDigits = 0u
    formatter.usesGroupingSeparator = false
    // Force `.` decimal separator regardless of locale.
    formatter.locale = platform.Foundation.NSLocale("en_US_POSIX")
    return formatter.stringFromNumber(platform.Foundation.NSNumber(double = value))
        ?: value.toString()
}

// ── Medication Formatting ───────────────────────────────────────────────────

@Composable
actual fun formatMedicationTypeAndStrength(medication: Medication): String {
    val typeString = stringResource(medication.type.displayNameRes)
    val strengthValue = medication.strengthValue
    val strengthUnit = medication.strengthUnit
    return if (strengthValue != null && strengthUnit != null) {
        // Localize the unit ("IU" → "IE" in de) via the existing displayNameRes mapping.
        val unitString = stringResource(strengthUnit.displayNameRes)
        "$typeString, ${formatDecimal(strengthValue)}$unitString"
    } else {
        typeString
    }
}

// ── Schedule Formatting ─────────────────────────────────────────────────────

@Composable
actual fun formatScheduleTitle(schedule: Schedule): String {
    val time = LocalTime.parse(schedule.timeOfDay)
    return "Schedule at ${formatTime(time)}"
}

@Composable
actual fun formatScheduleEndDate(schedule: Schedule): String? {
    return schedule.endDate?.let { formatDate(it) }
}

@Composable
actual fun formatDoseText(schedule: Schedule, medicationType: MedicationType): String {
    val doseUnitRes = getDoseUnitPluralRes(medicationType)
    val qty = schedule.dose.toInt()
    val unitString = pluralStringResource(doseUnitRes, qty)
    return "${formatDecimal(schedule.dose)} $unitString"
}

// ── Log Event Formatting ────────────────────────────────────────────────────

@Composable
actual fun formatLogEventDose(event: ProjectedEvent, medicationType: MedicationType): String {
    val dose = event.historyRecord?.actualDose ?: event.dose
    val doseUnitRes = getDoseUnitPluralRes(medicationType)
    val qty = dose.toInt()
    val unitString = pluralStringResource(doseUnitRes, qty)
    return "${formatDecimal(dose)} $unitString"
}

@Composable
actual fun formatLogEventTime(event: ProjectedEvent): String {
    val timeMillis = event.historyRecord?.takenTime ?: event.scheduledTime
    val time = Instant.fromEpochMilliseconds(timeMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).time
    return formatTime(time)
}

// ── Dose Unit Plurals ───────────────────────────────────────────────────────

actual fun getDoseUnitPluralRes(medicationType: MedicationType): PluralStringResource {
    return when (medicationType) {
        MedicationType.CAPSULE -> Res.plurals.dose_unit_capsule
        MedicationType.TABLET -> Res.plurals.dose_unit_tablet
        MedicationType.LIQUID -> Res.plurals.dose_unit_liquid
        MedicationType.TOPICAL -> Res.plurals.dose_unit_topical
        MedicationType.CREAM -> Res.plurals.dose_unit_cream
        MedicationType.DEVICE -> Res.plurals.dose_unit_device
        MedicationType.DROPS -> Res.plurals.dose_unit_drops
        MedicationType.FOAM -> Res.plurals.dose_unit_foam
        MedicationType.GEL -> Res.plurals.dose_unit_gel
        MedicationType.INHALER -> Res.plurals.dose_unit_inhaler
        MedicationType.INJECTION -> Res.plurals.dose_unit_injection
        MedicationType.LOTION -> Res.plurals.dose_unit_lotion
        MedicationType.OINTMENT -> Res.plurals.dose_unit_ointment
        MedicationType.PATCH -> Res.plurals.dose_unit_patch
        MedicationType.POWDER -> Res.plurals.dose_unit_powder
        MedicationType.SPRAY -> Res.plurals.dose_unit_spray
        MedicationType.SUPPOSITORY -> Res.plurals.dose_unit_suppository
    }
}

// ── Platform Utilities ──────────────────────────────────────────────────────

@Composable
actual fun is24HourFormat(): Boolean {
    // The "j" template asks NSDateFormatter for the locale's preferred hour
    // pattern. A 12-hour locale (or a user who toggled off "24-Hour Time" in
    // iOS Settings → General → Date & Time) returns a pattern containing "a"
    // (the AM/PM marker).
    val pattern = NSDateFormatter.dateFormatFromTemplate(
        tmplate = "j",
        options = 0u,
        locale = NSLocale.currentLocale,
    ) ?: return true
    return !pattern.contains("a")
}
