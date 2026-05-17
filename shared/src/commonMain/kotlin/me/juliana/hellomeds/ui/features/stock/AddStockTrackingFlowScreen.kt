// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_confirm
import me.juliana.hellomeds.shared.action_finish
import me.juliana.hellomeds.shared.action_next
import me.juliana.hellomeds.shared.action_skip
import me.juliana.hellomeds.shared.calculate_24px
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.stock_calculator_toggle
import me.juliana.hellomeds.shared.stock_container_generic
import me.juliana.hellomeds.shared.stock_container_started_default
import me.juliana.hellomeds.shared.stock_container_started_label
import me.juliana.hellomeds.shared.stock_container_type_label
import me.juliana.hellomeds.shared.stock_depletion_reminder_label
import me.juliana.hellomeds.shared.stock_depletion_step_desc
import me.juliana.hellomeds.shared.stock_discrete_current_subtitle
import me.juliana.hellomeds.shared.stock_discrete_current_title
import me.juliana.hellomeds.shared.stock_discrete_low_subtitle
import me.juliana.hellomeds.shared.stock_discrete_low_title
import me.juliana.hellomeds.shared.stock_estimated_container_subtitle
import me.juliana.hellomeds.shared.stock_estimated_container_title
import me.juliana.hellomeds.shared.stock_estimated_doses_info
import me.juliana.hellomeds.shared.stock_estimated_doses_title
import me.juliana.hellomeds.shared.stock_flow_title
import me.juliana.hellomeds.shared.stock_method_discrete_desc
import me.juliana.hellomeds.shared.stock_method_discrete_title
import me.juliana.hellomeds.shared.stock_method_estimated_desc
import me.juliana.hellomeds.shared.stock_method_estimated_title
import me.juliana.hellomeds.shared.stock_method_subtitle
import me.juliana.hellomeds.shared.stock_method_title
import me.juliana.hellomeds.shared.stock_settings_depletion_reminder_desc
import me.juliana.hellomeds.shared.stock_settings_doses_per_container
import me.juliana.hellomeds.ui.compat.PlatformBackHandler
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.IntegerInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListRadioItem
import me.juliana.hellomeds.ui.components.list.SmartListSwitchItem
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.components.medication.ContainerSelector
import me.juliana.hellomeds.ui.components.stock.CalculatorBottomSheet
import me.juliana.hellomeds.ui.components.common.ScreenHeader
import me.juliana.hellomeds.ui.features.stock.steps.DiscreteCurrentStockStep
import me.juliana.hellomeds.ui.features.stock.steps.DiscreteLowStockStep
import me.juliana.hellomeds.ui.features.stock.steps.DiscretePackagingStep
import me.juliana.hellomeds.ui.util.displayNameLowerRes
import me.juliana.hellomeds.data.service.StockPredictionEngine
import me.juliana.hellomeds.ui.components.stock.PredictionContext
import me.juliana.hellomeds.ui.components.stock.StockPredictionPreview
import me.juliana.hellomeds.ui.util.labelPluralRes
import me.juliana.hellomeds.ui.util.pluralFormRes
import me.juliana.hellomeds.ui.viewmodel.MedicationViewModel
import me.juliana.hellomeds.ui.viewmodel.ScheduleViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

