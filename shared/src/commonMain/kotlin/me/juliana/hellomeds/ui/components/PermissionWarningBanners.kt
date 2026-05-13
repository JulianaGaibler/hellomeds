// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.permission_warning_alarmkit_message
import me.juliana.hellomeds.shared.permission_warning_alarmkit_title
import me.juliana.hellomeds.shared.permission_warning_collapsed_title
import me.juliana.hellomeds.shared.permission_warning_critical_alerts_message
import me.juliana.hellomeds.shared.permission_warning_critical_alerts_title
import me.juliana.hellomeds.shared.permission_warning_dismiss_button
import me.juliana.hellomeds.shared.permission_warning_dnd_message
import me.juliana.hellomeds.shared.permission_warning_dnd_title
import me.juliana.hellomeds.shared.permission_warning_exact_alarms_message
import me.juliana.hellomeds.shared.permission_warning_exact_alarms_title
import me.juliana.hellomeds.shared.permission_warning_fix_button
import me.juliana.hellomeds.shared.permission_warning_fsi_message
import me.juliana.hellomeds.shared.permission_warning_fsi_title
import me.juliana.hellomeds.shared.permission_warning_notifications_message
import me.juliana.hellomeds.shared.permission_warning_notifications_title
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.util.PermissionUtils
import me.juliana.hellomeds.ui.util.PermissionWarning
import me.juliana.hellomeds.ui.util.PermissionWarningState
import org.jetbrains.compose.resources.stringResource

/**
 * Reusable permission warning banners.
 *
 * - 1 warning: single expanded card
 * - 2+ warnings: collapsed summary that expands on tap
 *
 * Each warning shows a title, message, "Fix" button (opens settings), and "Dismiss" button.
 * Dismissed warnings stay hidden for the session; they reappear if the permission state
 * changes (granted→revoked cycle) or on next cold start.
 */
@Composable
fun PermissionWarningBanners(
    state: PermissionWarningState,
    dismissedWarnings: Set<PermissionWarning>,
    onDismiss: (PermissionWarning) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeWarnings = state.warnings.filter { it !in dismissedWarnings }
    if (activeWarnings.isEmpty()) return

    val context = platformContext()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (activeWarnings.size == 1) {
            // Single warning — show expanded
            WarningCard(
                warning = activeWarnings.first(),
                context = context,
                onDismiss = onDismiss,
            )
        } else {
            // Multiple warnings — collapsed summary with expand
            var expanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.permission_warning_collapsed_title, activeWarnings.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (warning in activeWarnings) {
                        WarningCard(
                            warning = warning,
                            context = context,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(warning: PermissionWarning, context: Any, onDismiss: (PermissionWarning) -> Unit) {
    val containerColor = MaterialTheme.colorScheme.errorContainer
    val contentColor = MaterialTheme.colorScheme.onErrorContainer
    val buttonContainerColor = MaterialTheme.colorScheme.error
    val buttonContentColor = MaterialTheme.colorScheme.onError

    val (title, message) = when (warning) {
        PermissionWarning.NOTIFICATIONS_DISABLED -> Pair(
            stringResource(Res.string.permission_warning_notifications_title),
            stringResource(Res.string.permission_warning_notifications_message),
        )
        PermissionWarning.EXACT_ALARMS_DISABLED -> Pair(
            stringResource(Res.string.permission_warning_exact_alarms_title),
            stringResource(Res.string.permission_warning_exact_alarms_message),
        )
        PermissionWarning.FULL_SCREEN_INTENT_DISABLED -> Pair(
            stringResource(Res.string.permission_warning_fsi_title),
            stringResource(Res.string.permission_warning_fsi_message),
        )
        PermissionWarning.CRITICAL_CHANNEL_DND_BLOCKED -> Pair(
            stringResource(Res.string.permission_warning_dnd_title),
            stringResource(Res.string.permission_warning_dnd_message),
        )
        PermissionWarning.ALARMKIT_DISABLED -> Pair(
            stringResource(Res.string.permission_warning_alarmkit_title),
            stringResource(Res.string.permission_warning_alarmkit_message),
        )
        PermissionWarning.CRITICAL_ALERTS_DISABLED -> Pair(
            stringResource(Res.string.permission_warning_critical_alerts_title),
            stringResource(Res.string.permission_warning_critical_alerts_message),
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = contentColor)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(
                    onClick = { openSettingsFor(warning, context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonContainerColor,
                        contentColor = buttonContentColor,
                    ),
                ) {
                    Text(stringResource(Res.string.permission_warning_fix_button))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onDismiss(warning) }) {
                    Text(
                        stringResource(Res.string.permission_warning_dismiss_button),
                        color = contentColor,
                    )
                }
            }
        }
    }
}

private fun openSettingsFor(warning: PermissionWarning, context: Any) {
    when (warning) {
        PermissionWarning.NOTIFICATIONS_DISABLED -> PermissionUtils.openNotificationSettings(context)
        PermissionWarning.EXACT_ALARMS_DISABLED -> PermissionUtils.openExactAlarmSettings(context)
        PermissionWarning.FULL_SCREEN_INTENT_DISABLED -> PermissionUtils.openFullScreenIntentSettings(context)
        PermissionWarning.CRITICAL_CHANNEL_DND_BLOCKED -> PermissionUtils.openChannelSettings(
            context,
            me.juliana.hellomeds.ui.util.NotificationChannels.CRITICAL_CHANNEL_ID,
        )
        // iOS-specific: open app notification settings (no deeper settings URL available)
        PermissionWarning.ALARMKIT_DISABLED -> PermissionUtils.openNotificationSettings(context)
        PermissionWarning.CRITICAL_ALERTS_DISABLED -> PermissionUtils.openNotificationSettings(context)
    }
}
