// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.backup.AutoBackupResult
import me.juliana.hellomeds.data.backup.AutoBackupService
import me.juliana.hellomeds.data.backup.AutoBackupStorageProvider
import me.juliana.hellomeds.data.backup.BackupDestinationMode
import me.juliana.hellomeds.data.backup.BackupEncryption
import me.juliana.hellomeds.data.backup.DestinationAvailability
import me.juliana.hellomeds.data.backup.WriteBackupResult
import me.juliana.hellomeds.data.crypto.PassphraseManager
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
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

    // Separate from `uiState` because `destinationAvailability()` is suspending and may
    // touch disk (iOS bookmark resolve, SAF permission check) — must not run in `combine`.
    private val availability: StateFlow<DestinationAvailability> = combine(
        preferences.backupDestinationMode,
        preferences.backupDestinationUri,
    ) { mode, uri -> mode to uri }
        .distinctUntilChanged()
        .mapLatest { storageProvider.destinationAvailability() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DestinationAvailability.NotSet,
        )

    val uiState: StateFlow<AutoBackupUiState> = combine(
        preferences.autoBackupEnabled,
        preferences.lastBackupTimestamp,
        preferences.lastBackupStatus,
        preferences.lastBackupErrorMessage,
        preferences.lastBackupMedicationCount,
        preferences.consecutiveFailures,
        preferences.backupRetentionCount,
        preferences.backupDestinationMode,
        preferences.backupDestinationUri,
        preferences.backupDestinationName,
        preferences.passphraseHint,
        _passphraseChanged,
        availability,
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
            mode = values[7] as BackupDestinationMode,
            destinationUri = values[8] as? String,
            destinationDisplayName = values[9] as? String,
            hasPassphrase = passphraseManager.hasPassphrase(),
            destinationAvailability = values[12] as DestinationAvailability,
            passphraseHint = values[10] as? String,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AutoBackupUiState(),
    )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoBackupEnabled(enabled) }
    }

    // Walks the user through missing prerequisites (passphrase, destination) before
    // enabling. Reads from preferences directly because `uiState.value` may still hold
    // pre-write values when this runs inside the same coroutine as a preference edit.
    fun onEnableToggled(value: Boolean) {
        if (!value) {
            pendingEnable = false
            setEnabled(false)
            return
        }
        viewModelScope.launch {
            val passphraseOk = passphraseManager.hasPassphrase()
            val locationOk = isLocationConfigured()
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
        val locationOk = isLocationConfigured()
        if (passphraseOk && locationOk) {
            pendingEnable = false
            preferences.setAutoBackupEnabled(true)
        } else if (passphraseOk && !locationOk) {
            _events.tryEmit(AutoBackupEvent.RequestPickFolder)
        }
    }

    // Checks configuration only — live availability (e.g. iCloud sign-in status) is
    // surfaced at write time so enabling never blocks on a network round-trip.
    private suspend fun isLocationConfigured(): Boolean {
        if (!PlatformCapabilities.supportsAutoBackupFolderPicker()) return true
        return when (preferences.backupDestinationMode.first()) {
            BackupDestinationMode.ICLOUD -> true
            BackupDestinationMode.CUSTOM_FOLDER -> preferences.backupDestinationUri.first() != null
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

    fun setMode(mode: BackupDestinationMode) {
        viewModelScope.launch {
            preferences.setBackupDestinationMode(mode)
            if (mode == BackupDestinationMode.CUSTOM_FOLDER &&
                preferences.backupDestinationUri.first() == null
            ) {
                _events.tryEmit(AutoBackupEvent.RequestPickFolder)
            }
        }
    }

    fun setDestination(uri: String?, displayName: String?) {
        viewModelScope.launch {
            if (uri != null) {
                preferences.setBackupDestination(uri, displayName)
                completePendingEnableIfReady()
            } else {
                preferences.setBackupDestinationUri(null)
            }
        }
    }

    // Must NOT touch the stored destination — the user may have cancelled out of a
    // re-pick from the Location row, in which case the previous folder should remain.
    fun onFolderPickerCancelled() {
        pendingEnable = false
    }

    fun triggerManualBackup() {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = null

            val appVersion = PlatformCapabilities.appVersionString()
            when (val result = autoBackupService.generateAutoBackup(appVersion)) {
                is AutoBackupResult.Success -> handleWriteResult(
                    storageProvider.writeBackup(result.fileName, result.data),
                    result.medicationCount,
                )
                is AutoBackupResult.Failure -> {
                    preferences.recordFailure(result.reason.name, result.message)
                    _backupMessage.value = result.message
                }
            }

            _isBackingUp.value = false
        }
    }

    private suspend fun handleWriteResult(result: WriteBackupResult, medicationCount: Int) {
        when (result) {
            is WriteBackupResult.Success -> {
                preferences.recordSuccess(
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    medicationCount = medicationCount,
                )
                autoBackupService.cleanupOldBackups(storageProvider)
                _backupMessage.value = "Backup complete ($medicationCount medications)"
            }
            is WriteBackupResult.DestinationNotSet -> {
                preferences.recordFailure(
                    "FAILED_NO_DESTINATION",
                    "Backup folder not set",
                )
                _backupMessage.value = "Backup folder not set. Tap Location to pick one."
            }
            is WriteBackupResult.PermissionRevoked -> {
                preferences.recordFailure(
                    "FAILED_PERMISSION_REVOKED",
                    "Backup folder permission was revoked",
                )
                _backupMessage.value =
                    "Permission to that folder was revoked. Please pick it again."
            }
            is WriteBackupResult.ICloudUnavailable -> {
                preferences.recordFailure(
                    "FAILED_ICLOUD_UNAVAILABLE",
                    "iCloud Drive unavailable",
                )
                _backupMessage.value =
                    "iCloud Drive isn't available. Sign in and enable iCloud Drive, or switch to a folder."
            }
            is WriteBackupResult.WriteFailed -> {
                preferences.recordFailure("FAILED_STORAGE", result.message)
                _backupMessage.value = "Failed to write backup file"
            }
        }
    }

    fun openSettingsForICloud() {
        viewModelScope.launch {
            val opened = PlatformCapabilities.openSystemSettingsForICloud()
            if (!opened) {
                _backupMessage.value =
                    "Open Settings → Apple Account → iCloud → iCloud Drive."
            }
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
    val mode: BackupDestinationMode = BackupDestinationMode.ICLOUD,
    val destinationUri: String? = null,
    val destinationDisplayName: String? = null,
    val destinationAvailability: DestinationAvailability = DestinationAvailability.NotSet,
    val passphraseHint: String? = null,
)
