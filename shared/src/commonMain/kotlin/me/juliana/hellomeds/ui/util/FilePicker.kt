// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable

/**
 * Remembers a file creator that saves bytes to a user-chosen location.
 * Returns a launcher function that triggers the save dialog with the given filename and data.
 */
@Composable
expect fun rememberFileSaver(
    onResult: (success: Boolean) -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit

/**
 * Remembers a file sharer that sends bytes via the system share sheet.
 * Returns a launcher function that triggers the share dialog.
 */
@Composable
expect fun rememberFileSharer(
    onResult: (shared: Boolean) -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit

/**
 * Remembers a file picker that reads bytes from a user-chosen file.
 * Returns a launcher function that triggers the file selection dialog.
 */
@Composable
expect fun rememberFileLoader(onResult: (data: ByteArray?) -> Unit): () -> Unit
