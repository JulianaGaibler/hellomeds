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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_dismiss
import me.juliana.hellomeds.shared.permission_revoked_disable
import me.juliana.hellomeds.shared.permission_revoked_exact_alarms_message
import me.juliana.hellomeds.shared.permission_revoked_exact_alarms_title
import me.juliana.hellomeds.shared.permission_revoked_grant
import me.juliana.hellomeds.shared.permission_revoked_notifications_message
import me.juliana.hellomeds.shared.permission_revoked_notifications_title
import org.jetbrains.compose.resources.stringResource

enum class RevokedPermission {
    NOTIFICATIONS,
    EXACT_ALARMS,
}

/**
 * Dialog shown when a previously granted permission is revoked
 *
 * Offers options:
 * 1. Grant Permission - Opens system settings to re-grant
 * 2. Disable (NOTIFICATIONS/EXACT_ALARMS only) - Disables the feature in-app, stops warnings
 * 3. Dismiss - Closes the dialog (warnings will reappear on next app resume)
 */
@Composable
fun PermissionRevokedDialog(
    permission: RevokedPermission,
    onGrantPermission: () -> Unit,
    onDisableFeature: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val (title, message) = when (permission) {
        RevokedPermission.NOTIFICATIONS -> {
            stringResource(Res.string.permission_revoked_notifications_title) to
                stringResource(Res.string.permission_revoked_notifications_message)
        }

        RevokedPermission.EXACT_ALARMS -> {
            stringResource(Res.string.permission_revoked_exact_alarms_title) to
                stringResource(Res.string.permission_revoked_exact_alarms_message)
        }
    }

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
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Message
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Primary action: Grant Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onGrantPermission) {
                            Text(stringResource(Res.string.permission_revoked_grant))
                        }
                    }

                    // Secondary actions row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        // Disable button (only for features that have toggles)
                        onDisableFeature?.let { disable ->
                            TextButton(onClick = disable) {
                                Text(stringResource(Res.string.permission_revoked_disable))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        TextButton(onClick = onDismiss) {
                            Text(stringResource(Res.string.action_dismiss))
                        }
                    }
                }
            }
        }
    }
}
