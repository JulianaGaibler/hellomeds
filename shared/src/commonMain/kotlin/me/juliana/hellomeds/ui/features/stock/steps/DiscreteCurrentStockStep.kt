// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_discrete_current_subtitle
import me.juliana.hellomeds.shared.stock_discrete_current_title
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.common.ScreenHeader
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.features.stock.components.ContainerStockInput
import me.juliana.hellomeds.ui.util.pluralFormRes
import org.jetbrains.compose.resources.stringResource

/**
 * DISCRETE Step 3: Current stock input
 * If packaging is enabled, shows dual input (full containers + partial units).
 * If packaging is disabled, shows single input for total units.
 */
@Composable
fun DiscreteCurrentStockStep(
    packagingEnabled: Boolean,
    fullContainers: String,
    partialUnits: String,
    onFullContainersChange: (String) -> Unit,
    onPartialUnitsChange: (String) -> Unit,
    medication: Medication,
    packagingQuantity: Double?,
    container: MedicationContainer?,
    modifier: Modifier = Modifier,
) {
    val context = platformContext()
    // Get unit name from medication type
    val stockUnit = stringResource(medication.type.pluralFormRes)

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            headline = stringResource(Res.string.stock_discrete_current_title),
            title = stringResource(Res.string.stock_discrete_current_subtitle),
        )

        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (packagingEnabled) {
                // Dual input with auto-correction
                ContainerStockInput(
                    fullContainers = fullContainers,
                    partialUnits = partialUnits,
                    onFullContainersChange = onFullContainersChange,
                    onPartialUnitsChange = onPartialUnitsChange,
                    medication = medication,
                    packagingQuantity = packagingQuantity,
                    medicationContainer = container,
                )
            } else {
                // Single input for total units
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListTextItem(
                                label = "Current Stock",
                                value = partialUnits,
                                onValueChange = onPartialUnitsChange,
                                suffix = stockUnit,
                                shapes = shapes,
                                visible = visible,
                                inputTransformation = DecimalInputTransformation(),
                            )
                        },
                    ),
                )
            }
        }
    }
}
