// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.ui.theme.MedicationColor

/**
 * State holder for the Add Medication flow
 */
data class AddMedicationState(
    val name: String = "",
    val type: MedicationType = MedicationType.TABLET,
    val strengthValue: String = "",
    val strengthUnit: MedicationStrengthUnit = MedicationStrengthUnit.MG,
    val foregroundShape: MedicationForegroundShape = MedicationForegroundShape.CAPSULE_PILL,
    val backgroundShape: MedicationBackgroundShape = MedicationBackgroundShape.CIRCLE,
    val color1: MedicationColor? = null,
    val importanceLabelId: Int? = null,
    // Cycle / blister pack
    val cycleEnabled: Boolean = false,
    val cycleDaysActive: Int = 21,
    val cycleDaysBreak: Int = 7,
    val cycleHasPlacebos: Boolean = false,
    val cycleDayInCycle: Int = 1,
    // Timezone handling
    val timeZoneMode: TimeZoneMode = TimeZoneMode.LOCAL,
    val anchorTimeZone: String? = null,
    // Camera detection data
    val detectedNames: List<String> = emptyList(),
    val detectedTypes: List<MedicationType> = emptyList(),
    val detectedStrengthValue: Double? = null,
    val detectedStrengthUnit: MedicationStrengthUnit? = null,
)

/**
 * Converts detection result to AddMedicationState using top suggestions
 */
fun MedicationDetectionResult.toAddMedicationState(): AddMedicationState {
    val topType = typeSuggestions.firstOrNull() ?: MedicationType.CAPSULE
    val strength = strengthSuggestion

    return AddMedicationState(
        name = "",
        type = topType,
        strengthValue = strength?.value?.toString() ?: "",
        strengthUnit = strength?.unit ?: MedicationStrengthUnit.MG,
        // Detection data for user to choose from
        detectedNames = nameSuggestions,
        detectedTypes = typeSuggestions,
        detectedStrengthValue = strength?.value,
        detectedStrengthUnit = strength?.unit,
    )
}
