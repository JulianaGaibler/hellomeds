// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

/**
 * Platform-specific manager for the auto-backup encryption passphrase.
 *
 * Separate from DatabaseKeyManager because:
 * - The passphrase is user-chosen and recoverable (the user knows it)
 * - The DB key is auto-generated and opaque
 * - Different storage keys/files
 * - Different recovery semantics: if passphrase storage is corrupted,
 *   auto-backup pauses but the user can re-enter their passphrase
 */
expect class PassphraseManager {
    fun getPassphrase(): String?
    fun setPassphrase(passphrase: String): Boolean
    fun hasPassphrase(): Boolean
    fun clearPassphrase()
}
