// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database

import android.content.Context
import java.io.File

actual class DatabaseSchemaGuard(context: Context) {
    private val dbFile = context.applicationContext.getDatabasePath(DATABASE_NAME)
    private val marker = File(dbFile.parentFile, "$DATABASE_NAME.schema")

    actual fun storedVersion(): Int? = runCatching { marker.readText().trim().toInt() }.getOrNull()

    actual fun recordVersion(version: Int) {
        marker.writeText(version.toString())
    }

    actual fun wipeDatabase() {
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        marker.delete()
    }
}
