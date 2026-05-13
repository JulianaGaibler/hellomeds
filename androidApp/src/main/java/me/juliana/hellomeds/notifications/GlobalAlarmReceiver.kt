// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.model.NotificationSession
import me.juliana.hellomeds.data.model.SessionType
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.util.TimeProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Single BroadcastReceiver for all alarm triggers.
 * JIT (just-in-time) notification building from database state.
 *
 * Uses the "Watermark" model: when the alarm fires, process ALL work due up to now,
 * not just events matching a narrow tolerance window. This guarantees correct behavior
 * even when Android delays alarms (Doze mode, battery optimization, system load).
 *
 * Processing order (sequential, not mutually exclusive):
 * 1. ALL follow-up sessions where nextFollowUpTime <= now
 * 2. ALL snooze sessions where snoozeUntilTime <= now (clearing follow-up overlaps)
 * 3. ALL new pending events in [now - 4h, now] without existing sessions
 *    — delegated to [MissedDoseProcessor] so the same code path runs from boot.
 * 4. Chain next alarm via reconciler.reconcile()
 *
 * Follow-up architecture:
 * - COMBINED sessions stay combined through the entire follow-up chain
 * - INDIVIDUAL sessions (from GROUPED mode) handle their own follow-up chains independently
 *
 * Notification grouping architecture:
 * - Individual notifications always carry a groupKey ("hellomeds_{scheduledTime}") so they
 *   can seamlessly join a group if a sibling arrives later (e.g., staggered snooze).
 *   Android treats a single notification with a group key as a normal standalone notification.
 * - A group summary notification (setGroupSummary=true) is posted only when 2+ children exist.
 * - Summary uses GROUP_ALERT_SUMMARY: it owns all sound/vibration/screen wake. Children are
 *   silently muted by the OS. This avoids Android's burst spam filter, which mutes simultaneous
 *   high-priority notification posts from the same app.
 * - Posting order is always summary FIRST, then children — ensures the summary's alert fires
 *   cleanly before children populate the group.
 * - Summary is always re-posted idempotently whenever 2+ children are posted (follow-up,
 *   snooze fire, etc.). Never assume the summary persists — prevents orphaned children that
 *   appear as a broken/uncollapsible group in the notification shade.
 */
class GlobalAlarmReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarm(context)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarm(context: Context) {
        val now = get<TimeProvider>().nowMillis()
        val powerManager = context.getSystemService(android.os.PowerManager::class.java)
        val deviceIdleMode = powerManager?.isDeviceIdleMode ?: false
        AppLogger.i(TAG, "handleAlarm: now=$now, deviceIdleMode=$deviceIdleMode")
        val reconciler: AlarmReconciler = get()
        val projector: ScheduleProjector = get()
        val sessionManager: NotificationSessionManager = get()
        val notifPrefs: NotificationPreferences = get()
        val medicationDao: MedicationDao = get()
        val importanceLabelDao: ImportanceLabelDao = get()
        val missedDoseProcessor: MissedDoseProcessor = get()

        val lockScreenVisibility = notifPrefs.lockScreenVisibility.first()

        AppLogger.d(TAG, "Watermark processing at now=$now")

        // 1. Process ALL due follow-ups (SQL WHERE nextFollowUpTime <= now)
        val dueFollowUps = sessionManager.getDueFollowUps(now)
        if (dueFollowUps.isNotEmpty()) {
            AppLogger.d(TAG, "Processing ${dueFollowUps.size} due follow-ups")
            handleFollowUps(
                context,
                dueFollowUps,
                projector,
                sessionManager,
                medicationDao,
                importanceLabelDao,
                missedDoseProcessor,
                lockScreenVisibility,
            )
        }

