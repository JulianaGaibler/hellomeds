// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model

import kotlinx.serialization.Serializable
import me.juliana.hellomeds.data.util.currentTimeMillis

@Serializable
enum class SessionType {
    /** Initial combined notification (multiple scheduleIds, one notification) */
    COMBINED,

    /** Per-medication notification (single scheduleId, own notification ID) */
    INDIVIDUAL,
}

/**
 * Tracks an active notification follow-up session.
 *
 * COMBINED sessions group all meds at one time slot into a single notification.
 * INDIVIDUAL sessions track a single medication with its own notification.
 *
 * At the first follow-up, a COMBINED session is split into INDIVIDUAL sessions
 * (one per remaining pending medication) so that follow-ups are always per-medication.
 */
@Serializable
data class NotificationSession(
    val timeSlotKey: String,
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
    val createdAt: Long = currentTimeMillis(),
    val sessionType: SessionType = SessionType.COMBINED,
    /** For INDIVIDUAL sessions: the original scheduledTime string (used to find siblings) */
    val parentTimeSlotKey: String? = null,
    /** For INDIVIDUAL sessions: the specific schedule this tracks */
    val scheduleId: Int? = null,
)

/**
 * Configuration for creating an individual session during combined→individual split.
 */
data class IndividualSessionConfig(
    val scheduleId: Int,
    val notificationId: Int,
    val maxFollowUps: Int,
    val followUpIntervalMs: Long,
    val nextFollowUpTime: Long?,
    val channelId: String,
    val hasCriticalMed: Boolean,
    val criticalAfterFollowUp: Int?,
    val alarmAfterFollowUp: Int? = null,
)
