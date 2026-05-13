// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

import androidx.room.RoomDatabase
import me.juliana.hellomeds.data.database.AppDatabase

actual fun configureEncryptedDriver(
    builder: RoomDatabase.Builder<AppDatabase>,
    keyManager: DatabaseKeyManager,
): RoomDatabase.Builder<AppDatabase> {
    val key = keyManager.getOrCreateKey()
        ?: throw IllegalStateException(
            "Database encryption key unavailable. Device may need to be unlocked after reboot.",
        )
    return builder.setDriver(IOSEncryptedSQLiteDriver(key))
}
