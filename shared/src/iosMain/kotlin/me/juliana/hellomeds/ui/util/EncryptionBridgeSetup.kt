// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import me.juliana.hellomeds.data.backup.registerEncryptionBridge

/**
 * Re-export for Swift access. The actual registerEncryptionBridge lives in core/data
 * but may not be visible from Swift since core/data is a transitive dependency.
 */
fun setupEncryptionBridge(
    encrypt: (json: String, passphrase: String) -> ByteArray,
    decrypt: (data: ByteArray, passphrase: String) -> String,
) {
    registerEncryptionBridge(encrypt, decrypt)
}
