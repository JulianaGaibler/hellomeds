// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlinx.coroutines.suspendCancellableCoroutine
import me.juliana.hellomeds.data.dao.NotificationSessionDao
import me.juliana.hellomeds.data.database.entities.NotificationSessionEntity
import me.juliana.hellomeds.data.model.NotificationSession
import me.juliana.hellomeds.data.util.AppLogger
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Clock

private const val TAG = "IOSSessionManager"

/**
 * iOS-specific wrapper around the shared [NotificationSessionDao].
 *
 * Adds iOS notification center interactions (cancelling pending/delivered notifications
 * by identifier) on top of the shared database operations.
 *
 * Mirrors Android's NotificationSessionManager but adapted for iOS's pre-scheduled
 * notification model where follow-ups are scheduled upfront and cancelled on user action.
 */
class IOSNotificationSessionManager(
    private val dao: NotificationSessionDao,
    private val clock: Clock = Clock.System,
) {

    suspend fun createSession(session: NotificationSession) {
        dao.insert(NotificationSessionEntity.fromDomain(session))
        AppLogger.d(TAG, "Created session: ${session.timeSlotKey} with ${session.scheduleIds.size} schedules")
    }

    suspend fun getSession(timeSlotKey: String): NotificationSession? {
        return dao.getByKey(timeSlotKey)?.toDomain()
    }

    suspend fun getAllSessions(): List<NotificationSession> {
        return dao.getAll().map { it.toDomain() }
    }

    suspend fun removeSession(timeSlotKey: String) {
        dao.deleteByKey(timeSlotKey)
        AppLogger.d(TAG, "Removed session: $timeSlotKey")
    }

    /**
     * Find the session that tracks a given scheduled time and schedule ID.
     * Handles both COMBINED sessions (multiple scheduleIds) and INDIVIDUAL sessions.
     */
    suspend fun getSessionForSchedule(scheduledTime: Long, scheduleId: Int): NotificationSession? {
        // Try direct key match first (slot time as key)
        val timeKey = scheduledTime.toString()
        val directMatch = dao.getByKey(timeKey)?.toDomain()
        if (directMatch != null && scheduleId in directMatch.scheduleIds) return directMatch

        // Search all sessions for one containing this scheduleId near this time
        return dao.getAll()
            .map { it.toDomain() }
            .find { session ->
                scheduleId in session.scheduleIds &&
                    kotlin.math.abs(
                        session.timeSlotKey.toLongOrNull()?.minus(scheduledTime) ?: Long.MAX_VALUE,
                    ) < 60_000L
            }
    }

    suspend fun snoozeSession(timeSlotKey: String, snoozeUntilTime: Long) {
        dao.snoozeSessionAndSiblings(timeSlotKey, snoozeUntilTime)
        AppLogger.d(TAG, "Snoozed session: $timeSlotKey until $snoozeUntilTime")
    }

    /**
     * Returns sessions whose snooze has expired (snoozeUntilTime <= now).
     * iOS equivalent of Android's getDueSnoozes used in GlobalAlarmReceiver.
     */
    suspend fun getDueSnoozes(now: Long): List<NotificationSession> {
        return dao.getDueSnoozes(now).map { it.toDomain() }
    }

    /**
     * Clears snooze state for a session, allowing it to be rescheduled normally.
     */
    suspend fun clearSnooze(timeSlotKey: String) {
        dao.clearSnooze(timeSlotKey)
        AppLogger.d(TAG, "Cleared snooze for session: $timeSlotKey")
    }

    /**
     * Returns all sessions that are currently snoozed (snoozeUntilTime > now).
     */
    suspend fun getActiveSnoozedSessions(now: Long): List<NotificationSession> {
        return dao.getAll()
            .map { it.toDomain() }
            .filter { it.isSnoozed && (it.snoozeUntilTime ?: 0L) > now }
    }

    suspend fun clearStaleSessions(maxAgeMs: Long = 48 * 60 * 60 * 1000L) {
        val cutoff = clock.now().toEpochMilliseconds() - maxAgeMs
        val count = dao.countStale(cutoff)
        if (count > 0) {
            dao.deleteStale(cutoff)
            AppLogger.d(TAG, "Cleared $count stale sessions")
        }
    }

    /**
     * Cancel all pending follow-up notifications for a given time slot.
     * Identifies follow-ups by their identifier containing the slot time + "followup".
     */
    suspend fun cancelFollowUpsForSlot(slotTime: Long) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val slotStr = slotTime.toString()

        suspendCancellableCoroutine { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                @Suppress("UNCHECKED_CAST")
                val ids = (requests as? List<UNNotificationRequest>)
                    ?.filter { req ->
                        val id = req.identifier
                        id.contains(slotStr) && id.contains("followup")
                    }
                    ?.map { it.identifier }
                    ?: emptyList()

                if (ids.isNotEmpty()) {
                    center.removePendingNotificationRequestsWithIdentifiers(ids)
                    AppLogger.d(TAG, "Cancelled ${ids.size} pending follow-ups for slot $slotTime")
                }
                cont.resume(Unit)
            }
        }
    }

    /**
     * Remove delivered notifications (in Notification Center) for a given time slot.
     * Handles "ghost" notifications: notifications that fired before the user acted in-app.
     */
    suspend fun removeDeliveredForSlot(slotTime: Long) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val slotStr = slotTime.toString()

        suspendCancellableCoroutine { cont ->
            center.getDeliveredNotificationsWithCompletionHandler { delivered ->
                @Suppress("UNCHECKED_CAST")
                val ids = (delivered as? List<platform.UserNotifications.UNNotification>)
                    ?.filter { it.request.identifier.contains(slotStr) }
                    ?.map { it.request.identifier }
                    ?: emptyList()

                if (ids.isNotEmpty()) {
                    center.removeDeliveredNotificationsWithIdentifiers(ids)
                    AppLogger.d(TAG, "Removed ${ids.size} delivered notifications for slot $slotTime")
                }
                cont.resume(Unit)
            }
        }
    }

    /**
     * Cancel a specific snooze notification by time slot.
     */
    suspend fun cancelSnoozeForSlot(slotTime: Long) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val slotStr = slotTime.toString()

        suspendCancellableCoroutine { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                @Suppress("UNCHECKED_CAST")
                val ids = (requests as? List<UNNotificationRequest>)
                    ?.filter { it.identifier.startsWith("snooze_") && it.identifier.contains(slotStr) }
                    ?.map { it.identifier }
                    ?: emptyList()

                if (ids.isNotEmpty()) {
                    center.removePendingNotificationRequestsWithIdentifiers(ids)
                    AppLogger.d(TAG, "Cancelled ${ids.size} snooze notifications for slot $slotTime")
                }
                cont.resume(Unit)
            }
        }
    }
}
