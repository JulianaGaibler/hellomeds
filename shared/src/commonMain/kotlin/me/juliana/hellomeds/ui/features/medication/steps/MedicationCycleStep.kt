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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import me.juliana.hellomeds.shared.wizard_cycle_headline
import androidx.compose.foundation.layout.padding
import me.juliana.hellomeds.ui.components.common.ScreenHeader
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.IntegerInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListSwitchItem
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.components.medication.CycleDayPicker
import me.juliana.hellomeds.ui.components.medication.cyclePresets
import me.juliana.hellomeds.ui.test.TestTags
import org.jetbrains.compose.resources.stringResource

/**
 * Wizard step: cyclic-medication setup. The toggle enables cyclic dosing;
 * an expanded config block (active/break days, current day, placebos) is
 * shown when enabled. Time-zone behavior is no longer asked here — new
 * medications inherit FIXED-to-current-system-tz at save time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicationCycleStep(
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

    Column {
        ScreenHeader(
            headline = stringResource(Res.string.wizard_cycle_headline),
            title = stringResource(Res.string.cycle_enabled_description),
        )

        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CycleEnableToggle(
                cycleEnabled = cycleEnabled,
                onCycleEnabledChange = onCycleEnabledChange,
                showDescription = false,
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
}

/**
 * Switch + "calendar days" info card for cyclic-medication onboarding.
 *
 * @param showDescription when true, the switch row shows the long-form
 *   "Adjusts your schedule…" supporting text. The wizard step hides it
 *   because the step's [ScreenHeader] already carries that copy; the edit
 *   screen leaves it on.
 */
@Composable
fun CycleEnableToggle(
    cycleEnabled: Boolean,
    onCycleEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDescription: Boolean = true,
) {
    AutoSmartList(
        modifier = modifier,
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListSwitchItem(
                    label = stringResource(Res.string.cycle_enabled),
                    checked = cycleEnabled,
                    onCheckedChange = onCycleEnabledChange,
                    shapes = shapes,
                    modifier = Modifier.testTag(TestTags.CYCLE_TOGGLE),
                    visible = visible,
                    supportingText = if (showDescription) {
                        stringResource(Res.string.cycle_enabled_description)
                    } else {
                        null
                    },
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