/**
 * Multi-step flow for adding stock tracking to a medication.
 *
 * Flow structure:
 * - Step 0: Choose tracking precision (EXACT or ESTIMATED)
 * - EXACT path (4 steps total):
 *   1. Container Type + Packaging
 *   2. Current Stock
 *   3. Low Stock Warning → save
 * - ESTIMATED path (4 steps total):
 *   1. Container Type + optional doses-per-container
 *   2. Current Stock (how many containers)
 *   3. Low Stock (containers) + depletion reminder toggle → save
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStockTrackingFlowScreen(
    medication: Medication,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    medicationViewModel: MedicationViewModel = koinViewModel(),
    scheduleViewModel: ScheduleViewModel = koinViewModel(),
) {
    var currentStep by remember { mutableIntStateOf(0) }

    var state by remember {
        mutableStateOf(AddStockTrackingState())
    }

    // Compute daily consumption from existing schedules (for prediction preview)
    val schedules by scheduleViewModel.getSchedulesByMedicationId(medication.id)
        .collectAsStateWithLifecycle(initial = emptyList())
    val dailyConsumption = StockPredictionEngine.dailyConsumption(
        schedules.filter { !it.isEffectivelyArchived() },
    )

    // Calculate total steps based on precision
    val totalSteps = when (state.trackingPrecision) {
        TrackingPrecision.EXACT -> 4 // Precision + 3 exact steps
        TrackingPrecision.ESTIMATED -> 6 // Precision + 5 estimated steps
        null -> 1 // Only step 0 visible until precision is selected
    }

    // Validation per step
    val canProceed = when {
        currentStep == 0 -> state.trackingPrecision != null

        state.trackingPrecision == TrackingPrecision.EXACT -> when (currentStep) {
            1 -> {
                // With pre-selected container: user must enter quantity to proceed with Next
                // Skip button clears both and proceeds
                val quantityBlank = state.packagingQuantity.isBlank()
                val quantityValid =
                    !quantityBlank && state.packagingQuantity.toDoubleOrNull() != null
                val containerSelected = state.container != null

                // Can proceed if both container selected AND quantity valid
                quantityValid && containerSelected
            }

            2 -> { // Current stock
                val hasPackaging = state.packagingQuantity.isNotBlank() &&
                    state.packagingQuantity.toDoubleOrNull() != null &&
                    state.container != null
                if (hasPackaging) {
                    state.fullContainers.isNotBlank() &&
                        state.fullContainers.toIntOrNull() != null &&
                        (state.fullContainers.toIntOrNull() ?: 0) > 0 || // Full containers > 0 OR
                        (state.partialUnits.toDoubleOrNull() ?: 0.0) > 0.0 // Partial units > 0
                } else {
                    state.partialUnits.isNotBlank() &&
                        state.partialUnits.toDoubleOrNull() != null &&
                        (state.partialUnits.toDoubleOrNull() ?: 0.0) > 0.0 // Must be > 0
                }
            }

            3 -> { // Low stock: optional, but must be valid if enabled
                !state.lowStockEnabled || // Disabled = can proceed
                    (
                        state.lowStockThreshold.isNotBlank() &&
                            state.lowStockThreshold.toDoubleOrNull() != null &&
                            (state.lowStockThreshold.toDoubleOrNull() ?: 0.0) > 0.0
                        )
            }

            else -> false
        }

        state.trackingPrecision == TrackingPrecision.ESTIMATED -> when (currentStep) {
            1 -> state.estimatedContainer != null // Container type required
            2 -> state.estimatedContainerCount >= 1 // At least 1 container
            3 -> true // Doses per container: optional (has skip button)
            4 -> true // Depletion reminder: optional (has skip button)
            5 -> true // Low stock: optional (has skip button)
            else -> false
        }

        else -> false
    }

    // Function to save medication to database
    fun saveMedication() {
        when (state.trackingPrecision) {
            TrackingPrecision.EXACT -> {
                val hasPackaging =
                    state.packagingQuantity.isNotBlank() &&
                        state.packagingQuantity.toDoubleOrNull() != null &&
                        state.container != null

                val initialQuantity = if (hasPackaging) {
                    // Only partial units go into the active container; full containers become sealed
                    state.partialUnits.toDoubleOrNull() ?: 0.0
                } else {
                    calculateExactTotal(state)
                }

                val lowThreshold = if (state.lowStockEnabled) {
                    state.lowStockThreshold.toDoubleOrNull()
                } else {
                    null
                }

                val sealedContainerCount = if (hasPackaging) {
                    (state.fullContainers.toIntOrNull() ?: 0).coerceAtLeast(0)
                } else {
                    0
                }

                medicationViewModel.enableStockTracking(
                    medication = medication,
                    trackingPrecision = TrackingPrecision.EXACT,
                    initialQuantity = initialQuantity,
                    lowStockThreshold = lowThreshold,
                    packagingQuantity = if (hasPackaging) state.packagingQuantity.toDoubleOrNull() else null,
                    medicationContainer = if (hasPackaging) state.container else null,
                    sealedContainerCount = sealedContainerCount,
                    depletionReminderEnabled = false,
                )
            }

            TrackingPrecision.ESTIMATED -> {
                medicationViewModel.enableStockTracking(
                    medication = medication,
                    trackingPrecision = TrackingPrecision.ESTIMATED,
                    initialQuantity = state.estimatedContainerCount.toDouble(),
                    lowStockThreshold = state.estimatedLowStockThreshold?.toDouble(),
                    packagingQuantity = state.estimatedDosesPerContainer,
                    medicationContainer = state.estimatedContainer,
                    sealedContainerCount = 0,
                    depletionReminderEnabled = state.depletionReminderEnabled,
                    containerStartedAt = state.estimatedContainerStartedAt,
                )
            }

            null -> return // Should not happen
        }

        onClose()
    }

    // Handle back button
    PlatformBackHandler {
        if (currentStep > 0) {
            var prevStep = currentStep - 1
            if (state.trackingPrecision == TrackingPrecision.ESTIMATED &&
                prevStep == 4 && state.estimatedDosesPerContainer == null
            ) {
                prevStep = 3
            }
            currentStep = prevStep
        } else {
            onClose()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AppScaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.stock_flow_title)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentStep > 0) {
                                var prevStep = currentStep - 1
                                // Auto-skip depletion step (5) backward if no doses set
                                if (state.trackingPrecision == TrackingPrecision.ESTIMATED &&
                                    prevStep == 5 && state.estimatedDosesPerContainer == null
                                ) {
                                    prevStep = 4
                                }
                                currentStep = prevStep
                            } else {
                                onClose()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.content_description_back),
                            )
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                if (currentStep < totalSteps - 1) {
                                    var nextStep = currentStep + 1
                                    // Auto-skip depletion step (5) if no doses per container set
                                    if (state.trackingPrecision == TrackingPrecision.ESTIMATED &&
                                        nextStep == 5 && state.estimatedDosesPerContainer == null
                                    ) {
                                        saveMedication()
                                        return@Button
                                    }
                                    currentStep = nextStep
                                } else {
                                    saveMedication()
                                }
                            },
                            enabled = canProceed,
                        ) {
                            Text(
                                if (currentStep < totalSteps - 1) {
                                    stringResource(Res.string.action_next)
                                } else {
                                    stringResource(Res.string.action_finish)
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(bottom = 80.dp),
            ) {
                when {
                    currentStep == 0 -> {
                        // Step 0: Precision selection
                        TrackingPrecisionStep(
                            selectedPrecision = state.trackingPrecision,
                            onPrecisionSelected = { state = state.copy(trackingPrecision = it) },
                        )
                    }

                    state.trackingPrecision == TrackingPrecision.EXACT -> {
                        when (currentStep) {
                            1 -> DiscretePackagingStep(
                                packagingQuantity = state.packagingQuantity,
                                onPackagingQuantityChange = { state = state.copy(packagingQuantity = it) },
                                container = state.container,
                                onContainerChange = { state = state.copy(container = it) },
                                medicationType = medication.type,
                                onSkip = {
                                    // Clear packaging data when skipping
                                    state = state.copy(
                                        packagingQuantity = "",
                                        container = null,
                                    )
                                    currentStep++
                                },
                            )

                            2 -> {
                                val hasPackaging = state.packagingQuantity.isNotBlank() &&
                                    state.packagingQuantity.toDoubleOrNull() != null &&
                                    state.container != null
                                DiscreteCurrentStockStep(
                                    packagingEnabled = hasPackaging,
                                    fullContainers = state.fullContainers,
                                    partialUnits = state.partialUnits,
                                    onFullContainersChange = { state = state.copy(fullContainers = it) },
                                    onPartialUnitsChange = { state = state.copy(partialUnits = it) },
                                    medication = medication,
                                    packagingQuantity = state.packagingQuantity.toDoubleOrNull(),
                                    container = state.container,
                                )
                            }

                            3 -> DiscreteLowStockStep(
                                lowStockEnabled = state.lowStockEnabled,
                                onLowStockEnabledChange = { state = state.copy(lowStockEnabled = it) },
                                lowStockThreshold = state.lowStockThreshold,
                                onLowStockThresholdChange = { state = state.copy(lowStockThreshold = it) },
                                medication = medication,
                            )
                        }
                    }

                    state.trackingPrecision == TrackingPrecision.ESTIMATED -> {
                        when (currentStep) {
                            1 -> EstimatedContainerStep(
                                container = state.estimatedContainer,
                                onContainerChange = { state = state.copy(estimatedContainer = it) },
                            )

                            2 -> EstimatedCurrentStockStep(
                                containerCount = state.estimatedContainerCount,
                                onContainerCountChange = { state = state.copy(estimatedContainerCount = it) },
                                container = state.estimatedContainer,
                                containerStartedAt = state.estimatedContainerStartedAt,
                                onContainerStartedAtChange = {
                                    state = state.copy(estimatedContainerStartedAt = it)
                                },
                            )

                            3 -> EstimatedLowStockStep(
                                lowStockThreshold = state.estimatedLowStockThreshold,
                                onLowStockThresholdChange = { state = state.copy(estimatedLowStockThreshold = it) },
                                container = state.estimatedContainer,
                                onSkip = {
                                    state = state.copy(estimatedLowStockThreshold = null)
                                    currentStep++
                                },
                            )

                            4 -> EstimatedDosesStep(
                                dosesPerContainer = state.estimatedDosesPerContainer,
                                onDosesPerContainerChange = { state = state.copy(estimatedDosesPerContainer = it) },
                                medication = medication,
                                container = state.estimatedContainer,
                                containerCount = state.estimatedContainerCount,
                                dailyConsumption = dailyConsumption,
                                onSkip = {
                                    state =
                                        state.copy(estimatedDosesPerContainer = null, depletionReminderEnabled = false)
                                    saveMedication() // Last meaningful step when skipping
                                },
                            )

                            5 -> EstimatedDepletionStep(
                                depletionReminderEnabled = state.depletionReminderEnabled,
                                onDepletionReminderEnabledChange = {
                                    state = state.copy(depletionReminderEnabled = it)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// region Step 0: Tracking Precision

/**
 * Step 0: Choose between EXACT and ESTIMATED tracking precision.
 */
