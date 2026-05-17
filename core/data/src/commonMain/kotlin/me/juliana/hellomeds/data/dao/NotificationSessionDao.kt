// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import me.juliana.hellomeds.data.database.entities.NotificationSessionEntity

/**
 * DAO for notification session storage. Replaces the DataStore JSON blob
 * with atomic SQL operations — no more read-modify-write races.
 *
 * Compound operations use @Transaction to ensure consistency.
 * Simple updates (snooze, follow-up state) are single SQL statements
 * and therefore inherently atomic.
 */
@Dao
abstract class NotificationSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(session: NotificationSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(sessions: List<NotificationSessionEntity>)

    @Query("SELECT * FROM notification_sessions WHERE timeSlotKey = :key")
    abstract suspend fun getByKey(key: String): NotificationSessionEntity?

    @Query("SELECT * FROM notification_sessions")
    abstract suspend fun getAll(): List<NotificationSessionEntity>

    @Query("DELETE FROM notification_sessions WHERE timeSlotKey = :key")
    abstract suspend fun deleteByKey(key: String)

    @Query("SELECT * FROM notification_sessions WHERE parentTimeSlotKey = :parentKey")
    abstract suspend fun getByParent(parentKey: String): List<NotificationSessionEntity>

    // channelId escalation: criticalAfterFollowUp IS NOT NULL is the sole gate.
    // Meds that are critical from the start already have their platform channel ID set at
    // session creation (preserved by ELSE channelId). hasCriticalMed is NOT used as a gate
    // here — it's a separate flag for AlarmReconciler (setAlarmClock vs setExactAndAllowWhileIdle).
    // hasCriticalMed is dynamically flipped to 1 alongside channelId escalation so
    // AlarmReconciler upgrades to setAlarmClock() only when the med actually becomes critical
    // (avoids premature system alarm icon in the status bar).
    //
    // The critical channel ID is passed as :criticalChannelId (not hardcoded) because this DAO
    // lives in the shared core/data module and must not reference platform-specific channel names.
    // The caller (NotificationSessionManager in androidApp/) passes the real Android channel ID.
    // Two-stage escalation: NORMAL → CRITICAL (at criticalAfterFollowUp) → ALARM (at alarmAfterFollowUp).
    // Alarm check comes first in CASE expressions since it has higher priority (higher threshold).
    // notificationId is only changed when the channel ACTUALLY changes (guards prevent re-assignment).
    @Query(
        """
    UPDATE notification_sessions
    SET followUpsFired = followUpsFired + 1,
        nextFollowUpTime = CASE
            WHEN (followUpsFired + 1) < maxFollowUps THEN :currentTimeMs + followUpIntervalMs
            ELSE NULL
        END,
        channelId = CASE
            WHEN alarmAfterFollowUp IS NOT NULL
                 AND (followUpsFired + 1) >= alarmAfterFollowUp
            THEN :alarmChannelId
            WHEN criticalAfterFollowUp IS NOT NULL
                 AND (followUpsFired + 1) >= criticalAfterFollowUp
            THEN :criticalChannelId
            ELSE channelId
        END,
        hasCriticalMed = CASE
            WHEN alarmAfterFollowUp IS NOT NULL
                 AND (followUpsFired + 1) >= alarmAfterFollowUp
            THEN 1
            WHEN criticalAfterFollowUp IS NOT NULL
                 AND (followUpsFired + 1) >= criticalAfterFollowUp
            THEN 1
            ELSE hasCriticalMed
        END,
        notificationId = CASE
            WHEN alarmAfterFollowUp IS NOT NULL
                 AND (followUpsFired + 1) >= alarmAfterFollowUp
                 AND channelId != :alarmChannelId
            THEN :alarmEscalatedNotificationId
            WHEN criticalAfterFollowUp IS NOT NULL
                 AND (followUpsFired + 1) >= criticalAfterFollowUp
                 AND channelId != :criticalChannelId
                 AND channelId != :alarmChannelId
            THEN :criticalEscalatedNotificationId
            ELSE notificationId
        END
    WHERE timeSlotKey = :timeSlotKey
  """,
    )
    abstract suspend fun updateFollowUpFired(
        timeSlotKey: String,
        currentTimeMs: Long,
        criticalChannelId: String,
        alarmChannelId: String,
        criticalEscalatedNotificationId: Int,
        alarmEscalatedNotificationId: Int,
    )

    @Query(
        """
    UPDATE notification_sessions
    SET snoozeUntilTime = :snoozeUntil, isSnoozed = 1
    WHERE timeSlotKey = :targetKey
    OR (
        sessionType = 'INDIVIDUAL'
        AND parentTimeSlotKey IS NOT NULL
        AND parentTimeSlotKey = (
            SELECT parentTimeSlotKey FROM notification_sessions
            WHERE timeSlotKey = :targetKey AND sessionType = 'INDIVIDUAL'
        )
    )
  """,
    )
    abstract suspend fun snoozeSessionAndSiblings(targetKey: String, snoozeUntil: Long)

    @Query("SELECT * FROM notification_sessions WHERE nextFollowUpTime IS NOT NULL AND nextFollowUpTime <= :now")
    abstract suspend fun getDueFollowUps(now: Long): List<NotificationSessionEntity>

    @Query(
        "SELECT * FROM notification_sessions WHERE isSnoozed = 1 AND snoozeUntilTime IS NOT NULL AND snoozeUntilTime <= :now",
    )
    abstract suspend fun getDueSnoozes(now: Long): List<NotificationSessionEntity>

    @Query("UPDATE notification_sessions SET snoozeUntilTime = NULL, isSnoozed = 0 WHERE timeSlotKey = :key")
    abstract suspend fun clearSnooze(key: String)

    @Query("DELETE FROM notification_sessions WHERE createdAt < :cutoff")
    abstract suspend fun deleteStale(cutoff: Long)

    @Query("SELECT COUNT(*) FROM notification_sessions WHERE createdAt < :cutoff")
    abstract suspend fun countStale(cutoff: Long): Int

    /**
     * Split a COMBINED session into INDIVIDUAL per-medication sessions.
     * Atomic: the combined session is removed and all individuals are inserted
     * in a single transaction — no partial state visible.
     */
    @Transaction
    open suspend fun splitCombined(combinedKey: String, individuals: List<NotificationSessionEntity>) {
        deleteByKey(combinedKey)
        insertAll(individuals)
    }

    /**
     * Remove a scheduleId from all sessions that contain it.
     * Sessions that become empty are deleted entirely.
     *
     * Rare operation (medication/schedule deletion), so the read-filter-write
     * inside a transaction is acceptable for the small row count.
     */
    @Transaction
    open suspend fun removeScheduleFromAll(scheduleId: Int) {
        val all = getAll()
        for (session in all) {
            if (scheduleId !in session.scheduleIds) continue
            val updatedIds = session.scheduleIds - scheduleId
            if (updatedIds.isEmpty()) {
                deleteByKey(session.timeSlotKey)
            } else {
                insert(session.copy(scheduleIds = updatedIds))
            }
        }
    }
}
