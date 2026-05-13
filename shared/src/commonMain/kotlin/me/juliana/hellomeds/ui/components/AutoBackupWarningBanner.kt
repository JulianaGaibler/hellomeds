// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.permission_warning_dismiss_button
import me.juliana.hellomeds.shared.permission_warning_fix_button
import me.juliana.hellomeds.shared.warning_backup_failed_message
import me.juliana.hellomeds.shared.warning_backup_failed_title
import org.jetbrains.compose.resources.stringResource

/**
 * Warning banner shown on the tracking screen when auto-backup has consecutive failures.
 * Visually matches [PermissionWarningBanners] for consistency.
 */
@Composable
fun AutoBackupWarningBanner(
    consecutiveFailures: Int,
    onNavigateToSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (consecutiveFailures <= 0) return

    val containerColor = MaterialTheme.colorScheme.errorContainer
    val contentColor = MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.warning_backup_failed_title),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(Res.string.warning_backup_failed_message, consecutiveFailures),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(
                    onClick = onNavigateToSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(Res.string.permission_warning_fix_button))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(Res.string.permission_warning_dismiss_button),
                        color = contentColor,
                    )
                }
            }
        }
    }
}
