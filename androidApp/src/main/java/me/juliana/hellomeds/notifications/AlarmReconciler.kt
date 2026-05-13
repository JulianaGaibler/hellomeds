// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.model.SessionType
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.preferences.ReliabilityPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.support.ReconcilerDiagnostic
import me.juliana.hellomeds.data.util.TimeProvider
import kotlin.time.Instant

/**
 * Central idempotent reconciler. Called after ANY state mutation
 * (schedule change, medication taken, settings change, etc.).
 *
 * Responsibilities:
 * 1. Alarm management: maintain at most ONE alarm in AlarmManager
 * 2. Notification cleanup: dismiss/update notifications for completed events
 *
 * The notification cleanup is critical for in-app medication logging. When the user
 * marks a medication as taken from within the app (not from the notification buttons),
 * the notification would otherwise linger until the next follow-up chain breaker fires.
 * By cleaning up in reconcile(), notifications are dismissed immediately after any
 * state mutation — matching iOS behavior where reconcile() does a full wipe-and-reschedule.
 */
class AlarmReconciler(
    private val context: Context,
    private val projector: ScheduleProjector,
    private val medicationDao: MedicationDao,
    private val importanceLabelDao: ImportanceLabelDao,
    private val sessionManager: NotificationSessionManager,
    private val notificationPrefs: NotificationPreferences,
    private val alarmManager: AlarmManager,
    private val notifBuilder: NotificationBuilder,
    private val timeProvider: TimeProvider,
    private val reliabilityPrefs: ReliabilityPreferences,
    private val missedDoseProcessor: MissedDoseProcessor,
) : ScheduleReconciler {

    companion object {
        private const val TAG = "AlarmReconciler"
        const val RECONCILER_REQUEST_CODE = 88888
        const val ALARM_URI = "hellomeds://reconciler-alarm"
        const val ALARM_ACTION = "me.juliana.hellomeds.RECONCILER_ALARM"

        /** Format a millisecond delta as "+Xmin" or "-Xmin" for log readability. */
        fun deltaMin(target: Long?, now: Long): String {
            if (target == null) return "null"
            val delta = (target - now) / 60_000
            return if (delta >= 0) "+${delta}min" else "${delta}min"
        }
    }

    /**
     * Interface implementation — delegates to the parameterized overload.
     */
    override suspend fun reconcile() = reconcile(timeProvider.nowMillis())

    /**
     * Watermark catch-up for missed doses in [now - 4h, now]. Called from
     * [BootReceiver] at first unlock so doses whose alarms fell during a reboot
     * surface as notifications instead of being silently auto-skipped, and from
     * [GlobalAlarmReceiver]'s alarm-fire path indirectly through the same
     * processor instance.
     *
     * Idempotent — safe to call multiple times for the same window.
     */
    suspend fun processMissedDoses(now: Long = timeProvider.nowMillis()) {
        missedDoseProcessor.processMissedDoses(now)
    }

    /**
     * Idempotent. Called after ANY state mutation.
     * Cleans up completed notification sessions, then computes next wakeup and sets ONE alarm.
     */
    suspend fun reconcile(now: Long) = withContext(Dispatchers.IO) {
        // Clean up notifications for events that have been completed (e.g., logged in-app).
        // This must run before alarm computation so stale sessions don't influence the next wakeup.
        cleanupCompletedSessions()

        if (!notificationPrefs.notificationsEnabled.first()) {
            AppLogger.d(TAG, "Notifications disabled, cancelling alarm")
            cancelAlarm()
            return@withContext
        }

        val nextWakeup = computeNextWakeupTime(now)
        if (nextWakeup == null) {
            AppLogger.d(TAG, "No upcoming events or follow-ups, cancelling alarm")
            cancelAlarm()
            return@withContext
        }

        if (nextWakeup <= now) {
            // Past event — fire alarm immediately so handleAlarm() processes it
            // via the normal watermark pipeline. No recursion, no duplicated logic.
            AppLogger.w(
                TAG,
                "Next wakeup $nextWakeup is in the past (${deltaMin(nextWakeup, now)}), triggering immediate alarm",
            )
            setAlarm(now + 100L, isCritical = true)
        } else {
            val isCritical = isNextWakeupCritical(nextWakeup)
            setAlarm(nextWakeup, isCritical)
            AppLogger.i(
                TAG,
                "reconcile() now=$now, nextWakeup=$nextWakeup (${deltaMin(nextWakeup, now)}), critical=$isCritical",
            )
        }
    }

    /**
     * Computes the next time the alarm should fire.
     * Returns the soonest of: next pending scheduled event, next follow-up, next snooze.
     * Follow-ups and snoozes are independent timers in sessions.
     */
    suspend fun computeNextWakeupTime(now: Long = timeProvider.nowMillis()): Long? {
        // Next pending event from schedule projection
        val nextEvent = projector.findNextPendingEvent(now)
        val nextEventTime = nextEvent?.scheduledTime

        // Next session time: consider BOTH follow-up and snooze timers independently.
        // Include past-due times so reconcile() can trigger an immediate catch-up alarm
        // via the nextWakeup <= now branch — self-healing for missed follow-ups/snoozes.
        val sessions = sessionManager.getAllSessions()
        val allSessionTimes = sessions.flatMap { session ->
            listOfNotNull(session.nextFollowUpTime, session.snoozeUntilTime)
        }
        val nextSessionTime = allSessionTimes.minOrNull()

        val pastDueCount = allSessionTimes.count { it <= now }
        val result = listOfNotNull(nextEventTime, nextSessionTime).minOrNull()

        AppLogger.d(
            TAG,
            "computeNextWakeup: nextEvent=$nextEventTime (${deltaMin(nextEventTime, now)}), " +
                "sessions=${allSessionTimes.size} ($pastDueCount past-due), " +
                "nextSession=$nextSessionTime (${deltaMin(nextSessionTime, now)}), result=$result",
        )
        return result
    }

    /**
     * Check if the next wakeup involves a critical medication.
     * Critical → setAlarmClock() which bypasses Doze and shows alarm icon.
     */
    private suspend fun isNextWakeupCritical(wakeupTime: Long): Boolean {
        // Check if any session timer (follow-up or snooze) at this time is critical
        val sessions = sessionManager.getAllSessions()
        val criticalSession = sessions.any { session ->
            (session.nextFollowUpTime == wakeupTime || session.snoozeUntilTime == wakeupTime) &&
                session.hasCriticalMed
        }
        if (criticalSession) return true

        // Check if any event at this time has a critical importance label
        val events = projector.getPendingEventsAtTime(wakeupTime)
        for (event in events) {
            val medication = medicationDao.getByIdSync(event.medicationId) ?: continue
            val labelId = medication.importanceLabelId ?: continue
            val label = importanceLabelDao.getByIdSync(labelId) ?: continue
            if (label.isCritical || label.isAlarm) return true
        }

        return false
    }

    /**
     * Dismiss or update notifications for sessions whose events have been completed.
     * Handles in-app medication logging where the user marks meds as taken/skipped
     * from the app UI — the notification buttons have their own cleanup path in
     * NotificationActionHandler.
     *
     * For each active session:
     * - INDIVIDUAL: if the specific med is no longer pending → dismiss notification, remove session
     * - COMBINED: if ALL meds completed → dismiss. If some remain → silently rebuild notification.
     * - Group summary: cancel if ≤1 child notifications remain for the time slot
     */
    private suspend fun cleanupCompletedSessions() {
        val notifMgr = context.getSystemService(NotificationManager::class.java)
        val lockScreenVisibility = notificationPrefs.lockScreenVisibility.first()
        val allSessions = sessionManager.getAllSessions()
        if (allSessions.isEmpty()) return

        for (session in allSessions) {
            val scheduledTime = parseScheduledTime(session) ?: continue
            val pendingEvents = projector.getPendingEventsAtTime(scheduledTime)
            val remainingIds = session.scheduleIds.filter { id ->
                pendingEvents.any { it.scheduleId == id }
            }

            when {
                // All events in this session are completed
                remainingIds.isEmpty() -> {
                    notifMgr.cancel(session.notificationId)
                    sessionManager.removeSession(session.timeSlotKey)
                    AppLogger.d(TAG, "Cleanup: dismissed completed session ${session.timeSlotKey}")

                    // Cancel group summary if ≤1 pending events remain for this time slot
                    if (pendingEvents.size <= 1) {
                        val summaryId = NotificationIdGenerator.generateSessionNotificationId(scheduledTime)
                        notifMgr.cancel(summaryId)
                    }
                }

                // COMBINED session with some meds remaining — silently rebuild notification
                session.sessionType == SessionType.COMBINED &&
                    remainingIds.size < session.scheduleIds.size -> {
                    val remainingEvents = pendingEvents.filter { it.scheduleId in session.scheduleIds }
                    val medications =
                        remainingEvents.mapNotNull { medicationDao.getByIdSync(it.medicationId) }
                    if (medications.isNotEmpty()) {
                        val notification = notifBuilder.buildNotification(
                            events = remainingEvents,
                            medications = medications,
                            channelId = session.channelId,
                            notificationId = session.notificationId,
                            scheduledTime = scheduledTime,
                            lockScreenVisibility = lockScreenVisibility,
                            groupKey = "hellomeds_$scheduledTime",
                        )
                        notifMgr.notify(session.notificationId, notification)
                        AppLogger.d(
                            TAG,
                            "Cleanup: rebuilt combined notification with ${medications.size} remaining meds",
                        )
                    }
                }
                // Session still fully active — no cleanup needed
            }
        }
    }

    /**
     * Parse the original scheduledTime from a session's keys.
     * COMBINED keys are just the timestamp. INDIVIDUAL keys are "timestamp_scheduleId".
     */
    private fun parseScheduledTime(session: me.juliana.hellomeds.data.model.NotificationSession): Long? {
        return when (session.sessionType) {
            SessionType.COMBINED -> session.timeSlotKey.toLongOrNull()
            SessionType.INDIVIDUAL -> (
                session.parentTimeSlotKey
                    ?: session.timeSlotKey.substringBefore("_")
                ).toLongOrNull()
        }
    }

    private suspend fun setAlarm(triggerTime: Long, isCritical: Boolean) {
        val pendingIntent = createPendingIntent()

        // Permission check must run BEFORE scheduling: on Android 12+, calling
        // setAlarmClock without SCHEDULE_EXACT_ALARM throws (or is silently dropped
        // depending on target SDK). Falling back to setAndAllowWhileIdle preserves
        // Doze-piercing semantics with up to ~9-15 min inexactness.
        val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canSchedule) {
            // Exact path — pierces Doze immediately and shows alarm-clock icon.
            // Scheduling priority (CPU wake) is decoupled from presentation priority
            // (notification channel, DnD bypass), which is handled by GlobalAlarmReceiver.
            val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, me.juliana.hellomeds.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, showIntent),
                pendingIntent,
            )
            AppLogger.i(TAG, "Scheduling wakeup at $triggerTime via setAlarmClock (exact)")
        } else {
            // Inexact fallback. Still wakes the device (RTC_WAKEUP) and bypasses Doze
            // for the first few alarms, but the OS may delay by up to ~15 min.
            // The UI surfaces a recovery banner via reliabilityPrefs.exactAlarmsDisabled.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent,
            )
            AppLogger.w(
                TAG,
                "SCHEDULE_EXACT_ALARM not granted — falling back to setAndAllowWhileIdle (inexact, may delay up to ~15min)",
            )
        }

        // Persist permission state so the shared UI can surface a recovery banner.
        // Read by ReliabilityStateProvider via ReliabilityPreferences.exactAlarmsDisabled.
        reliabilityPrefs.setExactAlarmsDisabled(!canSchedule)
    }

    fun cancelAlarm() {
        val pendingIntent = createPendingIntent()
        alarmManager.cancel(pendingIntent)
        AppLogger.d(TAG, "Alarm cancelled")
    }

    /**
     * Verify whether the reconciler alarm currently exists in AlarmManager.
     * Used by the debug screen.
     */
    fun verifyAlarmExists(): Boolean {
        val intent = Intent(context, GlobalAlarmReceiver::class.java).apply {
            action = ALARM_ACTION
            data = Uri.parse(ALARM_URI)
        }
        val existing = PendingIntent.getBroadcast(
            context,
            RECONCILER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        return existing != null
    }

    override suspend fun getDiagnosticSummary(): ReconcilerDiagnostic = withContext(Dispatchers.IO) {
        val now = timeProvider.nowMillis()
        val alarmExists = verifyAlarmExists()
        val nextWakeup = computeNextWakeupTime(now)
        val sessions = sessionManager.getAllSessions()

        // Health check
        val healthy = (nextWakeup != null) == alarmExists
        val healthMessage = when {
            nextWakeup != null && !alarmExists -> "Alarm missing: reconciler expects wakeup but no alarm is set"
            nextWakeup == null && alarmExists -> "Stale alarm: no wakeup needed but alarm exists"
            else -> "Alarm system healthy"
        }

        // Wakeup reason
        val nextEventTime = projector.findNextPendingEvent(now)?.scheduledTime
        val nextFollowUpTime = sessions.mapNotNull { it.nextFollowUpTime }.filter { it > now }.minOrNull()
        val nextSnoozeTime = sessions.mapNotNull { it.snoozeUntilTime }.filter { it > now }.minOrNull()
        val wakeupReason = when {
            nextWakeup == null -> "NONE"
            nextWakeup == nextFollowUpTime -> "FOLLOW_UP"
            nextWakeup == nextSnoozeTime -> "SNOOZE"
            else -> "SCHEDULED_EVENT"
        }

        // Alarm type — always setAlarmClock now
        val alarmType = if (nextWakeup == null) "NONE" else "SET_ALARM_CLOCK"

        // Catch-up events
        val watermarkStart = now - ScheduleProjector.MAX_CATCH_UP_LOOKBACK_MS
        val catchUpCount = projector.getPendingEventsSince(watermarkStart, now).size

        // Timezone
        val tz = TimeZone.currentSystemDefault()
        val nowInstant = Instant.fromEpochMilliseconds(now)
        val localDt = nowInstant.toLocalDateTime(tz)
        val offsetSeconds = (
            (
                localDt.toInstant(
                    kotlinx.datetime.UtcOffset.ZERO,
                ).toEpochMilliseconds() - now
                ) / 1000
            ).toInt()

        val details = mutableMapOf(
            "alarmExists" to alarmExists.toString(),
            "nextWakeupTime" to (nextWakeup?.toString() ?: "none"),
            "wakeupReason" to wakeupReason,
            "alarmType" to alarmType,
            "totalSessions" to sessions.size.toString(),
            "activeFollowUps" to sessions.count { it.nextFollowUpTime != null }.toString(),
            "snoozedSessions" to sessions.count { it.isSnoozed }.toString(),
            "catchUpEventCount" to catchUpCount.toString(),
        )

        ReconcilerDiagnostic(
            healthy = healthy,
            healthMessage = healthMessage,
            timezoneId = tz.id,
            utcOffsetSeconds = offsetSeconds,
            details = details,
        )
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, GlobalAlarmReceiver::class.java).apply {
            action = ALARM_ACTION
            data = Uri.parse(ALARM_URI)
        }
        return PendingIntent.getBroadcast(
            context,
            RECONCILER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
