// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

import androidx.room.RoomDatabase
import me.juliana.hellomeds.data.database.AppDatabase

/**
 * Configures the Room database builder with platform-specific encrypted driver.
 * - Android: uses openHelperFactory with SQLCipher's SupportOpenHelperFactory
 * - iOS: uses setDriver with a NativeSQLiteDriver decorator that keys via PRAGMA
 */
expect fun configureEncryptedDriver(
    builder: RoomDatabase.Builder<AppDatabase>,
    keyManager: DatabaseKeyManager,
): RoomDatabase.Builder<AppDatabase>
