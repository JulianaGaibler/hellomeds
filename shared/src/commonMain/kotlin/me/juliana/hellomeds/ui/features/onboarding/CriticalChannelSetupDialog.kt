// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.dnd_warning_dismiss
import me.juliana.hellomeds.shared.dnd_warning_message
import me.juliana.hellomeds.shared.dnd_warning_open_settings
import me.juliana.hellomeds.shared.dnd_warning_title
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.util.NotificationChannels
import me.juliana.hellomeds.ui.util.PermissionUtils
import org.jetbrains.compose.resources.stringResource

/**
 * Simplified one-time dialog shown when a user first assigns a critical
 * importance label to a medication and DND bypass isn't enabled yet.
 *
 * Opens channel settings on tap, then dismisses. The persistent warning card
 * on the medication list screen handles the ongoing reminder.
 */
@Composable
fun CriticalChannelSetupDialog(onDismiss: () -> Unit) {
    val context = platformContext()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = stringResource(Res.string.dnd_warning_title),
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.dnd_warning_message),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            PermissionUtils.openChannelSettings(
                                context,
                                NotificationChannels.CRITICAL_CHANNEL_ID,
                            )
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(Res.string.dnd_warning_open_settings))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(Res.string.dnd_warning_dismiss))
                        }
                    }
                }
            }
        }
    }
}