@Composable
private fun TrackingPrecisionStep(
    selectedPrecision: TrackingPrecision?,
    onPrecisionSelected: (TrackingPrecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            headline = stringResource(Res.string.stock_method_title),
            title = stringResource(Res.string.stock_method_subtitle),
        )

        Column(modifier = Modifier.padding(horizontal = 32.dp)) {
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListRadioItem(
                            label = stringResource(Res.string.stock_method_discrete_title),
                            selected = selectedPrecision == TrackingPrecision.EXACT,
                            onClick = { onPrecisionSelected(TrackingPrecision.EXACT) },
                            shapes = shapes,
                            visible = visible,
                        )
                    },
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListRadioItem(
                            label = stringResource(Res.string.stock_method_estimated_title),
                            selected = selectedPrecision == TrackingPrecision.ESTIMATED,
                            onClick = { onPrecisionSelected(TrackingPrecision.ESTIMATED) },
                            shapes = shapes,
                            visible = visible,
                        )
                    },
                ),
            )

            Spacer(Modifier.height(48.dp))

            Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                TrackingPrecisionExplainer(
                    label = stringResource(Res.string.stock_method_discrete_title),
                    description = stringResource(Res.string.stock_method_discrete_desc),
                )
                TrackingPrecisionExplainer(
                    label = stringResource(Res.string.stock_method_estimated_title),
                    description = stringResource(Res.string.stock_method_estimated_desc),
                )
            }
        }
    }
}

