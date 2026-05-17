// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.domain.validation.MedicationValidation
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.action_delete
import me.juliana.hellomeds.shared.action_edit
import me.juliana.hellomeds.shared.content_description_edit_label
import me.juliana.hellomeds.shared.content_description_edit_medication
import me.juliana.hellomeds.shared.content_description_edit_schedule
import me.juliana.hellomeds.shared.medication_action_add_stock
import me.juliana.hellomeds.shared.medication_action_label
import me.juliana.hellomeds.shared.medication_action_manage_stock
import me.juliana.hellomeds.shared.medication_action_schedule
import me.juliana.hellomeds.shared.medication_dialog_archive_message
import me.juliana.hellomeds.shared.medication_dialog_archive_title
import me.juliana.hellomeds.shared.medication_dialog_delete_message
import me.juliana.hellomeds.shared.medication_dialog_delete_title
import me.juliana.hellomeds.shared.medication_dialog_unarchive_message
import me.juliana.hellomeds.shared.medication_dialog_unarchive_title
import me.juliana.hellomeds.shared.medication_notes_label
import me.juliana.hellomeds.shared.schedule_action_archive
import me.juliana.hellomeds.shared.schedule_action_unarchive
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.medication.MedicationActionIconButton
import me.juliana.hellomeds.ui.components.medication.MedicationShapeIcon
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import me.juliana.hellomeds.ui.theme.MedicationColor
import me.juliana.hellomeds.ui.util.displayName
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailBottomSheet(
    medication: Medication,
    importanceLabel: ImportanceLabel?,
    typeAndStrength: String,
    onDismiss: () -> Unit,
    onEditSchedule: () -> Unit,
    onEditMedication: () -> Unit,
    onEditLabel: () -> Unit,
    onManageStock: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var notes by remember(medication) { mutableStateOf(medication.notes ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var initialNotes by remember(medication) { mutableStateOf(medication.notes ?: "") }
    val isArchived = medication.isArchived

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

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
        platformContext()
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
                        onDismiss()
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
        platformContext()
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
                        onDismiss()
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

    ModalBottomSheet(
        onDismissRequest = {
            // Save notes if changed
            if (notes != initialNotes) {
                onNotesChange(notes)
            }
            onDismiss()
        },
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
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                run {
                    val stockIcon = if (medication.stockTrackingEnabled) {
                        Icons.AutoMirrored.Filled.List
                    } else {
                        Icons.Default.Add
                    }
                    val stockLabel = if (medication.stockTrackingEnabled) {
                        stringResource(Res.string.medication_action_manage_stock)
                    } else {
                        stringResource(Res.string.medication_action_add_stock)
                    }
                    MedicationActionIconButton(
                        onClick = onManageStock,
                        enabled = !isArchived,
                        icon = {
                            Icon(
                                imageVector = stockIcon,
                                contentDescription = stockLabel,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = stockLabel,
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
                    .height(120.dp),
                maxLines = 5,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Delete and Archive buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text(stringResource(Res.string.action_delete))
                }

                Button(
                    onClick = { showArchiveDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text(
                        if (isArchived) {
                            stringResource(Res.string.schedule_action_unarchive)
                        } else {
                            stringResource(Res.string.schedule_action_archive)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationDetailBottomSheetPreview() {
    HelloMedsTheme {
        MedicationDetailBottomSheet(
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
            onDismiss = {},
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
