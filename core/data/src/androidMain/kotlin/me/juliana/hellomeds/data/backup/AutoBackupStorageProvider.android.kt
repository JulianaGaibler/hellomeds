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

    actual suspend fun writeBackup(fileName: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = getValidatedUri() ?: return@withContext false
            val treeDoc = DocumentFile.fromTreeUri(context, uri) ?: return@withContext false
            val file = treeDoc.createFile("application/octet-stream", fileName)
                ?: return@withContext false
            context.contentResolver.openOutputStream(file.uri)?.use { stream ->
                stream.write(data)
            }
            true
        } catch (e: SecurityException) {
            // Permission revoked — reset URI
            preferences.setBackupDestinationUri(null)
            false
        } catch (e: Exception) {
            false
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

    actual suspend fun isDestinationAvailable(): Boolean {
        return getValidatedUri() != null
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
