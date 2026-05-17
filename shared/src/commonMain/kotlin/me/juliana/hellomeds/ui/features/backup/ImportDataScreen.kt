// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.backup.model.ImportDecision
import me.juliana.hellomeds.data.backup.model.ImportResult
import me.juliana.hellomeds.data.backup.model.ImportWarning
import me.juliana.hellomeds.data.backup.model.MedicationImportInfo
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_not_selected
import me.juliana.hellomeds.shared.accessibility_selected
import me.juliana.hellomeds.shared.action_back
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.backup_decision_new
import me.juliana.hellomeds.shared.backup_decision_replace
import me.juliana.hellomeds.shared.backup_decision_skip
import me.juliana.hellomeds.shared.backup_decrypt
import me.juliana.hellomeds.shared.backup_done
import me.juliana.hellomeds.shared.backup_duplicate_warning
import me.juliana.hellomeds.shared.backup_encrypted_description
import me.juliana.hellomeds.shared.backup_encrypted_title
import me.juliana.hellomeds.shared.backup_import_count
import me.juliana.hellomeds.shared.backup_import_error_title
import me.juliana.hellomeds.shared.backup_import_info_description
import me.juliana.hellomeds.shared.backup_import_item_cannot_import
import me.juliana.hellomeds.shared.backup_import_item_stock_tracked
import me.juliana.hellomeds.shared.backup_import_success_title
import me.juliana.hellomeds.shared.backup_import_summary_history_imported
import me.juliana.hellomeds.shared.backup_import_summary_labels_created
import me.juliana.hellomeds.shared.backup_import_summary_medications_imported
import me.juliana.hellomeds.shared.backup_import_summary_medications_replaced
import me.juliana.hellomeds.shared.backup_import_summary_medications_skipped
import me.juliana.hellomeds.shared.backup_import_summary_schedules_imported
import me.juliana.hellomeds.shared.backup_import_summary_stock_adjustments_imported
import me.juliana.hellomeds.shared.backup_import_title
import me.juliana.hellomeds.shared.backup_importing
import me.juliana.hellomeds.shared.backup_labels_count
import me.juliana.hellomeds.shared.backup_medications_count
import me.juliana.hellomeds.shared.backup_medications_section
import me.juliana.hellomeds.shared.backup_parsing
import me.juliana.hellomeds.shared.backup_passphrase
import me.juliana.hellomeds.shared.backup_preview_summary_format
import me.juliana.hellomeds.shared.backup_preview_summary_title
import me.juliana.hellomeds.shared.backup_preview_warnings
import me.juliana.hellomeds.shared.backup_schedules_count
import me.juliana.hellomeds.shared.backup_select_file
import me.juliana.hellomeds.shared.backup_try_again
import me.juliana.hellomeds.shared.backup_wrong_passphrase
import me.juliana.hellomeds.shared.import_warning_label_not_found
import me.juliana.hellomeds.shared.import_warning_newer_version
import me.juliana.hellomeds.shared.import_warning_unknown_container
import me.juliana.hellomeds.shared.import_warning_unknown_frequency_type
import me.juliana.hellomeds.shared.import_warning_unknown_med_type
import me.juliana.hellomeds.shared.import_warning_unknown_strength_unit
import me.juliana.hellomeds.shared.import_warning_unknown_tracking_precision
import me.juliana.hellomeds.ui.compat.ButtonGroupDefaults
import me.juliana.hellomeds.ui.compat.ListItemShapes
import me.juliana.hellomeds.ui.compat.ToggleButton
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.features.settings.SettingsHeader
import me.juliana.hellomeds.ui.features.settings.settingsContentPadding
import me.juliana.hellomeds.ui.util.displayNameRes
import me.juliana.hellomeds.ui.util.rememberFileLoader
import me.juliana.hellomeds.ui.viewmodel.BackupViewModel
import me.juliana.hellomeds.ui.viewmodel.ImportUiState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDataScreen(viewModel: BackupViewModel, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.importState.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward },
    )

    val fileLoader = rememberFileLoader { data ->
        if (data != null) {
            viewModel.parseImportFile(data)
        }
    }

    AppScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(Res.string.backup_import_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state is ImportUiState.Preview || state is ImportUiState.NeedsPassphrase) {
                            viewModel.resetImportState()
                        } else {
                            onNavigateBack()
                        }
                    }) {
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
    ) { paddingValues ->
        when (val currentState = state) {
            is ImportUiState.Idle -> {
                IdlePhase(
                    paddingValues = paddingValues,
                    onSelectFile = { fileLoader() },
                )
            }

            is ImportUiState.Parsing, is ImportUiState.Importing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (currentState is ImportUiState.Parsing) {
                                stringResource(Res.string.backup_parsing)
                            } else {
                                stringResource(Res.string.backup_importing)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            is ImportUiState.NeedsPassphrase -> {
                PassphrasePhase(
                    wrongPassphrase = currentState.wrongPassphrase,
                    passphraseHint = currentState.passphraseHint,
                    paddingValues = paddingValues,
                    onDecrypt = { passphrase -> viewModel.decryptAndParse(passphrase) },
                    onCancel = { viewModel.resetImportState() },
                )
            }

            is ImportUiState.Preview -> {
                PreviewPhase(
                    state = currentState,
                    paddingValues = paddingValues,
                    onUpdateDecision = { index, decision -> viewModel.updateDecision(index, decision) },
                    onImport = { viewModel.executeImport() },
                )
            }

            is ImportUiState.Success -> {
                SuccessPhase(
                    result = currentState.result,
                    paddingValues = paddingValues,
                    onDone = onNavigateBack,
                )
            }

            is ImportUiState.Error -> {
                ErrorPhase(
                    message = currentState.message,
                    paddingValues = paddingValues,
                    onRetry = { viewModel.resetImportState() },
                    onBack = onNavigateBack,
                )
            }
        }
    }
}

