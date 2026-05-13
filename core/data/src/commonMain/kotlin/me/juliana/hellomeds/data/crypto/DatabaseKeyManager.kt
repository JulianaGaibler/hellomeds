// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

/**
 * Platform-specific database encryption key manager.
 * Generates, stores, and retrieves a 32-byte AES-256 key for SQLCipher.
 *
 * Returns null if the device is in a state where key retrieval is impossible
 * (e.g., post-reboot pre-first-unlock on iOS, or Direct Boot mode on Android).
 */
expect class DatabaseKeyManager {
    fun getOrCreateKey(): ByteArray?
    fun hasKey(): Boolean
}
