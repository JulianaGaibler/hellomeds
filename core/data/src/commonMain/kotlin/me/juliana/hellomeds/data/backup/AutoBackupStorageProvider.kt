// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

expect class AutoBackupStorageProvider {
    suspend fun writeBackup(fileName: String, data: ByteArray): WriteBackupResult

    /** Sorted newest-first; only files matching the hellomeds-auto-* pattern. */
    suspend fun listBackups(): List<String>

    suspend fun deleteBackup(fileName: String): Boolean

    suspend fun destinationAvailability(): DestinationAvailability
}

/**
 * Android always behaves as [CUSTOM_FOLDER]; the preference is iOS-driven but lives in
 * shared code so the ViewModel can read it uniformly.
 */
enum class BackupDestinationMode {
    ICLOUD,
    CUSTOM_FOLDER,
}

sealed interface WriteBackupResult {
    data object Success : WriteBackupResult
    data object DestinationNotSet : WriteBackupResult
    data object PermissionRevoked : WriteBackupResult

    /**
     * Covers any reason the iCloud container can't be reached: no Apple ID signed in,
     * iCloud Drive toggled off, or entitlement issues. Apple's API does not reliably
     * distinguish these cases — `FileManager.ubiquityIdentityToken` returns nil for all
     * of them — so the UI surfaces a single message covering both fixes.
     */
    data object ICloudUnavailable : WriteBackupResult

    data class WriteFailed(val message: String) : WriteBackupResult
}

sealed interface DestinationAvailability {
    data object Available : DestinationAvailability
    data object NotSet : DestinationAvailability
    data object PermissionRevoked : DestinationAvailability
    data object ICloudUnavailable : DestinationAvailability
}

val DestinationAvailability.isAvailable: Boolean
    get() = this is DestinationAvailability.Available
