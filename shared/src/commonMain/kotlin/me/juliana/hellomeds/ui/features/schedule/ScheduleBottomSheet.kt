// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.designsystem.testing.ScreenshotTestTags
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.action_delete
import me.juliana.hellomeds.shared.action_save
import me.juliana.hellomeds.shared.day_friday
import me.juliana.hellomeds.shared.day_friday_short
import me.juliana.hellomeds.shared.day_monday
import me.juliana.hellomeds.shared.day_monday_short
import me.juliana.hellomeds.shared.day_saturday
import me.juliana.hellomeds.shared.day_saturday_short
import me.juliana.hellomeds.shared.day_sunday
import me.juliana.hellomeds.shared.day_sunday_short
import me.juliana.hellomeds.shared.day_thursday
import me.juliana.hellomeds.shared.day_thursday_short
import me.juliana.hellomeds.shared.day_tuesday
import me.juliana.hellomeds.shared.day_tuesday_short
import me.juliana.hellomeds.shared.day_wednesday
import me.juliana.hellomeds.shared.day_wednesday_short
import me.juliana.hellomeds.shared.schedule_action_archive
import me.juliana.hellomeds.shared.schedule_action_keep_editing
import me.juliana.hellomeds.shared.schedule_action_unarchive
import me.juliana.hellomeds.shared.schedule_dialog_archive_message
import me.juliana.hellomeds.shared.schedule_dialog_archive_title
import me.juliana.hellomeds.shared.schedule_dialog_delete_message
import me.juliana.hellomeds.shared.schedule_dialog_delete_title
import me.juliana.hellomeds.shared.schedule_dialog_discard_confirm
import me.juliana.hellomeds.shared.schedule_dialog_discard_message
import me.juliana.hellomeds.shared.schedule_dialog_discard_title
import me.juliana.hellomeds.shared.schedule_dialog_unarchive_message
import me.juliana.hellomeds.shared.schedule_dialog_unarchive_title
import me.juliana.hellomeds.shared.schedule_edit
import me.juliana.hellomeds.shared.schedule_end_date
import me.juliana.hellomeds.shared.schedule_end_date_unset
import me.juliana.hellomeds.shared.schedule_frequency_days_of_week
import me.juliana.hellomeds.shared.schedule_frequency_regular
import me.juliana.hellomeds.shared.schedule_interval_day
import me.juliana.hellomeds.shared.schedule_interval_days
import me.juliana.hellomeds.shared.schedule_interval_every
import me.juliana.hellomeds.shared.schedule_new
import me.juliana.hellomeds.shared.schedule_select_days
import me.juliana.hellomeds.shared.schedule_start_date
import me.juliana.hellomeds.shared.schedule_time
import me.juliana.hellomeds.ui.compat.ButtonGroupDefaults
import me.juliana.hellomeds.ui.compat.ToggleButton
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.IntegerInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListDatePickerItem
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.components.list.SmartListTimePickerItem
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import me.juliana.hellomeds.ui.util.formatDecimal
import me.juliana.hellomeds.ui.util.formatDecimalPlain
import me.juliana.hellomeds.ui.util.formatShortDate
import me.juliana.hellomeds.ui.util.formatTime
import me.juliana.hellomeds.ui.util.getDoseUnitPluralRes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

