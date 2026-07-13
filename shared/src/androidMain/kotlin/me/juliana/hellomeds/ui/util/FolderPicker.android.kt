// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
actual fun rememberFolderPicker(
    onResult: (uri: String?, displayName: String?) -> Unit,
): (initialUri: String?) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // Persistent permission survives process death so the worker can write later.
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            onResult(uri.toString(), displayNameFromTreeUri(uri.toString()))
        } else {
            onResult(null, null)
        }
    }
    return { initialUri -> launcher.launch(initialUri?.let(Uri::parse)) }
}

// SAF document URI for primary:Documents/HelloMeds/backups.
actual fun suggestedAutoBackupInitialUri(): String? =
    "content://com.android.externalstorage.documents/document/primary%3ADocuments%2FHelloMeds%2Fbackups"

private fun displayNameFromTreeUri(rawUri: String): String? {
    val decoded = try {
        URLDecoder.decode(rawUri, StandardCharsets.UTF_8.name())
    } catch (e: IllegalArgumentException) {
        return null
    }
    val afterTree = decoded.substringAfterLast("/tree/", missingDelimiterValue = "")
    val afterColon = afterTree.substringAfter(":", missingDelimiterValue = afterTree)
    return afterColon.substringAfterLast("/").ifEmpty {
        decoded.substringAfterLast("/").ifEmpty { null }
    }
}
