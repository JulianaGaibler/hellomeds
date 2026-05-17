// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.ProjectedEventWithMedication
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.action_delete
import me.juliana.hellomeds.shared.action_ok
import me.juliana.hellomeds.shared.action_save
import me.juliana.hellomeds.shared.log_medication_as_needed
import me.juliana.hellomeds.shared.log_medication_edit_title
import me.juliana.hellomeds.shared.log_medication_scheduled
import me.juliana.hellomeds.shared.log_medication_time
import me.juliana.hellomeds.shared.log_medication_title
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.medication.DoseInputListItem
import me.juliana.hellomeds.ui.components.medication.LogStatus
import me.juliana.hellomeds.ui.components.medication.StatusSegmentedButton
import me.juliana.hellomeds.ui.components.medication.medicationInfoItem
import me.juliana.hellomeds.ui.util.formatDateWithRelativeWeekday
import me.juliana.hellomeds.ui.util.formatTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Bottom sheet for logging or editing a single medication dose.
 *
 * Mode is derived from [eventWithMedication.event.historyRecord]:
 * - null  → new log: pre-selects TAKEN, omits the date subheader and Delete button.
 * - else  → edit existing log: shows date subheader and Delete button; reverting to
 *           pending is done via Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationLogBottomSheet(
    eventWithMedication: ProjectedEventWithMedication,
    date: LocalDate,
    isScheduled: Boolean,
    onDismiss: () -> Unit,
    onDelete: (ProjectedEvent) -> Unit,
    onSave: (ProjectedEvent, LocalTime, Double, LogStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = platformContext()
    val medication = eventWithMedication.medication
    val event = eventWithMedication.event
    val isNewLog = event.historyRecord == null

    val initialTime = (event.historyRecord?.takenTime ?: event.scheduledTime).let {
        Instant.fromEpochMilliseconds(it)
            .toLocalDateTime(TimeZone.currentSystemDefault()).time
    }

    var selectedTime by remember { mutableStateOf(initialTime) }
    var dose by remember { mutableStateOf(event.historyRecord?.actualDose ?: event.dose) }
    var status by remember {
        mutableStateOf(
            when (event.historyRecord?.status) {
                "SKIPPED", "AUTO_SKIPPED" -> LogStatus.SKIPPED
                else -> LogStatus.TAKEN
            },
        )
    }
    var showTimePicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (isNewLog) Res.string.log_medication_title else Res.string.log_medication_edit_title,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!isNewLog) {
                        Text(
                            text = formatDateWithRelativeWeekday(date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(
                            if (isScheduled) {
                                Res.string.log_medication_scheduled
                            } else {
                                Res.string.log_medication_as_needed
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Medication info and inputs
            item {
                AutoSmartList(
                    items = listOf(
                        medicationInfoItem(medication),
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.log_medication_time)) },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        text = formatTime(selectedTime),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                                onClick = { showTimePicker = true },
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            DoseInputListItem(
                                dose = dose,
                                medicationType = medication.type,
                                onDoseChange = { dose = it },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = isScheduled) { shapes, visible ->
                            SmartListItem(
                                headlineContent = {
                                    StatusSegmentedButton(
                                        selectedStatus = status,
                                        onStatusChange = { status = it },
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                                onClick = null,
                            )
                        },
                    ),
                )
            }

            // Actions — taller buttons so the primary edit decisions feel like
            // real CTAs; kept at row edges with whitespace between rather than
            // glued together, so Delete sits clearly apart from Save.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = if (isNewLog) Arrangement.End else Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isNewLog) {
                        FilledTonalButton(
                            onClick = { onDelete(event) },
                            modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                        ) {
                            Text(stringResource(Res.string.action_delete))
                        }
                    }

                    Button(
                        onClick = {
                            onSave(event, selectedTime, dose, if (isScheduled) status else null)
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
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
                            selectedTime = LocalTime(
                                timePickerState.hour,
                                timePickerState.minute,
                            )
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
