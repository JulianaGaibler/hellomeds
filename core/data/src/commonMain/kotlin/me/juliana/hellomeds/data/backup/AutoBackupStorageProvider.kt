// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

/**
 * Platform-specific storage for automatic backups.
 *
 * Android: Writes to a user-selected SAF folder (Google Drive, local, etc.)
 * iOS: Writes to iCloud Drive ubiquity container (or local fallback)
 */
expect class AutoBackupStorageProvider {
    /**
     * Writes backup data to the configured destination.
     * Returns true on success.
     */
    suspend fun writeBackup(fileName: String, data: ByteArray): Boolean

    /**
     * Lists existing auto-backup filenames, sorted newest-first.
     * Only returns files matching the hellomeds-auto-* pattern.
     */
    suspend fun listBackups(): List<String>

    /**
     * Deletes a backup file by name.
     */
    suspend fun deleteBackup(fileName: String): Boolean

    /**
     * Whether the storage destination is configured and accessible.
     */
    suspend fun isDestinationAvailable(): Boolean
}
