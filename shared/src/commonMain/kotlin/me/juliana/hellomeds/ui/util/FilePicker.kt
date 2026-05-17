// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable

/** Saves bytes to a user-chosen location. */
@Composable
expect fun rememberFileSaver(
    onResult: (success: Boolean) -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit

/** Sends bytes via the system share sheet. */
@Composable
expect fun rememberFileSharer(
    onResult: (shared: Boolean) -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit

/** Reads bytes from a user-chosen file. */
@Composable
expect fun rememberFileLoader(onResult: (data: ByteArray?) -> Unit): () -> Unit
