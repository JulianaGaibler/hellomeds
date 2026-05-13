// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.backup.HMBACKUP_MIME_TYPE
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_back
import me.juliana.hellomeds.shared.backup_action_save_to_files
import me.juliana.hellomeds.shared.backup_action_share
import me.juliana.hellomeds.shared.backup_confirm_passphrase
import me.juliana.hellomeds.shared.backup_deselect_all
import me.juliana.hellomeds.shared.backup_encrypt
import me.juliana.hellomeds.shared.backup_encrypt_description
import me.juliana.hellomeds.shared.backup_encryption_info_description
import me.juliana.hellomeds.shared.backup_encryption_info_title
import me.juliana.hellomeds.shared.backup_encryption_title
import me.juliana.hellomeds.shared.backup_export_options
import me.juliana.hellomeds.shared.backup_export_title
import me.juliana.hellomeds.shared.backup_exporting
import me.juliana.hellomeds.shared.backup_include_archived
import me.juliana.hellomeds.shared.backup_include_archived_description
import me.juliana.hellomeds.shared.backup_include_history
import me.juliana.hellomeds.shared.backup_include_history_description
import me.juliana.hellomeds.shared.backup_include_schedules
import me.juliana.hellomeds.shared.backup_include_schedules_description
import me.juliana.hellomeds.shared.backup_include_stock
import me.juliana.hellomeds.shared.backup_include_stock_description
import me.juliana.hellomeds.shared.backup_medications_section
import me.juliana.hellomeds.shared.backup_passphrase
import me.juliana.hellomeds.shared.backup_passphrase_mismatch
import me.juliana.hellomeds.shared.backup_select_all
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.features.settings.SettingsHeader
import me.juliana.hellomeds.ui.features.settings.settingsContentPadding
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListSwitchItem
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.util.rememberFileSaver
import me.juliana.hellomeds.ui.util.rememberFileSharer
import me.juliana.hellomeds.ui.viewmodel.BackupViewModel
import me.juliana.hellomeds.ui.viewmodel.ExportResult
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDataScreen(viewModel: BackupViewModel, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.exportState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward },
    )

    LaunchedEffect(Unit) {
        viewModel.loadMedications()
    }

    val dateStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    val defaultFileName = if (state.encryptBackup) {
        "hellomeds-backup-$dateStr.encrypted.hmbackup"
    } else {
        "hellomeds-backup-$dateStr.hmbackup"
    }

    val fileSaver = rememberFileSaver { success ->
        if (success) {
            viewModel.clearExportResult()
            onNavigateBack()
        }
    }

    val fileSharer = rememberFileSharer { shared ->
        if (shared) {
            viewModel.clearExportResult()
            onNavigateBack()
        }
    }

    // Handle export result
    LaunchedEffect(state.exportResult) {
        when (val result = state.exportResult) {
            is ExportResult.Success -> {
                viewModel.clearExportResult()
                onNavigateBack()
            }

            is ExportResult.Error -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.clearExportResult()
            }

            null -> {}
        }
    }

    val visibleMedications = state.allMedications.filter { !it.isArchived || state.includeArchived }
    val selectedCount = visibleMedications.count { it.id in state.selectedMedicationIds }

    AppScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(Res.string.backup_export_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            state = scrollState,
        ) {
            // === OPTIONS SECTION ===
            item {
                SettingsHeader(
                    text = stringResource(Res.string.backup_export_options),
                    isFirst = true,
                )
            }

            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.backup_include_schedules),
                                checked = state.includeSchedules,
                                onCheckedChange = { viewModel.setIncludeSchedules(it) },
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.backup_include_schedules_description),
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.backup_include_stock),
                                checked = state.includeStockSettings,
                                onCheckedChange = { viewModel.setIncludeStockSettings(it) },
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.backup_include_stock_description),
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.backup_include_archived),
                                checked = state.includeArchived,
                                onCheckedChange = { viewModel.setIncludeArchived(it) },
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.backup_include_archived_description),
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.backup_include_history),
                                checked = state.includeHistory,
                                onCheckedChange = { viewModel.setIncludeHistory(it) },
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.backup_include_history_description),
                            )
                        },
                    ),
                )
            }

            // === ENCRYPTION SECTION ===
            item {
                SettingsHeader(text = stringResource(Res.string.backup_encryption_title))
            }

            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.backup_encrypt),
                                checked = state.encryptBackup,
                                onCheckedChange = { viewModel.setEncryptBackup(it) },
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.backup_encrypt_description),
                            )
                        },
                        SmartListItemConfig(visible = state.encryptBackup) { shapes, visible ->
                            SmartListInfoCard(
                                headlineContent = {
                                    Text(
                                        stringResource(Res.string.backup_encryption_info_title),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(Res.string.backup_encryption_info_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = state.encryptBackup) { shapes, visible ->
                            SmartListTextItem(
                                label = stringResource(Res.string.backup_passphrase),
                                value = state.passphrase,
                                onValueChange = { viewModel.setPassphrase(it) },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = state.encryptBackup) { shapes, visible ->
                            SmartListTextItem(
                                label = stringResource(Res.string.backup_confirm_passphrase),
                                value = state.confirmPassphrase,
                                onValueChange = { viewModel.setConfirmPassphrase(it) },
                                shapes = shapes,
                                visible = visible,
                                validator = { it == state.passphrase },
                                supportingText = if (state.confirmPassphrase.isNotEmpty() && state.passphrase != state.confirmPassphrase) {
                                    stringResource(Res.string.backup_passphrase_mismatch)
                                } else {
                                    null
                                },
                            )
                        },
                    ),
                )
            }

            // === MEDICATION SELECTION SECTION ===
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsContentPadding()
                        .padding(top = 48.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.backup_medications_section),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text(stringResource(Res.string.backup_select_all))
                        }
                        TextButton(onClick = { viewModel.deselectAll() }) {
                            Text(stringResource(Res.string.backup_deselect_all))
                        }
                    }
                }
            }

            item {
                AutoSmartList(
                    items = visibleMedications.map { med ->
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListItem(
                                headlineContent = {
                                    Text(med.displayName ?: med.name)
                                },
                                supportingContent = if (med.displayName != null) {
                                    { Text(med.name) }
                                } else {
                                    null
                                },
                                leadingContent = {
                                    Checkbox(
                                        checked = med.id in state.selectedMedicationIds,
                                        onCheckedChange = { viewModel.toggleMedication(med.id) },
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                                onClick = { viewModel.toggleMedication(med.id) },
                            )
                        }
                    },
                )
            }

            // === EXPORT BUTTONS ===
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                val exportEnabled = selectedCount > 0 && !state.isExporting && state.passphraseValid
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Save to Files
                    OutlinedButton(
                        onClick = {
                            viewModel.performExport { _, bytes ->
                                if (bytes != null) fileSaver(defaultFileName, HMBACKUP_MIME_TYPE, bytes)
                            }
                        },
                        enabled = exportEnabled,
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp),
                    ) {
                        Text(
                            stringResource(Res.string.backup_action_save_to_files),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    // Share
                    Button(
                        onClick = {
                            viewModel.performExport { _, bytes ->
                                if (bytes != null) fileSharer(defaultFileName, HMBACKUP_MIME_TYPE, bytes)
                            }
                        },
                        enabled = exportEnabled,
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp),
                    ) {
                        Text(
                            if (state.isExporting) {
                                stringResource(Res.string.backup_exporting)
                            } else {
                                stringResource(Res.string.backup_action_share)
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
