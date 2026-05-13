// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.ui.components.medication.TransitioningMedicationIcon
import me.juliana.hellomeds.ui.theme.MedicationColor

@Composable
actual fun MedicationIconPreviewContent(
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    color1: MedicationColor?,
    size: Dp,
    modifier: Modifier,
) {
    TransitioningMedicationIcon(
        foregroundShape = foregroundShape,
        backgroundShape = backgroundShape,
        color1 = color1,
        size = size,
        modifier = modifier,
    )
}
