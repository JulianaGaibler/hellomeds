// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.backup.AutoBackupResult
import me.juliana.hellomeds.data.backup.AutoBackupService
import me.juliana.hellomeds.data.backup.AutoBackupStorageProvider
import me.juliana.hellomeds.data.backup.BackupEncryption
import me.juliana.hellomeds.data.crypto.PassphraseManager
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import kotlin.time.Clock

class AutoBackupViewModel(
    private val autoBackupService: AutoBackupService,
    private val storageProvider: AutoBackupStorageProvider,
    private val preferences: AutoBackupPreferences,
    private val passphraseManager: PassphraseManager,
) : ViewModel() {

    private val _isBackingUp = MutableStateFlow(false)
    private val _showPassphraseDialog = MutableStateFlow(false)
    private val _backupMessage = MutableStateFlow<String?>(null)

    val showPassphraseDialog: StateFlow<Boolean> = _showPassphraseDialog
    val isBackingUp: StateFlow<Boolean> = _isBackingUp
    val backupMessage: StateFlow<String?> = _backupMessage

    val uiState: StateFlow<AutoBackupUiState> = combine(
        preferences.autoBackupEnabled,
        preferences.lastBackupTimestamp,
        preferences.lastBackupStatus,
        preferences.lastBackupErrorMessage,
        preferences.lastBackupMedicationCount,
        preferences.consecutiveFailures,
        preferences.backupRetentionCount,
        preferences.backupDestinationUri,
        preferences.passphraseHint,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        AutoBackupUiState(
            isEnabled = values[0] as Boolean,
            lastBackupTimestamp = values[1] as Long,
            lastBackupStatus = values[2] as String,
            lastBackupErrorMessage = values[3] as? String,
            lastBackupMedicationCount = values[4] as Int,
            consecutiveFailures = values[5] as Int,
            retentionCount = values[6] as Int,
            destinationUri = values[7] as? String,
            hasPassphrase = passphraseManager.hasPassphrase(),
            isDestinationAvailable = storageProvider.isDestinationAvailable(),
            passphraseHint = values[8] as? String,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AutoBackupUiState(),
    )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoBackupEnabled(enabled) }
    }

    fun showSetPassphraseDialog() {
        _showPassphraseDialog.value = true
    }

    fun dismissPassphraseDialog() {
        _showPassphraseDialog.value = false
    }

    /**
     * Sets the passphrase after verifying it via a round-trip encrypt/decrypt test.
     * Returns true if successful, false if verification failed.
     */
    fun setPassphrase(passphrase: String, hint: String?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Round-trip verification: encrypt then decrypt a test string
            try {
                val testData = "HelloMeds passphrase verification test"
                val encrypted = BackupEncryption.encrypt(testData, passphrase)
                val decrypted = BackupEncryption.decrypt(encrypted, passphrase)
                if (decrypted != testData) {
                    onResult(false)
                    return@launch
                }
            } catch (e: Exception) {
                onResult(false)
                return@launch
            }

            val success = passphraseManager.setPassphrase(passphrase)
            if (success) {
                preferences.setPassphraseHint(hint) // null clears the hint
            }
            onResult(success)
        }
    }

    fun setRetentionCount(count: Int) {
        viewModelScope.launch { preferences.setBackupRetentionCount(count) }
    }

    fun setDestinationUri(uri: String?) {
        viewModelScope.launch { preferences.setBackupDestinationUri(uri) }
    }

    fun triggerManualBackup() {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = null

            val appVersion = PlatformCapabilities.appVersionString()
            when (val result = autoBackupService.generateAutoBackup(appVersion)) {
                is AutoBackupResult.Success -> {
                    val written = storageProvider.writeBackup(result.fileName, result.data)
                    if (written) {
                        preferences.recordSuccess(
                            timestamp = Clock.System.now().toEpochMilliseconds(),
                            medicationCount = result.medicationCount,
                        )
                        autoBackupService.cleanupOldBackups(storageProvider)
                        _backupMessage.value = "Backup complete (${result.medicationCount} medications)"
                    } else {
                        preferences.recordFailure("FAILED_STORAGE", "Failed to write backup file")
                        _backupMessage.value = "Failed to write backup file"
                    }
                }

                is AutoBackupResult.Failure -> {
                    preferences.recordFailure(result.reason.name, result.message)
                    _backupMessage.value = result.message
                }
            }

            _isBackingUp.value = false
        }
    }

    fun clearMessage() {
        _backupMessage.value = null
    }
}

data class AutoBackupUiState(
    val isEnabled: Boolean = false,
    val hasPassphrase: Boolean = false,
    val retentionCount: Int = 7,
    val lastBackupTimestamp: Long = 0,
    val lastBackupStatus: String = "NEVER",
    val lastBackupErrorMessage: String? = null,
    val lastBackupMedicationCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val destinationUri: String? = null,
    val isDestinationAvailable: Boolean = false,
    val passphraseHint: String? = null,
)
