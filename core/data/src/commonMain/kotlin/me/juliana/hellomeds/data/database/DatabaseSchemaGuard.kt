// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database

const val DATABASE_SCHEMA_VERSION = 7

/**
 * Remembers the newest schema version that opened the database, in a plain file beside it.
 * Room cannot read the version of an encrypted database without opening it, and it would
 * otherwise fail or wipe the data when an older app build meets a newer database.
 */
expect class DatabaseSchemaGuard {
    fun storedVersion(): Int?
    fun recordVersion(version: Int)
    fun wipeDatabase()
}

fun DatabaseSchemaGuard.isDowngrade(): Boolean = (storedVersion() ?: 0) > DATABASE_SCHEMA_VERSION

class DatabaseDowngradeException : IllegalStateException("The database was created by a newer app version")
