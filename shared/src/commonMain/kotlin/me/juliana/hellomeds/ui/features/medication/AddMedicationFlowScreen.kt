// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_finish
import me.juliana.hellomeds.shared.action_next
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.screen_add_medication
import me.juliana.hellomeds.ui.compat.PlatformBackHandler
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.features.medication.steps.ImportanceLabelStep
import me.juliana.hellomeds.ui.features.medication.steps.MedicationColorsStep
import me.juliana.hellomeds.ui.features.medication.steps.MedicationCycleStep
import me.juliana.hellomeds.ui.features.medication.steps.MedicationNameStep
import me.juliana.hellomeds.ui.features.medication.steps.MedicationShapeStep
import me.juliana.hellomeds.ui.features.medication.steps.MedicationStrengthStep
import me.juliana.hellomeds.ui.features.medication.steps.MedicationTypeStep
import me.juliana.hellomeds.ui.viewmodel.MedicationViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Multi-step flow for adding a new medication
 *
 * Steps:
 * 1. Name
 * 2. Type (tablet, capsule, etc.)
 * 3. Strength (optional)
 * 4. Dosing Cycle (optional)
 * 5. Shape (visual)
 * 6. Colors (visual)
 * 7. Importance label
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationFlowScreen(
    importanceLabels: List<ImportanceLabel>,
    onClose: () -> Unit,
    onMedicationAdded: (Long, AddMedicationState) -> Unit,
    initialState: AddMedicationState? = null,
    modifier: Modifier = Modifier,
    medicationViewModel: MedicationViewModel = koinViewModel(),
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(initialState ?: AddMedicationState()) }

    val canProceed = when (currentStep) {
        0 -> state.name.isNotBlank()
        1 -> true // Type is always selected
        2 -> state.strengthValue.isNotBlank() && state.strengthValue.toDoubleOrNull() != null // Must have valid number (or use skip)
        3 -> !state.cycleEnabled || (state.cycleDaysActive > 0 && state.cycleDayInCycle in 1..(state.cycleDaysActive + state.cycleDaysBreak))
        4 -> true // Shape is always selected
        5 -> true // Colors are optional
        6 -> state.importanceLabelId != null
        else -> false
    }

    // Function to save medication to database
    fun saveMedication(onComplete: (Long) -> Unit) {
        val importanceLabelId = state.importanceLabelId ?: return
        val medication = Medication(
            name = state.name.trim(),
            type = state.type,
            shape = "", // Deprecated field, using foregroundShape/backgroundShape instead
            importanceLabelId = importanceLabelId,
            strengthValue = state.strengthValue.toDoubleOrNull(),
            strengthUnit = if (state.strengthValue.isNotBlank()) state.strengthUnit else null,
            foregroundShape = state.foregroundShape.name,
            backgroundShape = state.backgroundShape.name,
            shapeColor = state.color1?.let { it::class.simpleName },
            cycleType = if (state.cycleEnabled) CycleType.CYCLIC else CycleType.NONE,
            cycleDaysActive = if (state.cycleEnabled) state.cycleDaysActive else null,
            cycleDaysBreak = if (state.cycleEnabled) state.cycleDaysBreak else null,
            cycleHasPlacebos = state.cycleEnabled && state.cycleHasPlacebos,
            cycleStartDate = if (state.cycleEnabled) {
                val today = kotlin.time.Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                today.minus(state.cycleDayInCycle - 1, DateTimeUnit.DAY)
            } else {
                null
            },
            timeZoneMode = state.timeZoneMode,
            anchorTimeZone = if (state.timeZoneMode == TimeZoneMode.FIXED) state.anchorTimeZone else null,
        )
        medicationViewModel.insertMedication(medication) { id ->
            onComplete(id)
        }
    }

    // Handle back button to navigate between steps or close
    PlatformBackHandler {
        if (currentStep > 0) {
            currentStep--
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
                    title = { Text(stringResource(Res.string.screen_add_medication)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentStep > 0) {
                                currentStep--
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
                                if (currentStep < 6) {
                                    currentStep++
                                } else {
                                    saveMedication { medicationId ->
                                        onMedicationAdded(medicationId, state)
                                    }
                                }
                            },
                            enabled = canProceed,
                        ) {
                            Text(
                                if (currentStep < 6) {
                                    stringResource(Res.string.action_next)
                                } else {
                                    stringResource(
                                        Res.string.action_finish,
                                    )
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
            // For shape and color screens, don't use Column scroll - they handle their own layout
            if (currentStep == 4 || currentStep == 5) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 32.dp),
                ) {
                    when (currentStep) {
                        4 -> MedicationShapeStep(
                            foregroundShape = state.foregroundShape,
                            backgroundShape = state.backgroundShape,
                            onForegroundShapeChange = { state = state.copy(foregroundShape = it) },
                            onBackgroundShapeChange = { state = state.copy(backgroundShape = it) },
                        )

                        5 -> MedicationColorsStep(
                            foregroundShape = state.foregroundShape,
                            backgroundShape = state.backgroundShape,
                            color1 = state.color1,
                            onColor1Change = { state = state.copy(color1 = it) },
                        )
                    }
                }
            } else {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(start = 32.dp, end = 32.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (currentStep) {
                        0 -> MedicationNameStep(
                            name = state.name,
                            onNameChange = { state = state.copy(name = it) },
                            detectedNames = state.detectedNames,
                        )

                        1 -> MedicationTypeStep(
                            selectedType = state.type,
                            onTypeSelected = { state = state.copy(type = it) },
                            detectedTypes = state.detectedTypes,
                        )

                        2 -> MedicationStrengthStep(
                            strengthValue = state.strengthValue,
                            strengthUnit = state.strengthUnit,
                            onStrengthValueChange = { state = state.copy(strengthValue = it) },
                            onStrengthUnitChange = { state = state.copy(strengthUnit = it) },
                            onSkip = {
                                state = state.copy(strengthValue = "", strengthUnit = MedicationStrengthUnit.MG)
                                currentStep++
                            },
                            detectedStrengthValue = state.detectedStrengthValue,
                            detectedStrengthUnit = state.detectedStrengthUnit,
                        )

                        3 -> MedicationCycleStep(
                            timeZoneMode = state.timeZoneMode,
                            onTimeZoneModeChange = { state = state.copy(timeZoneMode = it) },
                            anchorTimeZone = state.anchorTimeZone,
                            onAnchorTimeZoneChange = { state = state.copy(anchorTimeZone = it) },
                            cycleEnabled = state.cycleEnabled,
                            cycleDaysActive = state.cycleDaysActive,
                            cycleDaysBreak = state.cycleDaysBreak,
                            cycleHasPlacebos = state.cycleHasPlacebos,
                            cycleDayInCycle = state.cycleDayInCycle,
                            onCycleEnabledChange = { state = state.copy(cycleEnabled = it) },
                            onDaysActiveChange = { state = state.copy(cycleDaysActive = it) },
                            onDaysBreakChange = { state = state.copy(cycleDaysBreak = it) },
                            onHasPlacebosChange = { state = state.copy(cycleHasPlacebos = it) },
                            onDayInCycleChange = { state = state.copy(cycleDayInCycle = it) },
                        )

                        6 -> ImportanceLabelStep(
                            labels = importanceLabels,
                            selectedLabelId = state.importanceLabelId,
                            onLabelSelected = { state = state.copy(importanceLabelId = it) },
                        )
                    }
                }
            }
        }
    }
}
