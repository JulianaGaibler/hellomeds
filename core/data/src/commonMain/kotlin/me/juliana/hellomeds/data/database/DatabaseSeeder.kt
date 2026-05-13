// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database

import kotlinx.coroutines.flow.first
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.database.entities.ImportanceLabel

/**
 * The 5 built-in importance labels. These cannot be deleted by the user.
 * Users can rename and edit them, and reset them to these default values.
 */
enum class DefaultLabelType(
    val defaultType: String,
    val defaultName: String,
    val shouldRemind: Boolean,
    val isCritical: Boolean,
    val isAlarm: Boolean = false,
    val hasFollowUps: Boolean,
    val followUpCount: Int = 0,
    val followUpIntervalMinutes: Int = 0,
    val criticalAfterFollowUp: Int? = null,
    val alarmAfterFollowUp: Int? = null,
) {
    SILENT("SILENT", "Silent", shouldRemind = false, isCritical = false, hasFollowUps = false),
    ONCE("ONCE", "Once", shouldRemind = true, isCritical = false, hasFollowUps = false),
    FOLLOW_UPS(
        "FOLLOW_UPS",
        "Follow ups",
        shouldRemind = true,
        isCritical = false,
        hasFollowUps = true,
        followUpCount = 3,
        followUpIntervalMinutes = 20,
    ),
    CRITICAL_FOLLOW_UPS(
        "CRITICAL_FOLLOW_UPS",
        "Critical follow ups",
        shouldRemind = true,
        isCritical = false,
        hasFollowUps = true,
        followUpCount = 3,
        followUpIntervalMinutes = 20,
        criticalAfterFollowUp = 2,
    ),
    ALARM("ALARM", "Alarm", shouldRemind = true, isCritical = false, isAlarm = true, hasFollowUps = false),
    ;

    fun toLabel(existingId: Int = 0) = ImportanceLabel(
        id = existingId,
        name = defaultName,
        shouldRemind = shouldRemind,
        isCritical = isCritical,
        isAlarm = isAlarm,
        hasFollowUps = hasFollowUps,
        followUpCount = followUpCount,
        followUpIntervalMinutes = followUpIntervalMinutes,
        criticalAfterFollowUp = criticalAfterFollowUp,
        alarmAfterFollowUp = alarmAfterFollowUp,
        defaultType = defaultType,
    )
}

/**
 * Ensures all 5 default labels exist. Runs on every app launch.
 * Missing defaults are re-inserted. Existing ones (even if edited) are left alone.
 *
 * Uses a two-step lookup to prevent duplicates:
 * 1. Check by [ImportanceLabel.defaultType] (reliable identifier)
 * 2. Fallback: match by name for labels created before defaultType tracking — backfills
 *    the defaultType field so future checks work, without overriding user edits.
 */
suspend fun seedDefaultImportanceLabels(dao: ImportanceLabelDao) {
    val existingLabels = dao.getAll().first()

    for (type in DefaultLabelType.entries) {
        // Primary check: by defaultType (reliable identifier)
        val byType = existingLabels.find { it.defaultType == type.defaultType }
        if (byType != null) continue

        // Fallback: match by name for labels created before defaultType tracking
        val byName = existingLabels.find {
            it.name.equals(type.defaultName, ignoreCase = true) && it.defaultType == null
        }
        if (byName != null) {
            // Backfill defaultType so future checks work; preserve all other user edits
            dao.update(byName.copy(defaultType = type.defaultType))
            continue
        }

        // Truly missing — insert
        dao.insert(type.toLabel())
    }
}
