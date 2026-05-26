// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.stock_containers_generic
import me.juliana.hellomeds.shared.stock_top_up_submit
import me.juliana.hellomeds.shared.stock_top_up_title
import me.juliana.hellomeds.shared.stock_update_sealed_label
import me.juliana.hellomeds.shared.stock_update_sealed_title
import me.juliana.hellomeds.shared.stock_update_submit
import me.juliana.hellomeds.shared.stock_update_title
import me.juliana.hellomeds.shared.stock_update_total_label
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.IntegerInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.features.stock.components.DiscreteStockInput
import me.juliana.hellomeds.ui.features.stock.components.StockCurrentTotalSummary
import me.juliana.hellomeds.ui.features.stock.components.StockNewTotalSummary
import me.juliana.hellomeds.ui.util.formatDecimalPlain
import me.juliana.hellomeds.ui.util.labelPluralRes
import kotlin.math.floor
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Top Up Stock Bottom Sheet. Submit emits the amount to ADD (doses for EXACT,
 * containers for ESTIMATED) — caller passes it straight to `recordRefill`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopUpBottomSheet(
    medication: Medication,
    currentTotal: Double,
    packagingQuantity: Double?,
    container: MedicationContainer?,
    onDismiss: () -> Unit,
    onSubmit: (quantity: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEstimated = medication.trackingPrecision == TrackingPrecision.ESTIMATED
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.stock_top_up_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            if (isEstimated) {
                EstimatedTopUpContent(
                    medication = medication,
                    currentTotal = currentTotal,
                    container = container,
                    onSubmit = onSubmit,
                    onDismiss = onDismiss,
                )
            } else {
                ExactTopUpContent(
                    medication = medication,
                    currentTotal = currentTotal,
                    packagingQuantity = packagingQuantity,
                    container = container,
                    onSubmit = onSubmit,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ExactTopUpContent(
    medication: Medication,
    currentTotal: Double,
    packagingQuantity: Double?,
    container: MedicationContainer?,
    onSubmit: (quantity: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val packagingEnabled = packagingQuantity != null && packagingQuantity > 0.0 && container != null

    var fullContainers by remember { mutableStateOf("") }
    var partialUnits by remember { mutableStateOf("") }

    val added = if (packagingEnabled) {
        val full = fullContainers.toIntOrNull() ?: 0
        val partial = partialUnits.toDoubleOrNull() ?: 0.0
        full * packagingQuantity!! + partial
    } else {
        partialUnits.toDoubleOrNull() ?: 0.0
    }

    DiscreteStockInput(
        packagingEnabled = packagingEnabled,
        fullContainers = fullContainers,
        partialUnits = partialUnits,
        onFullContainersChange = { fullContainers = it },
        onPartialUnitsChange = { partialUnits = it },
        medication = medication,
        packagingQuantity = packagingQuantity,
        container = container,
        singleInputLabel = "Quantity to add",
    )

    StockNewTotalSummary(
        medication = medication,
        isEstimated = false,
        currentTotal = currentTotal,
        added = added,
        packagingQuantity = packagingQuantity,
        container = container,
    )

    Spacer(modifier = Modifier.height(8.dp))

    StockSheetActionRow(
        primaryLabel = stringResource(Res.string.stock_top_up_submit),
        primaryEnabled = added > 0.0,
        onPrimary = {
            if (added > 0.0) {
                onSubmit(added)
                onDismiss()
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun EstimatedTopUpContent(
    medication: Medication,
    currentTotal: Double,
    container: MedicationContainer?,
    onSubmit: (quantity: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var quantityInput by remember { mutableStateOf("") }
    val added = quantityInput.toIntOrNull() ?: 0

    // Use plural form for the label — "Bottles to add" reads cleanly regardless
    // of how many will actually be added.
    val containerPluralLabel = if (container != null) {
        pluralStringResource(container.labelPluralRes, 2)
    } else {
        pluralStringResource(Res.plurals.stock_containers_generic, 2, 2)
    }
    val label = "${containerPluralLabel.replaceFirstChar { it.uppercase() }} to add"

    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListTextItem(
                    label = label,
                    value = quantityInput,
                    onValueChange = { quantityInput = it },
                    shapes = shapes,
                    visible = visible,
                    inputTransformation = IntegerInputTransformation(),
                )
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    StockNewTotalSummary(
        medication = medication,
        isEstimated = true,
        currentTotal = currentTotal,
        added = added.toDouble(),
        packagingQuantity = null,
        container = container,
    )

    Spacer(modifier = Modifier.height(8.dp))

    StockSheetActionRow(
        primaryLabel = stringResource(Res.string.stock_top_up_submit),
        primaryEnabled = added > 0,
        onPrimary = {
            if (added > 0) {
                onSubmit(added.toDouble())
                onDismiss()
            }
        },
        onDismiss = onDismiss,
    )
}

/** Update Stock Bottom Sheet (discrete mode only). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateStockBottomSheet(
    medication: Medication,
    currentTotal: Double?,
    packagingQuantity: Double?,
    container: MedicationContainer?,
    onDismiss: () -> Unit,
    onSubmit: (newTotal: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val packagingEnabled = packagingQuantity != null && packagingQuantity > 0.0 && container != null

    // Back-calculate the full/partial split so the sheet reopens to the user's
    // current state instead of empty fields.
    val (initialFull, initialPartial) = remember(currentTotal, packagingQuantity, packagingEnabled) {
        when {
            currentTotal == null -> "" to ""
            packagingEnabled -> {
                val full = floor(currentTotal / packagingQuantity!!).toInt().coerceAtLeast(0)
                val partial = (currentTotal - full * packagingQuantity).coerceAtLeast(0.0)
                full.toString() to formatDecimalPlain(partial)
            }
            else -> "" to formatDecimalPlain(currentTotal)
        }
    }

    var fullContainers by remember { mutableStateOf(initialFull) }
    var partialUnits by remember { mutableStateOf(initialPartial) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val enteredTotal = if (packagingEnabled) {
        val full = fullContainers.toIntOrNull() ?: 0
        val partial = partialUnits.toDoubleOrNull() ?: 0.0
        full * packagingQuantity!! + partial
    } else {
        partialUnits.toDoubleOrNull() ?: 0.0
    }

    val isValid = if (packagingEnabled) {
        fullContainers.toIntOrNull() != null || partialUnits.toDoubleOrNull() != null
    } else {
        partialUnits.isNotBlank() && partialUnits.toDoubleOrNull() != null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.stock_update_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            DiscreteStockInput(
                packagingEnabled = packagingEnabled,
                fullContainers = fullContainers,
                partialUnits = partialUnits,
                onFullContainersChange = { fullContainers = it },
                onPartialUnitsChange = { partialUnits = it },
                medication = medication,
                packagingQuantity = packagingQuantity,
                container = container,
                singleInputLabel = stringResource(Res.string.stock_update_total_label),
            )

            StockCurrentTotalSummary(
                medication = medication,
                isEstimated = false,
                total = enteredTotal,
                packagingQuantity = packagingQuantity,
                container = container,
            )

            Spacer(modifier = Modifier.height(8.dp))

            StockSheetActionRow(
                primaryLabel = stringResource(Res.string.stock_update_submit),
                primaryEnabled = isValid,
                onPrimary = {
                    if (!isValid) return@StockSheetActionRow
                    onSubmit(enteredTotal)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * Update Sealed Containers Bottom Sheet (weight-based mode).
 * Allows user to set the absolute number of sealed (unopened) containers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSealedContainersBottomSheet(
    currentSealedCount: Int,
    onDismiss: () -> Unit,
    onSubmit: (newCount: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var quantityInput by remember { mutableStateOf(currentSealedCount.toString()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isValid = quantityInput.isNotBlank() && quantityInput.toIntOrNull() != null &&
        (quantityInput.toIntOrNull() ?: -1) >= 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.stock_update_sealed_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListTextItem(
                            label = stringResource(Res.string.stock_update_sealed_label),
                            value = quantityInput,
                            onValueChange = { quantityInput = it },
                            shapes = shapes,
                            visible = visible,
                            inputTransformation = IntegerInputTransformation(),
                        )
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            StockSheetActionRow(
                primaryLabel = stringResource(Res.string.stock_update_submit),
                primaryEnabled = isValid,
                onPrimary = {
                    quantityInput.toIntOrNull()?.let { newCount ->
                        onSubmit(newCount)
                        onDismiss()
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun StockSheetActionRow(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Text(stringResource(Res.string.action_cancel))
        }
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
        ) {
            Text(primaryLabel)
        }
    }
}
