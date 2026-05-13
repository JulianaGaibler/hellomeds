// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.cycle_active_days
import me.juliana.hellomeds.shared.cycle_active_days_hint
import me.juliana.hellomeds.shared.cycle_break_days
import me.juliana.hellomeds.shared.cycle_enabled
import me.juliana.hellomeds.shared.cycle_enabled_description
import me.juliana.hellomeds.shared.cycle_has_placebos
import me.juliana.hellomeds.shared.cycle_info_calendar_days
import me.juliana.hellomeds.shared.cycle_placebos_off_description
import me.juliana.hellomeds.shared.cycle_placebos_on_description
import me.juliana.hellomeds.shared.cycle_preset_label
import me.juliana.hellomeds.shared.cycle_section_title
import me.juliana.hellomeds.shared.timezone_anchor_description
import me.juliana.hellomeds.shared.timezone_mode_fixed_description
import me.juliana.hellomeds.shared.timezone_mode_fixed_hint
import me.juliana.hellomeds.shared.timezone_mode_label
import me.juliana.hellomeds.shared.timezone_mode_local_description
import me.juliana.hellomeds.shared.wizard_cycle_headline
import me.juliana.hellomeds.shared.wizard_cycle_title
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.IntegerInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListSwitchItem
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.components.medication.CycleDayPicker
import me.juliana.hellomeds.ui.components.medication.cyclePresets
import me.juliana.hellomeds.ui.components.pickers.TimeZonePickerDialog
import me.juliana.hellomeds.ui.components.pickers.formatTimeZoneForDisplay
import me.juliana.hellomeds.ui.test.TestTags
import org.jetbrains.compose.resources.stringResource

