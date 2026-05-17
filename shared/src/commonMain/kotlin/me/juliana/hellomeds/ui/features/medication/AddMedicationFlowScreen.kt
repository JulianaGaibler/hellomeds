// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

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
import me.juliana.hellomeds.data.database.DefaultLabelType
import me.juliana.hellomeds.ui.compat.PlatformBackHandler
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.features.medication.steps.MedicationCycleStep
import me.juliana.hellomeds.ui.features.medication.steps.MedicationIconStep
import me.juliana.hellomeds.ui.features.medication.steps.MedicationNameStep
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
 * 5. Icon (preset grid + opt-in customizer)
 *
 * Reminder type defaults to the seeded FOLLOW_UPS label and is editable
 * from the edit screen.
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
    var iconCustomizing by remember { mutableStateOf(false) }
    // Guards against a rapid second tap on Finish inserting the medication twice
    // while the async insert + navigation is still in flight.
    var isSaving by remember { mutableStateOf(false) }

    val canProceed = when (currentStep) {
        0 -> state.name.isNotBlank()
        1 -> true // Type is always selected
        2 -> state.strengthValue.isNotBlank() && state.strengthValue.toDoubleOrNull() != null // Must have valid number (or use skip)
        3 -> !state.cycleEnabled || (state.cycleDaysActive > 0 && state.cycleDayInCycle in 1..(state.cycleDaysActive + state.cycleDaysBreak))
        4 -> true // Icon always has a default; presets just shortcut it
        else -> false
    }

    // Function to save medication to database
    fun saveMedication(onComplete: (Long) -> Unit) {
        // Silent safe default: FOLLOW_UPS is one of the 5 seeded labels and is
        // undeletable, so this lookup is guaranteed to find it. The user can
        // change the reminder type from the edit screen later.
        val importanceLabelId = importanceLabels
            .firstOrNull { it.defaultType == DefaultLabelType.FOLLOW_UPS.defaultType }
            ?.id ?: return
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
            // Capture the user's current TZ at point of creation as the safe default.
            // They can switch to LOCAL or re-anchor via the edit screen later.
            timeZoneMode = TimeZoneMode.FIXED,
            anchorTimeZone = TimeZone.currentSystemDefault().id,
        )
        medicationViewModel.insertMedication(medication) { id ->
            onComplete(id)
        }
    }

    // Handle back button to navigate between steps or close.
    // When the user is mid-customize on the icon step, back pops the customizer first
    // rather than stepping out of the wizard — matches the visual nesting.
    PlatformBackHandler {
        when {
            currentStep == 4 && iconCustomizing -> iconCustomizing = false
            currentStep > 0 -> currentStep--
            else -> onClose()
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
                            when {
                                currentStep == 4 && iconCustomizing -> iconCustomizing = false
                                currentStep > 0 -> currentStep--
                                else -> onClose()
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
                                if (currentStep < 4) {
                                    currentStep++
                                } else if (!isSaving) {
                                    isSaving = true
                                    saveMedication { medicationId ->
                                        onMedicationAdded(medicationId, state)
                                    }
                                }
                            },
                            enabled = canProceed && !isSaving,
                        ) {
                            Text(
                                if (currentStep < 4) {
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
            // The icon step manages its own scroll (LazyVerticalGrid / LazyColumn with sticky preview),
            // so it can't be wrapped in verticalScroll.
            if (currentStep == 4) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    MedicationIconStep(
                        foregroundShape = state.foregroundShape,
                        backgroundShape = state.backgroundShape,
                        color1 = state.color1,
                        customizing = iconCustomizing,
                        onCustomizingChange = { iconCustomizing = it },
                        onIconChange = { fg, bg, c ->
                            state = state.copy(foregroundShape = fg, backgroundShape = bg, color1 = c)
                        },
                    )
                }
            } else {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(bottom = 80.dp),
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
                    }
                }
            }
        }
    }
}
