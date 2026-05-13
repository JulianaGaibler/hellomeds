// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import me.juliana.hellomeds.data.crypto.registerPassphraseBridge

/**
 * Re-export for Swift access. The actual registerPassphraseBridge lives in core/data
 * but may not be visible from Swift since core/data is a transitive dependency.
 */
fun setupPassphraseBridge(read: () -> String?, write: (String) -> Boolean, delete: () -> Unit) {
    registerPassphraseBridge(read, write, delete)
}