        // 2. Process ALL due snoozes, clearing overlaps with follow-ups.
        // Expand the "already handled by Step 1" set to include the parent slot
        // of any INDIVIDUAL follow-up. Without this, a snooze for a sibling-child
        // (or a COMBINED snooze at the same parent slot) drives handleSnoozes
        // into rebuilding the entire sibling group and re-posting the group
        // summary that Step 1 already posted with isFollowUpAlert=true — double
        // audible alert plus a race that can overwrite the freshly-escalated
        // child notification.
        val dueSnoozes = sessionManager.getDueSnoozes(now)
        val followUpKeys = buildSet {
            for (followUp in dueFollowUps) {
                add(followUp.timeSlotKey)
                followUp.parentTimeSlotKey?.let { add(it) }
            }
        }
        for (snooze in dueSnoozes) {
            val parentKey = snooze.parentTimeSlotKey ?: snooze.timeSlotKey
            if (snooze.timeSlotKey in followUpKeys || parentKey in followUpKeys) {
                sessionManager.handleSnoozeFired(snooze.timeSlotKey)
            }
        }
        val snoozesToProcess = dueSnoozes.filter { snooze ->
            val parentKey = snooze.parentTimeSlotKey ?: snooze.timeSlotKey
            snooze.timeSlotKey !in followUpKeys && parentKey !in followUpKeys
        }
        if (snoozesToProcess.isNotEmpty()) {
            AppLogger.d(TAG, "Processing ${snoozesToProcess.size} due snoozes")
            handleSnoozes(
                context,
                snoozesToProcess,
                projector,
                sessionManager,
                medicationDao,
                missedDoseProcessor,
                lockScreenVisibility,
            )
        }

        // 3. Catch-up: process new events in [now - 4h, now].
        //    Same path is also invoked from BootReceiver, so missed-during-reboot
        //    doses get a notification at first unlock instead of being silently auto-skipped.
        missedDoseProcessor.processMissedDoses(now)

