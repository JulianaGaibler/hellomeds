// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import me.juliana.hellomeds.shared.timezone_mode_detail_local
import me.juliana.hellomeds.shared.timezone_mode_detail_fixed
import me.juliana.hellomeds.shared.cycle_summary_off
import me.juliana.hellomeds.shared.cycle_summary_format
import me.juliana.hellomeds.shared.edit_medication_timezone_label
import me.juliana.hellomeds.shared.unlogged_dose_take_hint
import me.juliana.hellomeds.shared.unlogged_dose_action_skip
import me.juliana.hellomeds.shared.unlogged_dose_action_take
import me.juliana.hellomeds.shared.unlogged_dose_section_title
import me.juliana.hellomeds.data.model.enums.UnloggedDoseAction
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.domain.validation.MedicationValidation
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_save
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.content_description_clear_display_name
import me.juliana.hellomeds.shared.cycle_section_title
import me.juliana.hellomeds.shared.medication_display_name_description
import me.juliana.hellomeds.shared.medication_display_name_label
import me.juliana.hellomeds.shared.medication_edit_title_format
import me.juliana.hellomeds.shared.medication_has_strength
import me.juliana.hellomeds.shared.medication_icon_label
import me.juliana.hellomeds.shared.medication_name_description
import me.juliana.hellomeds.shared.medication_name_label
import me.juliana.hellomeds.shared.medication_name_section
import me.juliana.hellomeds.shared.medication_strength_unit
import me.juliana.hellomeds.shared.medication_strength_value
import me.juliana.hellomeds.shared.medication_type_label
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListDropdownItem
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.components.medication.MedicationIconCustomizer
import me.juliana.hellomeds.ui.components.medication.MedicationIconStickyPreview
import me.juliana.hellomeds.ui.components.pickers.formatTimeZoneForDisplay
import me.juliana.hellomeds.ui.features.settings.SettingsHeader
import me.juliana.hellomeds.ui.features.settings.settingsContentPadding
import me.juliana.hellomeds.ui.util.displayNameRes
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditMedicationScreen(
    draft: EditMedicationDraft,
    onNavigateBack: () -> Unit,
    onSave: ((Medication) -> Medication) -> Unit,
    onOpenTimeZone: () -> Unit,
    onOpenCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.medication_edit_title_format, draft.name)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            onSave(draft::applyTo)
                            onNavigateBack()
                        },
                        enabled = draft.canSave,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Name section ──
            // Headers use compact paddings since this LazyColumn has spacedBy(8.dp) which
            // would otherwise stack on top of the SettingsHeader defaults. The first header sits
            // closer to the top edge; subsequent headers get extra topPadding for visual breathing.
            item {
                SettingsHeader(
                    text = stringResource(Res.string.medication_name_section),
                    topPadding = 8.dp,
                    bottomPadding = 4.dp,
                )
            }

            // Editable medication name
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = {
                        if (it.length <= MedicationValidation.MAX_NAME_LENGTH) {
                            draft.name = it
                        }
                    },
                    label = { Text(stringResource(Res.string.medication_name_label)) },
                    isError = draft.nameError != null,
                    supportingText = {
                        Text(draft.nameError ?: stringResource(Res.string.medication_name_description))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Display name field
            item {
                OutlinedTextField(
                    value = draft.displayName,
                    onValueChange = {
                        if (it.length <= MedicationValidation.MAX_DISPLAY_NAME_LENGTH) {
                            draft.displayName = it
                        }
                    },
                    label = { Text(stringResource(Res.string.medication_display_name_label)) },
                    supportingText = {
                        Text(stringResource(Res.string.medication_display_name_description))
                    },
                    trailingIcon = {
                        if (draft.displayName.isNotEmpty()) {
                            IconButton(onClick = { draft.displayName = "" }) {
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

            item {
                val typeEntries = remember { MedicationType.entries }
                val typeDisplayNames = typeEntries.map { stringResource(it.displayNameRes) }
                val selectedTypeDisplay = stringResource(draft.type.displayNameRes)
                val takeUnlogged = draft.unloggedDoseAction == UnloggedDoseAction.TAKE
                val skipLabel = stringResource(Res.string.unlogged_dose_action_skip)
                val takeLabel = stringResource(Res.string.unlogged_dose_action_take)

                val timeZoneDescription = if (draft.timeZoneMode == TimeZoneMode.FIXED) {
                    val (city, region) = formatTimeZoneForDisplay(draft.anchorTimeZone ?: "")
                    stringResource(
                        Res.string.timezone_mode_detail_fixed,
                        if (region.isNotEmpty()) "$city, $region" else city,
                    )
                } else {
                    stringResource(Res.string.timezone_mode_detail_local)
                }
                val cycleDescription = if (draft.cycleEnabled) {
                    stringResource(Res.string.cycle_summary_format, draft.cycleDaysActive, draft.cycleDaysBreak)
                } else {
                    stringResource(Res.string.cycle_summary_off)
                }

                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(Res.string.edit_medication_timezone_label),
                                    )
                                },
                                supportingContent = { Text(timeZoneDescription) },
                                shapes = shapes,
                                visible = visible,
                                onClick = onOpenTimeZone,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.cycle_section_title)) },
                                supportingContent = { Text(cycleDescription) },
                                shapes = shapes,
                                visible = visible,
                                onClick = onOpenCycle,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListDropdownItem(
                                label = stringResource(Res.string.unlogged_dose_section_title),
                                selectedValue = if (takeUnlogged) takeLabel else skipLabel,
                                options = listOf(skipLabel, takeLabel),
                                onValueChange = {
                                    draft.unloggedDoseAction =
                                        if (it == takeLabel) UnloggedDoseAction.TAKE else UnloggedDoseAction.SKIP
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = takeUnlogged) { shapes, visible ->
                            SmartListInfoCard(
                                headlineContent = {
                                    Text(
                                        stringResource(Res.string.unlogged_dose_take_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListDropdownItem(
                                label = stringResource(Res.string.medication_type_label),
                                selectedValue = selectedTypeDisplay,
                                options = typeDisplayNames,
                                onValueChange = { picked ->
                                    val idx = typeDisplayNames.indexOf(picked)
                                    if (idx >= 0) draft.type = typeEntries[idx]
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.medication_has_strength)) },
                                trailingContent = {
                                    Switch(
                                        checked = draft.hasStrength,
                                        onCheckedChange = { draft.hasStrength = it },
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = draft.hasStrength) { shapes, visible ->
                            SmartListTextItem(
                                label = stringResource(Res.string.medication_strength_value),
                                value = draft.strengthValue,
                                onValueChange = { draft.strengthValue = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shapes = shapes,
                                visible = visible,
                                validator = { text ->
                                    text.isEmpty() || text.toDoubleOrNull()?.let { it > 0 } == true
                                },
                                inputTransformation = DecimalInputTransformation(),
                            )
                        },
                        SmartListItemConfig(visible = draft.hasStrength) { shapes, visible ->
                            SmartListDropdownItem(
                                label = stringResource(Res.string.medication_strength_unit),
                                selectedValue = draft.strengthUnit.value,
                                options = MedicationStrengthUnit.allDisplayValues(),
                                onValueChange = {
                                    draft.strengthUnit =
                                        MedicationStrengthUnit.fromValue(it) ?: MedicationStrengthUnit.MG
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                    ),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            val strengthError = draft.strengthError
            if (draft.hasStrength && strengthError != null) {
                item {
                    Text(
                        text = strengthError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.settingsContentPadding(),
                    )
                }
            }

            // ── Icon section ──
            item {
                SettingsHeader(
                    text = stringResource(Res.string.medication_icon_label),
                    topPadding = 24.dp,
                    bottomPadding = 0.dp,
                )
            }

            stickyHeader {
                MedicationIconStickyPreview(
                    foregroundShape = draft.foregroundShape,
                    backgroundShape = draft.backgroundShape,
                    color1 = draft.color1,
                )
            }

            item {
                MedicationIconCustomizer(
                    foregroundShape = draft.foregroundShape,
                    backgroundShape = draft.backgroundShape,
                    color1 = draft.color1,
                    onForegroundShapeChange = { draft.foregroundShape = it },
                    onBackgroundShapeChange = { draft.backgroundShape = it },
                    onColor1Change = { draft.color1 = it },
                    showPreview = false,
                )
            }
        }
    }
}
