// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.juliana.hellomeds.data.model.NotificationSession
import me.juliana.hellomeds.data.model.SessionType

/**
 * Room entity for active notification follow-up sessions.
 * Replaces the DataStore JSON blob with transactional SQLite storage.
 *
 * Maps 1:1 from the [NotificationSession] domain model.
 */
@Entity(tableName = "notification_sessions")
data class NotificationSessionEntity(
    @PrimaryKey
    val timeSlotKey: String,
    /** Schedule IDs in this session. Stored as JSON via TypeConverter. */
    val scheduleIds: List<Int>,
    val notificationId: Int,
    val followUpsFired: Int = 0,
    val maxFollowUps: Int,
    val followUpIntervalMs: Long,
    val nextFollowUpTime: Long?,
    val channelId: String,
    val hasCriticalMed: Boolean,
    val criticalAfterFollowUp: Int?,
    val alarmAfterFollowUp: Int? = null,
    val snoozeUntilTime: Long? = null,
    val isSnoozed: Boolean = false,
    val createdAt: Long,
    val sessionType: SessionType = SessionType.COMBINED,
    /** For INDIVIDUAL sessions: the original combined session key (used to find siblings) */
    val parentTimeSlotKey: String? = null,
    /** For INDIVIDUAL sessions: the specific schedule this tracks */
    val scheduleId: Int? = null,
) {
    fun toDomain(): NotificationSession = NotificationSession(
        timeSlotKey = timeSlotKey,
        scheduleIds = scheduleIds,
        notificationId = notificationId,
        followUpsFired = followUpsFired,
        maxFollowUps = maxFollowUps,
        followUpIntervalMs = followUpIntervalMs,
        nextFollowUpTime = nextFollowUpTime,
        channelId = channelId,
        hasCriticalMed = hasCriticalMed,
        criticalAfterFollowUp = criticalAfterFollowUp,
        alarmAfterFollowUp = alarmAfterFollowUp,
        snoozeUntilTime = snoozeUntilTime,
        isSnoozed = isSnoozed,
        createdAt = createdAt,
        sessionType = sessionType,
        parentTimeSlotKey = parentTimeSlotKey,
        scheduleId = scheduleId,
    )

    companion object {
        fun fromDomain(session: NotificationSession) = NotificationSessionEntity(
            timeSlotKey = session.timeSlotKey,
            scheduleIds = session.scheduleIds,
            notificationId = session.notificationId,
            followUpsFired = session.followUpsFired,
            maxFollowUps = session.maxFollowUps,
            followUpIntervalMs = session.followUpIntervalMs,
            nextFollowUpTime = session.nextFollowUpTime,
            channelId = session.channelId,
            hasCriticalMed = session.hasCriticalMed,
            criticalAfterFollowUp = session.criticalAfterFollowUp,
            alarmAfterFollowUp = session.alarmAfterFollowUp,
            snoozeUntilTime = session.snoozeUntilTime,
            isSnoozed = session.isSnoozed,
            createdAt = session.createdAt,
            sessionType = session.sessionType,
            parentTimeSlotKey = session.parentTimeSlotKey,
            scheduleId = session.scheduleId,
        )
    }
}
