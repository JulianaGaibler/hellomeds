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
import me.juliana.hellomeds.ui.features.stock.components.DiscreteStockInput
import me.juliana.hellomeds.ui.features.stock.components.StockCurrentTotalSummary
import org.jetbrains.compose.resources.stringResource

/** DISCRETE Step 3: Current stock input. */
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
    platformContext()

    val enteredTotal = if (packagingEnabled && packagingQuantity != null) {
        val full = fullContainers.toIntOrNull() ?: 0
        val partial = partialUnits.toDoubleOrNull() ?: 0.0
        full * packagingQuantity + partial
    } else {
        partialUnits.toDoubleOrNull() ?: 0.0
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            headline = stringResource(Res.string.stock_discrete_current_title),
            title = stringResource(Res.string.stock_discrete_current_subtitle),
        )

        DiscreteStockInput(
            packagingEnabled = packagingEnabled,
            fullContainers = fullContainers,
            partialUnits = partialUnits,
            onFullContainersChange = onFullContainersChange,
            onPartialUnitsChange = onPartialUnitsChange,
            medication = medication,
            packagingQuantity = packagingQuantity,
            container = container,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        StockCurrentTotalSummary(
            medication = medication,
            isEstimated = false,
            total = enteredTotal,
            packagingQuantity = packagingQuantity,
            container = container,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
