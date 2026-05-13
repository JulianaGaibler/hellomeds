// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_close
import me.juliana.hellomeds.shared.medication_added_title
import me.juliana.hellomeds.shared.schedule_add
import me.juliana.hellomeds.ui.theme.MedicationColor
import org.jetbrains.compose.resources.stringResource

/**
 * Dialog shown after successfully adding a medication.
 * Displays the medication icon and provides options to add a schedule or close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationAddedDialog(
    medicationName: String,
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    color1: MedicationColor?,
    onAddSchedule: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicAlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = modifier.padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MedicationShapeIcon(
                    foregroundShape = foregroundShape,
                    backgroundShape = backgroundShape,
                    color1 = color1,
                    size = 80.dp,
                )

                Text(
                    text = stringResource(Res.string.medication_added_title, medicationName),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Buttons — wraps to second line if translations are long
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onClose) {
                        Text(stringResource(Res.string.action_close))
                    }

                    Button(onClick = onAddSchedule) {
                        Text(stringResource(Res.string.schedule_add))
                    }
                }
            }
        }
    }
}
