// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable

/** A file written to a user-chosen location. [reference] is only meaningful to the platform that saved it. */
data class SavedFile(val name: String, val reference: String)

/** Saves bytes to a user-chosen location. The result is null when the user cancels or the write fails. */
@Composable
expect fun rememberFileSaver(
    onResult: (SavedFile?) -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit

/** Sends bytes via the system share sheet. */
@Composable
expect fun rememberFileSharer(
    onResult: (shared: Boolean) -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit

/** Opens the Files app at the folder that contains a saved file. */
@Composable
expect fun rememberFileLocationOpener(): (SavedFile) -> Unit

/** Reads bytes from a user-chosen file. */
@Composable
expect fun rememberFileLoader(onResult: (data: ByteArray?) -> Unit): () -> Unit
