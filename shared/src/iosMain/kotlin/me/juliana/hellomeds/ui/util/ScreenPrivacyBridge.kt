// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

/**
 * Bridge to communicate screen privacy state to the Swift layer.
 * Swift registers a callback at app startup; Kotlin calls it when the preference changes.
 */
private var privacyStateCallback: ((Boolean) -> Unit)? = null

fun registerScreenPrivacyBridge(callback: (Boolean) -> Unit) {
    privacyStateCallback = callback
}

internal fun updateScreenPrivacyState(enabled: Boolean) {
    privacyStateCallback?.invoke(enabled)
}
