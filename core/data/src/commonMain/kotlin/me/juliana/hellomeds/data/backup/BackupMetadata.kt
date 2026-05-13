// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Unencrypted metadata prepended to auto-backup files.
 *
 * File format: UTF-8 JSON metadata line + newline + HMEDS01 encrypted payload
 *
 * This allows the import screen to show backup details (hint, date, medication count)
 * BEFORE the user enters their passphrase. The metadata is not encrypted because
 * it must be readable when the user has no key — that's the whole point.
 *
 * Manual backups (HMEDS01 without metadata prefix) remain fully compatible.
 */
@Serializable
data class BackupFileMetadata(
    val exportedAt: String = "",
    val appVersion: String = "",
    val medicationCount: Int = 0,
    val passphraseHint: String? = null,
    val deviceName: String? = null,
    val autoBackup: Boolean = true,
)

private val metadataJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private const val METADATA_SEPARATOR = '\n'.code.toByte()
private val HMEDS_MAGIC = "HMEDS01\u0000".encodeToByteArray()

/**
 * Wraps encrypted data with an unencrypted metadata header.
 */
fun wrapWithMetadata(metadata: BackupFileMetadata, encryptedData: ByteArray): ByteArray {
    val metadataBytes = metadataJson.encodeToString(metadata).encodeToByteArray()
    return metadataBytes + byteArrayOf(METADATA_SEPARATOR) + encryptedData
}

/**
 * Extracts metadata from a backup file without decrypting.
 * Returns null if the file has no metadata prefix (plain HMEDS01 format).
 */
fun extractMetadata(data: ByteArray): BackupFileMetadata? {
    // If it starts with HMEDS01 magic directly, there's no metadata
    if (data.size >= HMEDS_MAGIC.size && data.copyOfRange(0, HMEDS_MAGIC.size)
            .contentEquals(HMEDS_MAGIC)
    ) {
        return null
    }

    // Find the newline separator
    val newlineIndex = data.indexOf(METADATA_SEPARATOR)
    if (newlineIndex <= 0) return null

    // Verify that what follows the newline is HMEDS01 encrypted data
    val afterNewline =
        data.copyOfRange(newlineIndex + 1, minOf(newlineIndex + 1 + HMEDS_MAGIC.size, data.size))
    if (!afterNewline.contentEquals(HMEDS_MAGIC)) return null

    return try {
        val metadataStr = data.copyOfRange(0, newlineIndex).decodeToString()
        metadataJson.decodeFromString<BackupFileMetadata>(metadataStr)
    } catch (e: Exception) {
        null
    }
}

/**
 * Strips the metadata prefix from a backup file, returning just the encrypted payload.
 * If there's no metadata prefix, returns the data as-is.
 */
fun stripMetadata(data: ByteArray): ByteArray {
    if (data.size >= HMEDS_MAGIC.size && data.copyOfRange(0, HMEDS_MAGIC.size)
            .contentEquals(HMEDS_MAGIC)
    ) {
        return data // Already plain HMEDS01
    }

    val newlineIndex = data.indexOf(METADATA_SEPARATOR)
    if (newlineIndex <= 0) return data

    return data.copyOfRange(newlineIndex + 1, data.size)
}

/**
 * Check if data is an encrypted backup (with or without metadata prefix).
 */
fun isEncryptedBackup(data: ByteArray): Boolean {
    // Direct HMEDS01
    if (BackupEncryption.isEncrypted(data)) return true
    // Metadata + HMEDS01
    val stripped = stripMetadata(data)
    return BackupEncryption.isEncrypted(stripped)
}
