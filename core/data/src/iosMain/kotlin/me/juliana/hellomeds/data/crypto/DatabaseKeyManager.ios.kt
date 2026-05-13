// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

private var keychainReadCallback: (() -> ByteArray?)? = null
private var keychainWriteCallback: ((ByteArray) -> Boolean)? = null

/**
 * Register Keychain bridge callbacks from Swift.
 * Must be called before Koin initialization.
 */
fun registerKeychainBridge(read: () -> ByteArray?, write: (ByteArray) -> Boolean) {
    keychainReadCallback = read
    keychainWriteCallback = write
}

actual class DatabaseKeyManager {

    actual fun getOrCreateKey(): ByteArray? {
        val read = keychainReadCallback
            ?: throw IllegalStateException(
                "Keychain bridge not registered. Call setupKeychainBridge() before Koin init.",
            )
        val write = keychainWriteCallback!!

        // Try to retrieve existing key
        val existing = read()
        if (existing != null) return existing

        // Generate new key (32 bytes via platform bridge)
        val key = generateKey() ?: return null
        return if (write(key)) key else null
    }

    actual fun hasKey(): Boolean {
        return keychainReadCallback?.invoke() != null
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun generateKey(): ByteArray? {
        val key = ByteArray(KEY_SIZE)
        val status = key.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, KEY_SIZE.convert(), pinned.addressOf(0))
        }
        if (status != 0) return null
        return key
    }

    companion object {
        private const val KEY_SIZE = 32
    }
}
