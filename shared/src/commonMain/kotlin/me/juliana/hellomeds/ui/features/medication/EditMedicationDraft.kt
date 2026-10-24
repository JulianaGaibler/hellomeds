// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.data.model.getCycleDay
import me.juliana.hellomeds.domain.validation.MedicationValidation
import me.juliana.hellomeds.ui.theme.MedicationColor
import me.juliana.hellomeds.ui.util.formatDecimalPlain

/**
 * Unsaved edits to a medication, shared by the edit screen and its subpages.
 *
 * It is built once from the medication as it was when editing started, so background writes that
 * re-emit the row don't overwrite edits in progress.
 */
class EditMedicationDraft(val medication: Medication, private val today: LocalDate) {
    var name by mutableStateOf(medication.name)
    var displayName by mutableStateOf(medication.displayName ?: "")
    var type by mutableStateOf(medication.type)
    var hasStrength by mutableStateOf(medication.strengthValue != null)
    var strengthValue by mutableStateOf(medication.strengthValue?.let { formatDecimalPlain(it) } ?: "")
    var strengthUnit by mutableStateOf(medication.strengthUnit ?: MedicationStrengthUnit.MG)

    var foregroundShape by mutableStateOf(
        MedicationForegroundShape.fromNameOrDefault(
            medication.foregroundShape,
            MedicationForegroundShape.CAPSULE_PILL,
        ),
    )
    var backgroundShape by mutableStateOf(MedicationBackgroundShape.fromNameOrDefault(medication.backgroundShape))
    var color1 by mutableStateOf(medication.shapeColor?.let { MedicationColor.fromName(it) })

    var timeZoneMode by mutableStateOf(medication.timeZoneMode)
    var anchorTimeZone by mutableStateOf(medication.anchorTimeZone)
    var unloggedDoseAction by mutableStateOf(medication.unloggedDoseAction)

    var cycleEnabled by mutableStateOf(medication.cycleType == CycleType.CYCLIC)
    var cycleDaysActive by mutableIntStateOf(medication.cycleDaysActive ?: 21)
    var cycleDaysBreak by mutableIntStateOf(medication.cycleDaysBreak ?: 7)
    var cycleHasPlacebos by mutableStateOf(medication.cycleHasPlacebos)
    var cycleDayInCycle by mutableIntStateOf(
        if (medication.cycleType == CycleType.CYCLIC) {
            getCycleDay(medication, today)?.let { it.dayInCycle + 1 } ?: 1
        } else {
            1
        },
    )

    val nameError: String? get() = MedicationValidation.validateMedicationName(name)
    val strengthError: String? get() = MedicationValidation.validateStrengthValue(strengthValue, hasStrength)

    val canSave: Boolean
        get() {
            val cycleLength = cycleDaysActive + cycleDaysBreak
            val cycleValid = !cycleEnabled ||
                (cycleDaysActive > 0 && cycleDayInCycle in 1..cycleLength && cycleLength <= 365)
            return cycleValid && nameError == null && strengthError == null
        }

    fun setTimeZoneMode(mode: TimeZoneMode, currentTimeZoneId: String) {
        timeZoneMode = mode
        if (mode == TimeZoneMode.FIXED && anchorTimeZone == null) {
            anchorTimeZone = currentTimeZoneId
        }
    }

    /** Applies the edits to [latest], so stock and alert fields written meanwhile are kept. */
    fun applyTo(latest: Medication): Medication = latest.copy(
        name = name.trim(),
        type = type,
        strengthValue = if (hasStrength) strengthValue.toDoubleOrNull() else null,
        strengthUnit = if (hasStrength) strengthUnit else null,
        displayName = displayName.trim().ifBlank { null },
        foregroundShape = foregroundShape.name,
        backgroundShape = backgroundShape.name,
        shapeColor = color1?.name,
        cycleType = if (cycleEnabled) CycleType.CYCLIC else CycleType.NONE,
        cycleDaysActive = if (cycleEnabled) cycleDaysActive else null,
        cycleDaysBreak = if (cycleEnabled) cycleDaysBreak else null,
        cycleHasPlacebos = cycleEnabled && cycleHasPlacebos,
        cycleStartDate = if (cycleEnabled) today.minus(cycleDayInCycle - 1, DateTimeUnit.DAY) else null,
        timeZoneMode = timeZoneMode,
        anchorTimeZone = if (timeZoneMode == TimeZoneMode.FIXED) anchorTimeZone else null,
        unloggedDoseAction = unloggedDoseAction,
    )
}
