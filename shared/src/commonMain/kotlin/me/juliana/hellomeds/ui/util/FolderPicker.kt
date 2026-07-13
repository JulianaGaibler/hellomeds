// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable

/**
 * The `uri` returned is an opaque token whose format is platform-specific: a SAF tree URI
 * on Android, a base64-encoded security-scoped bookmark on iOS. `displayName` is captured
 * at pick time so the UI can render a label without re-resolving the bookmark.
 */
@Composable
expect fun rememberFolderPicker(onResult: (uri: String?, displayName: String?) -> Unit): (initialUri: String?) -> Unit

/** A hint the SAF picker MAY honor; most stock pickers do, some OEMs ignore it. iOS returns null. */
expect fun suggestedAutoBackupInitialUri(): String?
