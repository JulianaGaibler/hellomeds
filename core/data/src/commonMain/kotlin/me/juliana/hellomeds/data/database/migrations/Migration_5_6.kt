// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds `schedules.resolveFrom` for `UnloggedDoseResolver`.
 *
 * The SQL `DEFAULT 0` is required by `ADD COLUMN ... NOT NULL` and must match the entity's
 * `@ColumnInfo(defaultValue = "0")` or Room's schema validation fails.
 *
 * Existing rows backfill to the schedule's creation, but never earlier than its first
 * surviving history row. Older history may have been deleted, and resolving it would turn
 * doses that were really taken into `AUTO_SKIPPED`.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE schedules ADD COLUMN resolveFrom INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            """
            UPDATE schedules SET resolveFrom = MAX(
                createdAt,
                COALESCE(
                    (SELECT MIN(h.scheduledTime) FROM medication_history h WHERE h.scheduleId = schedules.id),
                    createdAt
                )
            )
            """.trimIndent(),
        )
    }
}
