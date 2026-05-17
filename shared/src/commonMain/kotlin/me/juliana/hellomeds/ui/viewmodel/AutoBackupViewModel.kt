// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class AutoBackupViewModel(
    private val autoBackupService: AutoBackupService,
    private val storageProvider: AutoBackupStorageProvider,
    private val preferences: AutoBackupPreferences,
    private val passphraseManager: PassphraseManager,
) : ViewModel() {

    private val _isBackingUp = MutableStateFlow(false)
    private val _showPassphraseDialog = MutableStateFlow(false)
    private val _backupMessage = MutableStateFlow<String?>(null)

    private val _passphraseChanged = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ).apply { tryEmit(Unit) } // seed so combine fires at least once at startup

    private val _events = MutableSharedFlow<AutoBackupEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** True when the user toggled the switch ON but a prerequisite (passphrase/location) was missing. */
    private var pendingEnable = false

    val showPassphraseDialog: StateFlow<Boolean> = _showPassphraseDialog
    val isBackingUp: StateFlow<Boolean> = _isBackingUp
    val backupMessage: StateFlow<String?> = _backupMessage
    val events: SharedFlow<AutoBackupEvent> = _events.asSharedFlow()

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
        _passphraseChanged,
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

    /**
     * Called when the user taps the enable switch. Walks the user through any missing
     * prerequisite (passphrase, then destination on Android) before actually enabling.
     * Reads from the real sources rather than `uiState.value` so it stays correct even
     * if the combine-derived state hasn't recomputed yet (e.g. immediately after a
     * preference write inside the same coroutine).
     */
    fun onEnableToggled(value: Boolean) {
        if (!value) {
            pendingEnable = false
            setEnabled(false)
            return
        }
        viewModelScope.launch {
            val passphraseOk = passphraseManager.hasPassphrase()
            val locationOk = !PlatformCapabilities.supportsAutoBackupFolderPicker() ||
                preferences.backupDestinationUri.first() != null
            when {
                !passphraseOk -> {
                    pendingEnable = true
                    _events.tryEmit(AutoBackupEvent.RequestPassphrase)
                }
                !locationOk -> {
                    pendingEnable = true
                    _events.tryEmit(AutoBackupEvent.RequestPickFolder)
                }
                else -> {
                    pendingEnable = false
                    preferences.setAutoBackupEnabled(true)
                }
            }
        }
    }

    private suspend fun completePendingEnableIfReady() {
        if (!pendingEnable) return
        val passphraseOk = passphraseManager.hasPassphrase()
        val locationOk = !PlatformCapabilities.supportsAutoBackupFolderPicker() ||
            preferences.backupDestinationUri.first() != null
        if (passphraseOk && locationOk) {
            pendingEnable = false
            preferences.setAutoBackupEnabled(true)
        } else if (passphraseOk && !locationOk) {
            // Passphrase just got set; fire the next prerequisite.
            _events.tryEmit(AutoBackupEvent.RequestPickFolder)
        }
    }

    fun showSetPassphraseDialog() {
        _showPassphraseDialog.value = true
    }

    fun dismissPassphraseDialog() {
        _showPassphraseDialog.value = false
        // User backed out of the dialog. If we opened it as part of the enable flow,
        // abort the chain so a later setPassphrase from the Settings row doesn't
        // unexpectedly cascade into the folder picker.
        pendingEnable = false
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
                _passphraseChanged.tryEmit(Unit)
                completePendingEnableIfReady()
            }
            onResult(success)
        }
    }

    fun setRetentionCount(count: Int) {
        viewModelScope.launch { preferences.setBackupRetentionCount(count) }
    }

    fun setDestinationUri(uri: String?) {
        viewModelScope.launch {
            preferences.setBackupDestinationUri(uri)
            if (uri != null) {
                completePendingEnableIfReady()
            }
        }
    }

    /**
     * Called when the user opens the folder picker and dismisses it without choosing.
     * Must NOT touch [preferences.setBackupDestinationUri] — a previously stored destination
     * should remain intact. Only clears the pending-enable flag so the chain (if any) aborts.
     */
    fun onFolderPickerCancelled() {
        pendingEnable = false
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

sealed interface AutoBackupEvent {
    data object RequestPassphrase : AutoBackupEvent
    data object RequestPickFolder : AutoBackupEvent
}

/** Buckets [lastBackupTimestamp] (epoch ms) into a relative-time label. Pure for easy testing. */
fun lastBackupRelativeBucket(lastBackupTimestamp: Long, now: Instant): LastBackupBucket {
    if (lastBackupTimestamp <= 0L) return LastBackupBucket.Never
    val elapsed = now - Instant.fromEpochMilliseconds(lastBackupTimestamp)
    return when {
        elapsed < 1.minutes -> LastBackupBucket.JustNow
        elapsed < 1.hours -> LastBackupBucket.MinutesAgo(elapsed.inWholeMinutes.toInt())
        elapsed < 1.days -> LastBackupBucket.HoursAgo(elapsed.inWholeHours.toInt())
        elapsed < 30.days -> LastBackupBucket.DaysAgo(elapsed.inWholeDays.toInt())
        else -> LastBackupBucket.LongAgo
    }
}

sealed interface LastBackupBucket {
    data object Never : LastBackupBucket
    data object JustNow : LastBackupBucket
    data class MinutesAgo(val minutes: Int) : LastBackupBucket
    data class HoursAgo(val hours: Int) : LastBackupBucket
    data class DaysAgo(val days: Int) : LastBackupBucket
    data object LongAgo : LastBackupBucket
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
