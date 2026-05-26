// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.util.pluralFormRes
import org.jetbrains.compose.resources.stringResource

/**
 * Inputs for discrete (exact-count) stock. Dual full-containers + partial-units
 * when packaging is configured; otherwise a single total-stock field.
 *
 * Shared by the Add Stock Tracking wizard, the Update Stock sheet, and the
 * Top Up sheet. Callers render their own total summary alongside.
 */
@Composable
fun DiscreteStockInput(
    packagingEnabled: Boolean,
    fullContainers: String,
    partialUnits: String,
    onFullContainersChange: (String) -> Unit,
    onPartialUnitsChange: (String) -> Unit,
    medication: Medication,
    packagingQuantity: Double?,
    container: MedicationContainer?,
    modifier: Modifier = Modifier,
    singleInputLabel: String = "Current Stock",
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (packagingEnabled) {
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
            val stockUnit = stringResource(medication.type.pluralFormRes)
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListTextItem(
                            label = singleInputLabel,
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
