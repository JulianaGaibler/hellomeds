// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.NativeSQLiteDriver

/**
 * SQLiteDriver decorator that keys every connection via PRAGMA key for SQLCipher.
 *
 * Wraps NativeSQLiteDriver (from androidx.sqlite:sqlite-framework).
 * When SQLCipher is linked via SPM in the Xcode project, NativeSQLiteDriver
 * automatically uses SQLCipher's sqlite3 symbols. PRAGMA key activates encryption.
 *
 * Every connection from Room's pool is keyed before being returned, preventing
 * any unkeyed access that would fail with "file is not a database".
 */
class IOSEncryptedSQLiteDriver(private val key: ByteArray) : SQLiteDriver {

    private val delegate = NativeSQLiteDriver()
    private val hexKey: String = key.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    init {
        check(hexKey.length == 64) { "Invalid key length: expected 64 hex chars, got ${hexKey.length}" }
    }

    override fun open(fileName: String): SQLiteConnection {
        val connection = delegate.open(fileName)

        // PRAGMA key MUST be the absolute first statement on every connection
        connection.prepare("PRAGMA key = \"x'$hexKey'\";").use { it.step() }

        // Set WAL mode explicitly for Room compatibility
        connection.prepare("PRAGMA journal_mode = WAL;").use { it.step() }

        // Fail-fast: verify SQLCipher is actually active
        // Standard SQLite ignores unknown pragmas silently, so this catches
        // the case where system sqlite3 is linked instead of SQLCipher
        val cipherVersion = connection.prepare("PRAGMA cipher_version;").use {
            if (it.step()) it.getText(0) else null
        }
        check(!cipherVersion.isNullOrBlank()) {
            "SQLCipher is NOT active! Database is not encrypted. " +
                "Ensure SQLCipher is linked via SPM and system libsqlite3 is not linked."
        }

        // Verify we can actually read the database
        connection.prepare("SELECT count(*) FROM sqlite_master;").use { it.step() }

        return connection
    }
}
