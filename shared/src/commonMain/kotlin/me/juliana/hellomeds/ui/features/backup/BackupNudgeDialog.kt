// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.backup_nudge_body_risk
import me.juliana.hellomeds.shared.backup_nudge_body_solution
import me.juliana.hellomeds.shared.backup_nudge_dismiss
import me.juliana.hellomeds.shared.backup_nudge_never
import me.juliana.hellomeds.shared.backup_nudge_risk_after_import
import me.juliana.hellomeds.shared.backup_nudge_setup
import me.juliana.hellomeds.shared.backup_nudge_title
import me.juliana.hellomeds.ui.compat.GroupedOverflowDropdown
import me.juliana.hellomeds.ui.compat.SplitMenuButton
import org.jetbrains.compose.resources.stringResource

/**
 * Asks the user to turn on automatic backups. The dismiss button is a split button: its main
 * action asks again later and its menu stops the reminders for good.
 */
@Composable
fun BackupNudgeDialog(afterImport: Boolean, onSetUp: () -> Unit, onNotNow: () -> Unit, onNeverAsk: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(Res.string.backup_nudge_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        if (afterImport) Res.string.backup_nudge_risk_after_import else Res.string.backup_nudge_body_risk,
                    ),
                )
                Text(stringResource(Res.string.backup_nudge_body_solution))
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    SplitMenuButton(
                        primaryLabel = stringResource(Res.string.backup_nudge_dismiss),
                        onPrimaryClick = onNotNow,
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                    )
                    GroupedOverflowDropdown(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        groups = listOf { dismiss ->
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.backup_nudge_never)) },
                                onClick = {
                                    dismiss()
                                    onNeverAsk()
                                },
                            )
                        },
                    )
                }
                Button(onClick = onSetUp) {
                    Text(stringResource(Res.string.backup_nudge_setup))
                }
            }
        },
    )
}