enum class DayOfWeek(val displayNameRes: StringResource, val shortNameRes: StringResource) {
    MONDAY(Res.string.day_monday, Res.string.day_monday_short),
    TUESDAY(Res.string.day_tuesday, Res.string.day_tuesday_short),
    WEDNESDAY(Res.string.day_wednesday, Res.string.day_wednesday_short),
    THURSDAY(Res.string.day_thursday, Res.string.day_thursday_short),
    FRIDAY(Res.string.day_friday, Res.string.day_friday_short),
    SATURDAY(Res.string.day_saturday, Res.string.day_saturday_short),
    SUNDAY(Res.string.day_sunday, Res.string.day_sunday_short),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleBottomSheet(
    schedule: Schedule?,
    medicationId: Int,
    medicationType: MedicationType,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: ((Schedule) -> Unit)? = null,
    onArchive: ((Schedule) -> Unit)? = null,
    onUnarchive: ((Schedule) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    platformContext()
    // Parse existing schedule or set defaults
    val isNewSchedule = schedule == null
    val isManuallyArchived = schedule?.isManuallyArchived() == true
    schedule?.isDateArchived() == true
    val isEffectivelyArchived = schedule?.isEffectivelyArchived() == true

    val initialFrequencyType = schedule?.frequencyType ?: FrequencyType.INTERVAL

    var frequencyType by remember(schedule) { mutableStateOf(initialFrequencyType) }
    var intervalDays by remember(schedule) { mutableIntStateOf(schedule?.frequencyValue ?: 1) }
    var selectedDays by remember(schedule) {
        mutableStateOf(
            schedule?.daysOfWeek?.split(",")?.mapNotNull { day ->
                try {
                    DayOfWeek.valueOf(day)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }?.toSet() ?: emptySet(),
        )
    }

    val initialTime = schedule?.timeOfDay?.let { LocalTime.parse(it) } ?: LocalTime(8, 0)
    var selectedTime by remember(schedule) { mutableStateOf(initialTime) }

    var dose by remember(schedule) { mutableDoubleStateOf(schedule?.dose ?: 1.0) }

    val initialStartDate = schedule?.startDate?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
    } ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    var startDate by remember(schedule) { mutableStateOf(initialStartDate) }

    val initialEndDate = schedule?.endDate?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var endDate by remember(schedule) { mutableStateOf(initialEndDate) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showUnarchiveDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var allowDismissal by remember { mutableStateOf(false) }

    fun isFormValid(): Boolean {
        // Dose must be greater than 0
        if (dose <= 0) return false

        // If interval, intervalDays must be between 1 and 31
        if (frequencyType == FrequencyType.INTERVAL && (intervalDays < 1 || intervalDays > 31)) {
            return false
        }

        // If days of week, at least one day must be selected
        if (frequencyType == FrequencyType.DAYS_OF_WEEK && selectedDays.isEmpty()) {
            return false
        }

        // For new schedules, start date cannot be in the past
        if (isNewSchedule && startDate < Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
        ) {
            return false
        }

        // End date must be after start date (not same day)
        val currentEndDate = endDate
        return !(currentEndDate != null && currentEndDate <= startDate)
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newValue ->
            // Prevent dismissal if form is invalid, unless explicitly allowed
            if (newValue == SheetValue.Hidden && !isFormValid() && !allowDismissal) {
                showDiscardDialog = true
                false // Prevent dismissal
            } else {
                true // Allow dismissal
            }
        },
    )

    fun saveSchedule() {
        val timeOfDay = selectedTime.toString()
        val startDateMillis =
            startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val endDateMillis =
            endDate?.atStartOfDayIn(TimeZone.currentSystemDefault())?.toEpochMilliseconds()

        val updatedSchedule = Schedule(
            id = schedule?.id ?: 0,
            medicationId = medicationId,
            dose = dose,
            startDate = startDateMillis,
            endDate = endDateMillis,
            timeOfDay = timeOfDay,
            frequencyType = frequencyType,
            frequencyValue = if (frequencyType == FrequencyType.INTERVAL) intervalDays else 0,
            daysOfWeek = if (frequencyType == FrequencyType.DAYS_OF_WEEK) {
                selectedDays.joinToString(",") { it.name }
            } else {
                null
            },
            isArchived = schedule?.isArchived ?: false,
            originTimeZone = schedule?.originTimeZone ?: TimeZone.currentSystemDefault().id,
        )
        onSave(updatedSchedule)
    }

    fun handleDismiss() {
        if (!isFormValid()) {
            showDiscardDialog = true
        } else {
            allowDismissal = true
            onDismiss()
        }
    }

    // Discard dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(Res.string.schedule_dialog_discard_title)) },
            text = { Text(stringResource(Res.string.schedule_dialog_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        allowDismissal = true
                        onDismiss()
                    },
                ) {
                    Text(stringResource(Res.string.schedule_dialog_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false },
                ) {
                    Text(stringResource(Res.string.schedule_action_keep_editing))
                }
            },
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.schedule_dialog_delete_title)) },
            text = { Text(stringResource(Res.string.schedule_dialog_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        if (schedule != null && onDelete != null) {
                            allowDismissal = true
                            onDelete(schedule)
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // Archive confirmation dialog
    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text(stringResource(Res.string.schedule_dialog_archive_title)) },
            text = { Text(stringResource(Res.string.schedule_dialog_archive_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveDialog = false
                        if (schedule != null && onArchive != null) {
                            allowDismissal = true
                            onArchive(schedule)
                            onDismiss()
                        }
                    },
                ) {
                    Text(stringResource(Res.string.schedule_action_archive))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showArchiveDialog = false },
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // Unarchive confirmation dialog
    if (showUnarchiveDialog) {
        AlertDialog(
            onDismissRequest = { showUnarchiveDialog = false },
            title = { Text(stringResource(Res.string.schedule_dialog_unarchive_title)) },
            text = { Text(stringResource(Res.string.schedule_dialog_unarchive_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnarchiveDialog = false
                        if (schedule != null && onUnarchive != null) {
                            allowDismissal = true
                            onUnarchive(schedule)
                            onDismiss()
                        }
                    },
                ) {
                    Text(stringResource(Res.string.schedule_action_unarchive))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUnarchiveDialog = false },
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = { handleDismiss() },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (isNewSchedule) {
                    stringResource(Res.string.schedule_new)
                } else {
                    stringResource(Res.string.schedule_edit)
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Frequency type selection
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListItem(
                            headlineContent = { Text(stringResource(Res.string.schedule_frequency_regular)) },
                            trailingContent = {
                                RadioButton(
                                    selected = frequencyType == FrequencyType.INTERVAL,
                                    onClick = if (!isManuallyArchived) {
                                        { frequencyType = FrequencyType.INTERVAL }
                                    } else {
                                        null
                                    },
                                    enabled = !isManuallyArchived,
                                )
                            },
                            shapes = shapes,
                            visible = visible,
                            onClick = if (!isManuallyArchived) {
                                { frequencyType = FrequencyType.INTERVAL }
                            } else {
                                null
                            },
                        )
                    },
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListItem(
                            headlineContent = { Text(stringResource(Res.string.schedule_frequency_days_of_week)) },
                            trailingContent = {
                                RadioButton(
                                    selected = frequencyType == FrequencyType.DAYS_OF_WEEK,
                                    onClick = if (!isManuallyArchived) {
                                        { frequencyType = FrequencyType.DAYS_OF_WEEK }
                                    } else {
                                        null
                                    },
                                    enabled = !isManuallyArchived,
                                )
                            },
                            shapes = shapes,
                            visible = visible,
                            onClick = if (!isManuallyArchived) {
                                { frequencyType = FrequencyType.DAYS_OF_WEEK }
                            } else {
                                null
                            },
                            modifier = Modifier.testTag(ScreenshotTestTags.SCHEDULE_FREQ_DAYS_OF_WEEK),
                        )
                    },
                ),
            )

            // Interval days selector
            if (frequencyType == FrequencyType.INTERVAL) {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            if (!isManuallyArchived) {
                                SmartListTextItem(
                                    label = stringResource(Res.string.schedule_interval_every),
                                    value = if (intervalDays == 0) "" else intervalDays.toString(),
                                    onValueChange = { value ->
                                        intervalDays = value.toIntOrNull() ?: 0
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    suffix = if (intervalDays == 1) {
                                        stringResource(Res.string.schedule_interval_day)
                                    } else {
                                        stringResource(Res.string.schedule_interval_days)
                                    },
                                    shapes = shapes,
                                    visible = visible,
                                    validator = { text ->
                                        text.isEmpty() || text.toIntOrNull()
                                            ?.let { it in 1..31 } == true
                                    },
                                    inputTransformation = IntegerInputTransformation(),
                                )
                            } else {
                                val suffix = if (intervalDays == 1) {
                                    stringResource(Res.string.schedule_interval_day)
                                } else {
                                    stringResource(Res.string.schedule_interval_days)
                                }
                                SmartListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(
                                                Res.string.schedule_interval_every,
                                            ),
                                        )
                                    },
                                    trailingContent = {
                                        Text(
                                            text = "$intervalDays $suffix",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    shapes = shapes,
                                    visible = visible,
                                )
                            }
                        },
                    ),
                )
            }

            // Days of week selector
            if (frequencyType == FrequencyType.DAYS_OF_WEEK) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.schedule_select_days),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        DayOfWeek.entries.forEachIndexed { index, day ->
                            ToggleButton(
                                checked = selectedDays.contains(day),
                                onCheckedChange = {
                                    selectedDays = if (it) {
                                        selectedDays + day
                                    } else {
                                        selectedDays - day
                                    }
                                },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    DayOfWeek.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                enabled = !isManuallyArchived,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(ScreenshotTestTags.scheduleDayChip(day.name)),
                            ) {
                                Text(stringResource(day.shortNameRes))
                            }
                        }
                    }
                }
            }

            val doseUnitRes = getDoseUnitPluralRes(medicationType)

            val doseQuantity = dose.toInt()
            val doseLabel = pluralStringResource(doseUnitRes, doseQuantity)
                .replaceFirstChar { it.uppercase() }

            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        if (!isManuallyArchived) {
                            SmartListTimePickerItem(
                                label = stringResource(Res.string.schedule_time),
                                time = selectedTime,
                                onTimeChange = { selectedTime = it },
                                shapes = shapes,
                                visible = visible,
                            )
                        } else {
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.schedule_time)) },
                                trailingContent = {
                                    Text(
                                        text = formatTime(selectedTime),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        }
                    },
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        if (!isManuallyArchived) {
                            SmartListTextItem(
                                label = doseLabel,
                                value = if (dose == 0.0) "" else formatDecimalPlain(dose),
                                onValueChange = { value ->
                                    dose = value.toDoubleOrNull() ?: 0.0
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shapes = shapes,
                                validator = { text ->
                                    text.isEmpty() || text.toDoubleOrNull()?.let { it > 0 } == true
                                },
                                inputTransformation = DecimalInputTransformation(),
                                visible = visible,
                            )
                        } else {
                            SmartListItem(
                                headlineContent = { Text(doseLabel) },
                                trailingContent = {
                                    Text(
                                        text = formatDecimal(dose),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        }
                    },
                ),
            )

            // Date range settings
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        if (!isManuallyArchived) {
                            SmartListDatePickerItem(
                                label = stringResource(Res.string.schedule_start_date),
                                date = startDate,
                                onDateChange = { it?.let { startDate = it } },
                                shapes = shapes,
                                visible = visible,
                                allowClear = false,
                            )
                        } else {
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.schedule_start_date)) },
                                trailingContent = {
                                    Text(
                                        text = formatShortDate(startDate),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        }
                    },
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        if (!isManuallyArchived) {
                            SmartListDatePickerItem(
                                label = stringResource(Res.string.schedule_end_date),
                                date = endDate,
                                onDateChange = { endDate = it },
                                shapes = shapes,
                                visible = visible,
                                allowClear = true,
                                unsetLabel = stringResource(Res.string.schedule_end_date_unset),
                            )
                        } else {
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.schedule_end_date)) },
                                trailingContent = {
                                    Text(
                                        text = endDate?.let { formatShortDate(it) }
                                            ?: stringResource(Res.string.schedule_end_date_unset),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        }
                    },
                ),
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isNewSchedule && onDelete != null) {
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(stringResource(Res.string.action_delete))
                    }
                }

                // Show archive button only if not archived
                if (!isNewSchedule && onArchive != null && !isEffectivelyArchived) {
                    Button(
                        onClick = { showArchiveDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text(stringResource(Res.string.schedule_action_archive))
                    }
                }

                // Show unarchive button only if manually archived (not date-archived)
                if (!isNewSchedule && onUnarchive != null && isManuallyArchived) {
                    Button(
                        onClick = { showUnarchiveDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text(stringResource(Res.string.schedule_action_unarchive))
                    }
                }

                // Show save button only if not manually archived, or if date-archived (can update end date)
                if (!isManuallyArchived) {
                    Button(
                        onClick = {
                            saveSchedule()
                            onDismiss()
                        },
                        enabled = isFormValid(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleBottomSheetNewPreview() {
    HelloMedsTheme {
        ScheduleBottomSheet(
            schedule = null,
            medicationId = 1,
            medicationType = MedicationType.INJECTION,
            onDismiss = {},
            onSave = {},
        )
    }
}

@Composable
private fun ScheduleBottomSheetEditPreview() {
    HelloMedsTheme {
        ScheduleBottomSheet(
            schedule = Schedule(
                id = 1,
                medicationId = 1,
                dose = 1.0,
                startDate = Clock.System.now().toEpochMilliseconds(),
                endDate = null,
                timeOfDay = "08:00",
                frequencyType = FrequencyType.DAYS_OF_WEEK,
                frequencyValue = 0,
                daysOfWeek = "MONDAY,WEDNESDAY,FRIDAY",
                isArchived = false,
            ),
            medicationId = 1,
            medicationType = MedicationType.INJECTION,
            onDismiss = {},
            onSave = {},
            onDelete = {},
            onArchive = {},
        )
    }
}
