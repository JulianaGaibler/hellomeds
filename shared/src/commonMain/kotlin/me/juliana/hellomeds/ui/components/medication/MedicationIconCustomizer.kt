// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.ui.theme.MedicationColor

/**
 * Sticky preview Surface shared by the edit screen and the wizard customize step. Wraps
 * `MedicationIconPreview` in a `surfaceContainer` Surface that hides scrolling content
 * when used inside `LazyColumn.stickyHeader`.
 */
@Composable
fun MedicationIconStickyPreview(
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    color1: MedicationColor?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        MedicationIconPreview(
            foregroundShape = foregroundShape,
            backgroundShape = backgroundShape,
            color1 = color1,
        )
    }
}

/**
 * Icon customizer block (preview + foreground/background shape pickers + color picker).
 * Shared by the edit medication screen's icon section and the add medication wizard's
 * customize mode.
 *
 * Implemented as a plain composable (not a `LazyListScope` extension) so the inner color
 * grid is never composed via lazy-layout paused-composition prefetch — that path crashes
 * inside `ColorCircle` on Compose Multiplatform 1.10.0-beta02.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationIconCustomizer(
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    color1: MedicationColor?,
    onForegroundShapeChange: (MedicationForegroundShape) -> Unit,
    onBackgroundShapeChange: (MedicationBackgroundShape) -> Unit,
    onColor1Change: (MedicationColor?) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showPreview) {
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

        MedicationShapePickers(
            foregroundShape = foregroundShape,
            backgroundShape = backgroundShape,
            onForegroundShapeChange = onForegroundShapeChange,
            onBackgroundShapeChange = onBackgroundShapeChange,
        )

        MedicationColorPickers(
            color1 = color1,
            onColor1Change = onColor1Change,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
