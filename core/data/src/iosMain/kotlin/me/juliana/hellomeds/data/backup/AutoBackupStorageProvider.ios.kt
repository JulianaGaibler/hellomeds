// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToURL

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual class AutoBackupStorageProvider(
    private val preferences: AutoBackupPreferences,
) {

    actual suspend fun writeBackup(fileName: String, data: ByteArray): Boolean = withContext(Dispatchers.Default) {
        try {
            val dir = getOrCreateBackupDirectory() ?: return@withContext false
            val fileUrl = NSURL.fileURLWithPath(dir + "/" + fileName)
            val nsData = data.toNSData()
            nsData.writeToURL(fileUrl, atomically = true)
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun listBackups(): List<String> = withContext(Dispatchers.Default) {
        try {
            val dir = getOrCreateBackupDirectory() ?: return@withContext emptyList()
            val fm = NSFileManager.defaultManager
            val contents =
                fm.contentsOfDirectoryAtPath(dir, error = null) ?: return@withContext emptyList()
            @Suppress("UNCHECKED_CAST")
            (contents as List<String>)
                .filter { it.startsWith("hellomeds-auto-") }
                .sortedDescending()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual suspend fun deleteBackup(fileName: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val dir = getOrCreateBackupDirectory() ?: return@withContext false
            NSFileManager.defaultManager.removeItemAtPath(dir + "/" + fileName, error = null)
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun isDestinationAvailable(): Boolean {
        return getOrCreateBackupDirectory() != null
    }

    /** Prefers the iCloud ubiquity container; falls back to local Documents. */
    private fun getOrCreateBackupDirectory(): String? {
        val fm = NSFileManager.defaultManager

        // Try iCloud ubiquity container first
        val ubiquityUrl = fm.URLForUbiquityContainerIdentifier(null)
        val baseDir = if (ubiquityUrl != null) {
            ubiquityUrl.path!! + "/Documents/AutoBackups"
        } else {
            // Fallback to local Documents
            val docUrl = fm.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
            (docUrl?.path ?: return null) + "/AutoBackups"
        }

        if (!fm.fileExistsAtPath(baseDir)) {
            fm.createDirectoryAtPath(
                baseDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }

        return baseDir
    }

    private fun ByteArray.toNSData(): NSData {
        return if (isEmpty()) {
            NSData()
        } else {
            usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
            }
        }
    }
}
