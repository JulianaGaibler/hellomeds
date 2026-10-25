// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.recovered_choose_file
import me.juliana.hellomeds.shared.recovered_message_cause
import me.juliana.hellomeds.shared.recovered_message_next
import me.juliana.hellomeds.shared.recovered_restore_latest
import me.juliana.hellomeds.shared.recovered_start_fresh
import me.juliana.hellomeds.shared.recovered_title
import org.jetbrains.compose.resources.stringResource

/**
 * Explains that the local database could not be opened and offers to restore from a backup.
 * [latestBackupLabel] is the date of the newest automatic backup, or null when there is none.
 */
@Composable
fun DatabaseRecoveredDialog(
    latestBackupLabel: String?,
    onRestoreLatest: () -> Unit,
    onChooseFile: () -> Unit,
    onStartFresh: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(Res.string.recovered_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.recovered_message_cause))
                Text(stringResource(Res.string.recovered_message_next))
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (latestBackupLabel != null) {
                    Button(onClick = onRestoreLatest) {
                        Text(stringResource(Res.string.recovered_restore_latest, latestBackupLabel))
                    }
                    OutlinedButton(onClick = onChooseFile) {
                        Text(stringResource(Res.string.recovered_choose_file))
                    }
                } else {
                    Button(onClick = onChooseFile) {
                        Text(stringResource(Res.string.recovered_choose_file))
                    }
                }
                TextButton(onClick = onStartFresh) {
                    Text(stringResource(Res.string.recovered_start_fresh))
                }
            }
        },
    )
}

/** Turns an automatic backup file name into a date label such as 2026-10-24, or null. */
fun autoBackupDateLabel(fileName: String): String? =
    Regex("""hellomeds-auto-(\d{4}-\d{2}-\d{2})""").find(fileName)?.groupValues?.get(1)
