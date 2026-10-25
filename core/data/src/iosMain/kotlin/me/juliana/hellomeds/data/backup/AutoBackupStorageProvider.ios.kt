// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorWritingForReplacing
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkCreationWithSecurityScope
import platform.Foundation.NSURLBookmarkResolutionWithSecurityScope
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import kotlin.io.encoding.Base64

/**
 * I/O goes through [NSFileCoordinator] because the target folder may be managed by a
 * sync daemon (iCloud Drive, Google Drive, Dropbox); bare `NSData.writeToURL` races
 * the daemon and occasionally produces zero-byte files.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class AutoBackupStorageProvider(
    private val preferences: AutoBackupPreferences,
) {

    actual suspend fun writeBackup(fileName: String, data: ByteArray): WriteBackupResult =
        withContext(Dispatchers.Default) {
            when (preferences.backupDestinationMode.first()) {
                BackupDestinationMode.ICLOUD -> writeICloud(fileName, data)
                BackupDestinationMode.CUSTOM_FOLDER -> writeCustomFolder(fileName, data)
            }
        }

    actual suspend fun listBackups(): List<String> = withContext(Dispatchers.Default) {
        val dir = resolveDirectory() ?: return@withContext emptyList()
        try {
            val (url, isScoped) = dir
            if (isScoped) url.startAccessingSecurityScopedResource()
            try {
                listBackupsAt(url)
            } finally {
                if (isScoped) url.stopAccessingSecurityScopedResource()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual suspend fun readBackup(fileName: String): ByteArray? = withContext(Dispatchers.Default) {
        val dir = resolveDirectory() ?: return@withContext null
        val (url, isScoped) = dir
        if (isScoped) url.startAccessingSecurityScopedResource()
        try {
            val target = url.URLByAppendingPathComponent(fileName) ?: return@withContext null
            readCoordinated(target)
        } catch (e: Exception) {
            null
        } finally {
            if (isScoped) url.stopAccessingSecurityScopedResource()
        }
    }

    /** Coordinated so an iCloud file that is not downloaded yet is fetched first. */
    private fun readCoordinated(target: NSURL): ByteArray? {
        val coordinator = NSFileCoordinator(filePresenter = null)
        return memScoped {
            val errPtr = alloc<ObjCObjectVar<NSError?>>()
            var result: ByteArray? = null
            coordinator.coordinateReadingItemAtURL(
                url = target,
                options = 0uL,
                error = errPtr.ptr,
                byAccessor = { coordinatedUrl ->
                    if (coordinatedUrl != null) {
                        result = NSData.dataWithContentsOfURL(coordinatedUrl)?.toByteArray()
                    }
                },
            )
            result
        }
    }

    actual suspend fun deleteBackup(fileName: String): Boolean = withContext(Dispatchers.Default) {
        val dir = resolveDirectory() ?: return@withContext false
        val (url, isScoped) = dir
        if (isScoped) url.startAccessingSecurityScopedResource()
        try {
            val target = url.URLByAppendingPathComponent(fileName) ?: return@withContext false
            NSFileManager.defaultManager.removeItemAtURL(target, error = null)
        } catch (e: Exception) {
            false
        } finally {
            if (isScoped) url.stopAccessingSecurityScopedResource()
        }
    }

    actual suspend fun destinationAvailability(): DestinationAvailability = withContext(Dispatchers.Default) {
        when (preferences.backupDestinationMode.first()) {
            BackupDestinationMode.ICLOUD -> iCloudAvailability()
            BackupDestinationMode.CUSTOM_FOLDER -> customFolderAvailability()
        }
    }

    private fun iCloudAvailability(): DestinationAvailability {
        NSFileManager.defaultManager.URLForUbiquityContainerIdentifier(null)
            ?: return DestinationAvailability.ICloudUnavailable
        return DestinationAvailability.Available
    }

    private fun writeICloud(fileName: String, data: ByteArray): WriteBackupResult {
        val fm = NSFileManager.defaultManager
        val container = fm.URLForUbiquityContainerIdentifier(null)
            ?: return WriteBackupResult.ICloudUnavailable

        val backupsDir = container
            .URLByAppendingPathComponent("Documents")
            ?.URLByAppendingPathComponent("AutoBackups")
            ?: return WriteBackupResult.WriteFailed("Could not build iCloud backups path")

        fm.createDirectoryAtURL(
            url = backupsDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        val target = backupsDir.URLByAppendingPathComponent(fileName)
            ?: return WriteBackupResult.WriteFailed("Could not build iCloud target URL")

        return writeCoordinated(target, data)
    }

    private suspend fun customFolderAvailability(): DestinationAvailability {
        val token = preferences.backupDestinationUri.first()
            ?: return DestinationAvailability.NotSet
        return when (resolveBookmark(token)) {
            null -> DestinationAvailability.PermissionRevoked
            else -> DestinationAvailability.Available
        }
    }

    private suspend fun writeCustomFolder(fileName: String, data: ByteArray): WriteBackupResult {
        val token = preferences.backupDestinationUri.first()
            ?: return WriteBackupResult.DestinationNotSet
        val url = resolveBookmark(token)
            ?: return WriteBackupResult.PermissionRevoked
        url.startAccessingSecurityScopedResource()
        try {
            val target = url.URLByAppendingPathComponent(fileName)
                ?: return WriteBackupResult.WriteFailed("Could not build target URL in picked folder")
            return writeCoordinated(target, data)
        } finally {
            url.stopAccessingSecurityScopedResource()
        }
    }

    /** Null means the bookmark no longer resolves and the stored token has been cleared. */
    private suspend fun resolveBookmark(token: String): NSURL? {
        val nsData = decodeBookmark(token) ?: run {
            preferences.setBackupDestinationUri(null)
            return null
        }
        return memScoped {
            val stale = alloc<BooleanVar>().apply { value = false }
            val err = alloc<ObjCObjectVar<NSError?>>()
            val url = NSURL.URLByResolvingBookmarkData(
                bookmarkData = nsData,
                options = NSURLBookmarkResolutionWithSecurityScope,
                relativeToURL = null,
                bookmarkDataIsStale = stale.ptr,
                error = err.ptr,
            )
            if (url == null || err.value != null) {
                preferences.setBackupDestinationUri(null)
                return@memScoped null
            }
            if (stale.value) {
                // Apple's documented bookmark lifecycle: a stale-but-resolved URL is still
                // usable, but the caller is expected to regenerate the bookmark for next time.
                url.startAccessingSecurityScopedResource()
                try {
                    val refreshErr = alloc<ObjCObjectVar<NSError?>>()
                    val fresh = url.bookmarkDataWithOptions(
                        options = NSURLBookmarkCreationWithSecurityScope,
                        includingResourceValuesForKeys = null,
                        relativeToURL = null,
                        error = refreshErr.ptr,
                    )
                    if (fresh != null && refreshErr.value == null) {
                        val encoded = fresh.base64EncodedStringWithOptions(0uL)
                        preferences.setBackupDestinationUri(encoded)
                    }
                } finally {
                    url.stopAccessingSecurityScopedResource()
                }
            }
            url
        }
    }

    /**
     * The accessor block must write to the URL the coordinator passes in — the sync daemon
     * may have swapped it for a temp path that gets moved into place atomically.
     */
    private fun writeCoordinated(target: NSURL, data: ByteArray): WriteBackupResult {
        val nsData = data.toNSData()
        val coordinator = NSFileCoordinator(filePresenter = null)
        return memScoped {
            val coordErr = alloc<ObjCObjectVar<NSError?>>()
            var writeOk = false
            var accessorErr: String? = null
            coordinator.coordinateWritingItemAtURL(
                url = target,
                options = NSFileCoordinatorWritingForReplacing,
                error = coordErr.ptr,
                byAccessor = { coordinatedUrl ->
                    if (coordinatedUrl == null) {
                        accessorErr = "Coordinator passed nil URL to write accessor"
                    } else {
                        writeOk = nsData.writeToURL(coordinatedUrl, atomically = true)
                        if (!writeOk) accessorErr = "NSData.writeToURL returned false"
                    }
                },
            )
            val coordError = coordErr.value
            when {
                coordError != null -> WriteBackupResult.WriteFailed(
                    coordError.localizedDescription,
                )
                accessorErr != null -> WriteBackupResult.WriteFailed(accessorErr!!)
                writeOk -> WriteBackupResult.Success
                else -> WriteBackupResult.WriteFailed("Unknown write failure")
            }
        }
    }

    /** Second element is whether the caller needs to start/stop a security scope. */
    private suspend fun resolveDirectory(): Pair<NSURL, Boolean>? {
        return when (preferences.backupDestinationMode.first()) {
            BackupDestinationMode.ICLOUD -> {
                val fm = NSFileManager.defaultManager
                val container = fm.URLForUbiquityContainerIdentifier(null) ?: return null
                val backupsDir = container
                    .URLByAppendingPathComponent("Documents")
                    ?.URLByAppendingPathComponent("AutoBackups")
                    ?: return null
                fm.createDirectoryAtURL(
                    url = backupsDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )
                backupsDir to false
            }
            BackupDestinationMode.CUSTOM_FOLDER -> {
                val token = preferences.backupDestinationUri.first() ?: return null
                val url = resolveBookmark(token) ?: return null
                url to true
            }
        }
    }

    private fun listBackupsAt(dirUrl: NSURL): List<String> {
        val coordinator = NSFileCoordinator(filePresenter = null)
        return memScoped {
            val errPtr = alloc<ObjCObjectVar<NSError?>>()
            var results: List<String> = emptyList()
            coordinator.coordinateReadingItemAtURL(
                url = dirUrl,
                options = 0uL,
                error = errPtr.ptr,
                byAccessor = { coordinatedUrl ->
                    if (coordinatedUrl != null) {
                        val contents = NSFileManager.defaultManager.contentsOfDirectoryAtURL(
                            url = coordinatedUrl,
                            includingPropertiesForKeys = null,
                            options = 0uL,
                            error = null,
                        )
                        if (contents != null) {
                            @Suppress("UNCHECKED_CAST")
                            results = (contents as List<NSURL>)
                                .mapNotNull { it.lastPathComponent }
                                .filter { it.startsWith("hellomeds-auto-") }
                                .sortedDescending()
                        }
                    }
                },
            )
            results
        }
    }

    private fun decodeBookmark(token: String): NSData? {
        val bytes = try {
            Base64.decode(token)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return bytes.toNSData()
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

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), this@toByteArray.bytes, length.toULong())
    }
    return bytes
}
