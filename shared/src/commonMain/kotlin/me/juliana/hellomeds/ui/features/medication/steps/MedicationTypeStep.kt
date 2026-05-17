// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.wizard_medication_type_headline
import me.juliana.hellomeds.shared.wizard_medication_type_title
import me.juliana.hellomeds.ui.components.common.ScreenHeader
import me.juliana.hellomeds.ui.components.medication.MedicationTypeSelector
import org.jetbrains.compose.resources.stringResource

/**
 * Step 2: Select medication type
 */
@Composable
internal fun MedicationTypeStep(
    selectedType: MedicationType,
    onTypeSelected: (MedicationType) -> Unit,
    detectedTypes: List<MedicationType> = emptyList(),
) {
    // Default select first detected type if available on initial composition
    LaunchedEffect(Unit) {
        if (detectedTypes.isNotEmpty()) {
            onTypeSelected(detectedTypes.first())
        }
    }

    Column {
        ScreenHeader(
            headline = stringResource(Res.string.wizard_medication_type_headline),
            title = stringResource(Res.string.wizard_medication_type_title),
        )

        MedicationTypeSelector(
            selectedType = selectedType,
            onTypeSelected = onTypeSelected,
            detectedTypes = detectedTypes,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
