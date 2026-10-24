// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class DatabaseSchemaGuard {
    private val dbPath = documentDirectory() + "/hellomeds_database"
    private val markerPath = "$dbPath.schema"

    actual fun storedVersion(): Int? =
        NSString.stringWithContentsOfFile(markerPath, NSUTF8StringEncoding, null)?.trim()?.toIntOrNull()

    actual fun recordVersion(version: Int) {
        NSString.create(string = version.toString())
            .writeToFile(markerPath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    actual fun wipeDatabase() {
        val files = NSFileManager.defaultManager
        listOf("", "-wal", "-shm", "-journal").forEach { files.removeItemAtPath(dbPath + it, null) }
        files.removeItemAtPath(markerPath, null)
    }
}
