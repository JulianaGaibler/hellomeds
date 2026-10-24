// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds `medications.unloggedDoseAction`. The SQL `DEFAULT 'SKIP'` must match the entity's
 * `@ColumnInfo(defaultValue = "SKIP")` or Room's schema validation fails.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE medications ADD COLUMN unloggedDoseAction TEXT NOT NULL DEFAULT 'SKIP'")
    }
}
