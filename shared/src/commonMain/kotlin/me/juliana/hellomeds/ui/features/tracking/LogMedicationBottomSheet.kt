// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.ProjectedEventWithMedication
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_action_collapse
import me.juliana.hellomeds.shared.accessibility_action_expand
import me.juliana.hellomeds.shared.accessibility_not_selected
import me.juliana.hellomeds.shared.accessibility_selected
import me.juliana.hellomeds.shared.action_save
import me.juliana.hellomeds.shared.illustration_empty_no_schedule
import me.juliana.hellomeds.shared.log_medication_action_log
import me.juliana.hellomeds.shared.log_medication_as_needed
import me.juliana.hellomeds.shared.log_medication_empty_as_needed
import me.juliana.hellomeds.shared.log_medication_empty_scheduled
import me.juliana.hellomeds.shared.log_medication_scheduled
import me.juliana.hellomeds.shared.log_medication_status_taken
import me.juliana.hellomeds.shared.log_medication_time
import me.juliana.hellomeds.shared.log_medication_title
import me.juliana.hellomeds.ui.compat.ToggleButton
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.common.EmptyState
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTimePickerItem
import me.juliana.hellomeds.ui.components.medication.DoseInputListItem
import me.juliana.hellomeds.ui.components.medication.LogStatus
import me.juliana.hellomeds.ui.components.medication.MedicationShapeIcon
import me.juliana.hellomeds.ui.components.medication.StatusSegmentedButton
import me.juliana.hellomeds.ui.theme.MedicationColor
import me.juliana.hellomeds.ui.util.formatDateWithRelativeWeekday
import me.juliana.hellomeds.ui.util.formatLogEventDose
import me.juliana.hellomeds.ui.util.formatMedicationTypeAndStrength
import me.juliana.hellomeds.ui.util.formatTime
import me.juliana.hellomeds.ui.util.getDoseUnitPluralRes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

