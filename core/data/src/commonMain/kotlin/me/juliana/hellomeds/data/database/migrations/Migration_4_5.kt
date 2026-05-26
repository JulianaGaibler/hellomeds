// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v4 → v5: adds two columns to `medications` for the bubble-preview layout customization feature.
 *
 * - `bubbleManualLayout` (TEXT, nullable): packed CSV `"{cols},{spacer1},..."` when the user picks
 *   a manual grid; `NULL` means automatic. Decoded by `BubbleLayoutCodec` on read.
 * - `bubbleFlowDirection` (TEXT, NOT NULL): consumption order enum. Defaults to `LTR_TOP_BOTTOM`
 *   to backfill existing rows without a per-row UPDATE. The matching
 *   `@ColumnInfo(defaultValue = "LTR_TOP_BOTTOM")` on the entity keeps Room's schema validator happy.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE medications ADD COLUMN bubbleManualLayout TEXT")
        connection.execSQL(
            "ALTER TABLE medications ADD COLUMN bubbleFlowDirection TEXT NOT NULL DEFAULT 'LTR_TOP_BOTTOM'",
        )
    }
}
