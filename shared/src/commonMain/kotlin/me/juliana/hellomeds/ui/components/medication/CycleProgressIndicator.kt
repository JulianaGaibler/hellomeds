// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.medication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.getCycleDay
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.cycle_current_day
import me.juliana.hellomeds.shared.cycle_progress_active
import me.juliana.hellomeds.shared.cycle_progress_break
import me.juliana.hellomeds.shared.cycle_progress_next_cycle
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.IntegerInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import org.jetbrains.compose.resources.stringResource

/**
 * Displays cycle progress for a Medication entity.
 */
@Composable
fun CycleProgressIndicator(medication: Medication, currentDate: LocalDate, modifier: Modifier = Modifier) {
    if (medication.cycleType != CycleType.CYCLIC) return
    val daysActive = medication.cycleDaysActive ?: return
    val daysBreak = medication.cycleDaysBreak ?: return
    val info = getCycleDay(medication, currentDate) ?: return

    CycleProgressIndicator(
        daysActive = daysActive,
        daysBreak = daysBreak,
        dayInCycle = info.dayInCycle,
        hasPlacebos = medication.cycleHasPlacebos,
        modifier = modifier,
    )
}

/**
 * Raw-params overload for use in onboarding and edit screens.
 * Stretchy segmented bars with vertical centering.
 *
 * @param dayInCycle 0-based day within the cycle (0 = first active day)
 */
@Composable
fun CycleProgressIndicator(
    daysActive: Int,
    daysBreak: Int,
    dayInCycle: Int,
    hasPlacebos: Boolean,
    modifier: Modifier = Modifier,
) {
    val cycleLength = daysActive + daysBreak
    if (cycleLength <= 0) return
    val isActive = dayInCycle < daysActive

    // Build status text (also used as content description for screen readers)
    val statusText = if (isActive) {
        stringResource(Res.string.cycle_progress_active, dayInCycle + 1, cycleLength)
    } else {
        stringResource(Res.string.cycle_progress_break, dayInCycle + 1, cycleLength)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status text
        if (isActive) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Column {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val daysUntilNextCycle = cycleLength - dayInCycle
                Text(
                    text = stringResource(Res.string.cycle_progress_next_cycle, daysUntilNextCycle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Segmented progress bar (stretchy bars, vertically centered)
        // A1: clearAndSetSemantics so TalkBack reads a single description, not individual boxes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = statusText },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (day in 0 until cycleLength) {
                val isActiveDay = day < daysActive
                val isCurrent = day == dayInCycle
                val isBreakDay = !isActiveDay

                val color = when {
                    isCurrent && isActiveDay -> MaterialTheme.colorScheme.primary
                    isCurrent && isBreakDay && hasPlacebos -> MaterialTheme.colorScheme.tertiary
                    isCurrent && isBreakDay -> MaterialTheme.colorScheme.outline
                    isActiveDay -> MaterialTheme.colorScheme.primaryContainer
                    hasPlacebos -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isCurrent) 8.dp else 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
            }
        }
    }
}

/**
 * Day-in-cycle input with live [CycleProgressIndicator] preview.
 * Shared by cycle settings and the "Set day" dialog.
 *
 * @param dayInCycle 1-based current day (user-facing)
 * @param onDayInCycleChange called with the new 1-based day
 */
@Composable
fun CycleDayPicker(
    daysActive: Int,
    daysBreak: Int,
    dayInCycle: String,
    hasPlacebos: Boolean,
    onDayInCycleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cycleLength = daysActive + daysBreak
    val parsed = dayInCycle.toIntOrNull()
    val showPreview = parsed != null && parsed in 1..cycleLength && daysActive > 0

    AutoSmartList(
        modifier = modifier,
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListTextItem(
                    label = stringResource(Res.string.cycle_current_day),
                    value = dayInCycle,
                    onValueChange = onDayInCycleChange,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shapes = shapes,
                    visible = visible,
                    supportingText = if (cycleLength > 0) "1–$cycleLength" else null,
                    validator = { text ->
                        text.isEmpty() || text.toIntOrNull()?.let { it in 1..cycleLength } == true
                    },
                    inputTransformation = IntegerInputTransformation(),
                )
            },
            SmartListItemConfig(visible = showPreview) { shapes, visible ->
                SmartListItem(
                    headlineContent = {
                        CycleProgressIndicator(
                            daysActive = daysActive,
                            daysBreak = daysBreak,
                            dayInCycle = (parsed ?: 1) - 1,
                            hasPlacebos = hasPlacebos,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
        ),
    )
}
