// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.ui.components.common.ScreenHeader
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.wizard_medication_icon_customize
import me.juliana.hellomeds.shared.wizard_medication_icon_customize_headline
import me.juliana.hellomeds.shared.wizard_medication_icon_customize_title
import me.juliana.hellomeds.shared.wizard_medication_icon_headline
import me.juliana.hellomeds.shared.wizard_medication_icon_title
import me.juliana.hellomeds.shared.wizard_medication_icon_use_preset
import me.juliana.hellomeds.ui.components.medication.MedicationIconCustomizer
import me.juliana.hellomeds.ui.components.medication.MedicationIconStickyPreview
import me.juliana.hellomeds.ui.components.medication.MedicationShapeIcon
import me.juliana.hellomeds.ui.features.medication.MedicationIconPreset
import me.juliana.hellomeds.ui.features.medication.MedicationIconPresets
import me.juliana.hellomeds.ui.theme.MedicationColor
import org.jetbrains.compose.resources.stringResource

/**
 * Step 5: Pick a curated icon preset, or drop into the full customizer.
 *
 * @param customizing Hoisted to the parent so the wizard's back handlers can pop the customizer
 *   first before stepping backwards through the wizard.
 * @param onIconChange Atomic update for all three icon dimensions — a preset tap mutates fg, bg,
 *   and color in one shot to avoid intermediate mismatched triples.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun MedicationIconStep(
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    color1: MedicationColor?,
    customizing: Boolean,
    onCustomizingChange: (Boolean) -> Unit,
    onIconChange: (MedicationForegroundShape, MedicationBackgroundShape, MedicationColor?) -> Unit,
) {
    if (customizing) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeader(
                    headline = stringResource(Res.string.wizard_medication_icon_customize_headline),
                    title = stringResource(Res.string.wizard_medication_icon_customize_title),
                    contentPadding = PaddingValues(vertical = 48.dp),
                )
            }

            stickyHeader {
                MedicationIconStickyPreview(
                    foregroundShape = foregroundShape,
                    backgroundShape = backgroundShape,
                    color1 = color1,
                )
            }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(onClick = { onCustomizingChange(false) }) {
                        Text(stringResource(Res.string.wizard_medication_icon_use_preset))
                    }
                }
            }

            item {
                MedicationIconCustomizer(
                    foregroundShape = foregroundShape,
                    backgroundShape = backgroundShape,
                    color1 = color1,
                    onForegroundShapeChange = { onIconChange(it, backgroundShape, color1) },
                    onBackgroundShapeChange = { onIconChange(foregroundShape, it, color1) },
                    onColor1Change = { onIconChange(foregroundShape, backgroundShape, it) },
                    showPreview = false,
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ScreenHeader(
                    headline = stringResource(Res.string.wizard_medication_icon_headline),
                    title = stringResource(Res.string.wizard_medication_icon_title),
                    contentPadding = PaddingValues(vertical = 48.dp),
                )
            }

            items(MedicationIconPresets.size) { index ->
                val preset = MedicationIconPresets[index]
                IconPresetTile(
                    preset = preset,
                    selected = foregroundShape == preset.foregroundShape &&
                        backgroundShape == preset.backgroundShape &&
                        color1 == preset.color,
                    onClick = {
                        onIconChange(preset.foregroundShape, preset.backgroundShape, preset.color)
                    },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(onClick = { onCustomizingChange(true) }) {
                        Text(stringResource(Res.string.wizard_medication_icon_customize))
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPresetTile(preset: MedicationIconPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 4.dp else 0.dp,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MedicationShapeIcon(
                    foregroundShape = preset.foregroundShape,
                    backgroundShape = preset.backgroundShape,
                    color1 = preset.color,
                    size = 64.dp,
                )
            }
        }
    }
}