        // 4. Chain next alarm
        reconciler.reconcile()
    }

    // --- Follow-up handling ---

    private suspend fun handleFollowUps(
        context: Context,
        sessions: List<NotificationSession>,
        projector: ScheduleProjector,
        sessionManager: NotificationSessionManager,
        medicationDao: MedicationDao,
        importanceLabelDao: ImportanceLabelDao,
        missedDoseProcessor: MissedDoseProcessor,
        lockScreenVisibility: LockScreenVisibility,
    ) {
        for (session in sessions) {
            when (session.sessionType) {
                SessionType.COMBINED -> handleCombinedFollowUp(
                    context,
                    session,
                    projector,
                    sessionManager,
                    medicationDao,
                    missedDoseProcessor,
                    lockScreenVisibility,
                )

                SessionType.INDIVIDUAL -> handleIndividualFollowUp(
                    context,
                    session,
                    projector,
                    sessionManager,
                    medicationDao,
                    importanceLabelDao,
                    missedDoseProcessor,
                    lockScreenVisibility,
                )
            }
        }
    }

    /**
     * Follow-up for a COMBINED session: re-posts the combined notification with all
     * remaining medications. The session stays COMBINED through the entire follow-up chain.
     */
    private suspend fun handleCombinedFollowUp(
        context: Context,
        session: NotificationSession,
        projector: ScheduleProjector,
        sessionManager: NotificationSessionManager,
        medicationDao: MedicationDao,
        missedDoseProcessor: MissedDoseProcessor,
        lockScreenVisibility: LockScreenVisibility,
    ) {
        AppLogger.d(
            TAG,
            "Combined follow-up for session: key=${session.timeSlotKey}, fired=${session.followUpsFired}/${session.maxFollowUps}",
        )

        val scheduledTime = session.timeSlotKey.toLongOrNull() ?: run {
            AppLogger.e(TAG, "Invalid timeSlotKey: ${session.timeSlotKey}")
            sessionManager.removeSession(session.timeSlotKey)
            return
        }

        val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Query remaining pending events (meds taken mid-chain are automatically filtered out)
        val events = projector.getPendingEventsAtTime(scheduledTime)
        val remainingEvents = events.filter { it.scheduleId in session.scheduleIds }

        if (remainingEvents.isEmpty()) {
            AppLogger.i(TAG, "Chain breaker: all meds in combined session ${session.timeSlotKey} are completed")
            sessionManager.removeSession(session.timeSlotKey)
            notifMgr.cancel(session.notificationId)
            return
        }

        // Atomic: increment count, compute next time, escalate channel, assign new ID if escalated
        val oldChannelId = session.channelId
        val oldNotificationId = session.notificationId
        val updatedSession = sessionManager.updateFollowUpFired(session.timeSlotKey) ?: return
        val channelEscalated = oldChannelId != updatedSession.channelId

        // Cancel OLD notification ID when channel escalated.
        // updateFollowUpFired assigns a new notification ID on escalation, so
        // cancel(oldId) and notify(newId) never race.
        if (channelEscalated) {
            notifMgr.cancel(oldNotificationId)
            AppLogger.i(
                TAG,
                "Channel escalated $oldChannelId → ${updatedSession.channelId}, old notifId=$oldNotificationId → new=${updatedSession.notificationId}",
            )
        }

        val medications = remainingEvents.mapNotNull { medicationDao.getByIdSync(it.medicationId) }

        missedDoseProcessor.showNotification(
            remainingEvents, medications,
            updatedSession.channelId, updatedSession.notificationId,
            scheduledTime, lockScreenVisibility, isFollowUp = true,
            groupKey = missedDoseProcessor.groupKeyForTimeSlot(scheduledTime),
            followUpNumber = updatedSession.followUpsFired,
        )

        AppLogger.i(
            TAG,
            "Combined follow-up #${updatedSession.followUpsFired} shown: ${medications.size} meds, channel=${updatedSession.channelId}",
        )
    }

    /**
     * Follow-up for an INDIVIDUAL session: re-posts a single medication's notification.
     *
     * Also re-posts the group summary idempotently for two reasons:
     * 1. GROUP_ALERT_SUMMARY means children are muted — the summary must re-alert with
     *    isFollowUpAlert=true to trigger the Heads-Up screen wake on behalf of the child.
     * 2. DnD escalation: if this follow-up triggers criticalAfterFollowUp, the summary must
     *    upgrade to the CRITICAL channel so the entire group breaks through Do Not Disturb.
     */
    private suspend fun handleIndividualFollowUp(
        context: Context,
        session: NotificationSession,
        projector: ScheduleProjector,
        sessionManager: NotificationSessionManager,
        medicationDao: MedicationDao,
        importanceLabelDao: ImportanceLabelDao,
        missedDoseProcessor: MissedDoseProcessor,
        lockScreenVisibility: LockScreenVisibility,
    ) {
        AppLogger.d(
            TAG,
            "Individual follow-up for session: key=${session.timeSlotKey}, fired=${session.followUpsFired}/${session.maxFollowUps}",
        )

        val scheduledTime = (session.parentTimeSlotKey ?: session.timeSlotKey.substringBefore("_"))
            .toLongOrNull() ?: run {
            AppLogger.e(TAG, "Invalid scheduledTime for individual session: ${session.timeSlotKey}")
            sessionManager.removeSession(session.timeSlotKey)
            return
        }

        val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val scheduleId = session.scheduleId ?: session.scheduleIds.firstOrNull() ?: run {
            sessionManager.removeSession(session.timeSlotKey)
            return
        }

        // Check if this specific med is still pending
        val allPendingEvents = projector.getPendingEventsAtTime(scheduledTime)
        val event = allPendingEvents.firstOrNull { it.scheduleId == scheduleId }

        if (event == null) {
            AppLogger.i(TAG, "Chain breaker: individual med $scheduleId is completed")
            sessionManager.removeSession(session.timeSlotKey)
            notifMgr.cancel(session.notificationId)
            return
        }

        val medication = medicationDao.getByIdSync(event.medicationId) ?: run {
            sessionManager.removeSession(session.timeSlotKey)
            return
        }

        // Atomic: increments count, computes next time, escalates channel, assigns new ID if escalated
        val oldChannelId = session.channelId
        val oldNotificationId = session.notificationId
        val updatedSession = sessionManager.updateFollowUpFired(session.timeSlotKey) ?: return
        val channelEscalated = oldChannelId != updatedSession.channelId

        // CHANNEL ESCALATION:
        // updateFollowUpFired assigns a new notification ID on escalation, so
        // cancel(oldId) and notify(newId) never race (async cancel can't swallow the new post).
        // Also cancel the group summary so it can be re-posted on the CRITICAL channel.
        if (channelEscalated) {
            notifMgr.cancel(oldNotificationId)
            notifMgr.cancel(NotificationIdGenerator.generateSessionNotificationId(scheduledTime))
            AppLogger.i(
                TAG,
                "Channel escalated $oldChannelId → ${updatedSession.channelId}, old notifId=$oldNotificationId → new=${updatedSession.notificationId}",
            )
        }

        val groupKey = missedDoseProcessor.groupKeyForTimeSlot(scheduledTime)

        missedDoseProcessor.showNotification(
            listOf(event), listOf(medication),
            updatedSession.channelId, updatedSession.notificationId,
            scheduledTime, lockScreenVisibility, isFollowUp = true,
            groupKey = groupKey,
            followUpNumber = updatedSession.followUpsFired,
        )

        // Re-post group summary (idempotent) to trigger Heads-Up alert and handle DnD escalation.
        // Since GROUP_ALERT_SUMMARY is set, children are muted — the summary must re-alert.
        if (allPendingEvents.size >= 2) {
            val allMeds = allPendingEvents.mapNotNull { medicationDao.getByIdSync(it.medicationId) }
            val summaryChannel = missedDoseProcessor.determineSummaryChannelFromDao(
                allPendingEvents,
                checkSessions = true,
                scheduledTime = scheduledTime,
            )
            missedDoseProcessor.showGroupSummary(
                allPendingEvents,
                allMeds,
                summaryChannel,
                scheduledTime,
                lockScreenVisibility,
                isFollowUp = true,
                isFollowUpAlert = true,
            )
        }

        AppLogger.i(
            TAG,
            "Individual follow-up #${updatedSession.followUpsFired} shown for schedule=$scheduleId, channel=${updatedSession.channelId}",
        )
    }

    // --- Snooze handling ---

    private suspend fun handleSnoozes(
        context: Context,
        sessions: List<NotificationSession>,
        projector: ScheduleProjector,
        sessionManager: NotificationSessionManager,
        medicationDao: MedicationDao,
        missedDoseProcessor: MissedDoseProcessor,
        lockScreenVisibility: LockScreenVisibility,
    ) {
        // Deduplicate: if multiple siblings are snoozed together, handle the parent group once
        val handled = mutableSetOf<String>()

        for (session in sessions) {
            val groupKey = session.parentTimeSlotKey ?: session.timeSlotKey
            if (groupKey in handled) continue
            handled.add(groupKey)

            handleSnooze(
                context,
                session,
                projector,
                sessionManager,
                medicationDao,
                missedDoseProcessor,
                lockScreenVisibility,
            )
        }
    }

    private suspend fun handleSnooze(
        context: Context,
        session: NotificationSession,
        projector: ScheduleProjector,
        sessionManager: NotificationSessionManager,
        medicationDao: MedicationDao,
        missedDoseProcessor: MissedDoseProcessor,
        lockScreenVisibility: LockScreenVisibility,
    ) {
        AppLogger.d(TAG, "Snooze fired for session: key=${session.timeSlotKey}, type=${session.sessionType}")

        // Parse scheduledTime — different format for combined vs individual
        val scheduledTime = when (session.sessionType) {
            SessionType.COMBINED -> session.timeSlotKey.toLongOrNull()
            SessionType.INDIVIDUAL -> (
                session.parentTimeSlotKey
                    ?: session.timeSlotKey.substringBefore("_")
                ).toLongOrNull()
        } ?: run {
            AppLogger.e(TAG, "Invalid scheduledTime for session: ${session.timeSlotKey}")
            sessionManager.removeSession(session.timeSlotKey)
            return
        }

        val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (session.sessionType) {
            SessionType.COMBINED -> {
                // Snooze fired for combined session — show all remaining meds in one notification
                val events = projector.getPendingEventsAtTime(scheduledTime)
                val remainingEvents = events.filter { it.scheduleId in session.scheduleIds }

                if (remainingEvents.isEmpty()) {
                    AppLogger.i(TAG, "Chain breaker: all meds in snoozed combined session are completed")
                    sessionManager.removeSession(session.timeSlotKey)
                    notifMgr.cancel(session.notificationId)
                    return
                }

                sessionManager.handleSnoozeFired(session.timeSlotKey)

                val medications = remainingEvents.mapNotNull { medicationDao.getByIdSync(it.medicationId) }
                if (medications.isEmpty()) {
                    sessionManager.removeSession(session.timeSlotKey)
                    return
                }

                missedDoseProcessor.showNotification(
                    remainingEvents, medications,
                    session.channelId, session.notificationId,
                    scheduledTime, lockScreenVisibility, isFollowUp = false, isSnoozed = true,
                    groupKey = missedDoseProcessor.groupKeyForTimeSlot(scheduledTime),
                )
                AppLogger.i(TAG, "Snoozed combined notification shown: ${medications.size} meds")
            }

            SessionType.INDIVIDUAL -> {
                // Snooze fired for individual sessions — show each remaining med individually
                val parentKey = session.parentTimeSlotKey ?: session.timeSlotKey
                val siblings = sessionManager.getSessionsByParent(parentKey)
                val groupKey = missedDoseProcessor.groupKeyForTimeSlot(scheduledTime)

                // Clear snooze state for all siblings
                for (sibling in siblings) {
                    sessionManager.handleSnoozeFired(sibling.timeSlotKey)
                }

                // Show individual notifications for each remaining med
                val shownEvents = mutableListOf<me.juliana.hellomeds.data.model.ProjectedEvent>()
                val shownMeds = mutableListOf<me.juliana.hellomeds.data.database.entities.Medication>()
                val childChannelIds = mutableListOf<String>()

                for (sibling in siblings) {
                    val scheduleId = sibling.scheduleId ?: sibling.scheduleIds.firstOrNull() ?: continue
                    val events = projector.getPendingEventsAtTime(scheduledTime)
                    val event = events.firstOrNull { it.scheduleId == scheduleId }

                    if (event == null) {
                        // This med was taken/skipped during snooze
                        sessionManager.removeSession(sibling.timeSlotKey)
                        notifMgr.cancel(sibling.notificationId)
                        continue
                    }

                    val medication = medicationDao.getByIdSync(event.medicationId) ?: continue

                    missedDoseProcessor.showNotification(
                        listOf(event), listOf(medication),
                        sibling.channelId, sibling.notificationId,
                        scheduledTime, lockScreenVisibility, isFollowUp = true, isSnoozed = true,
                        groupKey = groupKey,
                    )
                    shownEvents.add(event)
                    shownMeds.add(medication)
                    childChannelIds.add(sibling.channelId)
                }

                // Re-post group summary if 2+ children were shown (idempotent).
                // Canonical channel ordering: ALARM > CRITICAL > NORMAL. Matches
                // MissedDoseProcessor.determineSummaryChannelFromDao and
                // handleNewEventsCombined — without the ALARM branch, an
                // alarm-importance med's snooze rebuild silently drops to NORMAL
                // and loses its DnD/Focus bypass on the summary.
                if (shownEvents.size >= 2) {
                    val summaryChannel = when {
                        childChannelIds.any { it == NotificationChannels.ALARM_CHANNEL_ID } ->
                            NotificationChannels.ALARM_CHANNEL_ID
                        childChannelIds.any { it == NotificationChannels.CRITICAL_CHANNEL_ID } ->
                            NotificationChannels.CRITICAL_CHANNEL_ID
                        else -> NotificationChannels.NORMAL_CHANNEL_ID
                    }
                    missedDoseProcessor.showGroupSummary(
                        shownEvents,
                        shownMeds,
                        summaryChannel,
                        scheduledTime,
                        lockScreenVisibility,
                        isFollowUp = true,
                        isSnoozed = true,
                        isFollowUpAlert = true,
                    )
                }

                AppLogger.i(TAG, "Snoozed individual notifications shown for parent=$parentKey")
            }
        }
    }

    companion object {
        private const val TAG = "GlobalAlarmReceiver"
    }
}
