// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFolderPicker(onResult: (uri: String?) -> Unit): (initialUri: String?) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // Take persistent permission so we can write to this folder in background
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            onResult(uri.toString())
        } else {
            onResult(null)
        }
    }
    return { initialUri -> launcher.launch(initialUri?.let(Uri::parse)) }
}

actual fun suggestedAutoBackupInitialUri(): String? =
    // primary:Documents/HelloMeds/backups encoded as a SAF tree URI document hint
    "content://com.android.externalstorage.documents/document/primary%3ADocuments%2FHelloMeds%2Fbackups"
