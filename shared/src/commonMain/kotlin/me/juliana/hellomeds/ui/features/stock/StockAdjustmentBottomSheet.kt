// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.stock_top_up_quantity_label
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
import org.jetbrains.compose.resources.stringResource

/**
 * Top Up Stock Bottom Sheet.
 * Allows user to add containers/quantity to their stock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopUpBottomSheet(onDismiss: () -> Unit, onSubmit: (quantity: Int) -> Unit, modifier: Modifier = Modifier) {
    var quantityInput by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isValid = quantityInput.isNotBlank() && (quantityInput.toIntOrNull() ?: 0) > 0

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

            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListTextItem(
                            label = stringResource(Res.string.stock_top_up_quantity_label),
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

            Button(
                onClick = {
                    quantityInput.toIntOrNull()?.let { qty ->
                        onSubmit(qty)
                        onDismiss()
                    }
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.stock_top_up_submit))
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    }
}

/**
 * Update Stock Bottom Sheet (discrete mode only).
 * Allows user to set the current total stock quantity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateStockBottomSheet(
    currentTotal: Int?,
    onDismiss: () -> Unit,
    onSubmit: (newTotal: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var totalInput by remember { mutableStateOf(currentTotal?.toString() ?: "") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isValid = totalInput.isNotBlank() && totalInput.toIntOrNull() != null

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

            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListTextItem(
                            label = stringResource(Res.string.stock_update_total_label),
                            value = totalInput,
                            onValueChange = { totalInput = it },
                            shapes = shapes,
                            visible = visible,
                            inputTransformation = IntegerInputTransformation(allowNegative = true),
                        )
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    totalInput.toIntOrNull()?.let { newTotal ->
                        onSubmit(newTotal)
                        onDismiss()
                    }
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.stock_update_submit))
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.action_cancel))
            }
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

            Button(
                onClick = {
                    quantityInput.toIntOrNull()?.let { newCount ->
                        onSubmit(newCount)
                        onDismiss()
                    }
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.stock_update_submit))
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    }
}
