// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_auto_correct_message
import me.juliana.hellomeds.shared.stock_container_generic
import me.juliana.hellomeds.shared.stock_containers_generic
import me.juliana.hellomeds.shared.stock_input_full_containers
import me.juliana.hellomeds.shared.stock_input_units_from_current
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.IntegerInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.util.displayNameLowerRes
import me.juliana.hellomeds.ui.util.formatDecimal
import me.juliana.hellomeds.ui.util.formatDecimalPlain
import me.juliana.hellomeds.ui.util.labelPluralRes
import me.juliana.hellomeds.ui.util.pluralFormRes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Dual input for full containers + partial units with auto-correction.
 * Automatically converts overflow in partial units to full containers.
 *
 * @param fullContainers Number of full containers
 * @param partialUnits Number of partial units
 * @param onFullContainersChange Callback when full containers changes
 * @param onPartialUnitsChange Callback when partial units changes
 * @param medication Medication entity (for deriving unit name)
 * @param packagingQuantity Number of units per container
 * @param medicationContainer Type of container (for display name)
 */

@Composable
fun ContainerStockInput(
    fullContainers: String,
    partialUnits: String,
    onFullContainersChange: (String) -> Unit,
    onPartialUnitsChange: (String) -> Unit,
    medication: Medication,
    packagingQuantity: Double?,
    medicationContainer: MedicationContainer?,
    modifier: Modifier = Modifier,
) {
    platformContext()
    // Get unit name from medication type
    val stockUnit = stringResource(medication.type.pluralFormRes)
    rememberCoroutineScope()
    var errorText by remember { mutableStateOf<String?>(null) }

    // Auto-clear error text after 3 seconds
    LaunchedEffect(errorText) {
        if (errorText != null) {
            delay(3000)
            errorText = null
        }
    }

    // Pre-compute auto-correct message template
    val autoCorrectTemplate = stringResource(Res.string.stock_auto_correct_message, 0, "0")

    // Validate and auto-correct when partial units exceed capacity
    fun validateAndCorrect() {
        val partial = partialUnits.toDoubleOrNull() ?: return
        val capacity = packagingQuantity ?: return

        if (partial > capacity) {
            val extraContainers = (partial / capacity).toInt()
            val remainder = partial % capacity
            val currentFull = fullContainers.toIntOrNull() ?: 0

            onFullContainersChange((currentFull + extraContainers).toString())
            onPartialUnitsChange(formatDecimalPlain(remainder))

            // Build error message without @Composable (template already resolved)
            errorText = autoCorrectTemplate
        }
    }

    // Calculate total stock
    val total = remember(fullContainers, partialUnits, packagingQuantity) {
        val full = fullContainers.toIntOrNull() ?: 0
        val partial = partialUnits.toDoubleOrNull() ?: 0.0
        val capacity = packagingQuantity ?: 1.0
        (full * capacity) + partial
    }

    // Get container name with pluralization (for full containers label)
    val containerLabel = if (medicationContainer != null) {
        pluralStringResource(medicationContainer.labelPluralRes, 2)
    } else {
        pluralStringResource(Res.plurals.stock_containers_generic, 2, 2)
    }
    val containerName = stringResource(Res.string.stock_input_full_containers, containerLabel)

    // Get singular container name (for partial units label)
    val containerLower = medicationContainer?.let {
        stringResource(it.displayNameLowerRes)
    } ?: stringResource(Res.string.stock_container_generic)
    // The unit (e.g. "tablets") is lowercase in en — title-case it for use as a
    // field label. No-op for German, where nouns like "Tabletten" are already
    // capitalized.
    val partialLabel = stringResource(
        Res.string.stock_input_units_from_current,
        stockUnit.replaceFirstChar { it.uppercase() },
        containerLower,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AutoSmartList(
            items = listOf(
                // REVERSED ORDER: Partial first, then full
                SmartListItemConfig(visible = true) { shapes, visible ->
                    SmartListTextItem(
                        label = partialLabel, // Dynamic: "Tablets from current blister pack"
                        value = partialUnits,
                        onValueChange = onPartialUnitsChange,
                        suffix = null, // REMOVED: No suffix to avoid duplication
                        shapes = shapes,
                        visible = visible,
                        inputTransformation = DecimalInputTransformation(),
                        modifier = Modifier.onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                validateAndCorrect()
                            }
                        },
                    )
                },
                SmartListItemConfig(visible = true) { shapes, visible ->
                    SmartListTextItem(
                        label = containerName, // Dynamic: "Full blister packs"
                        value = fullContainers,
                        onValueChange = onFullContainersChange,
                        suffix = null, // REMOVED: No suffix to avoid duplication
                        shapes = shapes,
                        visible = visible,
                        inputTransformation = IntegerInputTransformation(),
                    )
                },
            ),
        )

        // Total display outside SmartList, centered
        val totalFormatted = if (total % 1.0 == 0.0) {
            total.toInt().toString() // Whole number: "5" not "5.0"
        } else {
            formatDecimal(total) // With decimal: "5.5"
        }
        Text(
            text = "You have $totalFormatted ${stockUnit.ifBlank { "units" }} in total.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )

        // Show auto-correction message
        if (errorText != null) {
            Text(
                text = errorText!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
