// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

private var passphraseReadCallback: (() -> String?)? = null
private var passphraseWriteCallback: ((String) -> Boolean)? = null
private var passphraseDeleteCallback: (() -> Unit)? = null

/**
 * Register Keychain bridge callbacks for the auto-backup passphrase.
 * Must be called before Koin initialization.
 */
fun registerPassphraseBridge(read: () -> String?, write: (String) -> Boolean, delete: () -> Unit) {
    passphraseReadCallback = read
    passphraseWriteCallback = write
    passphraseDeleteCallback = delete
}

actual class PassphraseManager {

    actual fun getPassphrase(): String? {
        val read = passphraseReadCallback
            ?: throw IllegalStateException(
                "Passphrase bridge not registered. Call setupPassphraseBridge() before Koin init.",
            )
        return read()
    }

    actual fun setPassphrase(passphrase: String): Boolean {
        val write = passphraseWriteCallback!!
        return write(passphrase)
    }

    actual fun hasPassphrase(): Boolean {
        return passphraseReadCallback?.invoke() != null
    }

    actual fun clearPassphrase() {
        passphraseDeleteCallback?.invoke()
    }
}
