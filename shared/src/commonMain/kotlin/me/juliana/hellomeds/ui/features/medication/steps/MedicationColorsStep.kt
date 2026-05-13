// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.wizard_medication_colors_headline
import me.juliana.hellomeds.shared.wizard_medication_colors_title
import me.juliana.hellomeds.ui.components.medication.MedicationColorPickers
import me.juliana.hellomeds.ui.components.medication.MedicationIconPreview
import me.juliana.hellomeds.ui.theme.MedicationColor
import org.jetbrains.compose.resources.stringResource

/**
 * Step 5: Select medication color (single color pick; duo-tone complement is automatic white)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicationColorsStep(
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    color1: MedicationColor?,
    onColor1Change: (MedicationColor?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                headline = stringResource(Res.string.wizard_medication_colors_headline),
                title = stringResource(Res.string.wizard_medication_colors_title),
            )
        }

        stickyHeader {
            // Sticky preview with transition animation
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                MedicationIconPreview(
                    foregroundShape = foregroundShape,
                    backgroundShape = backgroundShape,
                    color1 = color1,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                MedicationColorPickers(
                    color1 = color1,
                    onColor1Change = onColor1Change,
                )
            }
        }
    }
}
