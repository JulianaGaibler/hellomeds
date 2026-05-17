// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_back
import me.juliana.hellomeds.shared.calculate_24px
import me.juliana.hellomeds.shared.stock_adjust_level_action
import me.juliana.hellomeds.shared.stock_adjust_level_action_desc
import me.juliana.hellomeds.shared.stock_calculator_toggle
import me.juliana.hellomeds.shared.stock_container_none
import me.juliana.hellomeds.shared.stock_discrete_low_threshold
import me.juliana.hellomeds.shared.stock_settings_container_type
import me.juliana.hellomeds.shared.stock_settings_depletion_reminder
import me.juliana.hellomeds.shared.stock_settings_depletion_reminder_desc
import me.juliana.hellomeds.shared.stock_settings_doses_per_container
import me.juliana.hellomeds.shared.stock_settings_doses_per_container_desc
import me.juliana.hellomeds.shared.stock_settings_low_stock_title
import me.juliana.hellomeds.shared.stock_settings_packaging_quantity
import me.juliana.hellomeds.shared.stock_settings_remove_tracking
import me.juliana.hellomeds.shared.stock_settings_section_container
import me.juliana.hellomeds.shared.stock_settings_section_general
import me.juliana.hellomeds.shared.stock_settings_title
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListDropdownItem
import me.juliana.hellomeds.ui.components.list.SmartListHeader
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListSwitchItem
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.components.stock.CalculatorBottomSheet
import me.juliana.hellomeds.ui.features.stock.components.DeleteTrackingDialog
import me.juliana.hellomeds.ui.util.StockFormatUtils
import me.juliana.hellomeds.ui.util.displayNameLowerRes
import me.juliana.hellomeds.ui.util.displayNameRes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTrackingSettingsScreen(
    medication: Medication,
    onNavigateBack: () -> Unit,
    onUpdateLowStockThreshold: (Double?) -> Unit,
    onUpdateContainerType: (MedicationContainer?) -> Unit,
    onUpdatePackagingQuantity: (Double?) -> Unit,
    onUpdateDepletionReminderEnabled: (Boolean) -> Unit,
    onDeleteTracking: () -> Unit,
    onAdjustStockLevel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isEstimated = medication.trackingPrecision == TrackingPrecision.ESTIMATED

    // Local state for inline controls
    var lowStockEnabled by remember(medication.lowStockThreshold) {
        mutableStateOf(medication.lowStockThreshold != null)
    }
    var thresholdText by remember(medication.lowStockThreshold) {
        mutableStateOf(
            medication.lowStockThreshold?.let { StockFormatUtils.formatInputDouble(it) }
                ?: "",
        )
    }
    var packagingEnabled by remember(medication.packagingQuantity) {
        mutableStateOf(medication.packagingQuantity != null)
    }
    var packagingText by remember(medication.packagingQuantity) {
        mutableStateOf(
            medication.packagingQuantity?.let { StockFormatUtils.formatInputDouble(it) }
                ?: "",
        )
    }
    var showCalculatorSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Container dropdown options
    val containerOptions = remember {
        MedicationContainer.entries.map { it.value to it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.stock_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- General section ---
            item {
                SmartListHeader(stringResource(Res.string.stock_settings_section_general))
            }
            item {
                AutoSmartList(
                    items = listOf(
                        // Low stock toggle
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.stock_settings_low_stock_title),
                                checked = lowStockEnabled,
                                onCheckedChange = { enabled ->
                                    lowStockEnabled = enabled
                                    if (enabled) {
                                        val parsed = StockFormatUtils.parseLocaleDouble(thresholdText)
                                        onUpdateLowStockThreshold(parsed ?: 5.0)
                                        if (thresholdText.isBlank()) thresholdText = "5"
                                    } else {
                                        onUpdateLowStockThreshold(null)
                                    }
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        // Low stock threshold input
                        SmartListItemConfig(visible = lowStockEnabled) { shapes, visible ->
                            SmartListTextItem(
                                label = stringResource(Res.string.stock_discrete_low_threshold),
                                value = thresholdText,
                                onValueChange = { newValue ->
                                    thresholdText = newValue
                                    val parsed = StockFormatUtils.parseLocaleDouble(newValue)
                                    if (parsed != null && parsed > 0) {
                                        onUpdateLowStockThreshold(parsed)
                                    }
                                },
                                shapes = shapes,
                                visible = visible,
                                inputTransformation = DecimalInputTransformation(),
                            )
                        },
                        // Container type dropdown
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            val noneLabel = stringResource(Res.string.stock_container_none)
                            val containerNames = containerOptions.map { (_, container) ->
                                stringResource(container.displayNameRes)
                            }
                            // In EXACT mode, prepend "None" so the type can be cleared.
                            // ESTIMATED requires a container type, so omit it there.
                            val optionNames = if (isEstimated) {
                                containerNames
                            } else {
                                listOf(noneLabel) + containerNames
                            }
                            val selectedName = medication.medicationContainer?.let {
                                stringResource(it.displayNameRes)
                            } ?: if (isEstimated) "" else noneLabel
                            SmartListDropdownItem(
                                label = stringResource(Res.string.stock_settings_container_type),
                                selectedValue = selectedName,
                                options = optionNames,
                                onValueChange = { selectedDisplayName ->
                                    val idx = optionNames.indexOf(selectedDisplayName)
                                    if (idx >= 0) {
                                        if (!isEstimated && idx == 0) {
                                            onUpdateContainerType(null)
                                            // Capacity is meaningless without a container — clear it too.
                                            packagingText = ""
                                            onUpdatePackagingQuantity(null)
                                        } else {
                                            val containerIdx = if (isEstimated) idx else idx - 1
                                            onUpdateContainerType(containerOptions[containerIdx].second)
                                        }
                                    }
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                    ),
                )
            }

            // --- Container section ---
            if (isEstimated) {
                item {
                    SmartListHeader(stringResource(Res.string.stock_settings_section_container))
                }
                item {
                    val containerNameLower = medication.medicationContainer?.let {
                        stringResource(it.displayNameLowerRes)
                    }
                    val packagingLabel = containerNameLower?.let {
                        stringResource(Res.string.stock_settings_doses_per_container, it)
                    } ?: stringResource(Res.string.stock_settings_packaging_quantity)

                    AutoSmartList(
                        items = listOf(
                            // Packaging quantity toggle
                            SmartListItemConfig(visible = true) { shapes, visible ->
                                SmartListSwitchItem(
                                    label = packagingLabel,
                                    supportingText = stringResource(Res.string.stock_settings_doses_per_container_desc),
                                    checked = packagingEnabled,
                                    onCheckedChange = { enabled ->
                                        packagingEnabled = enabled
                                        if (!enabled) {
                                            packagingText = ""
                                            onUpdatePackagingQuantity(null)
                                            onUpdateDepletionReminderEnabled(false)
                                        }
                                    },
                                    shapes = shapes,
                                    visible = visible,
                                )
                            },
                            // Packaging quantity input with calculator (only for estimated tracking)
                            SmartListItemConfig(visible = packagingEnabled) { shapes, visible ->
                                SmartListTextItem(
                                    label = packagingLabel,
                                    value = packagingText,
                                    onValueChange = { newValue ->
                                        packagingText = newValue
                                        val parsed = newValue.toDoubleOrNull()
                                        if (parsed != null && parsed > 0) {
                                            onUpdatePackagingQuantity(parsed)
                                        }
                                    },
                                    shapes = shapes,
                                    visible = visible,
                                    inputTransformation = DecimalInputTransformation(),
                                    trailingAction = if (isEstimated) {
                                        {
                                            IconButton(onClick = { showCalculatorSheet = true }) {
                                                Icon(
                                                    painter = painterResource(Res.drawable.calculate_24px),
                                                    contentDescription = stringResource(
                                                        Res.string.stock_calculator_toggle,
                                                    ),
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                )
                            },
                            // Adjust stock level action
                            SmartListItemConfig(visible = packagingEnabled) { shapes, visible ->
                                SmartListItem(
                                    headlineContent = {
                                        Text(stringResource(Res.string.stock_adjust_level_action))
                                    },
                                    supportingContent = {
                                        Text(stringResource(Res.string.stock_adjust_level_action_desc))
                                    },
                                    shapes = shapes,
                                    visible = visible,
                                    onClick = onAdjustStockLevel,
                                )
                            },
                            // Depletion reminder toggle
                            SmartListItemConfig(visible = true) { shapes, visible ->
                                SmartListSwitchItem(
                                    label = stringResource(Res.string.stock_settings_depletion_reminder),
                                    checked = medication.depletionReminderEnabled && packagingEnabled,
                                    onCheckedChange = { onUpdateDepletionReminderEnabled(it) },
                                    enabled = packagingEnabled,
                                    shapes = shapes,
                                    visible = visible,
                                    supportingText = stringResource(Res.string.stock_settings_depletion_reminder_desc),
                                )
                            },
                        ),
                    )
                }
            } else if (medication.medicationContainer != null) {
                item {
                    SmartListHeader(stringResource(Res.string.stock_settings_section_container))
                }
                item {
                    AutoSmartList(
                        items = listOf(
                            SmartListItemConfig(visible = true) { shapes, visible ->
                                SmartListTextItem(
                                    label = stringResource(Res.string.stock_settings_packaging_quantity),
                                    value = packagingText,
                                    onValueChange = { newValue ->
                                        packagingText = newValue
                                        // Quantity is required while a container is set —
                                        // the user clears both by setting container to None.
                                        StockFormatUtils.parseLocaleDouble(newValue)
                                            ?.takeIf { it > 0 }
                                            ?.let(onUpdatePackagingQuantity)
                                    },
                                    shapes = shapes,
                                    visible = visible,
                                    inputTransformation = DecimalInputTransformation(),
                                )
                            },
                        ),
                    )
                }
            }

            // --- Danger zone ---
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.stock_settings_remove_tracking))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Calculator bottom sheet
    if (showCalculatorSheet) {
        CalculatorBottomSheet(
            onDismiss = { showCalculatorSheet = false },
            onApplyResult = { result ->
                packagingText = StockFormatUtils.formatInputDouble(result)
                onUpdatePackagingQuantity(result)
                showCalculatorSheet = false
            },
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        DeleteTrackingDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                onDeleteTracking()
                showDeleteDialog = false
            },
        )
    }
}