enum class LogMedicationMode {
    SCHEDULED,
    AS_NEEDED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogMedicationBottomSheet(
    mode: LogMedicationMode,
    date: LocalDate,
    scheduledEvents: List<ProjectedEventWithMedication> = emptyList(),
    allMedications: List<Medication> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        mode: LogMedicationMode,
        scheduledLogs: List<ScheduledMedicationLog>,
        asNeededLogs: List<AsNeededMedicationLog>,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    platformContext()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var scheduledMedicationStates by remember(scheduledEvents) {
        mutableStateOf(
            scheduledEvents.map { eventWithMed ->
                val scheduledTime = Instant.fromEpochMilliseconds(eventWithMed.event.scheduledTime)
                    .toLocalDateTime(TimeZone.currentSystemDefault()).time

                ScheduledMedicationLog(
                    eventWithMedication = eventWithMed,
                    included = false,
                    status = LogStatus.TAKEN,
                    dose = eventWithMed.event.dose,
                    time = scheduledTime,
                    isExpanded = false,
                )
            },
        )
    }

    var asNeededMedicationStates by remember(allMedications) {
        mutableStateOf(
            allMedications.filter { !it.isArchived }.map { medication ->
                AsNeededMedicationLog(
                    medication = medication,
                    isTaken = false,
                    dose = 1.0,
                    time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time,
                )
            },
        )
    }

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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header - Title
            item {
                Text(
                    text = stringResource(Res.string.log_medication_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Header - Date
            item {
                Text(
                    text = formatDateWithRelativeWeekday(date),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Section title
            item {
                Text(
                    text = when (mode) {
                        LogMedicationMode.SCHEDULED -> stringResource(Res.string.log_medication_scheduled)
                        LogMedicationMode.AS_NEEDED -> stringResource(Res.string.log_medication_as_needed)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Medications list
            when (mode) {
                LogMedicationMode.SCHEDULED -> if (scheduledMedicationStates.isEmpty()) {
                    item {
                        EmptyState(
                            illustration = painterResource(Res.drawable.illustration_empty_no_schedule),
                            illustrationSize = 160.dp,
                            contentDescription = stringResource(Res.string.log_medication_empty_scheduled),
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                } else {
                    items(scheduledMedicationStates.size) { index ->
                        val medicationLog = scheduledMedicationStates[index]
                        val defaultTime = remember(scheduledEvents) {
                            Instant.fromEpochMilliseconds(
                                scheduledEvents[index].event.scheduledTime,
                            ).toLocalDateTime(TimeZone.currentSystemDefault()).time
                        }
                        val defaultDose = remember(scheduledEvents) {
                            scheduledEvents[index].event.dose
                        }
                        ScheduledMedicationItem(
                            medicationLog = medicationLog,
                            onStatusChange = { newStatus ->
                                scheduledMedicationStates =
                                    scheduledMedicationStates.toMutableList().apply {
                                        this[index] = this[index].copy(status = newStatus)
                                    }
                            },
                            onDoseChange = { newDose ->
                                scheduledMedicationStates =
                                    scheduledMedicationStates.toMutableList().apply {
                                        this[index] = this[index].copy(dose = newDose)
                                    }
                            },
                            onTimeChange = { newTime ->
                                scheduledMedicationStates =
                                    scheduledMedicationStates.toMutableList().apply {
                                        this[index] = this[index].copy(time = newTime)
                                    }
                            },
                            onLogToggle = {
                                scheduledMedicationStates =
                                    scheduledMedicationStates.toMutableList().apply {
                                        val current = this[index]
                                        this[index] = if (current.included) {
                                            current.copy(
                                                included = false,
                                                isExpanded = false,
                                                status = LogStatus.TAKEN,
                                                dose = defaultDose,
                                                time = defaultTime,
                                            )
                                        } else {
                                            current.copy(
                                                included = true,
                                                isExpanded = false,
                                                status = LogStatus.TAKEN,
                                            )
                                        }
                                    }
                            },
                            onExpandToggle = {
                                scheduledMedicationStates =
                                    scheduledMedicationStates.toMutableList().apply {
                                        this[index] =
                                            this[index].copy(isExpanded = !this[index].isExpanded)
                                    }
                            },
                        )
                    }
                }

                LogMedicationMode.AS_NEEDED -> if (asNeededMedicationStates.isEmpty()) {
                    item {
                        EmptyState(
                            illustration = painterResource(Res.drawable.illustration_empty_no_schedule),
                            illustrationSize = 160.dp,
                            contentDescription = stringResource(Res.string.log_medication_empty_as_needed),
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                } else {
                    items(asNeededMedicationStates.size) { index ->
                        val medicationLog = asNeededMedicationStates[index]
                        AsNeededMedicationItem(
                            medicationLog = medicationLog,
                            onTakenToggle = {
                                asNeededMedicationStates =
                                    asNeededMedicationStates.toMutableList().apply {
                                        this[index] =
                                            this[index].copy(isTaken = !this[index].isTaken)
                                    }
                            },
                            onDoseChange = { newDose ->
                                asNeededMedicationStates =
                                    asNeededMedicationStates.toMutableList().apply {
                                        this[index] = this[index].copy(dose = newDose)
                                    }
                            },
                            onTimeChange = { newTime ->
                                asNeededMedicationStates =
                                    asNeededMedicationStates.toMutableList().apply {
                                        this[index] = this[index].copy(time = newTime)
                                    }
                            },
                        )
                    }
                }
            }

            // Save button
            item {
                Button(
                    onClick = {
                        when (mode) {
                            LogMedicationMode.SCHEDULED -> {
                                onSave(mode, scheduledMedicationStates, emptyList())
                            }

                            LogMedicationMode.AS_NEEDED -> {
                                onSave(
                                    mode,
                                    emptyList(),
                                    asNeededMedicationStates.filter { it.isTaken },
                                )
                            }
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .padding(top = 16.dp, bottom = 32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(Res.string.action_save),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledMedicationItem(
    medicationLog: ScheduledMedicationLog,
    onStatusChange: (LogStatus) -> Unit,
    onDoseChange: (Double) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onLogToggle: () -> Unit,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    platformContext()
    val medication = medicationLog.eventWithMedication.medication
    val event = medicationLog.eventWithMedication.event
    val selectedText = stringResource(Res.string.accessibility_selected)
    val notSelectedText = stringResource(Res.string.accessibility_not_selected)

    val isLogged = medicationLog.included

    val displayName = medication.displayName?.takeIf { it.isNotBlank() } ?: medication.name
    val typeAndStrength = formatMedicationTypeAndStrength(medication)
    val foregroundShape = MedicationForegroundShape.fromNameOrDefault(medication.foregroundShape)
    val backgroundShape = MedicationBackgroundShape.fromNameOrDefault(medication.backgroundShape)

    val color1 = medication.shapeColor?.let {
        MedicationColor.fromName(it)
    }

    val doseText = formatLogEventDose(
        event.copy(dose = medicationLog.dose),
        medication.type,
    )
    val timeText = formatTime(medicationLog.time)
    val summary = "$doseText at $timeText"

    AutoSmartList(
        modifier = modifier,
        items = listOf(
            // Medication header with icon, name, type, and "Log" toggle
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text(displayName) },
                    supportingContent = {
                        Text(if (isLogged) typeAndStrength else summary)
                    },
                    leadingContent = {
                        MedicationShapeIcon(
                            foregroundShape = foregroundShape,
                            backgroundShape = backgroundShape,
                            color1 = color1,
                            size = 40.dp,
                        )
                    },
                    trailingContent = {
                        ToggleButton(
                            checked = isLogged,
                            onCheckedChange = { onLogToggle() },
                            modifier = Modifier.semantics {
                                stateDescription =
                                    if (isLogged) selectedText else notSelectedText
                            },
                        ) {
                            Text(stringResource(Res.string.log_medication_action_log))
                        }
                    },
                    shapes = shapes,
                    visible = visible,
                    onClick = null,
                )
            },
            // Summary row with expand/collapse arrow (visible when logged)
            SmartListItemConfig(visible = isLogged) { shapes, visible ->
                SmartListItem(
                    headlineContent = {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = if (medicationLog.isExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = if (medicationLog.isExpanded) {
                                stringResource(Res.string.accessibility_action_collapse)
                            } else {
                                stringResource(Res.string.accessibility_action_expand)
                            },
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                    onClick = onExpandToggle,
                    modifier = Modifier.heightIn(min = 48.dp, max = 48.dp),
                )
            },
            // Time picker (visible when logged AND detail-expanded)
            SmartListItemConfig(visible = isLogged && medicationLog.isExpanded) { shapes, visible ->
                SmartListTimePickerItem(
                    label = stringResource(Res.string.log_medication_time),
                    time = medicationLog.time,
                    onTimeChange = onTimeChange,
                    shapes = shapes,
                    visible = visible,
                )
            },
            // Dose picker (visible when logged AND detail-expanded)
            SmartListItemConfig(visible = isLogged && medicationLog.isExpanded) { shapes, visible ->
                DoseInputListItem(
                    dose = medicationLog.dose,
                    medicationType = medication.type,
                    onDoseChange = onDoseChange,
                    shapes = shapes,
                    visible = visible,
                )
            },
            // Status selector (Skip + Taken only, visible when logged)
            SmartListItemConfig(visible = isLogged) { shapes, visible ->
                SmartListItem(
                    headlineContent = {
                        StatusSegmentedButton(
                            selectedStatus = medicationLog.status,
                            onStatusChange = onStatusChange,
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

@Composable
private fun AsNeededMedicationItem(
    medicationLog: AsNeededMedicationLog,
    onTakenToggle: () -> Unit,
    onDoseChange: (Double) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    platformContext()
    val medication = medicationLog.medication
    val selectedText = stringResource(Res.string.accessibility_selected)
    val notSelectedText = stringResource(Res.string.accessibility_not_selected)

    val displayName = medication.displayName?.takeIf { it.isNotBlank() } ?: medication.name
    val typeAndStrength = formatMedicationTypeAndStrength(medication)
    val foregroundShape = MedicationForegroundShape.fromNameOrDefault(medication.foregroundShape)
    val backgroundShape = MedicationBackgroundShape.fromNameOrDefault(medication.backgroundShape)

    val color1 = medication.shapeColor?.let {
        MedicationColor.fromName(it)
    }

    val doseUnitRes = getDoseUnitPluralRes(medication.type)
    val doseQuantity = medicationLog.dose.toInt()
    val doseText = pluralStringResource(doseUnitRes, doseQuantity, doseQuantity)
    val timeText = formatTime(medicationLog.time)
    val summary = "$doseText at $timeText"

    AutoSmartList(
        modifier = modifier,
        items = listOf(
            // Medication header with icon, name, type, and taken toggle
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text(displayName) },
                    supportingContent = { Text(typeAndStrength) },
                    leadingContent = {
                        MedicationShapeIcon(
                            foregroundShape = foregroundShape,
                            backgroundShape = backgroundShape,
                            color1 = color1,
                            size = 40.dp,
                        )
                    },
                    trailingContent = {
                        ToggleButton(
                            checked = medicationLog.isTaken,
                            onCheckedChange = { onTakenToggle() },
                            modifier = Modifier.semantics {
                                stateDescription = if (medicationLog.isTaken) selectedText else notSelectedText
                            },
                        ) {
                            Text(stringResource(Res.string.log_medication_status_taken))
                        }
                    },
                    shapes = shapes,
                    visible = visible,
                    onClick = null,
                )
            },
            // Summary (non-collapsible, no arrow)
            SmartListItemConfig(visible = medicationLog.isTaken) { shapes, visible ->
                SmartListItem(
                    headlineContent = {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                    onClick = null,
                    modifier = Modifier.heightIn(min = 48.dp, max = 48.dp),
                )
            },
            // Time picker
            SmartListItemConfig(visible = medicationLog.isTaken) { shapes, visible ->
                SmartListTimePickerItem(
                    label = stringResource(Res.string.log_medication_time),
                    time = medicationLog.time,
                    onTimeChange = onTimeChange,
                    shapes = shapes,
                    visible = visible,
                )
            },
            // Dose picker
            SmartListItemConfig(visible = medicationLog.isTaken) { shapes, visible ->
                DoseInputListItem(
                    dose = medicationLog.dose,
                    medicationType = medication.type,
                    onDoseChange = onDoseChange,
                    shapes = shapes,
                    visible = visible,
                )
            },
        ),
    )
}
