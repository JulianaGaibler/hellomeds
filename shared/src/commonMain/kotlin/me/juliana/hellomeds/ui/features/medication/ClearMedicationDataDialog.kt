// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.service.ClearRange
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.clear_data_confirm
import me.juliana.hellomeds.shared.clear_data_continue
import me.juliana.hellomeds.shared.clear_data_go_to_backups
import me.juliana.hellomeds.shared.clear_data_logged_doses
import me.juliana.hellomeds.shared.clear_data_message
import me.juliana.hellomeds.shared.clear_data_no_backup_message
import me.juliana.hellomeds.shared.clear_data_no_backup_title
import me.juliana.hellomeds.shared.clear_data_range_24_hours
import me.juliana.hellomeds.shared.clear_data_range_label
import me.juliana.hellomeds.shared.clear_data_range_older_than
import me.juliana.hellomeds.shared.clear_data_stock_changes
import me.juliana.hellomeds.shared.clear_data_title
import me.juliana.hellomeds.shared.content_description_dropdown
import me.juliana.hellomeds.ui.compat.GroupedOverflowDropdown
import org.jetbrains.compose.resources.stringResource

@Composable
fun ClearMedicationDataDialog(
    showStockOption: Boolean,
    onConfirm: (clearHistory: Boolean, clearStock: Boolean, range: ClearRange) -> Unit,
    onDismiss: () -> Unit,
) {
    var clearHistory by remember { mutableStateOf(true) }
    var clearStock by remember { mutableStateOf(showStockOption) }
    var range by remember { mutableStateOf(ClearRange.OLDER_THAN_24_HOURS) }
    val canConfirm = clearHistory || (showStockOption && clearStock)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.clear_data_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(Res.string.clear_data_message))
                CheckboxRow(stringResource(Res.string.clear_data_logged_doses), clearHistory) { clearHistory = it }
                if (showStockOption) {
                    CheckboxRow(stringResource(Res.string.clear_data_stock_changes), clearStock) { clearStock = it }
                }
                RangeDropdownRow(range = range, onRangeChange = { range = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(clearHistory, showStockOption && clearStock, range) },
                enabled = canConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.clear_data_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

/** Shown before [ClearMedicationDataDialog] when there is no automatic backup to restore from. */
@Composable
fun ClearDataNoBackupDialog(onGoToBackups: () -> Unit, onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.clear_data_no_backup_title)) },
        text = { Text(stringResource(Res.string.clear_data_no_backup_message)) },
        confirmButton = {
            Row {
                TextButton(onClick = onContinue) {
                    Text(stringResource(Res.string.clear_data_continue))
                }
                TextButton(onClick = onGoToBackups) {
                    Text(stringResource(Res.string.clear_data_go_to_backups))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun rangeLabel(range: ClearRange): String = range.keepDays
    ?.let { stringResource(Res.string.clear_data_range_older_than, it) }
    ?: stringResource(Res.string.clear_data_range_24_hours)

@Composable
private fun RangeDropdownRow(range: ClearRange, onRangeChange: (ClearRange) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = ClearRange.entries.map { it to rangeLabel(it) }

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.clear_data_range_label), modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(rangeLabel(range))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(Res.string.content_description_dropdown),
                )
            }
            GroupedOverflowDropdown(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                groups = listOf { dismiss ->
                    options.forEach { (option, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onRangeChange(option)
                                dismiss()
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
