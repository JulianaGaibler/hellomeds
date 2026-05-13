// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.alarm_action_dismiss
import me.juliana.hellomeds.shared.alarm_action_skip
import me.juliana.hellomeds.shared.alarm_action_skip_all
import me.juliana.hellomeds.shared.alarm_action_snooze
import me.juliana.hellomeds.shared.alarm_action_take_all
import me.juliana.hellomeds.shared.alarm_action_taken
import me.juliana.hellomeds.shared.alarm_discreet_label
import me.juliana.hellomeds.shared.alarm_discreet_label_count
import me.juliana.hellomeds.ui.features.onboarding.components.onboardingBackgroundShapes
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen alarm interface for medication reminders.
 *
 * Styled after the onboarding splash screen with animated background shapes.
 * Content positioned at the bottom-left. Used on both Android (AlarmActivity)
 * and iOS (overlay when alarm notification is tapped).
 *
 * @param medicationNames List of medication names to display
 * @param timeText Formatted time string (e.g., "08:30")
 * @param onTaken Callback when user taps Take/Take All
 * @param onSkipped Callback when user taps Skip
 * @param onSnooze Callback when user taps Snooze
 * @param onDismiss Callback when user taps Dismiss (alarm stops, meds stay pending)
 */
@Composable
fun ReminderAlarmScreen(
    medicationNames: List<String>,
    timeText: String,
    onTaken: () -> Unit,
    onSkipped: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    discreet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onboardingBackgroundShapes(tertiary, primary)
            .padding(32.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Time display
            if (timeText.isNotEmpty()) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Start,
                )
            }

            // Medication names (hidden in discreet mode for lock screen privacy)
            val namesText = if (discreet) {
                if (medicationNames.size > 1) {
                    stringResource(Res.string.alarm_discreet_label_count, medicationNames.size)
                } else {
                    stringResource(Res.string.alarm_discreet_label)
                }
            } else {
                medicationNames.joinToString(", ")
            }
            if (namesText.isNotEmpty()) {
                Text(
                    text = namesText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary row: Snooze + Dismiss (large, tonal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.alarm_action_snooze),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.alarm_action_dismiss),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            // Secondary row: Taken + Skipped (regular, outlined)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onTaken,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(MaterialTheme.colorScheme.outline),
                    ),
                ) {
                    Text(
                        if (medicationNames.size > 1) {
                            stringResource(
                                Res.string.alarm_action_take_all,
                            )
                        } else {
                            stringResource(Res.string.alarm_action_taken)
                        },
                    )
                }
                OutlinedButton(
                    onClick = onSkipped,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(MaterialTheme.colorScheme.outline),
                    ),
                ) {
                    Text(
                        if (medicationNames.size > 1) {
                            stringResource(
                                Res.string.alarm_action_skip_all,
                            )
                        } else {
                            stringResource(Res.string.alarm_action_skip)
                        },
                    )
                }
            }
        }
    }
}
