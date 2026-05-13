// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.FrequencyType
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
import me.juliana.hellomeds.shared.importance_label_becomes_critical
import me.juliana.hellomeds.shared.importance_label_critical
import me.juliana.hellomeds.shared.importance_label_follow_ups_plural
import me.juliana.hellomeds.shared.importance_label_no_reminders
import me.juliana.hellomeds.shared.importance_label_reminds_once
import me.juliana.hellomeds.shared.medication_type_strength_format
import me.juliana.hellomeds.shared.schedule_ended_on
import me.juliana.hellomeds.shared.schedule_ends_on
import me.juliana.hellomeds.shared.schedule_every_day
import me.juliana.hellomeds.shared.schedule_every_n_days_plural
import me.juliana.hellomeds.shared.schedule_frequency_at_time
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

// ── Time Formatting ─────────────────────────────────────────────────────────

@Composable
actual fun formatTime(hour: Int, minute: Int): String {
    return formatTime(LocalTime(hour, minute))
}

@Composable
actual fun formatTime(time: LocalTime): String {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    return time.toJavaLocalTime().format(formatter)
}

// ── Date Formatting ─────────────────────────────────────────────────────────

actual fun formatDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
    return date.toJavaLocalDate().format(formatter)
}

actual fun formatDate(millis: Long): String {
    val localDate = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return formatDate(localDate)
}

actual fun formatShortDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
    return date.toJavaLocalDate().format(formatter)
}

actual fun formatShortDate(millis: Long): String {
    val localDate = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return formatShortDate(localDate)
}

@Composable
actual fun formatDateWithRelativeWeekday(date: LocalDate): String {
    val context = LocalContext.current
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val daysDifference = today.daysUntil(date)

    val weekdayOrRelative = when (daysDifference) {
        0 -> stringResource(Res.string.date_today)
        -1 -> stringResource(Res.string.date_yesterday)
        1 -> stringResource(Res.string.date_tomorrow)
        else -> {
            val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
            date.toJavaLocalDate().format(weekdayFormatter)
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
    if (value == value.toLong().toDouble()) {
        return value.toLong().toString()
    }
    val symbols = DecimalFormatSymbols(Locale.getDefault())
    val formatter = DecimalFormat("#.##########", symbols)
    return formatter.format(value)
}

// ── Medication Formatting ───────────────────────────────────────────────────

@Composable
actual fun formatMedicationTypeAndStrength(medication: Medication): String {
    val typeString = stringResource(medication.type.displayNameRes)

    val strengthValue = medication.strengthValue
    val strengthUnit = medication.strengthUnit
    return if (strengthValue != null && strengthUnit != null) {
        val formattedValue = formatDecimal(strengthValue)
        val strengthFormatted = "$formattedValue${strengthUnit.value}"
        stringResource(Res.string.medication_type_strength_format, typeString, strengthFormatted)
    } else {
        typeString
    }
}

// ── Schedule Formatting ─────────────────────────────────────────────────────

@Composable
actual fun formatScheduleTitle(schedule: Schedule): String {
    val frequencyText = formatFrequencyTextComposable(schedule)
    val time = LocalTime.parse(schedule.timeOfDay)
    val timeText = formatTime(time)
    return stringResource(Res.string.schedule_frequency_at_time, frequencyText, timeText)
}

@Composable
private fun formatFrequencyTextComposable(schedule: Schedule): String {
    return when (schedule.frequencyType) {
        FrequencyType.INTERVAL -> {
            pluralStringResource(
                Res.plurals.schedule_every_n_days_plural,
                schedule.frequencyValue,
                schedule.frequencyValue,
            )
        }

        FrequencyType.DAYS_OF_WEEK -> {
            val days = schedule.daysOfWeek?.split(",") ?: emptyList()
            if (days.size == 7) {
                stringResource(Res.string.schedule_every_day)
            } else {
                formatDaysOfWeekLocalized(days)
            }
        }
    }
}

private fun formatDaysOfWeekLocalized(days: List<String>): String {
    val locale = Locale.getDefault()
    val dayNames = days.mapNotNull { day ->
        try {
            java.time.DayOfWeek.valueOf(day.trim())
                .getDisplayName(TextStyle.SHORT, locale)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    return dayNames.joinToString(", ")
}

@Composable
actual fun formatScheduleEndDate(schedule: Schedule): String? {
    return schedule.endDate?.let { endMillis ->
        val endLocalDate = Instant.fromEpochMilliseconds(endMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val formattedDate = formatDate(endLocalDate)
        val isPast = endLocalDate < Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date

        if (isPast) {
            stringResource(Res.string.schedule_ended_on, formattedDate)
        } else {
            stringResource(Res.string.schedule_ends_on, formattedDate)
        }
    }
}

@Composable
actual fun formatDoseText(schedule: Schedule, medicationType: MedicationType): String {
    val doseUnitRes = getDoseUnitPluralRes(medicationType)
    val doseQuantity = schedule.dose.toInt()

    return if (schedule.dose == doseQuantity.toDouble()) {
        val quantityString = doseQuantity.toString()
        val unitString = pluralStringResource(doseUnitRes, doseQuantity)
        "$quantityString $unitString"
    } else {
        val quantityString = formatDecimal(schedule.dose)
        val pluralCategory = if (schedule.dose > 1.5) 2 else 1
        val unitString = pluralStringResource(doseUnitRes, pluralCategory)
        "$quantityString $unitString"
    }
}

// ── Log Event Formatting ────────────────────────────────────────────────────

@Composable
actual fun formatLogEventDose(event: ProjectedEvent, medicationType: MedicationType): String {
    val dose = event.historyRecord?.actualDose ?: event.dose
    val doseUnitRes = getDoseUnitPluralRes(medicationType)
    val doseQuantity = dose.toInt()

    return if (dose == doseQuantity.toDouble()) {
        val quantityString = doseQuantity.toString()
        val unitString = pluralStringResource(doseUnitRes, doseQuantity)
        "$quantityString $unitString"
    } else {
        val quantityString = formatDecimal(dose)
        val pluralCategory = if (dose > 1.5) 2 else 1
        val unitString = pluralStringResource(doseUnitRes, pluralCategory)
        "$quantityString $unitString"
    }
}

@Composable
actual fun formatLogEventTime(event: ProjectedEvent): String {
    val timeMillis = event.historyRecord?.takenTime ?: event.scheduledTime
    val time = Instant.fromEpochMilliseconds(timeMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).time
    return formatTime(time)
}

// ── Importance Label Formatting ─────────────────────────────────────────────

@Composable
actual fun formatImportanceLabelDescription(label: ImportanceLabel): String {
    if (!label.shouldRemind) {
        return stringResource(Res.string.importance_label_no_reminders)
    }

    val reminderParts = mutableListOf<String>()

    if (label.isCritical) {
        reminderParts.add(stringResource(Res.string.importance_label_critical))
    }

    if (!label.hasFollowUps) {
        reminderParts.add(stringResource(Res.string.importance_label_reminds_once))
    } else {
        reminderParts.add(
            pluralStringResource(
                Res.plurals.importance_label_follow_ups_plural,
                label.followUpCount,
                label.followUpCount,
            ),
        )
    }

    if (label.criticalAfterFollowUp != null && !label.isCritical) {
        reminderParts.add(
            stringResource(
                Res.string.importance_label_becomes_critical,
                label.criticalAfterFollowUp!!,
            ),
        )
    }

    return reminderParts.joinToString(", ")
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

@Composable
actual fun is24HourFormat(): Boolean {
    val context = LocalContext.current
    return DateFormat.is24HourFormat(context)
}
