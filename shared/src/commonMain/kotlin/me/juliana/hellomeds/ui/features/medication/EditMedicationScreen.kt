// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.data.model.getCycleDay
import me.juliana.hellomeds.domain.validation.MedicationValidation
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_save
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.content_description_clear_display_name
import me.juliana.hellomeds.shared.cycle_enabled
import me.juliana.hellomeds.shared.cycle_enabled_description
import me.juliana.hellomeds.shared.cycle_info_calendar_days
import me.juliana.hellomeds.shared.cycle_section_title
import me.juliana.hellomeds.shared.medication_display_name_description
import me.juliana.hellomeds.shared.medication_display_name_label
import me.juliana.hellomeds.shared.medication_edit_base_data
import me.juliana.hellomeds.shared.medication_edit_title_format
import me.juliana.hellomeds.shared.medication_icon_label
import me.juliana.hellomeds.shared.medication_name_label
import me.juliana.hellomeds.shared.medication_name_section
import me.juliana.hellomeds.shared.timezone_anchor_description
import me.juliana.hellomeds.shared.timezone_mode_fixed_description
import me.juliana.hellomeds.shared.timezone_mode_fixed_hint
import me.juliana.hellomeds.shared.timezone_mode_label
import me.juliana.hellomeds.shared.timezone_mode_local_description
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListSwitchItem
import me.juliana.hellomeds.ui.components.medication.MedicationColorPickers
import me.juliana.hellomeds.ui.components.medication.MedicationIconPreview
import me.juliana.hellomeds.ui.components.medication.MedicationShapePickers
import me.juliana.hellomeds.ui.components.pickers.TimeZonePickerDialog
import me.juliana.hellomeds.ui.components.pickers.formatTimeZoneForDisplay
import me.juliana.hellomeds.ui.theme.MedicationColor
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMedicationScreen(
    medication: Medication,
    onNavigateBack: () -> Unit,
    onSave: (Medication) -> Unit,
    onEditBaseData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEditBaseDataDialog by remember { mutableStateOf(false) }

    var displayName by remember(medication) {
        mutableStateOf(medication.displayName ?: "")
    }

    // Parse current shapes and colors from medication
    var foregroundShape by remember(medication) {
        mutableStateOf(
            MedicationForegroundShape.fromNameOrDefault(
                medication.foregroundShape,
                MedicationForegroundShape.CAPSULE_PILL,
            ),
        )
    }

    var backgroundShape by remember(medication) {
        mutableStateOf(
            MedicationBackgroundShape.fromNameOrDefault(medication.backgroundShape),
        )
    }

    var color1 by remember(medication) {
        mutableStateOf(
            medication.shapeColor?.let {
                MedicationColor.fromName(it)
            },
        )
    }

    // Timezone mode state
    var timeZoneMode by remember(medication) { mutableStateOf(medication.timeZoneMode) }
    var anchorTimeZone by remember(medication) { mutableStateOf(medication.anchorTimeZone) }
    var showTimeZonePicker by remember { mutableStateOf(false) }

    // Cycle state
    val isCyclic = medication.cycleType == CycleType.CYCLIC
    var cycleEnabled by remember(medication) { mutableStateOf(isCyclic) }
    var cycleDaysActive by remember(medication) { mutableIntStateOf(medication.cycleDaysActive ?: 21) }
    var cycleDaysBreak by remember(medication) { mutableIntStateOf(medication.cycleDaysBreak ?: 7) }
    var cycleHasPlacebos by remember(medication) { mutableStateOf(medication.cycleHasPlacebos) }

    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val initialDay = if (isCyclic) {
        getCycleDay(medication, today)?.let { it.dayInCycle + 1 } ?: 1
    } else {
        1
    }
    var cycleDayInCycle by remember(medication) { mutableIntStateOf(initialDay) }
    val cycleLength = cycleDaysActive + cycleDaysBreak

    fun buildUpdatedMedication(): Medication {
        val cycleStartDate = if (cycleEnabled) {
            today.minus(cycleDayInCycle - 1, DateTimeUnit.DAY)
        } else {
            null
        }

        return medication.copy(
            displayName = displayName.trim().ifBlank { null },
            foregroundShape = foregroundShape.name,
            backgroundShape = backgroundShape.name,
            shapeColor = color1?.let { it::class.simpleName },
            cycleType = if (cycleEnabled) CycleType.CYCLIC else CycleType.NONE,
            cycleDaysActive = if (cycleEnabled) cycleDaysActive else null,
            cycleDaysBreak = if (cycleEnabled) cycleDaysBreak else null,
            cycleHasPlacebos = cycleEnabled && cycleHasPlacebos,
            cycleStartDate = cycleStartDate,
            timeZoneMode = timeZoneMode,
            anchorTimeZone = if (timeZoneMode == TimeZoneMode.FIXED) anchorTimeZone else null,
        )
    }

    if (showTimeZonePicker) {
        TimeZonePickerDialog(
            selectedTimeZone = anchorTimeZone,
            onSelect = { anchorTimeZone = it },
            onDismiss = { showTimeZonePicker = false },
        )
    }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.medication_edit_title_format, medication.name)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                        )
                    }
                },
                actions = {
                    val canSave = !cycleEnabled || (cycleDaysActive > 0 && cycleDayInCycle in 1..cycleLength && cycleLength <= 365)
                    Button(
                        onClick = {
                            onSave(buildUpdatedMedication())
                            onNavigateBack()
                        },
                        enabled = canSave,
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Name section header
            item {
                Text(
                    text = stringResource(Res.string.medication_name_section),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Medication name (readonly)
            item {
                OutlinedTextField(
                    value = medication.name,
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.medication_name_label)) },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Display name field
            item {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        if (it.length <= MedicationValidation.MAX_DISPLAY_NAME_LENGTH) {
                            displayName = it
                        }
                    },
                    label = { Text(stringResource(Res.string.medication_display_name_label)) },
                    supportingText = {
                        Text(stringResource(Res.string.medication_display_name_description))
                    },
                    trailingIcon = {
                        if (displayName.isNotEmpty()) {
                            IconButton(onClick = { displayName = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(
                                        Res.string.content_description_clear_display_name,
                                    ),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Timezone mode section ──
            item {
                Text(
                    text = stringResource(Res.string.timezone_mode_label),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.timezone_mode_label),
                                checked = timeZoneMode == TimeZoneMode.FIXED,
                                onCheckedChange = {
                                    val newMode = if (it) TimeZoneMode.FIXED else TimeZoneMode.LOCAL
                                    timeZoneMode = newMode
                                    if (newMode == TimeZoneMode.FIXED && anchorTimeZone == null) {
                                        anchorTimeZone = TimeZone.currentSystemDefault().id
                                    }
                                },
                                shapes = shapes,
                                visible = visible,
                                supportingText = if (timeZoneMode == TimeZoneMode.FIXED) {
                                    stringResource(Res.string.timezone_mode_fixed_description)
                                } else {
                                    stringResource(Res.string.timezone_mode_local_description)
                                },
                            )
                        },
                        SmartListItemConfig(
                            visible = timeZoneMode == TimeZoneMode.FIXED && anchorTimeZone != null,
                        ) { shapes, visible ->
                            val (city, region) = formatTimeZoneForDisplay(anchorTimeZone ?: "")
                            val label = if (region.isNotEmpty()) "$city, $region" else city
                            SmartListItem(
                                headlineContent = { Text(label) },
                                supportingContent = { Text(stringResource(Res.string.timezone_anchor_description)) },
                                shapes = shapes,
                                visible = visible,
                                onClick = { showTimeZonePicker = true },
                            )
                        },
                        SmartListItemConfig(visible = timeZoneMode == TimeZoneMode.FIXED) { shapes, visible ->
                            SmartListInfoCard(
                                headlineContent = {
                                    Text(
                                        stringResource(Res.string.timezone_mode_fixed_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                    ),
                )
            }

            // ── Dosing Cycle section ──
            item {
                Text(
                    text = stringResource(Res.string.cycle_section_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Enable toggle with explanation
            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.cycle_enabled),
                                checked = cycleEnabled,
                                onCheckedChange = { cycleEnabled = it },
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.cycle_enabled_description),
                            )
                        },
                        SmartListItemConfig(visible = cycleEnabled) { shapes, visible ->
                            SmartListInfoCard(
                                headlineContent = {
                                    Text(
                                        stringResource(Res.string.cycle_info_calendar_days),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                    ),
                )
            }

            // Cycle config (when enabled) — reuses shared CycleConfigFields
            item {
                AnimatedVisibility(
                    visible = cycleEnabled,
                    enter = androidx.compose.animation.expandVertically(
                        expandFrom = androidx.compose.ui.Alignment.CenterVertically,
                    ) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically(
                        shrinkTowards = androidx.compose.ui.Alignment.CenterVertically,
                    ) + androidx.compose.animation.fadeOut(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        me.juliana.hellomeds.ui.features.medication.steps.CycleConfigFields(
                            cycleDaysActive = cycleDaysActive,
                            cycleDaysBreak = cycleDaysBreak,
                            cycleHasPlacebos = cycleHasPlacebos,
                            cycleDayInCycle = cycleDayInCycle,
                            cycleLength = cycleLength,
                            onDaysActiveChange = { cycleDaysActive = it },
                            onDaysBreakChange = { cycleDaysBreak = it },
                            onHasPlacebosChange = { cycleHasPlacebos = it },
                            onDayInCycleChange = { cycleDayInCycle = it },
                        )
                    }
                }
            }

            // ── Icon section ──
            item {
                Text(
                    text = stringResource(Res.string.medication_icon_label),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Sticky preview with morphing animation
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    MedicationIconPreview(
                        foregroundShape = foregroundShape,
                        backgroundShape = backgroundShape,
                        color1 = color1,
                    )
                }
            }

            // Shape pickers
            item {
                MedicationShapePickers(
                    foregroundShape = foregroundShape,
                    backgroundShape = backgroundShape,
                    onForegroundShapeChange = { foregroundShape = it },
                    onBackgroundShapeChange = { backgroundShape = it },
                )
            }

            // Color pickers
            item {
                MedicationColorPickers(
                    color1 = color1,
                    onColor1Change = { color1 = it },
                )
            }

            // Edit base data button
            item {
                Button(
                    onClick = { showEditBaseDataDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.medication_edit_base_data))
                }
            }
        }

        // Show edit base data dialog if requested
        if (showEditBaseDataDialog) {
            EditBaseDataDialog(
                onDismiss = { showEditBaseDataDialog = false },
                onConfirm = {
                    showEditBaseDataDialog = false
                    onEditBaseData()
                },
            )
        }
    }
}
