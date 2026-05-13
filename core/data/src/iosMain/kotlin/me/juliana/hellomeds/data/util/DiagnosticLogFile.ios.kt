// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual fun readDiagnosticLogFile(path: String): String? {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) as? String
}

@OptIn(ExperimentalForeignApi::class)
actual fun writeDiagnosticLogFile(path: String, content: String) {
    (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
}

actual fun appendDiagnosticLogFile(path: String, line: String) {
    val existing = readDiagnosticLogFile(path) ?: ""
    writeDiagnosticLogFile(path, existing + line + "\n")
}
