// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import platform.Foundation.NSURL

/**
 * Callback bridge for iOS share sheet.
 * Swift registers the implementation at app startup.
 */
private var shareCallback: ((NSURL) -> Unit)? = null

fun registerShareBridge(callback: (NSURL) -> Unit) {
    shareCallback = callback
}

internal fun presentShareSheet(fileUrl: NSURL) {
    shareCallback?.invoke(fileUrl)
}
