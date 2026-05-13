// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.database.DefaultLabelType
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.action_delete
import me.juliana.hellomeds.shared.action_discard
import me.juliana.hellomeds.shared.action_keep_editing
import me.juliana.hellomeds.shared.action_ok
import me.juliana.hellomeds.shared.importance_label_alarm_after
import me.juliana.hellomeds.shared.importance_label_alarm_after_critical_hint
import me.juliana.hellomeds.shared.importance_label_alarm_after_suffix
import me.juliana.hellomeds.shared.importance_label_alarm_limited_warning
import me.juliana.hellomeds.shared.importance_label_become_alarm
import me.juliana.hellomeds.shared.importance_label_become_alarm_description
import me.juliana.hellomeds.shared.importance_label_become_critical
import me.juliana.hellomeds.shared.importance_label_become_critical_description
import me.juliana.hellomeds.shared.importance_label_critical_after
import me.juliana.hellomeds.shared.importance_label_critical_after_suffix
import me.juliana.hellomeds.shared.importance_label_dialog_delete_blocked_message
import me.juliana.hellomeds.shared.importance_label_dialog_delete_blocked_title
import me.juliana.hellomeds.shared.importance_label_dialog_delete_message_safe
import me.juliana.hellomeds.shared.importance_label_dialog_delete_title
import me.juliana.hellomeds.shared.importance_label_dialog_discard_message
import me.juliana.hellomeds.shared.importance_label_dialog_discard_title
import me.juliana.hellomeds.shared.importance_label_escalation_range_hint
import me.juliana.hellomeds.shared.importance_label_follow_up_notifications
import me.juliana.hellomeds.shared.importance_label_fsi_dialog_message
import me.juliana.hellomeds.shared.importance_label_fsi_dialog_open_settings
import me.juliana.hellomeds.shared.importance_label_fsi_dialog_title
import me.juliana.hellomeds.shared.importance_label_interval
import me.juliana.hellomeds.shared.importance_label_name
import me.juliana.hellomeds.shared.importance_label_notification_type
import me.juliana.hellomeds.shared.importance_label_number_of_follow_ups
import me.juliana.hellomeds.shared.importance_label_reset_to_default
import me.juliana.hellomeds.shared.importance_label_send_reminders
import me.juliana.hellomeds.shared.importance_label_type_alarm
import me.juliana.hellomeds.shared.importance_label_type_alarm_description
import me.juliana.hellomeds.shared.importance_label_type_critical
import me.juliana.hellomeds.shared.importance_label_type_critical_description
import me.juliana.hellomeds.shared.importance_label_type_regular
import me.juliana.hellomeds.shared.importance_label_type_regular_description
import me.juliana.hellomeds.shared.time_unit_minutes
import me.juliana.hellomeds.ui.compat.ButtonGroupDefaults
import me.juliana.hellomeds.ui.compat.ToggleButton
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.IntegerInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import me.juliana.hellomeds.ui.util.PermissionUtils
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import me.juliana.hellomeds.ui.util.displayName
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private enum class NotificationType { REGULAR, CRITICAL, ALARM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportanceLabelBottomSheet(
    label: ImportanceLabel?,
    onDismiss: () -> Unit,
    onSave: (ImportanceLabel) -> Unit,
    onDelete: ((ImportanceLabel) -> Unit)? = null,
    onCheckDeletion: (suspend (Int) -> List<Medication>)? = null,
    onResetToDefault: ((ImportanceLabel) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val initialDisplayName = label?.displayName() ?: ""
    var name by remember(label) { mutableStateOf(initialDisplayName) }
    var shouldRemind by remember(label) { mutableStateOf(label?.shouldRemind ?: true) }
    var notificationType by remember(label) {
        mutableStateOf(
            when {
                label?.isAlarm == true -> NotificationType.ALARM
                label?.isCritical == true -> NotificationType.CRITICAL
                else -> NotificationType.REGULAR
            },
        )
    }
    var hasFollowUps by remember(label) { mutableStateOf(label?.hasFollowUps ?: false) }
    var followUpCount by remember(label) { mutableIntStateOf(label?.followUpCount ?: 3) }
    var followUpIntervalMinutes by remember(label) {
        mutableIntStateOf(label?.followUpIntervalMinutes ?: 20)
    }
    var becomeCritical by remember(label) { mutableStateOf(label?.criticalAfterFollowUp != null) }
    var criticalAfter by remember(label) { mutableIntStateOf(label?.criticalAfterFollowUp ?: 2) }
    var becomeAlarm by remember(label) { mutableStateOf(label?.alarmAfterFollowUp != null) }
    var alarmAfter by remember(label) { mutableIntStateOf(label?.alarmAfterFollowUp ?: 3) }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFsiPermissionDialog by remember { mutableStateOf(false) }
    var allowDismissal by remember { mutableStateOf(false) }
    var medicationsUsingLabel by remember { mutableStateOf<List<Medication>>(emptyList()) }

    // FSI permission check with lifecycle re-check
    val context = platformContext()
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasFsiPermission by remember {
        mutableStateOf(PermissionUtils.canUseFullScreenIntent(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasFsiPermission = PermissionUtils.canUseFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun isFormValid(): Boolean {
        if (name.isBlank()) return false
        if (hasFollowUps && shouldRemind) {
            if (followUpCount <= 0) return false
            if (followUpIntervalMinutes <= 0) return false
            if (becomeCritical && notificationType == NotificationType.REGULAR) {
                if (criticalAfter <= 0 || criticalAfter > followUpCount) return false
            }
            if (becomeAlarm && notificationType != NotificationType.ALARM) {
                if (alarmAfter <= 0 || alarmAfter > followUpCount) return false
                if (becomeCritical && notificationType == NotificationType.REGULAR && alarmAfter <= criticalAfter) return false
            }
        }
        return true
    }

    fun saveLabel() {
        // If the user didn't change the display name of a default label,
        // store the English default so locale-based detection continues to work.
        val nameToStore = if (label?.defaultType != null && name.trim() == initialDisplayName) {
            DefaultLabelType.entries.find { it.defaultType == label.defaultType }?.defaultName
                ?: name.trim()
        } else {
            name.trim()
        }
        val updatedLabel = ImportanceLabel(
            id = label?.id ?: 0,
            name = nameToStore,
            shouldRemind = shouldRemind,
            isCritical = notificationType == NotificationType.CRITICAL,
            isAlarm = notificationType == NotificationType.ALARM,
            hasFollowUps = hasFollowUps,
            followUpCount = followUpCount,
            followUpIntervalMinutes = followUpIntervalMinutes,
            criticalAfterFollowUp = if (becomeCritical && hasFollowUps && notificationType == NotificationType.REGULAR) criticalAfter else null,
            alarmAfterFollowUp = if (becomeAlarm && hasFollowUps && notificationType != NotificationType.ALARM) alarmAfter else null,
            defaultType = label?.defaultType,
        )
        onSave(updatedLabel)
    }

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newValue ->
            // Only block dismissal for blank names (nothing to save)
            if (newValue == SheetValue.Hidden && name.isBlank() && !allowDismissal) {
                showDiscardDialog = true
                false
            } else {
                true
            }
        },
    )

    // Auto-save on dismiss if the form is valid, otherwise just let it close.
    // Invalid fields show inline red text via their validators.
    fun handleDismiss() {
        if (name.isNotBlank() && isFormValid()) {
            saveLabel()
        }
        allowDismissal = true
        onDismiss()
    }

    fun trySelectAlarmType() {
        if (!hasFsiPermission) {
            showFsiPermissionDialog = true
        } else {
            notificationType = NotificationType.ALARM
        }
    }

    fun tryEnableBecomeAlarm() {
        if (!hasFsiPermission) {
            showFsiPermissionDialog = true
        } else {
            becomeAlarm = true
        }
    }

    // Discard dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(Res.string.importance_label_dialog_discard_title)) },
            text = { Text(stringResource(Res.string.importance_label_dialog_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        allowDismissal = true
                        onDismiss()
                    },
                ) {
                    Text(stringResource(Res.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false },
                ) {
                    Text(stringResource(Res.string.action_keep_editing))
                }
            },
        )
    }

    // FSI permission dialog
    if (showFsiPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showFsiPermissionDialog = false },
            title = { Text(stringResource(Res.string.importance_label_fsi_dialog_title)) },
            text = { Text(stringResource(Res.string.importance_label_fsi_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFsiPermissionDialog = false
                        PermissionUtils.openFullScreenIntentSettings(context)
                    },
                ) {
                    Text(stringResource(Res.string.importance_label_fsi_dialog_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFsiPermissionDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        if (medicationsUsingLabel.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    medicationsUsingLabel = emptyList()
                },
                title = { Text(stringResource(Res.string.importance_label_dialog_delete_blocked_title)) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            pluralStringResource(
                                Res.plurals.importance_label_dialog_delete_blocked_message,
                                medicationsUsingLabel.size,
                                medicationsUsingLabel.size,
                            ),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            medicationsUsingLabel.forEach { medication ->
                                Text(
                                    text = "\u2022 ${medication.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            medicationsUsingLabel = emptyList()
                        },
                    ) {
                        Text(stringResource(Res.string.action_ok))
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(Res.string.importance_label_dialog_delete_title)) },
                text = { Text(stringResource(Res.string.importance_label_dialog_delete_message_safe)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            if (label != null && onDelete != null) {
                                allowDismissal = true
                                onDelete(label)
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
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                },
            )
        }
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
            // Group 1: Name and Send reminders
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListTextItem(
                            label = stringResource(Res.string.importance_label_name),
                            value = name,
                            onValueChange = { name = it },
                            shapes = shapes,
                            visible = visible,
                        )
                    },
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListItem(
                            headlineContent = { Text(stringResource(Res.string.importance_label_send_reminders)) },
                            trailingContent = {
                                Switch(
                                    checked = shouldRemind,
                                    onCheckedChange = { shouldRemind = it },
                                )
                            },
                            shapes = shapes,
                            visible = visible,
                        )
                    },
                ),
            )

            // Notification type button row + info card
            if (shouldRemind) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.importance_label_notification_type),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val types = NotificationType.entries
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        types.forEachIndexed { index, type ->
                            ToggleButton(
                                checked = notificationType == type,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        when (type) {
                                            NotificationType.ALARM -> trySelectAlarmType()
                                            else -> notificationType = type
                                        }
                                    }
                                },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    types.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    when (type) {
                                        NotificationType.REGULAR -> stringResource(
                                            Res.string.importance_label_type_regular,
                                        )
                                        NotificationType.CRITICAL -> stringResource(
                                            Res.string.importance_label_type_critical,
                                        )
                                        NotificationType.ALARM -> stringResource(Res.string.importance_label_type_alarm)
                                    },
                                )
                            }
                        }
                    }

                    // Info card explaining the selected notification type
                    SmartListInfoCard(
                        headlineContent = {
                            Text(
                                when (notificationType) {
                                    NotificationType.REGULAR -> stringResource(
                                        Res.string.importance_label_type_regular_description,
                                    )
                                    NotificationType.CRITICAL -> stringResource(
                                        Res.string.importance_label_type_critical_description,
                                    )
                                    NotificationType.ALARM -> stringResource(
                                        Res.string.importance_label_type_alarm_description,
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                    )

                    // Warning when alarm type is selected but native alarms are unavailable (iOS < 26)
                    if (notificationType == NotificationType.ALARM &&
                        !PlatformCapabilities.isNativeAlarmSupported() &&
                        !PlatformCapabilities.supportsNotificationChannels()
                    ) {
                        SmartListInfoCard(
                            headlineContent = {
                                Text(
                                    stringResource(Res.string.importance_label_alarm_limited_warning),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }

            // Group 2: Follow-up basics (toggle + count + interval)
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListItem(
                            headlineContent = {
                                Text(
                                    stringResource(Res.string.importance_label_follow_up_notifications),
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = hasFollowUps && shouldRemind,
                                    onCheckedChange = { hasFollowUps = it },
                                    enabled = shouldRemind,
                                )
                            },
                            shapes = shapes,
                            visible = visible,
                        )
                    },
                    SmartListItemConfig(visible = hasFollowUps && shouldRemind) { shapes, visible ->
                        SmartListTextItem(
                            label = stringResource(Res.string.importance_label_number_of_follow_ups),
                            value = if (followUpCount == 0) "" else followUpCount.toString(),
                            onValueChange = { followUpCount = it.toIntOrNull() ?: 0 },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shapes = shapes,
                            visible = visible,
                            validator = { text ->
                                text.isEmpty() || text.toIntOrNull()?.let { it > 0 } == true
                            },
                            inputTransformation = IntegerInputTransformation(),
                        )
                    },
                    SmartListItemConfig(visible = hasFollowUps && shouldRemind) { shapes, visible ->
                        SmartListTextItem(
                            label = stringResource(Res.string.importance_label_interval),
                            value = if (followUpIntervalMinutes == 0) "" else followUpIntervalMinutes.toString(),
                            onValueChange = { followUpIntervalMinutes = it.toIntOrNull() ?: 0 },
                            suffix = stringResource(Res.string.time_unit_minutes),
                            shapes = shapes,
                            visible = visible,
                            validator = { text ->
                                text.isEmpty() || text.toIntOrNull()?.let { it > 0 } == true
                            },
                            inputTransformation = IntegerInputTransformation(),
                        )
                    },
                ),
            )

            // Group 3: Escalation options (separate from follow-up basics)
            // Only shown when follow-ups are enabled and type allows escalation
            val showBecomeCritical = hasFollowUps && shouldRemind && notificationType == NotificationType.REGULAR
            val showCriticalAfter = showBecomeCritical && becomeCritical
            val showBecomeAlarm = hasFollowUps && shouldRemind && notificationType != NotificationType.ALARM
            val showAlarmAfter = showBecomeAlarm && becomeAlarm
            val showEscalationGroup = showBecomeCritical || showBecomeAlarm

            if (showEscalationGroup) {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = showBecomeCritical) { shapes, visible ->
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.importance_label_become_critical)) },
                                supportingContent = {
                                    Text(
                                        stringResource(Res.string.importance_label_become_critical_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = becomeCritical,
                                        onCheckedChange = { becomeCritical = it },
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = showCriticalAfter) { shapes, visible ->
                            SmartListTextItem(
                                label = stringResource(Res.string.importance_label_critical_after),
                                value = if (criticalAfter == 0) "" else criticalAfter.toString(),
                                onValueChange = { criticalAfter = it.toIntOrNull() ?: 0 },
                                suffix = stringResource(Res.string.importance_label_critical_after_suffix),
                                supportingText = stringResource(
                                    Res.string.importance_label_escalation_range_hint,
                                    followUpCount,
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shapes = shapes,
                                visible = visible,
                                validator = { text ->
                                    val n = text.toIntOrNull()
                                    text.isEmpty() || (n != null && n > 0 && n <= followUpCount)
                                },
                                inputTransformation = IntegerInputTransformation(),
                            )
                        },
                        SmartListItemConfig(visible = showBecomeAlarm) { shapes, visible ->
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.importance_label_become_alarm)) },
                                supportingContent = {
                                    Text(
                                        stringResource(Res.string.importance_label_become_alarm_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = becomeAlarm,
                                        onCheckedChange = { checked ->
                                            if (checked) tryEnableBecomeAlarm() else becomeAlarm = false
                                        },
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = showAlarmAfter) { shapes, visible ->
                            SmartListTextItem(
                                label = stringResource(Res.string.importance_label_alarm_after),
                                value = if (alarmAfter == 0) "" else alarmAfter.toString(),
                                onValueChange = { alarmAfter = it.toIntOrNull() ?: 0 },
                                suffix = stringResource(Res.string.importance_label_alarm_after_suffix),
                                supportingText = if (becomeCritical && notificationType == NotificationType.REGULAR) {
                                    stringResource(Res.string.importance_label_alarm_after_critical_hint, criticalAfter)
                                } else {
                                    stringResource(Res.string.importance_label_escalation_range_hint, followUpCount)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shapes = shapes,
                                visible = visible,
                                validator = { text ->
                                    val n = text.toIntOrNull()
                                    text.isEmpty() || (n != null && n > 0 && n <= followUpCount)
                                },
                                inputTransformation = IntegerInputTransformation(),
                            )
                        },
                    ),
                )
            }

            // Reset to default (for built-in labels) or Delete (for custom labels)
            if (label != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    if (label.isDefault && onResetToDefault != null) {
                        Button(
                            onClick = {
                                onResetToDefault(label)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text(stringResource(Res.string.importance_label_reset_to_default))
                        }
                    } else if (!label.isDefault && onDelete != null && onCheckDeletion != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val activeMeds = onCheckDeletion(label.id)
                                    medicationsUsingLabel = activeMeds
                                    showDeleteDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text(stringResource(Res.string.action_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportanceLabelBottomSheetNewPreview() {
    HelloMedsTheme {
        ImportanceLabelBottomSheet(
            label = null,
            onDismiss = {},
            onSave = {},
        )
    }
}

@Composable
private fun ImportanceLabelBottomSheetEditPreview() {
    HelloMedsTheme {
        ImportanceLabelBottomSheet(
            label = ImportanceLabel(
                id = 1,
                name = "Follow ups",
                shouldRemind = true,
                isCritical = false,
                hasFollowUps = true,
                followUpCount = 3,
                followUpIntervalMinutes = 20,
                criticalAfterFollowUp = 2,
            ),
            onDismiss = {},
            onSave = {},
            onDelete = {},
        )
    }
}
