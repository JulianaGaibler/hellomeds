// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.URLPathAllowedCharacterSet
import platform.Foundation.create
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
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

/** The picker only holds its delegate weakly. */
private var activePickerDelegate: NSObject? = null

/**
 * Save to Files — uses UIDocumentPickerViewController in export/move mode.
 */
@Composable
actual fun rememberFileSaver(
    onResult: (SavedFile?) -> Unit,
    onError: () -> Unit,
): (fileName: String, mimeType: String, data: ByteArray) -> Unit {
    return remember {
        { fileName: String, _: String, data: ByteArray ->
            val tempPath = NSTemporaryDirectory() + fileName
            if (data.writeToFile(tempPath)) {
                val picker = UIDocumentPickerViewController(
                    forExportingURLs = listOf(NSURL.fileURLWithPath(tempPath)),
                )
                val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>,
                    ) {
                        activePickerDelegate = null
                        deleteFile(tempPath)
                        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                        onResult(url?.let { SavedFile(it.lastPathComponent ?: fileName, it.absoluteString ?: "") })
                    }

                    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                        activePickerDelegate = null
                        deleteFile(tempPath)
                        onResult(null)
                    }
                }
                activePickerDelegate = delegate
                picker.delegate = delegate
                topViewController()?.presentViewController(picker, animated = true, completion = null)
            } else {
                deleteFile(tempPath)
                onError()
            }
        }
    }
}

/**
 * Opens the Files app at the folder of a saved file via the shareddocuments scheme.
 */
@Composable
actual fun rememberFileLocationOpener(): (SavedFile) -> Unit {
    return remember {
        { file: SavedFile ->
            val folderPath = NSURL.URLWithString(file.reference)?.URLByDeletingLastPathComponent?.path
            val encoded = folderPath?.let {
                NSString.create(string = it).stringByAddingPercentEncodingWithAllowedCharacters(
                    NSCharacterSet.URLPathAllowedCharacterSet,
                )
            }
            val target = NSURL.URLWithString("shareddocuments://" + (encoded ?: ""))
            if (target != null) {
                UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any>(), null)
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
                if (data.writeToFile(tempPath)) {
                    presentShareSheet(NSURL.fileURLWithPath(tempPath))
                    onResult(true)
                } else {
                    deleteFile(tempPath)
                    onResult(false)
                }
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
actual fun rememberFileLoader(onResult: (data: ByteArray?) -> Unit, onError: () -> Unit): () -> Unit {
    return remember {
        {
            val types = listOf(UTTypeJSON, UTTypeData).filterNotNull()
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)

            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>,
                ) {
                    activePickerDelegate = null
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                    if (url != null) {
                        val accessing = url.startAccessingSecurityScopedResource()
                        try {
                            val nsData = NSData.dataWithContentsOfURL(url)
                            val bytes = nsData?.toByteArray()
                            if (bytes != null && bytes.isNotEmpty()) onResult(bytes) else onError()
                        } finally {
                            if (accessing) url.stopAccessingSecurityScopedResource()
                        }
                    } else {
                        onResult(null)
                    }
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    activePickerDelegate = null
                    onResult(null)
                }
            }

            activePickerDelegate = delegate
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
    val file = platform.posix.fopen(path, "wb") ?: return false
    val written = usePinned { pinned -> platform.posix.fwrite(pinned.addressOf(0), 1u, size.toULong(), file) }
    val closed = platform.posix.fclose(file)
    return written == size.toULong() && closed == 0
}

private fun deleteFile(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
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
