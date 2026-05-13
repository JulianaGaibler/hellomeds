// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_calculator_apply
import me.juliana.hellomeds.shared.stock_calculator_dose_label
import me.juliana.hellomeds.shared.stock_calculator_error_dose_exceeds
import me.juliana.hellomeds.shared.stock_calculator_result
import me.juliana.hellomeds.shared.stock_calculator_sheet_description
import me.juliana.hellomeds.shared.stock_calculator_sheet_title
import me.juliana.hellomeds.shared.stock_calculator_total_label
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Reusable UI component for calculating doses-per-container from total container
 * amount and per-dose amount (e.g., 50g container / 1.2g per pump = ~41 doses).
 *
 * Used in the stock tracking wizard (both EXACT and ESTIMATED modes) and the
 * packaging quantity settings dialog.
 */
@Composable
fun DosesPerContainerCalculator(
    totalAmount: String,
    onTotalAmountChange: (String) -> Unit,
    doseAmount: String,
    onDoseAmountChange: (String) -> Unit,
    onApplyResult: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = totalAmount.toDoubleOrNull()
    val dose = doseAmount.toDoubleOrNull()
    val doseExceedsTotal = total != null && dose != null && dose > total
    val calculatedDoses by remember(totalAmount, doseAmount) {
        derivedStateOf {
            val t = totalAmount.toDoubleOrNull()
            val d = doseAmount.toDoubleOrNull()
            if (t != null && d != null && d > 0 && d <= t) {
                (t / d).roundToInt().toDouble()
            } else {
                null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = totalAmount,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                    onTotalAmountChange(newValue)
                }
            },
            label = { Text(stringResource(Res.string.stock_calculator_total_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = doseAmount,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                    onDoseAmountChange(newValue)
                }
            },
            label = { Text(stringResource(Res.string.stock_calculator_dose_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = doseExceedsTotal,
            supportingText = if (doseExceedsTotal) {
                { Text(stringResource(Res.string.stock_calculator_error_dose_exceeds)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        val doses = calculatedDoses
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (doses != null) {
                    stringResource(
                        Res.string.stock_calculator_result,
                        doses.roundToInt(),
                    )
                } else {
                    ""
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { doses?.let { onApplyResult(it) } },
                enabled = doses != null,
            ) {
                Text(stringResource(Res.string.stock_calculator_apply))
            }
        }
    }
}

/**
 * Bottom sheet wrapping the dose calculator with explanation text.
 * Reused across the wizard steps and settings dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorBottomSheet(onDismiss: () -> Unit, onApplyResult: (Double) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var calcTotal by remember { mutableStateOf("") }
    var calcDose by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.stock_calculator_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(Res.string.stock_calculator_sheet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DosesPerContainerCalculator(
                totalAmount = calcTotal,
                onTotalAmountChange = { calcTotal = it },
                doseAmount = calcDose,
                onDoseAmountChange = { calcDose = it },
                onApplyResult = onApplyResult,
                modifier = Modifier.padding(horizontal = 0.dp),
            )
        }
    }
}
