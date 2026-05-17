// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.backup.BackupEncryption
import me.juliana.hellomeds.data.backup.BackupExportService
import me.juliana.hellomeds.data.backup.BackupImportService
import me.juliana.hellomeds.data.backup.BadPassphraseException
import me.juliana.hellomeds.data.backup.extractMetadata
import me.juliana.hellomeds.data.backup.isEncryptedBackup
import me.juliana.hellomeds.data.backup.model.BackupData
import me.juliana.hellomeds.data.backup.model.ImportAnalysis
import me.juliana.hellomeds.data.backup.model.ImportDecision
import me.juliana.hellomeds.data.backup.model.ImportResult
import me.juliana.hellomeds.data.backup.stripMetadata
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.ui.util.PlatformCapabilities

class BackupViewModel(
    private val exportService: BackupExportService,
    private val importService: BackupImportService,
    private val medicationDao: MedicationDao,
) : ViewModel() {

    // === Export State ===

    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    fun loadMedications() {
        viewModelScope.launch {
            val allMeds = medicationDao.getAll().first()
            val items = allMeds.map { med ->
                MedicationExportItem(
                    id = med.id,
                    name = med.name,
                    displayName = med.displayName,
                    isArchived = med.isArchived,
                )
            }
            val activeMedIds = items.filter { !it.isArchived }.map { it.id }.toSet()
            _exportState.value = _exportState.value.copy(
                allMedications = items,
                selectedMedicationIds = activeMedIds,
            )
        }
    }

    fun toggleMedication(id: Int) {
        val current = _exportState.value.selectedMedicationIds
        _exportState.value = _exportState.value.copy(
            selectedMedicationIds = if (id in current) current - id else current + id,
        )
    }

    fun selectAll() {
        val visible = getVisibleMedications()
        _exportState.value = _exportState.value.copy(
            selectedMedicationIds = _exportState.value.selectedMedicationIds + visible.map { it.id },
        )
    }

    fun deselectAll() {
        val visible = getVisibleMedications()
        _exportState.value = _exportState.value.copy(
            selectedMedicationIds = _exportState.value.selectedMedicationIds - visible.map { it.id }
                .toSet(),
        )
    }

    fun setExportAll(value: Boolean) {
        _exportState.value = if (value) {
            _exportState.value.copy(
                exportAll = true,
                includeSchedules = true,
                includeStockSettings = true,
                includeArchived = true,
                includeHistory = true,
            )
        } else {
            _exportState.value.copy(exportAll = false)
        }
    }

    fun setIncludeArchived(include: Boolean) {
        _exportState.value = _exportState.value.copy(includeArchived = include)
        if (!include) {
            val archivedIds =
                _exportState.value.allMedications.filter { it.isArchived }.map { it.id }.toSet()
            _exportState.value = _exportState.value.copy(
                selectedMedicationIds = _exportState.value.selectedMedicationIds - archivedIds,
            )
        }
    }

    fun setIncludeSchedules(include: Boolean) {
        _exportState.value = _exportState.value.copy(includeSchedules = include)
    }

    fun setIncludeStockSettings(include: Boolean) {
        _exportState.value = _exportState.value.copy(includeStockSettings = include)
    }

    fun setIncludeHistory(include: Boolean) {
        _exportState.value = _exportState.value.copy(includeHistory = include)
    }

    fun setEncryptBackup(encrypt: Boolean) {
        _exportState.value = _exportState.value.copy(
            encryptBackup = encrypt,
            passphrase = "",
            confirmPassphrase = "",
        )
    }

    fun setPassphrase(passphrase: String) {
        _exportState.value = _exportState.value.copy(passphrase = passphrase)
    }

    fun setConfirmPassphrase(confirmPassphrase: String) {
        _exportState.value = _exportState.value.copy(confirmPassphrase = confirmPassphrase)
    }

    /**
     * Generates backup data and returns it as bytes.
     * The caller (screen) handles writing to a file via the platform file picker.
     */
    fun performExport(onResult: (ExportResult, ByteArray?) -> Unit) {
        viewModelScope.launch {
            _exportState.value = _exportState.value.copy(isExporting = true, exportResult = null)
            try {
                val state = _exportState.value
                val backup = exportService.generateBackup(
                    selectedMedicationIds = state.selectedMedicationIds,
                    includeSchedules = state.includeSchedules,
                    includeStockSettings = state.includeStockSettings,
                    includeHistory = state.includeHistory,
                    appVersion = PlatformCapabilities.appVersionString(),
                )
                val json = exportService.serialize(backup)

                val outputBytes = if (state.encryptBackup && state.passphrase.isNotEmpty()) {
                    BackupEncryption.encrypt(json, state.passphrase)
                } else {
                    json.encodeToByteArray()
                }

                val result = ExportResult.Success(backup.medications.size)
                _exportState.value = _exportState.value.copy(
                    isExporting = false,
                    exportResult = result,
                )
                onResult(result, outputBytes)
            } catch (e: Exception) {
                val result = ExportResult.Error(e.message ?: "Export failed")
                _exportState.value = _exportState.value.copy(
                    isExporting = false,
                    exportResult = result,
                )
                onResult(result, null)
            }
        }
    }

    fun clearExportResult() {
        _exportState.value = _exportState.value.copy(exportResult = null)
    }

    private fun getVisibleMedications(): List<MedicationExportItem> {
        val state = _exportState.value
        return state.allMedications.filter { !it.isArchived || state.includeArchived }
    }

    // === Import State ===

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    /**
     * Parse import data from raw bytes (read by the platform file picker).
     */
    fun parseImportFile(rawBytes: ByteArray) {
        viewModelScope.launch {
            _importState.value = ImportUiState.Parsing
            try {
                // Extract metadata (hint, date, count) if present — shown before passphrase entry
                val metadata = extractMetadata(rawBytes)
                val encryptedPayload = stripMetadata(rawBytes)

                if (isEncryptedBackup(rawBytes)) {
                    _importState.value = ImportUiState.NeedsPassphrase(
                        rawBytes = encryptedPayload,
                        passphraseHint = metadata?.passphraseHint,
                    )
                    return@launch
                }

                val jsonString = rawBytes.decodeToString()
                proceedWithJson(jsonString)
            } catch (e: Exception) {
                _importState.value = ImportUiState.Error(e.message ?: "Failed to read file")
            }
        }
    }

    fun decryptAndParse(passphrase: String) {
        val current = _importState.value
        if (current !is ImportUiState.NeedsPassphrase) return

        viewModelScope.launch {
            try {
                val jsonString = BackupEncryption.decrypt(current.rawBytes, passphrase)
                proceedWithJson(jsonString)
            } catch (_: BadPassphraseException) {
                _importState.value = current.copy(wrongPassphrase = true)
            } catch (e: Exception) {
                _importState.value = ImportUiState.Error(e.message ?: "Decryption failed")
            }
        }
    }

    private suspend fun proceedWithJson(jsonString: String) {
        val backupResult = importService.parseBackup(jsonString)
        val backup = backupResult.getOrElse { e ->
            _importState.value = ImportUiState.Error("Invalid backup file: ${e.message}")
            return
        }

        val analysis = importService.analyzeImport(backup)
        val defaultDecisions = analysis.medications.associate { info ->
            if (info.hasErrors) {
                info.index to ImportDecision.SKIP
            } else {
                info.index to ImportDecision.IMPORT_AS_NEW
            }
        }

        _importState.value = ImportUiState.Preview(
            backup = backup,
            analysis = analysis,
            decisions = defaultDecisions,
        )
    }

    fun updateDecision(index: Int, decision: ImportDecision) {
        val current = _importState.value
        if (current is ImportUiState.Preview) {
            _importState.value = current.copy(decisions = current.decisions + (index to decision))
        }
    }

    fun executeImport() {
        val current = _importState.value
        if (current !is ImportUiState.Preview) return

        viewModelScope.launch {
            _importState.value = ImportUiState.Importing
            try {
                val result = importService.executeImport(
                    backup = current.backup,
                    decisions = current.decisions,
                )
                _importState.value = ImportUiState.Success(result)
            } catch (e: Exception) {
                _importState.value = ImportUiState.Error(e.message ?: "Import failed")
            }
        }
    }

    fun resetImportState() {
        _importState.value = ImportUiState.Idle
    }
}

