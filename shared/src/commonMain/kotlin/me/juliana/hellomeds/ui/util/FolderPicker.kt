// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable

/**
 * Platform-specific folder picker for auto-backup destination.
 * Android: SAF ACTION_OPEN_DOCUMENT_TREE with persistent URI permission
 * iOS: no-op (iCloud Drive is used automatically)
 */
@Composable
expect fun rememberFolderPicker(onResult: (uri: String?) -> Unit): () -> Unit
