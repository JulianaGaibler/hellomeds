// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hard cap on imported backup file size to prevent OOM from oversized/malicious files. */
private const val MAX_IMPORT_FILE_BYTES = 50L * 1024L * 1024L // 50 MB

@Composable
actual fun rememberFileSaver(
    onResult: (SavedFile?) -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingData by remember { mutableStateOf<ByteArray?>(null) }
    var pendingName by remember { mutableStateOf("") }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val data = pendingData
        if (uri != null && data != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        // Truncate, because a provider may otherwise keep the tail of a longer old file.
                        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")
                        stream.use { it.write(data) }
                        SavedFile(displayName(context, uri) ?: pendingName, uri.toString())
                    } catch (_: Exception) {
                        null
                    }
                }
                onResult(saved)
                pendingData = null
            }
        } else {
            onResult(null)
            pendingData = null
        }
    }

    return { fileName, _, data ->
        pendingData = data
        pendingName = fileName
        saveLauncher.launch(fileName)
    }
}

private fun displayName(context: Context, uri: Uri): String? = context.contentResolver
    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

@Composable
actual fun rememberFileLocationOpener(): (SavedFile) -> Unit {
    val context = LocalContext.current
    return { file ->
        val uri = Uri.parse(file.reference)
        val folder = parentFolderUri(uri)
        val intent = if (folder != null) {
            Intent(Intent.ACTION_VIEW).setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
        } else {
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "*/*")
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

/** Only the external storage provider has document ids that encode a folder path. */
private fun parentFolderUri(uri: Uri): Uri? {
    if (uri.authority != "com.android.externalstorage.documents") return null
    val documentId = DocumentsContract.getDocumentId(uri)
    val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = "")
        .ifEmpty { documentId.substringBefore(':') + ":" }
    return DocumentsContract.buildDocumentUri(uri.authority, parentId)
}

@Composable
actual fun rememberFileSharer(
    onResult: (shared: Boolean) -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return { fileName, mimeType, data ->
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                try {
                    val cacheDir = java.io.File(context.cacheDir, "shared_backups")
                    cacheDir.mkdirs()
                    // Best-effort cleanup of stale shares (>1 day old) — never grows unbounded.
                    val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
                    cacheDir.listFiles()?.forEach { stale ->
                        if (stale.lastModified() < cutoff) stale.delete()
                    }
                    val file = java.io.File(cacheDir, fileName)
                    file.writeBytes(data)
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                } catch (_: Exception) {
                    null
                }
            }

            if (uri == null) {
                onResult(false)
                return@launch
            }

            try {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    android.content.Intent.createChooser(shareIntent, null),
                )
                onResult(true)
            } catch (_: Exception) {
                onResult(false)
            }
        }
    }
}

@Composable
actual fun rememberFileLoader(onResult: (data: ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    // Stat first — reject anything that would OOM the app.
                    val size = context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.SIZE),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
                    } ?: -1L
                    if (size in 1L..MAX_IMPORT_FILE_BYTES || size == -1L) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
            onResult(bytes)
        }
    }

    return {
        launcher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
    }
}
