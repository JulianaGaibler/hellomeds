// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.util.Log
import me.juliana.hellomeds.data.dao.NotificationSessionDao
import me.juliana.hellomeds.data.database.entities.NotificationSessionEntity
import me.juliana.hellomeds.data.model.IndividualSessionConfig
import me.juliana.hellomeds.data.model.NotificationSession
import me.juliana.hellomeds.data.model.SessionType
import me.juliana.hellomeds.data.util.TimeProvider

/**
 * Room-backed manager for active notification follow-up sessions.
 *
 * Tracks which notifications are active and when their next follow-up should fire.
 * The AlarmReconciler checks these sessions when computing the next wakeup time.
 * When all meds in a session are taken/skipped, the session is removed (chain breaker).
 *
 * Replaces the previous DataStore JSON blob with transactional Room storage,
 * eliminating read-modify-write race conditions.
 */
class NotificationSessionManager(
    private val dao: NotificationSessionDao,
    private val timeProvider: TimeProvider,
) {

    companion object {
        private const val TAG = "SessionManager"
    }

    suspend fun createSession(session: NotificationSession) {
        dao.insert(NotificationSessionEntity.fromDomain(session))
        Log.d(
            TAG,
            "Created session: key=${session.timeSlotKey}, schedules=${session.scheduleIds}, maxFollowUps=${session.maxFollowUps}",
        )
    }

    suspend fun getSession(timeSlotKey: String): NotificationSession? {
        return dao.getByKey(timeSlotKey)?.toDomain()
    }

    suspend fun getAllSessions(): List<NotificationSession> {
        return dao.getAll().map { it.toDomain() }
    }

    suspend fun getDueFollowUps(now: Long): List<NotificationSession> {
        return dao.getDueFollowUps(now).map { it.toDomain() }
    }

    suspend fun getDueSnoozes(now: Long): List<NotificationSession> {
        return dao.getDueSnoozes(now).map { it.toDomain() }
    }

    /**
     * Mark a follow-up as fired for a session. Increment, next-time computation,
     * and channel escalation are all handled atomically in a single SQL UPDATE.
     * Returns the updated session, or null if session not found.
     */
    suspend fun updateFollowUpFired(timeSlotKey: String): NotificationSession? {
        val original = dao.getByKey(timeSlotKey) ?: return null
        val criticalEscalatedId = NotificationIdGenerator.generateEscalatedNotificationId(original.notificationId)
        val alarmEscalatedId = NotificationIdGenerator.generateAlarmEscalatedNotificationId(original.notificationId)
        dao.updateFollowUpFired(
            timeSlotKey,
            timeProvider.nowMillis(),
            NotificationChannels.CRITICAL_CHANNEL_ID,
            NotificationChannels.ALARM_CHANNEL_ID,
            criticalEscalatedId,
            alarmEscalatedId,
        )
        val updated = dao.getByKey(timeSlotKey) ?: return null
        Log.d(
            TAG,
            "Follow-up fired: key=$timeSlotKey, fired=${updated.followUpsFired}/${updated.maxFollowUps}, channel=${updated.channelId}, notifId=${updated.notificationId}",
        )
        return updated.toDomain()
    }

    suspend fun removeSession(timeSlotKey: String) {
        dao.deleteByKey(timeSlotKey)
        Log.d(TAG, "Removed session: key=$timeSlotKey")
    }

    /**
     * Remove a specific schedule ID from all sessions.
     * If a session becomes empty, remove it entirely.
     */
    suspend fun removeScheduleFromSessions(scheduleId: Int) {
        dao.removeScheduleFromAll(scheduleId)
    }

    /**
     * Split a combined session into individual per-medication sessions.
     * Called at the first follow-up for COMBINED sessions.
     *
     * Removes the combined session and creates one INDIVIDUAL session per config.
     * Each individual session inherits the follow-up count from the combined session.
     *
     * @param combinedKey The timeSlotKey of the combined session to split
     * @param perMedConfigs Per-medication follow-up configurations
     * @param inheritedFollowUpsFired Follow-up count to carry over from the combined session
     * @return List of newly created individual sessions
     */
    suspend fun splitCombinedSession(
        combinedKey: String,
        perMedConfigs: List<IndividualSessionConfig>,
        inheritedFollowUpsFired: Int,
    ): List<NotificationSession> {
        val combined = dao.getByKey(combinedKey) ?: return emptyList()

        val individuals = perMedConfigs.map { config ->
            NotificationSessionEntity(
                timeSlotKey = "${combinedKey}_${config.scheduleId}",
                scheduleIds = listOf(config.scheduleId),
                notificationId = config.notificationId,
                followUpsFired = inheritedFollowUpsFired,
                maxFollowUps = config.maxFollowUps,
                followUpIntervalMs = config.followUpIntervalMs,
                nextFollowUpTime = config.nextFollowUpTime,
                channelId = config.channelId,
                hasCriticalMed = config.hasCriticalMed,
                criticalAfterFollowUp = config.criticalAfterFollowUp,
                alarmAfterFollowUp = config.alarmAfterFollowUp,
                createdAt = combined.createdAt,
                sessionType = SessionType.INDIVIDUAL,
                parentTimeSlotKey = combinedKey,
                scheduleId = config.scheduleId,
            )
        }

        dao.splitCombined(combinedKey, individuals)
        Log.d(TAG, "Split combined session $combinedKey into ${individuals.size} individual sessions")
        return individuals.map { it.toDomain() }
    }

    /**
     * Find all individual sessions that were split from a given combined session.
     * Used for snooze-all behavior (snoozing one sibling snoozes all).
     */
    suspend fun getSessionsByParent(parentTimeSlotKey: String): List<NotificationSession> {
        return dao.getByParent(parentTimeSlotKey).map { it.toDomain() }
    }

    /**
     * Find the session containing a given scheduleId at a given scheduledTime.
     * Checks individual sessions first (more specific), then combined sessions.
     */
    suspend fun getSessionForSchedule(scheduledTime: Long, scheduleId: Int): NotificationSession? {
        val timeKey = scheduledTime.toString()

        // Check individual session first
        dao.getByKey("${timeKey}_$scheduleId")?.let { return it.toDomain() }

        // Check combined session
        dao.getByKey(timeKey)?.let { combined ->
            if (scheduleId in combined.scheduleIds) return combined.toDomain()
        }

        return null
    }

    /**
     * Snooze a session: sets snoozeUntilTime independently of nextFollowUpTime.
     * Follow-up chain continues on its own schedule; snooze is a separate timer.
     * The reconciler picks min(nextFollowUpTime, snoozeUntilTime) as the wakeup.
     *
     * If the session is INDIVIDUAL, all sibling sessions (same parentTimeSlotKey)
     * are also snoozed via a sub-select query — no read needed.
     */
    suspend fun snoozeSession(timeSlotKey: String, snoozeUntilTime: Long) {
        dao.snoozeSessionAndSiblings(timeSlotKey, snoozeUntilTime)
        Log.d(TAG, "Snoozed session(s) from key=$timeSlotKey, until=$snoozeUntilTime")
    }

    /**
     * Clear snooze state when a snoozed alarm fires.
     * Does NOT touch follow-up chain — they are independent systems.
     * Returns the updated session, or null if not found.
     */
    suspend fun handleSnoozeFired(timeSlotKey: String): NotificationSession? {
        dao.clearSnooze(timeSlotKey)
        Log.d(TAG, "Snooze fired: key=$timeSlotKey, cleared snooze state")
        return dao.getByKey(timeSlotKey)?.toDomain()
    }

    /**
     * Remove sessions older than maxAgeMs (default 48 hours).
     */
    suspend fun clearStaleSessions(maxAgeMs: Long = 48 * 60 * 60 * 1000L) {
        val cutoff = timeProvider.nowMillis() - maxAgeMs
        val staleCount = dao.countStale(cutoff)
        if (staleCount > 0) {
            dao.deleteStale(cutoff)
            Log.d(TAG, "Cleared $staleCount stale sessions")
        }
    }
}
