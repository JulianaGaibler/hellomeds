// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_discrete_low_enable
import me.juliana.hellomeds.shared.stock_discrete_low_subtitle
import me.juliana.hellomeds.shared.stock_discrete_low_threshold
import me.juliana.hellomeds.shared.stock_discrete_low_title
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.features.medication.steps.ScreenHeader
import me.juliana.hellomeds.ui.util.pluralFormRes
import org.jetbrains.compose.resources.stringResource

/**
 * DISCRETE Step 4: Low stock warning
 * Optional configuration for low stock alerts.
 */
@Composable
fun DiscreteLowStockStep(
    lowStockEnabled: Boolean,
    onLowStockEnabledChange: (Boolean) -> Unit,
    lowStockThreshold: String,
    onLowStockThresholdChange: (String) -> Unit,
    medication: Medication,
    modifier: Modifier = Modifier,
) {
    val context = platformContext()
    // Get unit name from medication type
    val stockUnit = stringResource(medication.type.pluralFormRes)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Use ScreenHeader
        ScreenHeader(
            headline = stringResource(Res.string.stock_discrete_low_title),
            title = stringResource(Res.string.stock_discrete_low_subtitle),
        )

        // Single SmartList with toggle and conditional threshold input
        AutoSmartList(
            items = listOf(
                SmartListItemConfig(visible = true) { shapes, visible ->
                    SmartListItem(
                        headlineContent = { Text(stringResource(Res.string.stock_discrete_low_enable)) },
                        trailingContent = {
                            Switch(
                                checked = lowStockEnabled,
                                onCheckedChange = onLowStockEnabledChange,
                            )
                        },
                        shapes = shapes,
                        visible = visible,
                        onClick = { onLowStockEnabledChange(!lowStockEnabled) },
                    )
                },
                SmartListItemConfig(visible = lowStockEnabled) { shapes, visible ->
                    SmartListTextItem(
                        label = stringResource(Res.string.stock_discrete_low_threshold),
                        value = lowStockThreshold,
                        onValueChange = onLowStockThresholdChange,
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
