// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.action_save
import me.juliana.hellomeds.shared.auto_backup_change_passphrase
import me.juliana.hellomeds.shared.auto_backup_confirm_passphrase
import me.juliana.hellomeds.shared.auto_backup_days_ago
import me.juliana.hellomeds.shared.auto_backup_destination_icloud
import me.juliana.hellomeds.shared.auto_backup_destination_unavailable
import me.juliana.hellomeds.shared.auto_backup_disabled_banner
import me.juliana.hellomeds.shared.auto_backup_enable
import me.juliana.hellomeds.shared.auto_backup_enable_description
import me.juliana.hellomeds.shared.auto_backup_enabled_banner
import me.juliana.hellomeds.shared.auto_backup_hours_ago
import me.juliana.hellomeds.shared.auto_backup_intro_p1
import me.juliana.hellomeds.shared.auto_backup_intro_p2
import me.juliana.hellomeds.shared.auto_backup_just_now
import me.juliana.hellomeds.shared.auto_backup_location_label
import me.juliana.hellomeds.shared.auto_backup_location_not_set
import me.juliana.hellomeds.shared.auto_backup_long_ago
import me.juliana.hellomeds.shared.auto_backup_minutes_ago
import me.juliana.hellomeds.shared.auto_backup_no_backups_yet
import me.juliana.hellomeds.shared.auto_backup_passphrase
import me.juliana.hellomeds.shared.auto_backup_passphrase_hint
import me.juliana.hellomeds.shared.auto_backup_passphrase_label
import me.juliana.hellomeds.shared.auto_backup_passphrase_mismatch
import me.juliana.hellomeds.shared.auto_backup_passphrase_not_set
import me.juliana.hellomeds.shared.auto_backup_passphrase_saved
import me.juliana.hellomeds.shared.auto_backup_passphrase_set
import me.juliana.hellomeds.shared.auto_backup_passphrase_too_short
import me.juliana.hellomeds.shared.auto_backup_remember_warning
import me.juliana.hellomeds.shared.auto_backup_retention_count
import me.juliana.hellomeds.shared.auto_backup_retention_title
import me.juliana.hellomeds.shared.auto_backup_set_passphrase
import me.juliana.hellomeds.shared.auto_backup_settings_title
import me.juliana.hellomeds.shared.auto_backup_status_failed
import me.juliana.hellomeds.shared.auto_backup_title
import me.juliana.hellomeds.shared.auto_backup_trigger
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListSwitchItem
import me.juliana.hellomeds.ui.features.settings.SettingsHeader
import me.juliana.hellomeds.ui.features.settings.settingsContentPadding
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import me.juliana.hellomeds.ui.viewmodel.AutoBackupEvent
import me.juliana.hellomeds.ui.viewmodel.AutoBackupUiState
import me.juliana.hellomeds.ui.viewmodel.AutoBackupViewModel
import me.juliana.hellomeds.ui.viewmodel.LastBackupBucket
import me.juliana.hellomeds.ui.viewmodel.lastBackupRelativeBucket
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupSettingsScreen(
    viewModel: AutoBackupViewModel,
    onNavigateBack: () -> Unit,
    onPickFolder: ((initialUri: String?) -> Unit)? = null,
    suggestedInitialUri: String? = null,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward },
    )
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isBackingUp by viewModel.isBackingUp.collectAsStateWithLifecycle()
    val showPassphraseDialog by viewModel.showPassphraseDialog.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val passphraseMessage = stringResource(Res.string.auto_backup_passphrase_saved)

    // 1-minute ticker so the "last backup X ago" label updates while the screen is open.
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1.minutes)
            now = Clock.System.now()
        }
    }

    // Drive prerequisite prompts from the VM (enable flow walks the user through passphrase
    // and destination if missing).
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AutoBackupEvent.RequestPassphrase -> viewModel.showSetPassphraseDialog()
                AutoBackupEvent.RequestPickFolder -> onPickFolder?.invoke(suggestedInitialUri)
            }
        }
    }

    backupMessage?.let { message ->
        scope.launch {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    if (showPassphraseDialog) {
        PassphraseDialog(
            isChange = uiState.hasPassphrase,
            existingHint = uiState.passphraseHint,
            onConfirm = { passphrase, hint ->
                viewModel.setPassphrase(passphrase, hint) { success ->
                    viewModel.dismissPassphraseDialog()
                    if (success) {
                        scope.launch {
                            snackbarHostState.showSnackbar(passphraseMessage)
                        }
                    }
                }
            },
            onDismiss = { viewModel.dismissPassphraseDialog() },
        )
    }

    AppScaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.auto_backup_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.auto_backup_intro_p1),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .settingsContentPadding()
                        .padding(top = 8.dp),
                )
            }
            item {
                Text(
                    text = stringResource(Res.string.auto_backup_intro_p2),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .settingsContentPadding()
                        .padding(bottom = 8.dp),
                )
            }

            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = uiState.isEnabled) { shapes, visible ->
                            SmartListInfoCard(
                                headlineContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                        )
                                        Text(stringResource(Res.string.auto_backup_enabled_banner))
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = !uiState.isEnabled) { shapes, visible ->
                            SmartListInfoCard(
                                headlineContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                        )
                                        Text(stringResource(Res.string.auto_backup_disabled_banner))
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                    ),
                )
            }

            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.auto_backup_enable),
                                supportingText = stringResource(Res.string.auto_backup_enable_description),
                                checked = uiState.isEnabled,
                                onCheckedChange = { viewModel.onEnableToggled(it) },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            val lastLabel = remember(uiState.lastBackupTimestamp, now) {
                                lastBackupLabelKey(uiState.lastBackupTimestamp, now)
                            }
                            SmartListItem(
                                headlineContent = {
                                    Text(stringResource(Res.string.auto_backup_trigger))
                                },
                                supportingContent = {
                                    Text(text = lastBackupLabelText(lastLabel, uiState))
                                },
                                trailingContent = if (isBackingUp) {
                                    {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                } else {
                                    null
                                },
                                shapes = shapes,
                                visible = visible,
                                onClick = if (uiState.isEnabled && !isBackingUp) {
                                    { viewModel.triggerManualBackup() }
                                } else {
                                    null
                                },
                            )
                        },
                    ),
                )
            }

            item {
                SettingsHeader(stringResource(Res.string.auto_backup_settings_title))
            }

            item {
                AutoSmartList(
                    items = buildList {
                        // Passphrase
                        add(
                            SmartListItemConfig(visible = true) { shapes, visible ->
                                SmartListItem(
                                    headlineContent = {
                                        Text(stringResource(Res.string.auto_backup_passphrase_label))
                                    },
                                    supportingContent = {
                                        Text(
                                            if (uiState.hasPassphrase) {
                                                stringResource(Res.string.auto_backup_passphrase_set)
                                            } else {
                                                stringResource(Res.string.auto_backup_passphrase_not_set)
                                            },
                                        )
                                    },
                                    trailingContent = if (uiState.hasPassphrase) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    shapes = shapes,
                                    visible = visible,
                                    onClick = { viewModel.showSetPassphraseDialog() },
                                )
                            },
                        )

                        // Location (Android only)
                        if (PlatformCapabilities.supportsAutoBackupFolderPicker()) {
                            add(
                                SmartListItemConfig(visible = true) { shapes, visible ->
                                    val locationOk = uiState.destinationUri != null &&
                                        uiState.isDestinationAvailable
                                    SmartListItem(
                                        headlineContent = {
                                            Text(stringResource(Res.string.auto_backup_location_label))
                                        },
                                        supportingContent = {
                                            val text = if (uiState.destinationUri != null) {
                                                val decoded = percentDecode(uiState.destinationUri.orEmpty())
                                                decoded
                                                    .substringAfterLast("/tree/")
                                                    .substringAfter(":")
                                                    .ifEmpty { decoded.substringAfterLast("/") }
                                            } else {
                                                stringResource(Res.string.auto_backup_location_not_set)
                                            }
                                            Text(text = text, maxLines = 1)
                                        },
                                        trailingContent = if (locationOk) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        shapes = shapes,
                                        visible = visible,
                                        onClick = { onPickFolder?.invoke(suggestedInitialUri) },
                                    )
                                },
                            )
                        }

                        // Retention
                        add(
                            SmartListItemConfig(visible = true) { shapes, visible ->
                                SmartListItem(
                                    headlineContent = {
                                        Text(stringResource(Res.string.auto_backup_retention_title))
                                    },
                                    supportingContent = {
                                        Text(
                                            pluralStringResource(
                                                Res.plurals.auto_backup_retention_count,
                                                uiState.retentionCount,
                                                uiState.retentionCount,
                                            ),
                                        )
                                    },
                                    shapes = shapes,
                                    visible = visible,
                                )
                            },
                        )
                    },
                )
            }

            if (PlatformCapabilities.supportsAutoBackupFolderPicker() &&
                uiState.destinationUri != null &&
                !uiState.isDestinationAvailable
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.auto_backup_destination_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .settingsContentPadding()
                            .padding(top = 12.dp),
                    )
                }
            }

            if (!PlatformCapabilities.supportsAutoBackupFolderPicker()) {
                item {
                    Text(
                        text = stringResource(Res.string.auto_backup_destination_icloud),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .settingsContentPadding()
                            .padding(top = 12.dp),
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun lastBackupLabelText(bucket: LastBackupBucket, uiState: AutoBackupUiState): String = when {
    uiState.consecutiveFailures > 0 ->
        stringResource(Res.string.auto_backup_status_failed) + " (${uiState.consecutiveFailures}x)"

    bucket == LastBackupBucket.Never -> stringResource(Res.string.auto_backup_no_backups_yet)
    bucket == LastBackupBucket.JustNow -> stringResource(Res.string.auto_backup_just_now)
    bucket is LastBackupBucket.MinutesAgo -> pluralStringResource(
        Res.plurals.auto_backup_minutes_ago,
        bucket.minutes,
        bucket.minutes,
    )
    bucket is LastBackupBucket.HoursAgo -> pluralStringResource(
        Res.plurals.auto_backup_hours_ago,
        bucket.hours,
        bucket.hours,
    )
    bucket is LastBackupBucket.DaysAgo -> pluralStringResource(
        Res.plurals.auto_backup_days_ago,
        bucket.days,
        bucket.days,
    )
    else -> stringResource(Res.string.auto_backup_long_ago)
}

private fun lastBackupLabelKey(lastBackupTimestamp: Long, now: Instant): LastBackupBucket =
    lastBackupRelativeBucket(lastBackupTimestamp, now)

@Composable
private fun PassphraseDialog(
    isChange: Boolean,
    existingHint: String?,
    onConfirm: (passphrase: String, hint: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf(existingHint ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val tooShortError = stringResource(Res.string.auto_backup_passphrase_too_short)
    val mismatchError = stringResource(Res.string.auto_backup_passphrase_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isChange) {
                    stringResource(Res.string.auto_backup_change_passphrase)
                } else {
                    stringResource(Res.string.auto_backup_set_passphrase)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.auto_backup_remember_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.auto_backup_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPassphrase,
                    onValueChange = {
                        confirmPassphrase = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.auto_backup_confirm_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text(stringResource(Res.string.auto_backup_passphrase_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    passphrase.length < 8 -> {
                        error = tooShortError
                    }

                    passphrase != confirmPassphrase -> {
                        error = mismatchError
                    }

                    else -> {
                        onConfirm(passphrase, hint.ifBlank { null })
                    }
                }
            }) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

private fun percentDecode(encoded: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < encoded.length) {
        if (encoded[i] == '%' && i + 2 < encoded.length) {
            val hex = encoded.substring(i + 1, i + 3)
            val code = hex.toIntOrNull(16)
            if (code != null) {
                sb.append(code.toChar())
                i += 3
                continue
            }
        }
        sb.append(encoded[i])
        i++
    }
    return sb.toString()
}