/**
 * Step 4: Schedule settings — timezone mode + dosing cycle.
 * Timezone mode at the top, cycle config below (optional).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicationCycleStep(
    timeZoneMode: TimeZoneMode,
    onTimeZoneModeChange: (TimeZoneMode) -> Unit,
    anchorTimeZone: String?,
    onAnchorTimeZoneChange: (String?) -> Unit,
    cycleEnabled: Boolean,
    cycleDaysActive: Int,
    cycleDaysBreak: Int,
    cycleHasPlacebos: Boolean,
    cycleDayInCycle: Int,
    onCycleEnabledChange: (Boolean) -> Unit,
    onDaysActiveChange: (Int) -> Unit,
    onDaysBreakChange: (Int) -> Unit,
    onHasPlacebosChange: (Boolean) -> Unit,
    onDayInCycleChange: (Int) -> Unit,
) {
    val cycleLength = cycleDaysActive + cycleDaysBreak
    var showTimeZonePicker by remember { mutableStateOf(false) }

    if (showTimeZonePicker) {
        TimeZonePickerDialog(
            selectedTimeZone = anchorTimeZone,
            onSelect = { onAnchorTimeZoneChange(it) },
            onDismiss = { showTimeZonePicker = false },
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            headline = stringResource(Res.string.wizard_cycle_headline),
            title = stringResource(Res.string.wizard_cycle_title),
        )

        // ── Timezone mode ──
        Text(
            text = stringResource(Res.string.timezone_mode_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        AutoSmartList(
            items = listOf(
                SmartListItemConfig(visible = true) { shapes, visible ->
                    SmartListSwitchItem(
                        label = stringResource(Res.string.timezone_mode_label),
                        checked = timeZoneMode == TimeZoneMode.FIXED,
                        onCheckedChange = {
                            val newMode = if (it) TimeZoneMode.FIXED else TimeZoneMode.LOCAL
                            onTimeZoneModeChange(newMode)
                            if (newMode == TimeZoneMode.FIXED && anchorTimeZone == null) {
                                onAnchorTimeZoneChange(TimeZone.currentSystemDefault().id)
                            }
                        },
                        shapes = shapes,
                        modifier = Modifier.testTag(TestTags.TIMEZONE_TOGGLE),
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

        // ── Dosing cycle ──
        Text(
            text = stringResource(Res.string.cycle_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Enable toggle
        AutoSmartList(
            items = listOf(
                SmartListItemConfig(visible = true) { shapes, visible ->
                    SmartListSwitchItem(
                        label = stringResource(Res.string.cycle_enabled),
                        checked = cycleEnabled,
                        onCheckedChange = onCycleEnabledChange,
                        shapes = shapes,
                        modifier = Modifier.testTag(TestTags.CYCLE_TOGGLE),
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

        // Expanded cycle config (only shown when enabled)
        AnimatedVisibility(
            visible = cycleEnabled,
            enter = expandVertically(expandFrom = Alignment.CenterVertically) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.CenterVertically) + fadeOut(),
            modifier = Modifier.testTag(TestTags.CYCLE_CONFIG),
        ) {
            CycleConfigFields(
                cycleDaysActive = cycleDaysActive,
                cycleDaysBreak = cycleDaysBreak,
                cycleHasPlacebos = cycleHasPlacebos,
                cycleDayInCycle = cycleDayInCycle,
                cycleLength = cycleLength,
                onDaysActiveChange = onDaysActiveChange,
                onDaysBreakChange = onDaysBreakChange,
                onHasPlacebosChange = onHasPlacebosChange,
                onDayInCycleChange = onDayInCycleChange,
            )
        }
    }
}

/**
 * Reusable cycle configuration fields — used by both the wizard step and the edit screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CycleConfigFields(
    cycleDaysActive: Int,
    cycleDaysBreak: Int,
    cycleHasPlacebos: Boolean,
    cycleDayInCycle: Int,
    cycleLength: Int,
    onDaysActiveChange: (Int) -> Unit,
    onDaysBreakChange: (Int) -> Unit,
    onHasPlacebosChange: (Boolean) -> Unit,
    onDayInCycleChange: (Int) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Preset chips
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.cycle_preset_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cyclePresets.forEach { preset ->
                    val description = stringResource(preset.descriptionRes)
                    FilterChip(
                        selected = cycleDaysActive == preset.active && cycleDaysBreak == preset.breakDays,
                        onClick = {
                            onDaysActiveChange(preset.active)
                            onDaysBreakChange(preset.breakDays)
                            // Clamp dayInCycle if it exceeds the new cycle length
                            val newLength = preset.active + preset.breakDays
                            if (cycleDayInCycle > newLength) {
                                onDayInCycleChange(newLength)
                            }
                        },
                        label = { Text(stringResource(preset.labelRes)) },
                        modifier = Modifier.semantics { contentDescription = description },
                    )
                }
            }
        }

        // Active / break days inputs
        AutoSmartList(
            items = listOf(
                SmartListItemConfig(visible = true) { shapes, visible ->
                    SmartListTextItem(
                        label = stringResource(Res.string.cycle_active_days),
                        value = if (cycleDaysActive == 0) "" else cycleDaysActive.toString(),
                        onValueChange = { value ->
                            onDaysActiveChange(value.toIntOrNull() ?: 0)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shapes = shapes,
                        visible = visible,
                        supportingText = if (cycleDaysActive == 0) {
                            stringResource(
                                Res.string.cycle_active_days_hint,
                            )
                        } else {
                            null
                        },
                        validator = { text ->
                            text.isEmpty() || text.toIntOrNull()?.let { it in 1..365 } == true
                        },
                        inputTransformation = IntegerInputTransformation(),
                    )
                },
                SmartListItemConfig(visible = true) { shapes, visible ->
                    SmartListTextItem(
                        label = stringResource(Res.string.cycle_break_days),
                        value = if (cycleDaysBreak == 0 && cycleDaysActive > 0) "0" else cycleDaysBreak.toString(),
                        onValueChange = { value ->
                            onDaysBreakChange(value.toIntOrNull() ?: 0)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shapes = shapes,
                        visible = visible,
                        validator = { text ->
                            text.isEmpty() || text.toIntOrNull()?.let { it in 0..365 } == true
                        },
                        inputTransformation = IntegerInputTransformation(),
                    )
                },
            ),
        )

        // Placebo toggle + impact description (only relevant when there are break days)
        if (cycleDaysBreak > 0) {
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListSwitchItem(
                            label = stringResource(Res.string.cycle_has_placebos),
                            checked = cycleHasPlacebos,
                            onCheckedChange = onHasPlacebosChange,
                            shapes = shapes,
                            visible = visible,
                            supportingText = if (cycleHasPlacebos) {
                                stringResource(Res.string.cycle_placebos_on_description)
                            } else {
                                stringResource(Res.string.cycle_placebos_off_description)
                            },
                        )
                    },
                ),
            )
        }

        // Current day in cycle + live preview
        CycleDayPicker(
            daysActive = cycleDaysActive,
            daysBreak = cycleDaysBreak,
            dayInCycle = cycleDayInCycle.toString(),
            hasPlacebos = cycleHasPlacebos,
            onDayInCycleChange = { value ->
                onDayInCycleChange(value.toIntOrNull() ?: 1)
            },
        )
    }
}
