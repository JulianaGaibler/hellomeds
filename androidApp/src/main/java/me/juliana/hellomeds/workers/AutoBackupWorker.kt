// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.workers

import android.content.Context
import android.os.UserManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import me.juliana.hellomeds.data.backup.AutoBackupResult
import me.juliana.hellomeds.data.backup.AutoBackupService
import me.juliana.hellomeds.data.backup.AutoBackupStorageProvider
import me.juliana.hellomeds.data.backup.WriteBackupResult
import me.juliana.hellomeds.data.backup.isAvailable
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Clock

/**
 * Daily auto-backup worker. Runs at ~4 AM.
 * Generates a full encrypted backup and writes it to the user's chosen folder.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        // Direct Boot guard — EncryptedSharedPreferences unavailable before first unlock
        if (!applicationContext.getSystemService(UserManager::class.java).isUserUnlocked) {
            AppLogger.w(TAG, "Device not unlocked, deferring auto-backup")
            return Result.retry()
        }

        val preferences: AutoBackupPreferences = get()

        if (!preferences.autoBackupEnabled.first()) {
            AppLogger.d(TAG, "Auto-backup disabled, skipping")
            return Result.success()
        }

        val service: AutoBackupService = get()
        val storageProvider: AutoBackupStorageProvider = get()

        // Check destination before generating backup
        if (!storageProvider.destinationAvailability().isAvailable) {
            AppLogger.w(TAG, "Backup destination unavailable")
            preferences.recordFailure(
                "FAILED_DESTINATION_UNAVAILABLE",
                "Storage location no longer accessible",
            )
            return Result.success() // Don't retry — user needs to re-select folder
        }

        return try {
            val appVersion = PlatformCapabilities.appVersionString()
            when (val result = service.generateAutoBackup(appVersion)) {
                is AutoBackupResult.Success -> {
                    when (val written = storageProvider.writeBackup(result.fileName, result.data)) {
                        is WriteBackupResult.Success -> {
                            preferences.recordSuccess(
                                timestamp = Clock.System.now().toEpochMilliseconds(),
                                medicationCount = result.medicationCount,
                            )
                            service.cleanupOldBackups(storageProvider)
                            AppLogger.i(
                                TAG,
                                "Auto-backup complete: ${result.fileName} (${result.medicationCount} medications)",
                            )
                            Result.success()
                        }
                        // No-retry cases: the user has to take action before this can succeed.
                        is WriteBackupResult.DestinationNotSet -> {
                            preferences.recordFailure(
                                "FAILED_NO_DESTINATION",
                                "Backup folder not set",
                            )
                            Result.success()
                        }
                        is WriteBackupResult.PermissionRevoked -> {
                            preferences.recordFailure(
                                "FAILED_PERMISSION_REVOKED",
                                "Backup folder permission revoked",
                            )
                            Result.success()
                        }
                        is WriteBackupResult.WriteFailed -> {
                            preferences.recordFailure("FAILED_STORAGE", written.message)
                            Result.retry()
                        }
                        // Cannot occur on Android; kept for `when` exhaustiveness.
                        is WriteBackupResult.ICloudUnavailable -> {
                            preferences.recordFailure(
                                "FAILED_STORAGE",
                                "Unexpected iCloud result on Android",
                            )
                            Result.success()
                        }
                    }
                }

                is AutoBackupResult.Failure -> {
                    AppLogger.w(TAG, "Auto-backup failed: ${result.reason} — ${result.message}")
                    preferences.recordFailure(result.reason.name, result.message)
                    if (result.reason == me.juliana.hellomeds.data.backup.AutoBackupFailureReason.NO_PASSPHRASE) {
                        // Post-reboot pre-unlock — retry later
                        Result.retry()
                    } else {
                        Result.success() // No point retrying for NO_DATA etc.
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Auto-backup error", e)
            preferences.recordFailure("FAILED_UNKNOWN", e.message)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AutoBackup"
    }
}