// === UI State Models ===

data class ExportUiState(
    val allMedications: List<MedicationExportItem> = emptyList(),
    val selectedMedicationIds: Set<Int> = emptySet(),
    val exportAll: Boolean = true,
    val includeArchived: Boolean = true,
    val includeSchedules: Boolean = true,
    val includeStockSettings: Boolean = true,
    val includeHistory: Boolean = true,
    val encryptBackup: Boolean = false,
    val passphrase: String = "",
    val confirmPassphrase: String = "",
    val isExporting: Boolean = false,
    val exportResult: ExportResult? = null,
) {
    val passphraseValid: Boolean
        get() = !encryptBackup || (passphrase.isNotEmpty() && passphrase == confirmPassphrase)
}

data class MedicationExportItem(
    val id: Int,
    val name: String,
    val displayName: String?,
    val isArchived: Boolean,
)

sealed class ExportResult {
    data class Success(val count: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

sealed class ImportUiState {
    data object Idle : ImportUiState()
    data object Parsing : ImportUiState()
    data class NeedsPassphrase(
        val rawBytes: ByteArray,
        val wrongPassphrase: Boolean = false,
        val passphraseHint: String? = null,
    ) : ImportUiState()

    data class Preview(
        val backup: BackupData,
        val analysis: ImportAnalysis,
        val decisions: Map<Int, ImportDecision>,
    ) : ImportUiState()

    data object Importing : ImportUiState()
    data class Success(val result: ImportResult) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}