@Composable
private fun TrackingPrecisionExplainer(label: String, description: String) {
    Column {
        Text(
            text = label,
            // bodyLargeEmphasized is still internal in material3-1.5.0-alpha17.
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion

// region ESTIMATED mode steps

/**
 * ESTIMATED Step 1: Container type selection with optional doses-per-container.
 */
@Composable
private fun EstimatedContainerStep(
    container: MedicationContainer?,
    onContainerChange: (MedicationContainer?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            headline = stringResource(Res.string.stock_estimated_container_title),
            title = stringResource(Res.string.stock_estimated_container_subtitle),
        )

        ContainerSelector(
            selectedContainer = container,
            onContainerSelected = onContainerChange,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

/**
 * ESTIMATED Step 3: Doses per container (optional, skippable).
 * Enables depletion estimation and the depletion reminder feature.
 */
@Composable
private fun EstimatedDosesStep(
    dosesPerContainer: Double?,
    onDosesPerContainerChange: (Double?) -> Unit,
    medication: Medication,
    container: MedicationContainer?,
    containerCount: Int = 1,
    dailyConsumption: Double = 0.0,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = platformContext()
    var dosesText by remember {
        mutableStateOf(
            dosesPerContainer?.let {
                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            } ?: "",
        )
    }
    var showCalculatorSheet by remember { mutableStateOf(false) }

    // Per-container prediction so users can verify their input makes sense
    val containerRemainingDoses = dosesPerContainer

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            headline = stringResource(Res.string.stock_estimated_doses_title),
            title = stringResource(Res.string.stock_estimated_doses_info),
            actionLabel = stringResource(Res.string.action_skip),
            onAction = onSkip,
        )

        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        stringResource(medication.type.pluralFormRes)
                        val containerLower = container?.let { stringResource(it.displayNameLowerRes) }
                            ?: stringResource(Res.string.stock_container_generic)
                        SmartListTextItem(
                            label = stringResource(Res.string.stock_settings_doses_per_container, containerLower),
                            value = dosesText,
                            onValueChange = { newValue ->
                                dosesText = newValue
                                onDosesPerContainerChange(newValue.toDoubleOrNull())
                            },
                            shapes = shapes,
                            visible = visible,
                            inputTransformation = DecimalInputTransformation(),
                            trailingAction = {
                                IconButton(onClick = { showCalculatorSheet = true }) {
                                    Icon(
                                        painter = painterResource(Res.drawable.calculate_24px),
                                        contentDescription = stringResource(Res.string.stock_calculator_toggle),
                                    )
                                }
                            },
                        )
                    },
                ),
            )

            // Live per-container prediction preview
            StockPredictionPreview(
                remainingDoses = containerRemainingDoses,
                dailyConsumption = dailyConsumption,
                context = PredictionContext.PER_CONTAINER,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    if (showCalculatorSheet) {
        CalculatorBottomSheet(
            onDismiss = { showCalculatorSheet = false },
            onApplyResult = { result ->
                val resultStr = if (result == result.toLong().toDouble()) {
                    result.toLong()
                        .toString()
                } else {
                    result.toString()
                }
                dosesText = resultStr
                onDosesPerContainerChange(result)
                showCalculatorSheet = false
            },
        )
    }
}

/**
 * ESTIMATED Step 4: Depletion reminder (optional, skippable).
 * Only shown when doses per container is configured.
 */
@Composable
private fun EstimatedDepletionStep(
    depletionReminderEnabled: Boolean,
    onDepletionReminderEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            headline = stringResource(Res.string.stock_depletion_reminder_label),
            title = stringResource(Res.string.stock_depletion_step_desc),
        )

        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListSwitchItem(
                            label = stringResource(Res.string.stock_depletion_reminder_label),
                            supportingText = stringResource(Res.string.stock_settings_depletion_reminder_desc),
                            checked = depletionReminderEnabled,
                            onCheckedChange = onDepletionReminderEnabledChange,
                            shapes = shapes,
                            visible = visible,
                        )
                    },
                ),
            )
        }
    }
}

