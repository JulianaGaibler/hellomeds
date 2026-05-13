// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v3 → v4: adds a unique compound index on
 * `medication_history (medicationId, scheduleId, scheduledTime)` so duplicate
 * dose-log entries for the same scheduled occurrence become structurally impossible.
 *
 * Cleanup pass: any pre-existing duplicate rows are collapsed to the highest-id row
 * per group. `id` is autoincrement, so the row with the largest id is the most
 * recently inserted — the user's latest action wins.
 *
 * PRN ("as-needed") doses are unaffected: their scheduleId / scheduledTime are NULL,
 * so they fall outside both the cleanup filter and the unique constraint
 * (SQLite treats NULL values in UNIQUE indexes as distinct).
 *
 * StockAdjustment rows tied to deleted history rows are removed via the existing
 * `ON DELETE CASCADE` on `StockAdjustment.historyId`. This is correct: a duplicate
 * history row's stock entry was a double-count to begin with.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            DELETE FROM medication_history
            WHERE scheduleId IS NOT NULL
              AND scheduledTime IS NOT NULL
              AND id NOT IN (
                SELECT MAX(id) FROM medication_history
                WHERE scheduleId IS NOT NULL AND scheduledTime IS NOT NULL
                GROUP BY medicationId, scheduleId, scheduledTime
              )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_medication_history_dedup` " +
                "ON `medication_history` (`medicationId`, `scheduleId`, `scheduledTime`)",
        )
    }
}
