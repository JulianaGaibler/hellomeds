// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.error

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.downgrade_confirm_message
import me.juliana.hellomeds.shared.downgrade_confirm_title
import me.juliana.hellomeds.shared.downgrade_erase
import me.juliana.hellomeds.shared.downgrade_erased
import me.juliana.hellomeds.shared.downgrade_message
import me.juliana.hellomeds.shared.downgrade_title
import org.jetbrains.compose.resources.stringResource

/** Shown instead of the app when the database was written by a newer app version. */
@Composable
fun DowngradeDetectedScreen(onErase: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    var erased by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            if (erased) {
                Text(stringResource(Res.string.downgrade_erased), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(stringResource(Res.string.downgrade_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(Res.string.downgrade_message), style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { confirming = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.downgrade_erase))
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.downgrade_confirm_title)) },
            text = { Text(stringResource(Res.string.downgrade_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onErase()
                        erased = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.downgrade_erase))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}
