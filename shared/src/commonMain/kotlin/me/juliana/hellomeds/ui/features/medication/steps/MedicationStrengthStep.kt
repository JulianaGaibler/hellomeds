// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.camera_detection_from_camera
import me.juliana.hellomeds.shared.camera_detection_strength_warning
import me.juliana.hellomeds.shared.medication_skip_step
import me.juliana.hellomeds.shared.wizard_medication_strength_headline
import me.juliana.hellomeds.shared.wizard_medication_strength_title
import me.juliana.hellomeds.ui.components.common.ScreenHeader
import me.juliana.hellomeds.ui.components.medication.StrengthInputFields
import org.jetbrains.compose.resources.stringResource

/**
 * Step 3: Enter medication strength
 */
@Composable
internal fun MedicationStrengthStep(
    strengthValue: String,
    strengthUnit: MedicationStrengthUnit,
    onStrengthValueChange: (String) -> Unit,
    onStrengthUnitChange: (MedicationStrengthUnit) -> Unit,
    onSkip: () -> Unit,
    detectedStrengthValue: Double? = null,
    detectedStrengthUnit: MedicationStrengthUnit? = null,
) {
    // Pre-fill detected strength values
    LaunchedEffect(detectedStrengthValue, detectedStrengthUnit) {
        if (detectedStrengthValue != null && detectedStrengthUnit != null && strengthValue.isEmpty()) {
            onStrengthValueChange(detectedStrengthValue.toString())
            onStrengthUnitChange(detectedStrengthUnit)
        }
    }

    Column {
        ScreenHeader(
            headline = stringResource(Res.string.wizard_medication_strength_headline),
            title = stringResource(Res.string.wizard_medication_strength_title),
            actionLabel = stringResource(Res.string.medication_skip_step),
            onAction = onSkip,
        )

        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Show info box if detected strength is available
            if (detectedStrengthValue != null && detectedStrengthUnit != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.camera_detection_from_camera),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = stringResource(Res.string.camera_detection_strength_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            StrengthInputFields(
                strengthValue = strengthValue,
                strengthUnit = strengthUnit,
                onStrengthValueChange = onStrengthValueChange,
                onStrengthUnitChange = onStrengthUnitChange,
            )
        }
    }
}
