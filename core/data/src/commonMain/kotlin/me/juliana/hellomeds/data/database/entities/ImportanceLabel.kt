// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "importance_labels")
data class ImportanceLabel(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val shouldRemind: Boolean,
    val isCritical: Boolean,
    val isAlarm: Boolean = false,
    val hasFollowUps: Boolean,
    val followUpCount: Int = 0,
    val followUpIntervalMinutes: Int = 0,
    val criticalAfterFollowUp: Int? = null,
    val alarmAfterFollowUp: Int? = null,
    val defaultType: String? = null,
) {
    val isDefault: Boolean get() = defaultType != null
}
