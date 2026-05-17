// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable

/**
 * Platform-specific folder picker for auto-backup destination.
 * Android: SAF ACTION_OPEN_DOCUMENT_TREE with persistent URI permission. The returned launcher
 * accepts an optional initial URI hint (a `content://` tree URI) that the SAF picker MAY honor
 * to position the user near a suggested folder. Some OEM file pickers ignore it.
 * iOS: no-op (iCloud Drive is used automatically).
 */
@Composable
expect fun rememberFolderPicker(onResult: (uri: String?) -> Unit): (initialUri: String?) -> Unit

/**
 * Suggested initial directory hint for the auto-backup folder picker on Android. Points at
 * `primary:Documents/HelloMeds/backups`. The picker treats it as a hint — most stock pickers
 * honor it; some OEMs ignore it and open at the device root.
 */
expect fun suggestedAutoBackupInitialUri(): String?
