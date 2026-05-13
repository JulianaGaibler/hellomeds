// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.stock_adjust_level_confirm
import me.juliana.hellomeds.shared.stock_adjust_level_remaining
import me.juliana.hellomeds.shared.stock_adjust_level_remaining_disabled
import me.juliana.hellomeds.shared.stock_adjust_level_title
import me.juliana.hellomeds.ui.compat.StockLevelSlider
import me.juliana.hellomeds.ui.components.stock.PredictionContext
import me.juliana.hellomeds.ui.components.stock.StockPredictionPreview
import me.juliana.hellomeds.ui.util.StockFormatUtils
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet for adjusting the ESTIMATED stock level.
 *
 * Uses a vertical slider (Android) or horizontal slider (iOS) for intuitive
 * fill-level adjustment with range-dependent precision (finer steps for small
 * containers). Live per-container prediction preview updates as the slider moves.
 *
 * Emits raw user inputs on confirm — business logic (back-calculation) is handled by the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustStockLevelBottomSheet(
    currentPackagingQuantity: Double,
    currentContainerRemaining: Double,
    dailyConsumption: Double,
    onDismiss: () -> Unit,
    onConfirm: (newPackagingQuantity: Double, newDesiredRemaining: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasSchedule = dailyConsumption > 0.0

    val stepConfig = remember(currentPackagingQuantity) {
        StockFormatUtils.stepConfigForRange(currentPackagingQuantity)
    }

    val snappedInitial = remember(currentContainerRemaining, stepConfig) {
        StockFormatUtils.snapToStep(
            currentContainerRemaining,
            stepConfig.stepSize,
            currentPackagingQuantity,
        ).toFloat()
    }

    var sliderValue by remember { mutableFloatStateOf(snappedInitial) }

    // Snap the display/output value to clean up Float→Double precision drift
    val snappedValue = StockFormatUtils.snapToStep(
        sliderValue.toDouble(),
        stepConfig.stepSize,
        currentPackagingQuantity,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.stock_adjust_level_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            // Label
            Text(
                text = stringResource(Res.string.stock_adjust_level_remaining),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Formatted value display
            Text(
                text = if (hasSchedule) {
                    StockFormatUtils.formatSteppedValue(snappedValue, stepConfig.decimals)
                } else {
                    "-"
                },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (hasSchedule) {
                val steps = remember(currentPackagingQuantity, stepConfig) {
                    StockFormatUtils.sliderStepsCount(
                        currentPackagingQuantity,
                        stepConfig.stepSize,
                    )
                }

                StockLevelSlider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..currentPackagingQuantity.toFloat(),
                    steps = steps,
                )
            } else {
                Text(
                    text = stringResource(Res.string.stock_adjust_level_remaining_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Live per-container prediction preview
            StockPredictionPreview(
                remainingDoses = if (hasSchedule) snappedValue else currentPackagingQuantity,
                dailyConsumption = dailyConsumption,
                context = PredictionContext.THIS_CONTAINER,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val remaining = if (hasSchedule) snappedValue else currentPackagingQuantity
                    onConfirm(currentPackagingQuantity, remaining)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.stock_adjust_level_confirm))
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
