// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.cycle_section_title
import me.juliana.hellomeds.shared.edit_medication_timezone_label
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
import me.juliana.hellomeds.ui.components.pickers.TimeZonePickerDialog
import me.juliana.hellomeds.ui.components.pickers.formatTimeZoneForDisplay
import me.juliana.hellomeds.ui.features.medication.steps.CycleConfigFields
import me.juliana.hellomeds.ui.features.medication.steps.CycleEnableToggle
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditMedicationTimeZoneScreen(
    draft: EditMedicationDraft,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTimeZonePicker by remember { mutableStateOf(false) }

    if (showTimeZonePicker) {
        TimeZonePickerDialog(
            selectedTimeZone = draft.anchorTimeZone,
            onSelect = { draft.anchorTimeZone = it },
            onDismiss = { showTimeZonePicker = false },
        )
    }

    EditMedicationSubpageScaffold(
        title = stringResource(Res.string.edit_medication_timezone_label),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        item {
            val fixed = draft.timeZoneMode == TimeZoneMode.FIXED
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListSwitchItem(
                            label = stringResource(Res.string.timezone_mode_label),
                            checked = fixed,
                            onCheckedChange = {
                                draft.setTimeZoneMode(
                                    if (it) TimeZoneMode.FIXED else TimeZoneMode.LOCAL,
                                    TimeZone.currentSystemDefault().id,
                                )
                            },
                            shapes = shapes,
                            visible = visible,
                            supportingText = if (fixed) {
                                stringResource(Res.string.timezone_mode_fixed_description)
                            } else {
                                stringResource(Res.string.timezone_mode_local_description)
                            },
                        )
                    },
                    SmartListItemConfig(visible = fixed && draft.anchorTimeZone != null) { shapes, visible ->
                        val (city, region) = formatTimeZoneForDisplay(draft.anchorTimeZone ?: "")
                        SmartListItem(
                            headlineContent = { Text(if (region.isNotEmpty()) "$city, $region" else city) },
                            supportingContent = { Text(stringResource(Res.string.timezone_anchor_description)) },
                            shapes = shapes,
                            visible = visible,
                            onClick = { showTimeZonePicker = true },
                        )
                    },
                    SmartListItemConfig(visible = fixed) { shapes, visible ->
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
    }
}

@Composable
fun EditMedicationCycleScreen(draft: EditMedicationDraft, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    EditMedicationSubpageScaffold(
        title = stringResource(Res.string.cycle_section_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        item {
            CycleEnableToggle(
                cycleEnabled = draft.cycleEnabled,
                onCycleEnabledChange = { draft.cycleEnabled = it },
            )
        }

        item {
            AnimatedVisibility(
                visible = draft.cycleEnabled,
                enter = expandVertically(expandFrom = Alignment.CenterVertically) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.CenterVertically) + fadeOut(),
            ) {
                CycleConfigFields(
                    cycleDaysActive = draft.cycleDaysActive,
                    cycleDaysBreak = draft.cycleDaysBreak,
                    cycleHasPlacebos = draft.cycleHasPlacebos,
                    cycleDayInCycle = draft.cycleDayInCycle,
                    onDaysActiveChange = { draft.cycleDaysActive = it },
                    onDaysBreakChange = { draft.cycleDaysBreak = it },
                    onHasPlacebosChange = { draft.cycleHasPlacebos = it },
                    onDayInCycleChange = { draft.cycleDayInCycle = it },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMedicationSubpageScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                        )
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
            content = content,
        )
    }
}
