// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkCreationWithSecurityScope
import platform.Foundation.base64EncodedStringWithOptions
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject

private fun topViewController(): platform.UIKit.UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) {
        vc = vc?.presentedViewController
    }
    return vc
}

/**
 * `UIDocumentPickerViewController.delegate` is a weak reference; without a strong Kotlin
 * reference, the K/N GC can reclaim the delegate between `presentViewController` returning
 * and the user finishing the pick. The holder is kept alive by Compose's `remember`.
 */
private class FolderPickerHolder {
    var delegate: NSObject? = null
}

@Composable
actual fun rememberFolderPicker(
    onResult: (uri: String?, displayName: String?) -> Unit,
): (initialUri: String?) -> Unit {
    val holder = remember { FolderPickerHolder() }
    return remember {
        { _: String? ->
            val folderType = UTTypeFolder
            if (folderType == null) {
                onResult(null, null)
            } else {
                val picker = UIDocumentPickerViewController(
                    forOpeningContentTypes = listOf(folderType),
                )
                val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>,
                    ) {
                        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                        try {
                            if (url == null) {
                                onResult(null, null)
                                return
                            }
                            val accessing = url.startAccessingSecurityScopedResource()
                            try {
                                val token = captureBookmark(url)
                                val name = url.lastPathComponent
                                if (token != null) {
                                    onResult(token, name)
                                } else {
                                    onResult(null, null)
                                }
                            } finally {
                                if (accessing) url.stopAccessingSecurityScopedResource()
                            }
                        } finally {
                            holder.delegate = null
                        }
                    }

                    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                        try {
                            onResult(null, null)
                        } finally {
                            holder.delegate = null
                        }
                    }
                }
                holder.delegate = delegate
                picker.delegate = delegate
                picker.allowsMultipleSelection = false
                topViewController()?.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

actual fun suggestedAutoBackupInitialUri(): String? = null

private fun captureBookmark(url: NSURL): String? {
    return memScoped {
        val err = alloc<ObjCObjectVar<NSError?>>()
        val data = url.bookmarkDataWithOptions(
            options = NSURLBookmarkCreationWithSecurityScope,
            includingResourceValuesForKeys = null,
            relativeToURL = null,
            error = err.ptr,
        )
        if (data == null || err.value != null) return@memScoped null
        data.base64EncodedStringWithOptions(0uL)
    }
}