/**
 * ESTIMATED Step 2: Current stock input (how many containers).
 */
@Composable
private fun EstimatedCurrentStockStep(
    containerCount: Int,
    onContainerCountChange: (Int) -> Unit,
    container: MedicationContainer?,
    containerStartedAt: Long?,
    onContainerStartedAtChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = platformContext()
    var countText by remember {
        mutableStateOf(containerCount.toString())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val containerLabel = container?.let {
        pluralStringResource(it.labelPluralRes, containerCount)
    } ?: stringResource(Res.string.stock_container_type_label)

    val startedDateText = containerStartedAt?.let { millis ->
        me.juliana.hellomeds.ui.util.formatDate(millis)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            headline = stringResource(Res.string.stock_discrete_current_title),
            title = stringResource(Res.string.stock_discrete_current_subtitle),
        )

        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListTextItem(
                            label = containerLabel,
                            value = countText,
                            onValueChange = { newValue ->
                                countText = newValue
                                val parsed = newValue.toIntOrNull()
                                if (parsed != null && parsed >= 0) {
                                    onContainerCountChange(parsed)
                                }
                            },
                            shapes = shapes,
                            visible = visible,
                            inputTransformation = IntegerInputTransformation(),
                        )
                    },
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListItem(
                            headlineContent = { Text(stringResource(Res.string.stock_container_started_label)) },
                            supportingContent = {
                                Text(startedDateText ?: stringResource(Res.string.stock_container_started_default))
                            },
                            shapes = shapes,
                            visible = visible,
                            onClick = { showDatePicker = true },
                        )
                    },
                ),
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = containerStartedAt ?: Clock.System.now().toEpochMilliseconds(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= Clock.System.now().toEpochMilliseconds()
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { onContainerStartedAtChange(it) }
                    showDatePicker = false
                }) {
                    Text(stringResource(Res.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onContainerStartedAtChange(null)
                    showDatePicker = false
                }) {
                    Text(stringResource(Res.string.stock_container_started_default))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * ESTIMATED Step 3: Low stock threshold + depletion reminder toggle.
 */
@Composable
private fun EstimatedLowStockStep(
    lowStockThreshold: Int?,
    onLowStockThresholdChange: (Int?) -> Unit,
    container: MedicationContainer?,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = platformContext()
    var thresholdText by remember {
        mutableStateOf(lowStockThreshold?.toString() ?: "2")
    }

    // Default to 2 if not yet set
    LaunchedEffect(Unit) {
        if (lowStockThreshold == null) {
            onLowStockThresholdChange(2)
        }
    }

    val containerLabel = container?.let {
        val count = thresholdText.toIntOrNull() ?: 2
        pluralStringResource(it.labelPluralRes, count)
    } ?: stringResource(Res.string.stock_container_type_label)

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            headline = stringResource(Res.string.stock_discrete_low_title),
            title = stringResource(Res.string.stock_discrete_low_subtitle),
            actionLabel = stringResource(Res.string.action_skip),
            onAction = onSkip,
        )

        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListTextItem(
                            label = containerLabel,
                            value = thresholdText,
                            onValueChange = { newValue ->
                                thresholdText = newValue
                                val parsed = newValue.toIntOrNull()
                                if (parsed != null && parsed > 0) {
                                    onLowStockThresholdChange(parsed)
                                }
                            },
                            shapes = shapes,
                            visible = visible,
                            inputTransformation = IntegerInputTransformation(),
                        )
                    },
                ),
            )
        }
    }
}

// endregion

/**
 * Calculate total stock for EXACT mode.
 */
private fun calculateExactTotal(state: AddStockTrackingState): Double {
    val full = state.fullContainers.toIntOrNull() ?: 0
    val partial = state.partialUnits.toDoubleOrNull() ?: 0.0
    val perContainer = state.packagingQuantity.toDoubleOrNull() ?: 1.0
    return (full * perContainer) + partial
}
