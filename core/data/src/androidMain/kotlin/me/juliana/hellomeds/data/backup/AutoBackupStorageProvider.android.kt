// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences

actual class AutoBackupStorageProvider(
    private val context: Context,
    private val preferences: AutoBackupPreferences,
) {

    actual suspend fun writeBackup(fileName: String, data: ByteArray): WriteBackupResult = withContext(Dispatchers.IO) {
        val savedUri = preferences.backupDestinationUri.first()
            ?: return@withContext WriteBackupResult.DestinationNotSet
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == savedUri && it.isWritePermission
        }
        if (!hasPermission) {
            preferences.setBackupDestinationUri(null)
            return@withContext WriteBackupResult.PermissionRevoked
        }
        try {
            val treeDoc = DocumentFile.fromTreeUri(context, Uri.parse(savedUri))
                ?: return@withContext WriteBackupResult.PermissionRevoked.also {
                    preferences.setBackupDestinationUri(null)
                }
            // Leftovers from an interrupted write would otherwise count as backups.
            treeDoc.listFiles().filter { it.name?.startsWith(PARTIAL_PREFIX) == true }.forEach { it.delete() }
            // The final name only appears once the whole file is written.
            val file = treeDoc.createFile("application/octet-stream", PARTIAL_PREFIX + fileName)
                ?: return@withContext WriteBackupResult.WriteFailed(
                    "Could not create file in backup folder",
                )
            try {
                val stream = context.contentResolver.openOutputStream(file.uri, "wt")
                    ?: return@withContext WriteBackupResult.WriteFailed("Could not open output stream")
                stream.use { it.write(data) }
                if (!file.renameTo(fileName)) {
                    return@withContext WriteBackupResult.WriteFailed("Could not rename the finished backup")
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            WriteBackupResult.Success
        } catch (e: SecurityException) {
            preferences.setBackupDestinationUri(null)
            WriteBackupResult.PermissionRevoked
        } catch (e: Exception) {
            WriteBackupResult.WriteFailed(e.message ?: "Unknown write error")
        }
    }

    actual suspend fun listBackups(): List<String> = withContext(Dispatchers.IO) {
        try {
            val uri = getValidatedUri() ?: return@withContext emptyList()
            val treeDoc = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
            treeDoc.listFiles()
                .filter { it.name?.startsWith("hellomeds-auto-") == true }
                .mapNotNull { it.name }
                .sortedDescending()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual suspend fun readBackup(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val uri = getValidatedUri() ?: return@withContext null
            val treeDoc = DocumentFile.fromTreeUri(context, uri) ?: return@withContext null
            val file = treeDoc.listFiles().find { it.name == fileName } ?: return@withContext null
            context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun deleteBackup(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = getValidatedUri() ?: return@withContext false
            val treeDoc = DocumentFile.fromTreeUri(context, uri) ?: return@withContext false
            val file = treeDoc.listFiles().find { it.name == fileName }
            file?.delete() ?: false
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun destinationAvailability(): DestinationAvailability = withContext(Dispatchers.IO) {
        val savedUri = preferences.backupDestinationUri.first()
            ?: return@withContext DestinationAvailability.NotSet
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == savedUri && it.isWritePermission
        }
        if (!hasPermission) {
            preferences.setBackupDestinationUri(null)
            return@withContext DestinationAvailability.PermissionRevoked
        }
        DestinationAvailability.Available
    }

    private companion object {
        const val PARTIAL_PREFIX = "partial-"
    }

    private suspend fun getValidatedUri(): Uri? {
        val savedUri = preferences.backupDestinationUri.first() ?: return null
        val uri = Uri.parse(savedUri)
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == savedUri && it.isWritePermission
        }
        if (!hasPermission) {
            preferences.setBackupDestinationUri(null)
            return null
        }
        return uri
    }
}
