// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import me.juliana.hellomeds.ui.components.medication.MedicationActionIconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.model.getCycleDay
import me.juliana.hellomeds.domain.validation.MedicationValidation
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.action_confirm
import me.juliana.hellomeds.shared.action_delete
import me.juliana.hellomeds.shared.action_edit
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.content_description_edit_label
import me.juliana.hellomeds.shared.content_description_edit_medication
import me.juliana.hellomeds.shared.content_description_edit_schedule
import me.juliana.hellomeds.shared.content_description_manage_stock
import me.juliana.hellomeds.shared.cycle_reset
import me.juliana.hellomeds.shared.cycle_reset_confirm_message
import me.juliana.hellomeds.shared.cycle_reset_confirm_title
import me.juliana.hellomeds.shared.cycle_set_day
import me.juliana.hellomeds.shared.cycle_set_day_title
import me.juliana.hellomeds.shared.medication_action_label
import me.juliana.hellomeds.shared.medication_action_schedule
import me.juliana.hellomeds.shared.medication_dialog_archive_message
import me.juliana.hellomeds.shared.medication_dialog_archive_title
import me.juliana.hellomeds.shared.medication_dialog_delete_message
import me.juliana.hellomeds.shared.medication_dialog_delete_title
import me.juliana.hellomeds.shared.medication_dialog_unarchive_message
import me.juliana.hellomeds.shared.medication_dialog_unarchive_title
import me.juliana.hellomeds.shared.medication_notes_label
import me.juliana.hellomeds.shared.outline_box_24px
import me.juliana.hellomeds.shared.schedule_action_archive
import me.juliana.hellomeds.shared.schedule_action_unarchive
import me.juliana.hellomeds.shared.screen_medication_details
import me.juliana.hellomeds.shared.screen_stock
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.list.SmartList
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.smartListSegmentedShapes
import me.juliana.hellomeds.ui.components.medication.CycleDayPicker
import me.juliana.hellomeds.ui.components.medication.MedicationShapeIcon
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import me.juliana.hellomeds.ui.theme.MedicationColor
import me.juliana.hellomeds.ui.util.displayName
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    medication: Medication,
    importanceLabel: ImportanceLabel?,
    typeAndStrength: String,
    onNavigateBack: () -> Unit,
    onEditSchedule: () -> Unit,
    onEditMedication: () -> Unit,
    onEditLabel: () -> Unit,
    onManageStock: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onNotesChange: (String) -> Unit,
    onResetCycle: () -> Unit = {},
    onSetCycleDay: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var notes by remember(medication) { mutableStateOf(medication.notes ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showResetCycleDialog by remember { mutableStateOf(false) }
    var showSetDayDialog by remember { mutableStateOf(false) }
    var initialNotes by remember(medication) { mutableStateOf(medication.notes ?: "") }
    val isArchived = medication.isArchived

    // Parse enum values from strings
    val foregroundShape = MedicationForegroundShape.fromNameOrDefault(
        medication.foregroundShape,
        MedicationForegroundShape.CAPSULE_PILL,
    )
    val backgroundShape = MedicationBackgroundShape.fromNameOrDefault(medication.backgroundShape)

    val color1 = medication.shapeColor?.let {
        MedicationColor.fromName(it)
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        val context = platformContext()
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.medication_dialog_delete_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.medication_dialog_delete_message,
                        medication.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                        onNavigateBack()
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

    // Archive/Unarchive confirmation dialog
    if (showArchiveDialog) {
        val context = platformContext()
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = {
                Text(
                    if (isArchived) {
                        stringResource(Res.string.medication_dialog_unarchive_title)
                    } else {
                        stringResource(Res.string.medication_dialog_archive_title)
                    },
                )
            },
            text = {
                Text(
                    if (isArchived) {
                        stringResource(
                            Res.string.medication_dialog_unarchive_message,
                            medication.name,
                        )
                    } else {
                        stringResource(
                            Res.string.medication_dialog_archive_message,
                            medication.name,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveDialog = false
                        if (isArchived) {
                            onUnarchive()
                        } else {
                            onArchive()
                        }
                        onNavigateBack()
                    },
                ) {
                    Text(
                        if (isArchived) {
                            stringResource(Res.string.schedule_action_unarchive)
                        } else {
                            stringResource(Res.string.schedule_action_archive)
                        },
                    )
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

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_medication_details)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // Save notes if changed
                            if (notes != initialNotes) {
                                onNotesChange(notes)
                            }
                            onNavigateBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Medication icon and info header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Medication shape icon
                MedicationShapeIcon(
                    foregroundShape = foregroundShape,
                    backgroundShape = backgroundShape,
                    color1 = color1,
                    size = 96.dp,
                )

                // Medication name (display name if set, otherwise actual name)
                Text(
                    text = MedicationValidation.getEffectiveDisplayName(medication),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                // Show actual medication name if display name is set
                if (MedicationValidation.hasCustomDisplayName(medication)) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                // Type and strength
                Text(
                    text = typeAndStrength,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                // Importance label chip
                if (importanceLabel != null) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = Color.Transparent,
                    ) {
                        Text(
                            text = importanceLabel.displayName(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Schedule button
                MedicationActionIconButton(
                    onClick = onEditSchedule,
                    enabled = !isArchived,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = stringResource(Res.string.content_description_edit_schedule),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = stringResource(Res.string.medication_action_schedule),
                )

                // Edit button
                MedicationActionIconButton(
                    onClick = onEditMedication,
                    enabled = !isArchived,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(Res.string.content_description_edit_medication),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = stringResource(Res.string.action_edit),
                )

                // Label button
                MedicationActionIconButton(
                    onClick = onEditLabel,
                    enabled = !isArchived,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(Res.string.content_description_edit_label),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = stringResource(Res.string.medication_action_label),
                )

                // Stock button
                MedicationActionIconButton(
                    onClick = onManageStock,
                    enabled = !isArchived,
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.outline_box_24px),
                            contentDescription = stringResource(Res.string.content_description_manage_stock),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = stringResource(Res.string.screen_stock),
                )
            }

            // Cycle progress card (when cyclic)
            if (medication.cycleType == CycleType.CYCLIC) {
                val currentDate = kotlin.time.Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date

                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        me.juliana.hellomeds.ui.components.medication.CycleProgressIndicator(
                            medication = medication,
                            currentDate = currentDate,
                        )

                        if (!isArchived) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                androidx.compose.material3.FilledTonalButton(
                                    onClick = { showResetCycleDialog = true },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) {
                                    Text(stringResource(Res.string.cycle_reset))
                                }
                                androidx.compose.material3.FilledTonalButton(
                                    onClick = { showSetDayDialog = true },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) {
                                    Text(stringResource(Res.string.cycle_set_day))
                                }
                            }
                        }
                    }
                }
            }

            // Reset cycle confirmation dialog
            if (showResetCycleDialog) {
                AlertDialog(
                    onDismissRequest = { showResetCycleDialog = false },
                    title = { Text(stringResource(Res.string.cycle_reset_confirm_title)) },
                    text = { Text(stringResource(Res.string.cycle_reset_confirm_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showResetCycleDialog = false
                            onResetCycle()
                        }) {
                            Text(stringResource(Res.string.cycle_reset))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetCycleDialog = false }) {
                            Text(stringResource(Res.string.action_cancel))
                        }
                    },
                )
            }

            // Set cycle day dialog
            if (showSetDayDialog && medication.cycleType == CycleType.CYCLIC) {
                val currentDate = kotlin.time.Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                val cycleInfo = getCycleDay(medication, currentDate)
                if (cycleInfo != null) {
                    SetCycleDayDialog(
                        daysActive = medication.cycleDaysActive ?: 0,
                        daysBreak = medication.cycleDaysBreak ?: 0,
                        currentDayInCycle = cycleInfo.dayInCycle + 1,
                        hasPlacebos = medication.cycleHasPlacebos,
                        onDismiss = { showSetDayDialog = false },
                        onConfirm = { day ->
                            showSetDayDialog = false
                            onSetCycleDay(day)
                        },
                    )
                }
            }

            // Notes text field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(Res.string.medication_notes_label)) },
                enabled = !isArchived,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 120.dp),
                maxLines = Int.MAX_VALUE,
            )

            // Actions
            SmartList(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SmartListItem(
                    headlineContent = { Text(stringResource(Res.string.action_delete)) },
                    shapes = smartListSegmentedShapes(index = 0, count = 2),
                    onClick = { showDeleteDialog = true },
                )

                SmartListItem(
                    headlineContent = {
                        Text(
                            if (isArchived) {
                                stringResource(Res.string.schedule_action_unarchive)
                            } else {
                                stringResource(Res.string.schedule_action_archive)
                            },
                        )
                    },
                    shapes = smartListSegmentedShapes(index = 1, count = 2),
                    onClick = { showArchiveDialog = true },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetCycleDayDialog(
    daysActive: Int,
    daysBreak: Int,
    currentDayInCycle: Int,
    hasPlacebos: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val cycleLength = daysActive + daysBreak
    var dayInput by remember { mutableStateOf(currentDayInCycle.toString()) }
    val parsedDay = dayInput.toIntOrNull()
    val isValid = parsedDay != null && parsedDay in 1..cycleLength

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.cycle_set_day_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                CycleDayPicker(
                    daysActive = daysActive,
                    daysBreak = daysBreak,
                    dayInCycle = dayInput,
                    hasPlacebos = hasPlacebos,
                    onDayInCycleChange = { dayInput = it },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                    Button(
                        onClick = { if (isValid) onConfirm(parsedDay!!) },
                        enabled = isValid,
                    ) {
                        Text(stringResource(Res.string.action_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationDetailScreenPreview() {
    HelloMedsTheme {
        MedicationDetailScreen(
            medication = Medication(
                id = 1,
                name = "Vitamin D3",
                type = MedicationType.CAPSULE,
                shape = "",
                importanceLabelId = 1,
                strengthValue = 1.0,
                strengthUnit = MedicationStrengthUnit.MG,
                foregroundShape = MedicationForegroundShape.CAPSULE_PILL.name,
                backgroundShape = MedicationBackgroundShape.EIGHT_LEAF_CLOVER.name,
                shapeColor = MedicationColor.Purple::class.simpleName,
                notes = "Take with food",
            ),
            importanceLabel = ImportanceLabel(
                id = 1,
                name = "Silent",
                shouldRemind = false,
                isCritical = false,
                hasFollowUps = false,
            ),
            typeAndStrength = "Pill, 1mg",
            onNavigateBack = {},
            onEditSchedule = {},
            onEditMedication = {},
            onEditLabel = {},
            onManageStock = {},
            onDelete = {},
            onArchive = {},
            onUnarchive = {},
            onNotesChange = {},
        )
    }
}
