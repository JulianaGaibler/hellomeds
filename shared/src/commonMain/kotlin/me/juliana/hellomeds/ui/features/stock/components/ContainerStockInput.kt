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
import me.juliana.hellomeds.shared.container_inline_generic
import me.juliana.hellomeds.shared.stock_auto_correct_message
import me.juliana.hellomeds.shared.stock_container_generic
import me.juliana.hellomeds.shared.stock_containers_generic
import me.juliana.hellomeds.shared.stock_current_total_compound
import me.juliana.hellomeds.shared.stock_current_total_single
import me.juliana.hellomeds.shared.stock_input_full_containers
import me.juliana.hellomeds.shared.stock_input_units_from_current
import me.juliana.hellomeds.shared.stock_new_total_compound
import me.juliana.hellomeds.shared.stock_new_total_single
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.IntegerInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.util.displayNameLowerRes
import me.juliana.hellomeds.ui.util.doseUnitPluralRes
import me.juliana.hellomeds.ui.util.formatDecimal
import me.juliana.hellomeds.ui.util.formatDecimalPlain
import me.juliana.hellomeds.ui.util.inlinePluralRes
import me.juliana.hellomeds.ui.util.labelPluralRes
import me.juliana.hellomeds.ui.util.pluralFormRes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.floor

/**
 * Dual input for full containers + partial units with auto-correction:
 * partial units that exceed [packagingQuantity] roll into full containers.
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
    val stockUnit = stringResource(medication.type.pluralFormRes)
    rememberCoroutineScope()
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(errorText) {
        if (errorText != null) {
            delay(3000)
            errorText = null
        }
    }

    val autoCorrectTemplate = stringResource(Res.string.stock_auto_correct_message, 0, "0")

    fun validateAndCorrect() {
        val partial = partialUnits.toDoubleOrNull() ?: return
        val capacity = packagingQuantity ?: return

        if (partial > capacity) {
            val extraContainers = (partial / capacity).toInt()
            val remainder = partial % capacity
            val currentFull = fullContainers.toIntOrNull() ?: 0

            onFullContainersChange((currentFull + extraContainers).toString())
            onPartialUnitsChange(formatDecimalPlain(remainder))

            errorText = autoCorrectTemplate
        }
    }

    val containerLabel = if (medicationContainer != null) {
        pluralStringResource(medicationContainer.labelPluralRes, 2)
    } else {
        pluralStringResource(Res.plurals.stock_containers_generic, 2, 2)
    }
    val containerName = stringResource(Res.string.stock_input_full_containers, containerLabel)

    val containerLower = medicationContainer?.let {
        stringResource(it.displayNameLowerRes)
    } ?: stringResource(Res.string.stock_container_generic)
    // Title-case the unit for use as a field label; no-op in de where nouns
    // are already capitalized.
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
                SmartListItemConfig(visible = true) { shapes, visible ->
                    SmartListTextItem(
                        label = partialLabel,
                        value = partialUnits,
                        onValueChange = onPartialUnitsChange,
                        suffix = null,
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
                        label = containerName,
                        value = fullContainers,
                        onValueChange = onFullContainersChange,
                        suffix = null,
                        shapes = shapes,
                        visible = visible,
                        inputTransformation = IntegerInputTransformation(),
                    )
                },
            ),
        )

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

/** Previews the new total after a top-up; hidden until something is added. */
@Composable
fun StockNewTotalSummary(
    medication: Medication,
    isEstimated: Boolean,
    currentTotal: Double,
    added: Double,
    packagingQuantity: Double?,
    container: MedicationContainer?,
    modifier: Modifier = Modifier,
) {
    if (!added.isFinite() || added <= 0.0) return
    StockTotalText(
        total = currentTotal + added,
        medication = medication,
        isEstimated = isEstimated,
        packagingQuantity = packagingQuantity,
        container = container,
        singleTemplate = Res.string.stock_new_total_single,
        compoundTemplate = Res.string.stock_new_total_compound,
        modifier = modifier,
    )
}

/** Describes the entered/current stock total. */
@Composable
fun StockCurrentTotalSummary(
    medication: Medication,
    isEstimated: Boolean,
    total: Double,
    packagingQuantity: Double?,
    container: MedicationContainer?,
    modifier: Modifier = Modifier,
) {
    if (!total.isFinite() || total <= 0.0) return
    StockTotalText(
        total = total,
        medication = medication,
        isEstimated = isEstimated,
        packagingQuantity = packagingQuantity,
        container = container,
        singleTemplate = Res.string.stock_current_total_single,
        compoundTemplate = Res.string.stock_current_total_compound,
        modifier = modifier,
    )
}

@Composable
private fun StockTotalText(
    total: Double,
    medication: Medication,
    isEstimated: Boolean,
    packagingQuantity: Double?,
    container: MedicationContainer?,
    singleTemplate: StringResource,
    compoundTemplate: StringResource,
    modifier: Modifier = Modifier,
) {
    val packagingEnabled = !isEstimated &&
        packagingQuantity != null && packagingQuantity > 0.0 && container != null

    val rendered: String = when {
        isEstimated && container != null -> {
            val count = total.toInt().coerceAtLeast(0)
            val noun = pluralStringResource(container.inlinePluralRes, count)
            stringResource(singleTemplate, formatCount(count.toDouble()), noun)
        }
        isEstimated -> {
            val count = total.toInt().coerceAtLeast(0)
            val noun = pluralStringResource(Res.plurals.container_inline_generic, count)
            stringResource(singleTemplate, formatCount(count.toDouble()), noun)
        }
        packagingEnabled -> {
            val cap = packagingQuantity!!
            val full = floor(total / cap).toInt().coerceAtLeast(0)
            // Clamp floating-point drift (both directions) before zero-checks.
            val rawPartial = total - full * cap
            val partialDoses = if (rawPartial < 1e-9) 0.0 else rawPartial
            when {
                full == 0 -> {
                    val noun = pluralStringResource(
                        medication.type.doseUnitPluralRes,
                        cldrPluralCount(partialDoses),
                    )
                    stringResource(singleTemplate, formatCount(partialDoses), noun)
                }
                partialDoses == 0.0 -> {
                    val noun = pluralStringResource(container!!.inlinePluralRes, full)
                    stringResource(singleTemplate, formatCount(full.toDouble()), noun)
                }
                else -> {
                    val containerNoun = pluralStringResource(container!!.inlinePluralRes, full)
                    val doseNoun = pluralStringResource(
                        medication.type.doseUnitPluralRes,
                        cldrPluralCount(partialDoses),
                    )
                    stringResource(
                        compoundTemplate,
                        formatCount(full.toDouble()),
                        containerNoun,
                        formatCount(partialDoses),
                        doseNoun,
                    )
                }
            }
        }
        else -> {
            val noun = pluralStringResource(
                medication.type.doseUnitPluralRes,
                cldrPluralCount(total),
            )
            stringResource(singleTemplate, formatCount(total), noun)
        }
    }

    Text(
        text = rendered,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    )
}

// Strip trailing ".0" so integer counts don't render as "250,0" in de.
private fun formatCount(n: Double): String = if (n % 1.0 == 0.0) n.toInt().toString() else formatDecimal(n)

// FIXME (i18n): noun-splicing into a static sentence only works for languages
// where the noun form depends solely on count (en, de, Romance). Slavic/Arabic
// locales need full-sentence plurals or a Double-aware CLDR category lookup.
private fun cldrPluralCount(n: Double): Int {
    val rounded = n.toInt()
    return if (abs(n - rounded) < 1e-9) rounded else 2
}