@Composable
private fun IdlePhase(paddingValues: PaddingValues, onSelectFile: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            Text(
                text = stringResource(Res.string.backup_import_info_description),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .settingsContentPadding()
                    .padding(top = 8.dp),
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Button(
                onClick = onSelectFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                Text(
                    stringResource(Res.string.backup_select_file),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun PreviewPhase(
    state: ImportUiState.Preview,
    paddingValues: PaddingValues,
    onUpdateDecision: (Int, ImportDecision) -> Unit,
    onImport: () -> Unit,
) {
    val analysis = state.analysis
    val totalSchedules = analysis.medications.sumOf { it.backupMedication.schedules.size }
    val importableMeds = analysis.medications.count { info ->
        val decision = state.decisions[info.index]
        decision != null && decision != ImportDecision.SKIP && !info.hasErrors
    }

    val selectedText = stringResource(Res.string.accessibility_selected)
    val notSelectedText = stringResource(Res.string.accessibility_not_selected)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Summary
        item {
            SettingsHeader(
                text = stringResource(Res.string.backup_preview_summary_title),
                isFirst = true,
            )
        }

        item {
            val medsStr = pluralStringResource(
                Res.plurals.backup_medications_count,
                analysis.medications.size,
                analysis.medications.size,
            )
            val schedsStr = pluralStringResource(
                Res.plurals.backup_schedules_count,
                totalSchedules,
                totalSchedules,
            )
            val labelsStr = pluralStringResource(
                Res.plurals.backup_labels_count,
                analysis.labels.size,
                analysis.labels.size,
            )
            Text(
                text = stringResource(
                    Res.string.backup_preview_summary_format,
                    medsStr,
                    schedsStr,
                    labelsStr,
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.settingsContentPadding(),
            )
        }

        // Warnings
        if (analysis.warnings.isNotEmpty()) {
            item {
                SettingsHeader(text = stringResource(Res.string.backup_preview_warnings))
            }
            item {
                Column(
                    modifier = Modifier.settingsContentPadding(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    analysis.warnings.forEach { warning ->
                        Text(
                            text = "\u2022 ${resolveImportWarning(warning)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        // Medication list header
        item {
            SettingsHeader(text = stringResource(Res.string.backup_medications_section))
        }

        // Medication items
        item {
            AutoSmartList(
                items = analysis.medications.map { info ->
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        MedicationImportItem(
                            info = info,
                            decision = state.decisions[info.index] ?: ImportDecision.IMPORT_AS_NEW,
                            onDecisionChanged = { decision -> onUpdateDecision(info.index, decision) },
                            shapes = shapes,
                            visible = visible,
                            selectedText = selectedText,
                            notSelectedText = notSelectedText,
                        )
                    }
                },
            )
        }

        // Import button
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Button(
                onClick = onImport,
                enabled = importableMeds > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    pluralStringResource(Res.plurals.backup_import_count, importableMeds, importableMeds),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MedicationImportItem(
    info: MedicationImportInfo,
    decision: ImportDecision,
    onDecisionChanged: (ImportDecision) -> Unit,
    shapes: ListItemShapes,
    visible: Boolean,
    selectedText: String,
    notSelectedText: String,
) {
    val med = info.backupMedication
    val scheduleCount = med.schedules.size

    val parsedType = MedicationType.fromValue(med.type)
    val typeLabel = if (parsedType != null) {
        stringResource(parsedType.displayNameRes)
    } else {
        med.type
    }
    val scheduleLabel = if (scheduleCount > 0) {
        pluralStringResource(Res.plurals.backup_schedules_count, scheduleCount, scheduleCount)
    } else {
        null
    }
    val stockLabel = if (med.stock != null) {
        stringResource(Res.string.backup_import_item_stock_tracked)
    } else {
        null
    }
    val cannotImportFallback = stringResource(Res.string.backup_import_item_cannot_import)

    SmartListItem(
        headlineContent = {
            Text(med.displayName ?: med.name)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Medication details
                val details = buildString {
                    if (med.displayName != null) append("${med.name} \u2022 ")
                    append(typeLabel)
                    if (scheduleLabel != null) append(" \u2022 $scheduleLabel")
                    if (stockLabel != null) append(" \u2022 $stockLabel")
                }
                Text(details, style = MaterialTheme.typography.bodySmall)

                // Error state
                if (info.hasErrors) {
                    Text(
                        info.errorMessage ?: cannotImportFallback,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Duplicate warning + decision buttons
                if (info.duplicateOf != null && !info.hasErrors) {
                    Text(
                        stringResource(Res.string.backup_duplicate_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        ToggleButton(
                            checked = decision == ImportDecision.SKIP,
                            onCheckedChange = { if (it) onDecisionChanged(ImportDecision.SKIP) },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics {
                                    stateDescription =
                                        if (decision == ImportDecision.SKIP) selectedText else notSelectedText
                                },
                        ) {
                            Text(
                                stringResource(Res.string.backup_decision_skip),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                        ToggleButton(
                            checked = decision == ImportDecision.REPLACE,
                            onCheckedChange = { if (it) onDecisionChanged(ImportDecision.REPLACE) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics {
                                    stateDescription =
                                        if (decision == ImportDecision.REPLACE) selectedText else notSelectedText
                                },
                        ) {
                            Text(
                                stringResource(Res.string.backup_decision_replace),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                        ToggleButton(
                            checked = decision == ImportDecision.IMPORT_AS_NEW,
                            onCheckedChange = { if (it) onDecisionChanged(ImportDecision.IMPORT_AS_NEW) },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics {
                                    stateDescription =
                                        if (decision == ImportDecision.IMPORT_AS_NEW) selectedText else notSelectedText
                                },
                        ) {
                            Text(
                                stringResource(Res.string.backup_decision_new),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // Validation warnings
                if (info.validationWarnings.isNotEmpty() && !info.hasErrors) {
                    info.validationWarnings.forEach { warning ->
                        Text(
                            "\u26A0 ${resolveImportWarning(warning)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        },
        shapes = shapes,
        visible = visible,
    )
}

@Composable
private fun PassphrasePhase(
    wrongPassphrase: Boolean,
    passphraseHint: String? = null,
    paddingValues: PaddingValues,
    onDecrypt: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.backup_encrypted_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.backup_encrypted_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (passphraseHint != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Hint: $passphraseHint",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(stringResource(Res.string.backup_passphrase)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            isError = wrongPassphrase,
            supportingText = if (wrongPassphrase) {
                { Text(stringResource(Res.string.backup_wrong_passphrase)) }
            } else {
                null
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onDecrypt(passphrase) },
            enabled = passphrase.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(
                stringResource(Res.string.backup_decrypt),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilledTonalButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(Res.string.action_cancel))
        }
    }
}

@Composable
private fun SuccessPhase(result: ImportResult, paddingValues: PaddingValues, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.backup_import_success_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        val medicationsImportedLine = if (result.medicationsImported > 0) {
            pluralStringResource(
                Res.plurals.backup_import_summary_medications_imported,
                result.medicationsImported,
                result.medicationsImported,
            )
        } else {
            null
        }
        val medicationsReplacedLine = if (result.medicationsReplaced > 0) {
            pluralStringResource(
                Res.plurals.backup_import_summary_medications_replaced,
                result.medicationsReplaced,
                result.medicationsReplaced,
            )
        } else {
            null
        }
        val medicationsSkippedLine = if (result.medicationsSkipped > 0) {
            pluralStringResource(
                Res.plurals.backup_import_summary_medications_skipped,
                result.medicationsSkipped,
                result.medicationsSkipped,
            )
        } else {
            null
        }
        val schedulesImportedLine = if (result.schedulesImported > 0) {
            pluralStringResource(
                Res.plurals.backup_import_summary_schedules_imported,
                result.schedulesImported,
                result.schedulesImported,
            )
        } else {
            null
        }
        val labelsCreatedLine = if (result.labelsCreated > 0) {
            pluralStringResource(
                Res.plurals.backup_import_summary_labels_created,
                result.labelsCreated,
                result.labelsCreated,
            )
        } else {
            null
        }
        val historyImportedLine = if (result.historyImported > 0) {
            pluralStringResource(
                Res.plurals.backup_import_summary_history_imported,
                result.historyImported,
                result.historyImported,
            )
        } else {
            null
        }
        val stockAdjustmentsImportedLine = if (result.stockAdjustmentsImported > 0) {
            pluralStringResource(
                Res.plurals.backup_import_summary_stock_adjustments_imported,
                result.stockAdjustmentsImported,
                result.stockAdjustmentsImported,
            )
        } else {
            null
        }
        val summary = listOfNotNull(
            medicationsImportedLine,
            medicationsReplacedLine,
            medicationsSkippedLine,
            schedulesImportedLine,
            labelsCreatedLine,
            historyImportedLine,
            stockAdjustmentsImportedLine,
        ).joinToString("\n")

        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            Text(
                stringResource(Res.string.backup_done),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ErrorPhase(message: String, paddingValues: PaddingValues, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.backup_import_error_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(
                stringResource(Res.string.backup_try_again),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilledTonalButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(Res.string.action_back))
        }
    }
}

@Composable
private fun resolveImportWarning(warning: ImportWarning): String = when (warning) {
    is ImportWarning.NewerVersion -> stringResource(Res.string.import_warning_newer_version)
    is ImportWarning.UnknownMedicationType -> stringResource(Res.string.import_warning_unknown_med_type, warning.type)
    is ImportWarning.UnknownStrengthUnit -> stringResource(
        Res.string.import_warning_unknown_strength_unit,
        warning.unit,
    )
    is ImportWarning.UnknownFrequencyType -> stringResource(
        Res.string.import_warning_unknown_frequency_type,
        warning.scheduleIndex,
        warning.type,
    )
    is ImportWarning.UnknownTrackingPrecision -> stringResource(
        Res.string.import_warning_unknown_tracking_precision,
        warning.precision,
    )
    is ImportWarning.UnknownContainerType -> stringResource(
        Res.string.import_warning_unknown_container,
        warning.container,
    )
    is ImportWarning.LabelNotFound -> stringResource(Res.string.import_warning_label_not_found, warning.labelName)
}
