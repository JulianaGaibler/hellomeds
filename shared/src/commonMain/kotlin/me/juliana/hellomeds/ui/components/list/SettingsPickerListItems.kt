// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.action_ok
import me.juliana.hellomeds.shared.schedule_end_date_clear
import me.juliana.hellomeds.shared.schedule_end_date_unset
import me.juliana.hellomeds.ui.compat.ListItemShapes
import me.juliana.hellomeds.ui.util.formatShortDate
import me.juliana.hellomeds.ui.util.formatTime
import me.juliana.hellomeds.ui.util.is24HourFormat
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * A smart list item that shows a time and opens a time picker dialog when clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartListTimePickerItem(
    label: String,
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    shapes: ListItemShapes,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val is24Hour = is24HourFormat()
    var showTimePicker by remember { mutableStateOf(false) }

    SmartListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            TextButton(onClick = { showTimePicker = true }) {
                Text(formatTime(time))
            }
        },
        shapes = shapes,
        visible = visible,
        onClick = { showTimePicker = true },
        modifier = modifier,
    )

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = is24Hour,
        )

        BasicAlertDialog(
            onDismissRequest = { showTimePicker = false },
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(Res.string.action_cancel))
                        }
                        TextButton(onClick = {
                            onTimeChange(LocalTime(timePickerState.hour, timePickerState.minute))
                            showTimePicker = false
                        }) {
                            Text(stringResource(Res.string.action_ok))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A smart list item that shows a date and opens a date picker dialog when clicked
 * Optionally supports clearing the date with a delete button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartListDatePickerItem(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    shapes: ListItemShapes,
    visible: Boolean = true,
    allowClear: Boolean = false,
    unsetLabel: String = stringResource(Res.string.schedule_end_date_unset),
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    SmartListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { showDatePicker = true }) {
                    Text(date?.let { formatShortDate(it) } ?: unsetLabel)
                }
                if (allowClear && date != null) {
                    IconButton(onClick = { onDateChange(null) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.schedule_end_date_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        shapes = shapes,
        visible = visible,
        onClick = { showDatePicker = true },
        modifier = modifier,
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDayIn(TimeZone.currentSystemDefault())
                ?.toEpochMilliseconds(),
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateChange(
                            Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.currentSystemDefault()).date,
                        )
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        ) {
            DatePicker(
                state = datePickerState,
                title = { Text(label, modifier = Modifier.padding(16.dp)) },
            )
        }
    }
}
