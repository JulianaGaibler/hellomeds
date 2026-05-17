// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFolderPicker(onResult: (uri: String?) -> Unit): (initialUri: String?) -> Unit {
    // iOS uses iCloud Drive automatically — no folder picker needed
    return { _ -> }
}

actual fun suggestedAutoBackupInitialUri(): String? = null
