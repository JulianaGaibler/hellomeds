// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.crypto.PassphraseManager
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import kotlin.time.Clock

class AutoBackupService(
    private val exportService: BackupExportService,
    private val medicationDao: MedicationDao,
    private val passphraseManager: PassphraseManager,
    private val preferences: AutoBackupPreferences,
) {

    /**
     * Generates a full encrypted backup of all data.
     * Returns null-passphrase failure if device is post-reboot pre-unlock
     * (does NOT clear settings — the passphrase will be available after unlock).
     */
    suspend fun generateAutoBackup(appVersion: String): AutoBackupResult {
        val passphrase = passphraseManager.getPassphrase()
            ?: return AutoBackupResult.Failure(
                AutoBackupFailureReason.NO_PASSPHRASE,
                "Passphrase unavailable (device may need to be unlocked)",
            )

        val allMedications = medicationDao.getAll().first()
        if (allMedications.isEmpty()) {
            return AutoBackupResult.Failure(
                AutoBackupFailureReason.NO_DATA,
                "No medications to back up",
            )
        }

        return try {
            val allIds = allMedications.map { it.id }.toSet()
            val backup = exportService.generateBackup(
                selectedMedicationIds = allIds,
                includeSchedules = true,
                includeStockSettings = true,
                includeHistory = true,
                appVersion = appVersion,
            )

            val json = backupJson.encodeToString(backup)
            val encrypted = BackupEncryption.encrypt(json, passphrase)

            val now = Clock.System.now()
            val dt = now.toLocalDateTime(TimeZone.currentSystemDefault())

            // Prepend unencrypted metadata (hint, date, count) so the import
            // screen can show backup info before the user enters their passphrase
            val hint = preferences.passphraseHint.first()
            val metadata = BackupFileMetadata(
                exportedAt = now.toString(),
                appVersion = appVersion,
                medicationCount = allMedications.size,
                passphraseHint = hint,
            )
            val fileData = wrapWithMetadata(metadata, encrypted)

            val fileName = "hellomeds-auto-" +
                "${dt.year.pad(4)}-${(dt.month.ordinal + 1).pad()}-${dt.day.pad()}-" +
                "${dt.hour.pad()}${dt.minute.pad()}${dt.second.pad()}.hmbackup"

            AutoBackupResult.Success(
                data = fileData,
                fileName = fileName,
                medicationCount = allMedications.size,
            )
        } catch (e: Exception) {
            AutoBackupResult.Failure(
                AutoBackupFailureReason.EXPORT_FAILED,
                e.message ?: "Unknown error during backup",
            )
        }
    }

    /**
     * Runs retention cleanup: keeps only the N most recent backups.
     */
    suspend fun cleanupOldBackups(storageProvider: AutoBackupStorageProvider) {
        val retentionCount = preferences.backupRetentionCount.first()
        val existing = storageProvider.listBackups()
        if (existing.size > retentionCount) {
            // Files are sorted newest-first by the provider
            val toDelete = existing.drop(retentionCount)
            toDelete.forEach { storageProvider.deleteBackup(it) }
        }
    }
}

private fun Int.pad(length: Int = 2): String = toString().padStart(length, '0')

sealed class AutoBackupResult {
    data class Success(
        val data: ByteArray,
        val fileName: String,
        val medicationCount: Int,
    ) : AutoBackupResult()

    data class Failure(
        val reason: AutoBackupFailureReason,
        val message: String,
    ) : AutoBackupResult()
}

enum class AutoBackupFailureReason {
    NO_PASSPHRASE,
    NO_DATA,
    ENCRYPTION_FAILED,
    EXPORT_FAILED,
    STORAGE_FAILED,
    DESTINATION_UNAVAILABLE,
}
