// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import me.juliana.hellomeds.data.crypto.registerKeychainBridge

/**
 * Re-export for Swift access. The actual registerKeychainBridge lives in core/data
 * but may not be visible from Swift since core/data is a transitive dependency.
 */
fun setupKeychainBridge(read: () -> ByteArray?, write: (ByteArray) -> Boolean) {
    registerKeychainBridge(read, write)
}
