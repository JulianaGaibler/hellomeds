// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import me.juliana.hellomeds.data.util.AppLogger
import kotlinx.coroutines.sync.Mutex
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.model.NotificationSession
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.SessionType
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.repository.MedicationHistoryRepository
import me.juliana.hellomeds.data.repository.StockTrackingRepository
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.TimeProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles all notification actions (Taken, Skipped, Snoozed).
 * Called directly from NotificationActionReceiver via goAsync() + coroutine.
 *
 * Key behaviors:
 * - TAKEN/SKIPPED: marks events, then immediately updates or dismisses the notification
 * - For COMBINED sessions: rebuilds notification with remaining meds (silent update)
 * - For INDIVIDUAL sessions: dismisses the specific notification
 * - SNOOZED: snoozes all meds at the time slot, cancels sibling notifications
 *
 * Uses Mutex-based deduplication to prevent duplicate actions from rapid taps.
 */
class NotificationActionHandler(
    private val projector: ScheduleProjector,
    private val historyRepo: MedicationHistoryRepository,
    private val reconciler: AlarmReconciler,
    private val sessionManager: NotificationSessionManager,
    private val notifPrefs: NotificationPreferences,
    private val medicationDao: MedicationDao,
    private val notifBuilder: NotificationBuilder,
    private val stockTrackingRepository: StockTrackingRepository,
    private val timeProvider: TimeProvider,
) {

    private val activeLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun processAction(
        context: Context,
        action: String,
        scheduleIds: List<Int>,
        scheduledTime: Long,
        notificationId: Int,
    ) {
        val workKey = "${action}_${scheduleIds.hashCode()}_$scheduledTime"
        // computeIfAbsent is atomic on ConcurrentHashMap; the default getOrPut
        // is (get → put) and lets two concurrent arrivals each create-and-store
        // distinct Mutex instances, so both tryLock() succeed for the same key.
        val mutex = activeLocks.computeIfAbsent(workKey) { Mutex() }

        if (!mutex.tryLock()) {
            Log.d(TAG, "Duplicate action for $workKey, dropping")
            return
        }

        try {
            Log.d(
                TAG,
                "Processing $action action for ${scheduleIds.size} schedules at time $scheduledTime",
            )

            // Get the projected events for these schedules at this time
            val tolerance = 60_000L
            val events = projector.projectEvents(scheduledTime - tolerance, scheduledTime + tolerance)
            val matchingEvents = events.filter { event ->
                event.scheduleId in scheduleIds &&
                    kotlin.math.abs(event.scheduledTime - scheduledTime) < tolerance
            }

            when (action) {
                "TAKEN" -> {
                    handleTaken(matchingEvents)
                    handlePostAction(context, scheduledTime, scheduleIds, notificationId)
                }

                "SKIPPED" -> {
                    handleSkipped(matchingEvents)
                    handlePostAction(context, scheduledTime, scheduleIds, notificationId)
                }

                "SNOOZED" -> {
                    handleSnoozeAction(context, scheduledTime, scheduleIds)
                }

                else -> {
                    AppLogger.e(TAG, "Unknown action: $action")
                    return
                }
            }

            // Reconcile to update alarm for next event/follow-up
            reconciler.reconcile()
        } finally {
            // Remove BEFORE unlock so a fresh arrival in the unlock-to-remove
            // window can't tryLock our now-unlocked Mutex. The two-arg
            // remove(key, value) only deletes if the entry still maps to OUR
            // Mutex (defends against a parallel cleanup beating us to it).
            activeLocks.remove(workKey, mutex)
            mutex.unlock()
        }
    }

    private suspend fun handleTaken(events: List<ProjectedEvent>) {
        val now = timeProvider.nowMillis()
        events.forEach { event ->
            if (event.isPending) {
                historyRepo.markAsTaken(event, event.dose, now)
                Log.d(TAG, "Marked event schedule=${event.scheduleId} as taken")
            }
        }
    }

    private suspend fun handleSkipped(events: List<ProjectedEvent>) {
        events.forEach { event ->
            if (event.isPending) {
                historyRepo.markAsSkipped(event)
                Log.d(TAG, "Marked event schedule=${event.scheduleId} as skipped")
            }
        }
    }

    /**
     * After take/skip, update or dismiss the notification based on session type.
     *
     * COMBINED: if remaining meds exist, silently rebuild the notification. If none remain, dismiss.
     * INDIVIDUAL: if this specific med is completed, dismiss its notification and remove its session.
     * No session: dismiss notification if all meds at this time are done.
     */
    private suspend fun handlePostAction(
        context: Context,
        scheduledTime: Long,
        actionScheduleIds: List<Int>,
        notificationId: Int,
    ) {
        val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val lockScreenVisibility = notifPrefs.lockScreenVisibility.first()

        // Find the session for the acted-upon schedule
        val actedScheduleId = actionScheduleIds.first()
        val session = sessionManager.getSessionForSchedule(scheduledTime, actedScheduleId)

        // Multi-schedule action from group summary ("Take All"/"Skip All"):
        // After the COMBINED→INDIVIDUAL split, the summary has "Take All" buttons carrying ALL
        // scheduleIds. If we only process the first scheduleId's session, the other siblings'
        // sessions and notifications would be orphaned until their follow-up chain breaker fires.
        // Route to handlePostActionGroup() to clean up all children + summary in one pass.
        if (actionScheduleIds.size > 1 && (session == null || session.sessionType == SessionType.INDIVIDUAL)) {
            handlePostActionGroup(scheduledTime, actionScheduleIds, notifMgr)
            return
        }

        if (session == null) {
            // No session (no follow-ups configured) — just dismiss if all done
            val remaining = projector.getPendingEventsAtTime(scheduledTime)
            if (remaining.isEmpty() && notificationId != -1) {
                notifMgr.cancel(notificationId)
            }
            // Cancel group summary if 0-1 pending remain for this time slot
            if (remaining.size <= 1) {
                cancelGroupSummary(scheduledTime, notifMgr)
            }
            return
        }

        when (session.sessionType) {
            SessionType.COMBINED -> handlePostActionCombined(
                session,
                scheduledTime,
                notifMgr,
                lockScreenVisibility,
            )

            SessionType.INDIVIDUAL -> handlePostActionIndividual(
                session,
                scheduledTime,
                notifMgr,
            )
        }
    }

    /**
     * After take/skip on a COMBINED session:
     * - If remaining meds: silently rebuild and re-post the notification
     * - If none remain: chain breaker (remove session, cancel notification)
     */
    private suspend fun handlePostActionCombined(
        session: NotificationSession,
        scheduledTime: Long,
        notifMgr: NotificationManager,
        lockScreenVisibility: LockScreenVisibility,
    ) {
        val remainingEvents = projector.getPendingEventsAtTime(scheduledTime)
            .filter { it.scheduleId in session.scheduleIds }

        if (remainingEvents.isEmpty()) {
            // All done — chain breaker
            sessionManager.removeSession(session.timeSlotKey)
            notifMgr.cancel(session.notificationId)
            Log.i(TAG, "Chain breaker: all meds completed for combined session ${session.timeSlotKey}")
            return
        }

        // Rebuild notification with remaining meds (silent update — same notification ID + setOnlyAlertOnce)
        val medications = remainingEvents.mapNotNull { medicationDao.getByIdSync(it.medicationId) }
        if (medications.isEmpty()) {
            sessionManager.removeSession(session.timeSlotKey)
            notifMgr.cancel(session.notificationId)
            return
        }

        val notification = notifBuilder.buildNotification(
            events = remainingEvents,
            medications = medications,
            channelId = session.channelId,
            notificationId = session.notificationId,
            scheduledTime = scheduledTime,
            lockScreenVisibility = lockScreenVisibility,
            isFollowUp = false,
            isSnoozed = false,
            groupKey = "hellomeds_$scheduledTime",
        )
        notifMgr.notify(session.notificationId, notification)
        Log.d(TAG, "Updated combined notification: ${medications.size} meds remaining")
    }

    /**
     * After take/skip on an INDIVIDUAL session:
     * - If this specific med is completed, remove its session and dismiss its notification
     */
    private suspend fun handlePostActionIndividual(
        session: NotificationSession,
        scheduledTime: Long,
        notifMgr: NotificationManager,
    ) {
        val scheduleId = session.scheduleId ?: session.scheduleIds.firstOrNull() ?: return

        val events = projector.getPendingEventsAtTime(scheduledTime)
        val stillPending = events.any { it.scheduleId == scheduleId }

        if (!stillPending) {
            sessionManager.removeSession(session.timeSlotKey)
            notifMgr.cancel(session.notificationId)
            Log.d(TAG, "Individual session ${session.timeSlotKey} completed and dismissed")

            // Cancel group summary if 0-1 pending events remain for this time slot
            if (events.size <= 1) {
                cancelGroupSummary(scheduledTime, notifMgr)
            }
        }
    }

    /**
     * After "Take All"/"Skip All" from the group summary (post-split or GROUPED mode):
     * Clean up all individual sessions and notifications for the time slot.
     *
     * Stale action guard: The summary's PendingIntent carries scheduleIds from when it was
     * built. If the user resolved a med in-app but the notification shade is stale, tapping
     * "Take All" would send already-resolved IDs. We cross-reference against the projector
     * to filter out stale IDs before processing.
     */
    private suspend fun handlePostActionGroup(
        scheduledTime: Long,
        actionScheduleIds: List<Int>,
        notifMgr: NotificationManager,
    ) {
        cancelGroupSummary(scheduledTime, notifMgr)

        // Stale action guard: only process schedule IDs that are still pending
        val pendingEvents = projector.getPendingEventsAtTime(scheduledTime)
        val stillPendingIds = pendingEvents.map { it.scheduleId }.toSet()

        for (scheduleId in actionScheduleIds) {
            // Remove session if one exists
            val session = sessionManager.getSessionForSchedule(scheduledTime, scheduleId)
            if (session != null) {
                notifMgr.cancel(session.notificationId)
                sessionManager.removeSession(session.timeSlotKey)
            }

            // Also cancel the medication notification ID (covers session-less meds in GROUPED mode)
            val medNotifId =
                NotificationIdGenerator.generateMedicationNotificationId(scheduledTime, scheduleId)
            notifMgr.cancel(medNotifId)
        }

        // Log only the IDs that were actually still pending (stale ones were silently skipped)
        val staleCount = actionScheduleIds.count { it !in stillPendingIds }
        if (staleCount > 0) {
            Log.d(TAG, "handlePostActionGroup: $staleCount stale schedule IDs filtered out")
        }
        Log.d(
            TAG,
            "handlePostActionGroup: cleaned up ${actionScheduleIds.size} notifications at $scheduledTime",
        )
    }

    /**
     * Cancel the group summary notification for a time slot.
     */
    private fun cancelGroupSummary(scheduledTime: Long, notifMgr: NotificationManager) {
        val summaryId = NotificationIdGenerator.generateSessionNotificationId(scheduledTime)
        notifMgr.cancel(summaryId)
        Log.d(TAG, "Cancelled group summary: notifId=$summaryId")
    }

    /**
     * Handle snooze action: find the correct session and snooze all siblings.
     * For INDIVIDUAL sessions, also cancel all sibling notification IDs and the group summary.
     */
    private suspend fun handleSnoozeAction(context: Context, scheduledTime: Long, scheduleIds: List<Int>) {
        val snoozeMinutes = notifPrefs.snoozeIntervalMinutes.first()
        val snoozeUntil = timeProvider.nowMillis() + (snoozeMinutes * 60 * 1000L)

        // Find the session — could be combined (timeSlotKey = scheduledTime) or individual
        val session = sessionManager.getSession(scheduledTime.toString())
            ?: sessionManager.getSessionForSchedule(scheduledTime, scheduleIds.first())

        if (session != null) {
            // snoozeSession already propagates to siblings for INDIVIDUAL sessions
            sessionManager.snoozeSession(session.timeSlotKey, snoozeUntil)

            // Cancel sibling notification IDs and group summary for individual sessions
            val parentKey = session.parentTimeSlotKey
            if (session.sessionType == SessionType.INDIVIDUAL && parentKey != null) {
                val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val siblings = sessionManager.getSessionsByParent(parentKey)
                for (sibling in siblings) {
                    notifMgr.cancel(sibling.notificationId)
                }
                // Cancel the group summary
                val summaryId = NotificationIdGenerator.generateSessionNotificationId(
                    parentKey.toLongOrNull() ?: scheduledTime,
                )
                notifMgr.cancel(summaryId)
            }
        } else {
            // No session found — just log (notification was already cancelled by NotificationActionReceiver)
            AppLogger.w(TAG, "No session found for snooze at $scheduledTime")
        }

        Log.d(TAG, "Snoozed for $snoozeMinutes minutes (until $snoozeUntil)")
    }

    /**
     * Handle "Mark Depleted" action from a depletion reminder notification.
     * Records container depletion, which resets the alert flag and triggers notifiers.
     */
    suspend fun processDepletionAction(medicationId: Int, notificationId: Int) {
        Log.d(TAG, "Processing CONTAINER_DEPLETED for medicationId=$medicationId")
        stockTrackingRepository.recordContainerDepleted(medicationId)
        reconciler.reconcile()
    }

    companion object {
        private const val TAG = "NotificationActionHandler"
    }
}
