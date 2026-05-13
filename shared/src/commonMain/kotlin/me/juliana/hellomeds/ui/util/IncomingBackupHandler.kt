// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PendingImport {
    data class Bytes(val data: ByteArray) : PendingImport
    data class Reader(val read: suspend () -> ByteArray?) : PendingImport
}

/**
 * Handles backup files opened from external apps (e.g. messenger, email, Files).
 * Platform code sets pending import, the navigation layer observes and opens import screen.
 */
object IncomingBackupHandler {
    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    fun setPendingImportBytes(bytes: ByteArray) {
        _pendingImport.value = PendingImport.Bytes(bytes)
    }

    fun setPendingImportReader(reader: suspend () -> ByteArray?) {
        _pendingImport.value = PendingImport.Reader(reader)
    }

    fun clearPendingImport() {
        _pendingImport.value = null
    }
}

// Top-level function for iOS Swift bridge access
fun setPendingImportBytes(bytes: ByteArray) {
    IncomingBackupHandler.setPendingImportBytes(bytes)
}
