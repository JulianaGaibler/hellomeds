// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_skip
import me.juliana.hellomeds.shared.stock_container_generic
import me.juliana.hellomeds.shared.stock_container_type_label
import me.juliana.hellomeds.shared.stock_discrete_packaging_subtitle
import me.juliana.hellomeds.shared.stock_discrete_packaging_title
import me.juliana.hellomeds.shared.stock_settings_doses_per_container
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.medication.ContainerSelector
import me.juliana.hellomeds.ui.features.medication.steps.ScreenHeader
import me.juliana.hellomeds.ui.util.displayNameLowerRes
import org.jetbrains.compose.resources.stringResource

/**
 * DISCRETE Step 2: Optional packaging configuration
 * Allows user to specify packaging quantity and container type, or skip this step.
 */
@Composable
fun DiscretePackagingStep(
    packagingQuantity: String,
    onPackagingQuantityChange: (String) -> Unit,
    container: MedicationContainer?,
    onContainerChange: (MedicationContainer?) -> Unit,
    medicationType: MedicationType,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = platformContext()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Use ScreenHeader
        ScreenHeader(
            headline = stringResource(Res.string.stock_discrete_packaging_title),
            title = stringResource(Res.string.stock_discrete_packaging_subtitle),
        )

        // Skip button (tonal style)
        FilledTonalButton(
            onClick = onSkip,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(Res.string.action_skip))
        }

        // Dynamic TextField label based on medication type and container
        val containerLower = container?.let { stringResource(it.displayNameLowerRes) }
            ?: stringResource(Res.string.stock_container_generic)
        val labelText = stringResource(Res.string.stock_settings_doses_per_container, containerLower)

        // TextField for packaging quantity
        TextField(
            value = packagingQuantity,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                    onPackagingQuantityChange(newValue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(labelText) },
            placeholder = { Text("50") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )

        // Container Type Selector
        Text(
            text = stringResource(Res.string.stock_container_type_label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )

        ContainerSelector(
            selectedContainer = container,
            onContainerSelected = onContainerChange,
        )
    }
}
