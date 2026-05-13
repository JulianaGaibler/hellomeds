// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.first
import me.juliana.hellomeds.MainActivity
import me.juliana.hellomeds.R
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.model.NotificationSession
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.SessionType
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.model.enums.NotificationGroupingMode
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger

/**
 * Owns the watermark catch-up pipeline and all "new pending event" notification
 * posting. Originally lived inside [GlobalAlarmReceiver]; extracted so the same
 * code path runs from both alarm-fire and post-boot reconciliation, since the
 * boot path used to skip the catch-up entirely and silently auto-skipped any
 * dose whose alarm fell during a reboot/locked window.
 *
 * Public surface:
 *   - [processMissedDoses] — called from boot reconciliation and from
 *     [GlobalAlarmReceiver.handleAlarm]'s catch-up step.
 *   - [showNotification], [showGroupSummary], [determineSummaryChannelFromDao] —
 *     reused by [GlobalAlarmReceiver]'s follow-up and snooze handlers.
 */
class MissedDoseProcessor(
    private val context: Context,
    private val projector: ScheduleProjector,
    private val sessionManager: NotificationSessionManager,
    private val medicationDao: MedicationDao,
    private val importanceLabelDao: ImportanceLabelDao,
    private val notifBuilder: NotificationBuilder,
    private val notifPrefs: NotificationPreferences,
    private val historyDao: MedicationHistoryDao,
) {

    /**
     * Watermark catch-up: process every pending event in [now - 4h, now] that
     * does not yet have a notification session. Idempotent — safe to call
     * multiple times for the same window.
     */
    suspend fun processMissedDoses(now: Long) {
        val watermarkStart = now - ScheduleProjector.MAX_CATCH_UP_LOOKBACK_MS
        val catchUpEvents = projector.getPendingEventsSince(watermarkStart, now)
        if (catchUpEvents.isEmpty()) return

        AppLogger.d(TAG, "Watermark window [$watermarkStart..$now]: ${catchUpEvents.size} pending events")
        val lockScreenVisibility = notifPrefs.lockScreenVisibility.first()

        // Group by scheduledTime — different time slots may have pending events
        val eventsByTimeSlot = catchUpEvents.groupBy { it.scheduledTime }
        for ((scheduledTime, slotEvents) in eventsByTimeSlot) {
            // Only handle events that don't already have a session (idempotency guard)
            val unhandledEvents = slotEvents.filter { event ->
                sessionManager.getSessionForSchedule(event.scheduledTime, event.scheduleId) == null
            }
            if (unhandledEvents.isEmpty()) continue

            // Pre-flight history re-check: getPendingEventsSince() above returned
            // an unlocked snapshot; a concurrent in-app markAsTaken may have
            // committed since. findByCompositeKey hits the same unique index
            // markAsTaken writes against, so any committed TAKEN/SKIPPED row
            // is observed here. If markAsTaken commits AFTER this check but
            // before handleNewEvents posts, its own reconcile() →
            // cleanupCompletedSessions() dismisses the phantom.
            val stillPending = unhandledEvents.filter { event ->
                historyDao.findByCompositeKey(
                    event.medicationId,
                    event.scheduleId,
                    event.scheduledTime,
                ) == null
            }
            AppLogger.d(
                TAG,
                "Catch-up at $scheduledTime: ${slotEvents.size} events, " +
                    "${unhandledEvents.size} session-unhandled, " +
                    "${stillPending.size} still pending after re-check",
            )
            if (stillPending.isNotEmpty()) {
                handleNewEvents(now, stillPending, lockScreenVisibility)
            }
        }
    }

    // --- New event handling ---

    private suspend fun handleNewEvents(
        now: Long,
        pendingEvents: List<ProjectedEvent>,
        lockScreenVisibility: LockScreenVisibility,
    ) {
        if (pendingEvents.isEmpty()) return

        AppLogger.d(
            TAG,
            "handleNewEvents: ${pendingEvents.size} events, " +
                "scheduleIds=${pendingEvents.map { it.scheduleId }}, " +
                "scheduledTimes=${pendingEvents.map { it.scheduledTime }}",
        )

        val groupingMode = notifPrefs.groupingMode.first()

        when (groupingMode) {
            NotificationGroupingMode.COMBINED -> handleNewEventsCombined(
                now,
                pendingEvents,
                lockScreenVisibility,
            )

            NotificationGroupingMode.GROUPED -> handleNewEventsGrouped(
                now,
                pendingEvents,
                lockScreenVisibility,
            )
        }
    }

    private suspend fun handleNewEventsCombined(
        now: Long,
        pendingEvents: List<ProjectedEvent>,
        lockScreenVisibility: LockScreenVisibility,
    ) {
        val medications = pendingEvents.mapNotNull { event ->
            medicationDao.getByIdSync(event.medicationId)
        }

        if (medications.isEmpty()) {
            AppLogger.w(TAG, "No medications found for pending events")
            return
        }

        val scheduledTime = pendingEvents.first().scheduledTime
        val notificationId = NotificationIdGenerator.generateSessionNotificationId(scheduledTime)

        var hasCriticalMed = false
        var hasAlarmMed = false
        var maxFollowUps = 0
        var followUpIntervalMinutes = 0
        var criticalAfterFollowUp: Int? = null
        var alarmAfterFollowUp: Int? = null

        for (event in pendingEvents) {
            val medication = medicationDao.getByIdSync(event.medicationId) ?: continue
            val labelId = medication.importanceLabelId ?: continue
            val label = importanceLabelDao.getByIdSync(labelId) ?: continue

            if (label.isAlarm) hasAlarmMed = true
            if (label.isCritical) hasCriticalMed = true
            if (label.hasFollowUps && label.followUpCount > maxFollowUps) {
                maxFollowUps = label.followUpCount
                followUpIntervalMinutes = label.followUpIntervalMinutes
            }
            if (label.criticalAfterFollowUp != null) {
                criticalAfterFollowUp = label.criticalAfterFollowUp
            }
            if (label.alarmAfterFollowUp != null) {
                alarmAfterFollowUp = label.alarmAfterFollowUp
            }
        }

        val channelId = when {
            hasAlarmMed -> NotificationChannels.ALARM_CHANNEL_ID
            hasCriticalMed -> NotificationChannels.CRITICAL_CHANNEL_ID
            else -> NotificationChannels.NORMAL_CHANNEL_ID
        }

        showNotification(
            pendingEvents,
            medications,
            channelId,
            notificationId,
            scheduledTime,
            lockScreenVisibility,
            isFollowUp = false,
            groupKey = groupKeyForTimeSlot(scheduledTime),
        )

        if (maxFollowUps > 0) {
            val session = NotificationSession(
                timeSlotKey = scheduledTime.toString(),
                scheduleIds = pendingEvents.map { it.scheduleId },
                notificationId = notificationId,
                followUpsFired = 0,
                maxFollowUps = maxFollowUps,
                followUpIntervalMs = followUpIntervalMinutes * 60 * 1000L,
                nextFollowUpTime = now + (followUpIntervalMinutes * 60 * 1000L),
                channelId = channelId,
                hasCriticalMed = hasCriticalMed || hasAlarmMed,
                criticalAfterFollowUp = criticalAfterFollowUp,
                alarmAfterFollowUp = alarmAfterFollowUp,
                sessionType = SessionType.COMBINED,
            )
            sessionManager.createSession(session)
            AppLogger.d(
                TAG,
                "Created COMBINED follow-up session: maxFollowUps=$maxFollowUps, interval=${followUpIntervalMinutes}min",
            )
        }

        AppLogger.i(
            TAG,
            "Combined notification shown: ${medications.size} meds at $scheduledTime, channel=$channelId",
        )
    }

    private suspend fun handleNewEventsGrouped(
        now: Long,
        pendingEvents: List<ProjectedEvent>,
        lockScreenVisibility: LockScreenVisibility,
    ) {
        val scheduledTime = pendingEvents.first().scheduledTime
        val groupKey = groupKeyForTimeSlot(scheduledTime)

        // Post group summary FIRST if multiple meds (summary owns the alert)
        if (pendingEvents.size > 1) {
            val allMedications = pendingEvents.mapNotNull { medicationDao.getByIdSync(it.medicationId) }
            val summaryChannel = determineSummaryChannelFromDao(pendingEvents)
            showGroupSummary(
                pendingEvents,
                allMedications,
                summaryChannel,
                scheduledTime,
                lockScreenVisibility,
                isFollowUp = false,
            )
        }

        // Post individual children (always with groupKey for future-proof grouping)
        for (event in pendingEvents) {
            val medication = medicationDao.getByIdSync(event.medicationId) ?: continue
            val label = medication.importanceLabelId?.let { importanceLabelDao.getByIdSync(it) }

            val isAlarmLabel = label?.isAlarm == true
            val isCritical = label?.isCritical == true
            val channelId = when {
                isAlarmLabel -> NotificationChannels.ALARM_CHANNEL_ID
                isCritical -> NotificationChannels.CRITICAL_CHANNEL_ID
                else -> NotificationChannels.NORMAL_CHANNEL_ID
            }
            val notifId =
                NotificationIdGenerator.generateMedicationNotificationId(scheduledTime, event.scheduleId)

            showNotification(
                listOf(event),
                listOf(medication),
                channelId,
                notifId,
                scheduledTime,
                lockScreenVisibility,
                isFollowUp = false,
                groupKey = groupKey,
            )

            // Create individual session if follow-ups are configured
            val hasFollowUps = label?.hasFollowUps == true
            val maxFollowUps = if (hasFollowUps) label!!.followUpCount else 0
            val intervalMinutes = if (hasFollowUps) label!!.followUpIntervalMinutes else 0

            if (maxFollowUps > 0) {
                val individualKey = "${scheduledTime}_${event.scheduleId}"
                val session = NotificationSession(
                    timeSlotKey = individualKey,
                    scheduleIds = listOf(event.scheduleId),
                    notificationId = notifId,
                    followUpsFired = 0,
                    maxFollowUps = maxFollowUps,
                    followUpIntervalMs = intervalMinutes * 60_000L,
                    nextFollowUpTime = now + (intervalMinutes * 60_000L),
                    channelId = channelId,
                    hasCriticalMed = isCritical || isAlarmLabel,
                    criticalAfterFollowUp = label?.criticalAfterFollowUp,
                    alarmAfterFollowUp = label?.alarmAfterFollowUp,
                    sessionType = SessionType.INDIVIDUAL,
                    parentTimeSlotKey = scheduledTime.toString(),
                    scheduleId = event.scheduleId,
                )
                sessionManager.createSession(session)
            }

            AppLogger.d(TAG, "Grouped notification shown for schedule=${event.scheduleId}, channel=$channelId")
        }

        AppLogger.i(
            TAG,
            "Grouped notifications shown: ${pendingEvents.size} individual meds at $scheduledTime",
        )
    }

    // --- Shared notification display (also called by GlobalAlarmReceiver follow-up/snooze paths) ---

    internal fun groupKeyForTimeSlot(scheduledTime: Long): String = "hellomeds_$scheduledTime"

    /**
     * Determine summary channel by checking importance labels AND session-based escalation.
     *
     * Two sources of criticality exist:
     * 1. Static: ImportanceLabel.isCritical — med is always critical.
     * 2. Dynamic: criticalAfterFollowUp — med starts NORMAL but the session escalates to
     *    CRITICAL after N follow-ups. This escalation is tracked in the session's channelId
     *    field (updated atomically by updateFollowUpFired SQL), NOT in the label.
     *
     * Without checking sessions, a scenario like this would break DnD:
     *   - Follow-up 2: Med A escalates to CRITICAL → summary becomes CRITICAL ✓
     *   - Follow-up 3: Med B (same slot, still NORMAL) triggers this method → would see
     *     only NORMAL labels → rebuild summary as NORMAL → DnD bypass lost for Med A ✗
     * Checking sibling sessions ensures the summary stays CRITICAL as long as ANY child is.
     *
     * @param checkSessions Pass true to also check session escalation (for follow-up paths).
     *   False for initial notification paths where sessions don't exist yet.
     * @param scheduledTime The time slot key for session lookup. Required when checkSessions is true.
     */
    internal suspend fun determineSummaryChannelFromDao(
        events: List<ProjectedEvent>,
        checkSessions: Boolean = false,
        scheduledTime: Long? = null,
    ): String {
        // Check static label criticality — alarm first (highest priority), then critical
        for (event in events) {
            val medication = medicationDao.getByIdSync(event.medicationId) ?: continue
            val label = medication.importanceLabelId?.let { importanceLabelDao.getByIdSync(it) }
            if (label?.isAlarm == true) return NotificationChannels.ALARM_CHANNEL_ID
            if (label?.isCritical == true) return NotificationChannels.CRITICAL_CHANNEL_ID
        }
        // Check dynamic session-based escalation (threshold crossed during follow-ups)
        if (checkSessions && scheduledTime != null) {
            val siblings = sessionManager.getSessionsByParent(scheduledTime.toString())
            if (siblings.any { it.channelId == NotificationChannels.ALARM_CHANNEL_ID }) {
                return NotificationChannels.ALARM_CHANNEL_ID
            }
            if (siblings.any { it.channelId == NotificationChannels.CRITICAL_CHANNEL_ID }) {
                return NotificationChannels.CRITICAL_CHANNEL_ID
            }
        }
        return NotificationChannels.NORMAL_CHANNEL_ID
    }

    @VisibleForTesting
    internal fun showNotification(
        events: List<ProjectedEvent>,
        medications: List<me.juliana.hellomeds.data.database.entities.Medication>,
        channelId: String,
        notificationId: Int,
        scheduledTime: Long,
        lockScreenVisibility: LockScreenVisibility,
        isFollowUp: Boolean,
        isSnoozed: Boolean = false,
        groupKey: String? = null,
        followUpNumber: Int = 0,
    ) {
        try {
            AppLogger.d(
                TAG,
                "showNotification: notifId=$notificationId, " +
                    "eventCount=${events.size}, medNames=${medications.map { it.name }}, " +
                    "scheduledTime=$scheduledTime, isFollowUp=$isFollowUp, isSnoozed=$isSnoozed, " +
                    "groupKey=$groupKey, followUpNumber=$followUpNumber",
            )

            val notification = notifBuilder.buildNotification(
                events = events,
                medications = medications,
                channelId = channelId,
                notificationId = notificationId,
                scheduledTime = scheduledTime,
                lockScreenVisibility = lockScreenVisibility,
                isFollowUp = isFollowUp,
                isSnoozed = isSnoozed,
                groupKey = groupKey,
                followUpNumber = followUpNumber,
            )

            val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifMgr.notify(notificationId, notification)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to build notification", e)
            try {
                val fallback = NotificationCompat.Builder(context, channelId)
                    .setContentTitle("Medication reminder")
                    .setContentText("Something went wrong. You have medications to log — tap to open.")
                    .setSmallIcon(R.drawable.notification_pill_24dp)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            0,
                            Intent(context, MainActivity::class.java),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    )
                    .build()
                val notifMgr =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notifMgr.notify(notificationId, fallback)
            } catch (fallbackError: Exception) {
                AppLogger.e(TAG, "Failed to display fallback notification", fallbackError)
            }
        }
    }

    /**
     * Post or re-post the group summary notification for a time slot.
     * Idempotent: safe to call whenever 2+ children exist. Uses the same notification ID
     * (generateSessionNotificationId) so re-posts update in place.
     *
     * The summary owns the alert (GROUP_ALERT_SUMMARY). Pass isFollowUpAlert=true
     * to trigger Heads-Up screen wake on behalf of a child follow-up.
     *
     * @param summaryChannelId CRITICAL if any child is critical, else NORMAL. Caller determines this.
     */
    internal fun showGroupSummary(
        allEvents: List<ProjectedEvent>,
        allMedications: List<me.juliana.hellomeds.data.database.entities.Medication>,
        summaryChannelId: String,
        scheduledTime: Long,
        lockScreenVisibility: LockScreenVisibility,
        isFollowUp: Boolean,
        isSnoozed: Boolean = false,
        isFollowUpAlert: Boolean = false,
    ) {
        try {
            val groupKey = groupKeyForTimeSlot(scheduledTime)
            val summaryNotifId = NotificationIdGenerator.generateSessionNotificationId(scheduledTime)

            val notification = notifBuilder.buildGroupSummaryNotification(
                events = allEvents,
                medications = allMedications,
                channelId = summaryChannelId,
                notificationId = summaryNotifId,
                scheduledTime = scheduledTime,
                lockScreenVisibility = lockScreenVisibility,
                groupKey = groupKey,
                isFollowUp = isFollowUp,
                isSnoozed = isSnoozed,
                isFollowUpAlert = isFollowUpAlert,
            )

            val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifMgr.notify(summaryNotifId, notification)

            AppLogger.d(
                TAG,
                "Group summary posted: notifId=$summaryNotifId, childCount=${allEvents.size}, " +
                    "channel=$summaryChannelId, isFollowUpAlert=$isFollowUpAlert",
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to build group summary", e)
            try {
                val groupKey = groupKeyForTimeSlot(scheduledTime)
                val summaryNotifId = NotificationIdGenerator.generateSessionNotificationId(scheduledTime)
                val fallback = NotificationCompat.Builder(context, summaryChannelId)
                    .setContentTitle("Medication reminder")
                    .setContentText("Something went wrong. You have medications to log — tap to open.")
                    .setSmallIcon(R.drawable.notification_pill_24dp)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setGroup(groupKey)
                    .setGroupSummary(true)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            0,
                            Intent(context, MainActivity::class.java),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    )
                    .build()
                val notifMgr =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notifMgr.notify(summaryNotifId, fallback)
            } catch (fallbackError: Exception) {
                AppLogger.e(TAG, "Failed to display fallback group summary", fallbackError)
            }
        }
    }

    companion object {
        private const val TAG = "MissedDoseProcessor"
    }
}
