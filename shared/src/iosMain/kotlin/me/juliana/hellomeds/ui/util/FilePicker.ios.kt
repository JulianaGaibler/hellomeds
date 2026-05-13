// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject

private fun topViewController(): platform.UIKit.UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) {
        vc = vc?.presentedViewController
    }
    return vc
}

/**
 * Save to Files — uses UIDocumentPickerViewController in export/move mode.
 */
@Composable
actual fun rememberFileSaver(
    onResult: (success: Boolean) -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit {
    return remember {
        { fileName: String, _: String, data: ByteArray ->
            try {
                val tempPath = NSTemporaryDirectory() + fileName
                data.writeToFile(tempPath)
                val tempUrl = NSURL.fileURLWithPath(tempPath)

                val picker = UIDocumentPickerViewController(
                    forExportingURLs = listOf(tempUrl),
                )
                topViewController()?.presentViewController(picker, animated = true, completion = null)
                onResult(true)
            } catch (_: Exception) {
                onResult(false)
            }
        }
    }
}

/**
 * Share via system share sheet — uses UIActivityViewController.
 */
@Composable
actual fun rememberFileSharer(
    onResult: (shared: Boolean) -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit {
    return remember {
        { fileName: String, _: String, data: ByteArray ->
            try {
                val tempPath = NSTemporaryDirectory() + fileName
                data.writeToFile(tempPath)
                val tempUrl = NSURL.fileURLWithPath(tempPath)

                presentShareSheet(tempUrl)
                onResult(true)
            } catch (_: Exception) {
                onResult(false)
            }
        }
    }
}

/**
 * File picker for import — opens document picker to select a file.
 */
@Composable
actual fun rememberFileLoader(onResult: (data: ByteArray?) -> Unit): () -> Unit {
    return remember {
        {
            val types = listOf(UTTypeJSON, UTTypeData).filterNotNull()
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)

            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>,
                ) {
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                    if (url != null) {
                        val accessing = url.startAccessingSecurityScopedResource()
                        try {
                            val nsData = NSData.dataWithContentsOfURL(url)
                            if (nsData != null) {
                                onResult(nsData.toByteArray())
                            } else {
                                onResult(null)
                            }
                        } finally {
                            if (accessing) url.stopAccessingSecurityScopedResource()
                        }
                    } else {
                        onResult(null)
                    }
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    onResult(null)
                }
            }

            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.writeToFile(path: String): Boolean {
    if (isEmpty()) return false
    usePinned { pinned ->
        val file = platform.posix.fopen(path, "wb") ?: return false
        platform.posix.fwrite(pinned.addressOf(0), 1u, size.toULong(), file)
        platform.posix.fclose(file)
    }
    return true
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), this@toByteArray.bytes, length.toULong())
    }
    return bytes
}
