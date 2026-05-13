// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

/**
 * iOS encryption bridge using CryptoKit (registered from Swift at app startup).
 *
 * Uses the same byte format as Android:
 * HMEDS01\0 (8B) + salt (16B) + iv (12B) + ciphertext + GCM tag (16B)
 * PBKDF2-SHA256 with 210,000 iterations, AES-256-GCM.
 */

private var encryptCallback: ((String, String) -> ByteArray)? = null
private var decryptCallback: ((ByteArray, String) -> String)? = null

/**
 * Register the CryptoKit encryption implementation from Swift.
 * Called once during app initialization.
 */
fun registerEncryptionBridge(
    encrypt: (json: String, passphrase: String) -> ByteArray,
    decrypt: (data: ByteArray, passphrase: String) -> String,
) {
    encryptCallback = encrypt
    decryptCallback = decrypt
}

actual object BackupEncryption {

    private val MAGIC = "HMEDS01\u0000".encodeToByteArray()

    actual fun encrypt(json: String, passphrase: String): ByteArray {
        val callback = encryptCallback
            ?: throw UnsupportedOperationException("Encryption bridge not registered")
        return callback(json, passphrase)
    }

    actual fun decrypt(data: ByteArray, passphrase: String): String {
        val callback = decryptCallback
            ?: throw UnsupportedOperationException("Encryption bridge not registered")
        try {
            return callback(data, passphrase)
        } catch (e: Exception) {
            throw BadPassphraseException()
        }
    }

    actual fun isEncrypted(data: ByteArray): Boolean {
        if (data.size < MAGIC.size) return false
        return data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
    }
}
