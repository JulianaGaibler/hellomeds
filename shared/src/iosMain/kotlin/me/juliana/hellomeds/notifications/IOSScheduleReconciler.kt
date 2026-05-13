// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.model.NotificationSession
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.SessionType
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.preferences.ReliabilityPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.support.ReconcilerDiagnostic
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.util.currentTimeMillis
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.notification_text_time_log_generic
import me.juliana.hellomeds.shared.notification_text_time_log_named
import me.juliana.hellomeds.shared.notification_title_followup
import me.juliana.hellomeds.shared.notification_title_followup_numbered
import me.juliana.hellomeds.shared.notification_title_reminder
import org.jetbrains.compose.resources.getPluralString
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSUUID
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSettingEnabled
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * Filter the snooze session's captured scheduleIds down to the subset that is
 * still pending at the slot, so a restored snooze notification names only the
 * meds the user has not yet logged. Extracted as a top-level helper so the
 * regression contract (`session=[1,2]` ∧ `pending={2}` ⇒ `[2]`) can be pinned
 * by a focused test without the full UN center + DataStore + DAO graph.
 */
internal fun computeStillPendingScheduleIds(sessionScheduleIds: List<Int>, pendingScheduleIds: Set<Int>): List<Int> =
    sessionScheduleIds.filter { it in pendingScheduleIds }

/**
 * Bridge for setting interruption level from Swift.
 * K/N can't reference UNNotificationInterruptionLevel enum directly (causes FileFailedToInitializeException).
 * Swift registers a callback that sets content.interruptionLevel using the native enum.
 * Raw values: active=0, critical=1, passive=2, timeSensitive=3
 */
private var notificationConfigurator: ((UNMutableNotificationContent, Long) -> Unit)? = null

fun registerNotificationConfigurator(callback: (UNMutableNotificationContent, Long) -> Unit) {
    notificationConfigurator = callback
}

/**
 * Sets the interruption level on notification content via the Swift bridge.
 * Falls back silently if the bridge isn't registered (e.g. older iOS).
 */
internal fun setInterruptionLevel(content: UNMutableNotificationContent, level: Long) {
    try {
        notificationConfigurator?.invoke(content, level)
    } catch (_: Exception) {
    }
}

/**
 * iOS implementation of ScheduleReconciler.
 *
 * Unlike Android's single-alarm AlarmReconciler, iOS requires pre-scheduling
 * local notifications because the app may not be running when they fire.
 * This reconciler batch-schedules up to [MAX_NOTIFICATIONS] notifications
 * covering a 7-day lookahead window.
 *
 * Called after every state mutation (schedule CRUD, medication taken/skipped,
 * settings change) — same contract as AlarmReconciler on Android.
 *
 * Algorithm:
 * 1. Remove all pending medication notifications (idempotent wipe)
 * 2. Project events for the next 7 days via ScheduleProjector
 * 3. Filter to pending events only
 * 4. Group by time slot (events within 1 minute are same slot)
 * 5. Schedule each group as a single notification with all medication names
 * 6. Set interruption level based on importance label (timeSensitive vs critical)
 */
class IOSScheduleReconciler(
    private val projector: ScheduleProjector,
    private val medicationDao: MedicationDao,
    private val importanceLabelDao: ImportanceLabelDao,
    private val notificationPrefs: NotificationPreferences,
    private val sessionManager: IOSNotificationSessionManager,
    private val reliabilityPrefs: ReliabilityPreferences,
) : ScheduleReconciler {

    private val timeFormatter = NSDateFormatter().apply { dateFormat = "HH:mm" }

    private fun formatSlotTime(slotTime: Long): String {
        val date = NSDate.dateWithTimeIntervalSince1970(slotTime / 1000.0)
        return timeFormatter.stringFromDate(date)
    }

    // Constants moved to NotificationConstants.kt to avoid class initialization issues.
    // Private constants kept here as they are only used within this class.
    private val TAG = "IOSScheduleReconciler"
    private val LOOKAHEAD_MS = SCHEDULING_WINDOW_MS
    private val TIME_SLOT_TOLERANCE_MS = 60_000L

    // How far backward in time to include still-pending events in the
    // projection. Without this, removeAllMedicationNotifications() in Step 1
    // wipes the delivered initial AND every pre-scheduled follow-up for past
    // pending slots, and the forward-only filter never re-arms them — silently
    // breaking the escalation chain whenever an unrelated state mutation
    // triggers reconcile. UNCalendarNotificationTrigger with past
    // dateComponents (repeats=false) has no future match and is silently
    // inert, so re-creating the initial request for a past slot is a no-op
    // while the future-dated follow-ups in the same chain are correctly armed
    // by the existing `if (followUpTime <= now) continue` guard.
    private val PAST_PENDING_LOOKBACK_MS = 4L * 60 * 60 * 1000L

    // Serialize all reconcile() calls. reconcile() is called from ~20 repository methods
    // after every state mutation. Rapid user actions (take 3 meds quickly) would otherwise
    // trigger concurrent wipe-and-reschedule cycles, risking duplicate or dropped notifications.
    private val reconcileMutex = Mutex()

    init {
        // Observe timezone changes and trigger reconciliation.
        // On Android, TimezoneChangeReceiver handles this. On iOS, we use NSNotificationCenter.
        platform.Foundation.NSNotificationCenter.defaultCenter.addObserverForName(
            name = platform.Foundation.NSSystemTimeZoneDidChangeNotification,
            `object` = null,
            queue = platform.Foundation.NSOperationQueue.mainQueue,
        ) { _ ->
            AppLogger.d(TAG, "Timezone changed, triggering reconciliation")
            MainScope().launch {
                reconcile()
            }
        }
    }

    override suspend fun reconcile() = reconcileMutex.withLock {
        // Check if notifications are enabled in user preferences
        val enabled = notificationPrefs.notificationsEnabled.first()
        if (!enabled) {
            AppLogger.d(TAG, "Notifications disabled, removing all pending")
            removeAllMedicationNotifications()
            return@withLock
        }

        val center = UNUserNotificationCenter.currentNotificationCenter()
        val now = currentTimeMillis()

        // Step 0: Clear expired snoozes so they can be rescheduled normally.
        // This is the iOS equivalent of Android's GlobalAlarmReceiver.getDueSnoozes().
        val dueSnoozes = sessionManager.getDueSnoozes(now)
        for (snooze in dueSnoozes) {
            sessionManager.clearSnooze(snooze.timeSlotKey)
            AppLogger.d(TAG, "Auto-cleared expired snooze for slot ${snooze.timeSlotKey}")
        }

        // Collect still-active snoozed sessions (snoozeUntilTime > now).
        // Their AlarmKit alarms must survive the wipe to preserve Dynamic Island countdown.
        val activeSnoozedSessions = sessionManager.getActiveSnoozedSessions(now)
        val snoozedSlotKeys = activeSnoozedSessions.map { it.timeSlotKey }.toSet()

        // Step 1: Remove all existing medication notifications + AlarmKit alarms + stale sessions (idempotent)
        removeAllMedicationNotifications()
        removeSnoozeNotifications()
        if (isAlarmKitAvailable()) {
            if (snoozedSlotKeys.isEmpty()) {
                cancelAllAlarmKitAlarms()
            } else {
                // Selectively cancel — preserve snoozed alarms (their countdown is still active)
                cancelNonSnoozedAlarmKitAlarms(snoozedSlotKeys)
            }
        }
        sessionManager.clearStaleSessions()

        // Step 2: Project events for the next 7 days, widened backward by
        // PAST_PENDING_LOOKBACK_MS so still-pending events whose follow-up
        // chain was wiped in Step 1 can have their unfired tail re-armed below.
        val windowEnd = now + LOOKAHEAD_MS

        val events = projector.projectEvents(now - PAST_PENDING_LOOKBACK_MS, windowEnd)
        val pendingEvents = events.filter { it.isPending }

        if (pendingEvents.isEmpty()) {
            AppLogger.d(TAG, "No pending events in next 7 days")
            return@withLock
        }

        // Step 3: Group events by time slot (within 1 minute tolerance)
        val timeSlots = groupByTimeSlot(pendingEvents)

        AppLogger.i(
            TAG,
            "Scheduling ${timeSlots.size} notification groups " +
                "(${pendingEvents.size} total events)",
        )

        // Step 3a: Detect budget exhaustion. iOS hard-caps at 64 pending local
        // notifications. We schedule up to MAX_EVENT_NOTIFICATIONS (= 55) event
        // slots and reserve SNOOZE_RESERVED_SLOTS (= 5) for runtime snooze adds.
        // When projected slot count exceeds the event ceiling, the user has more
        // reminders than we can pre-schedule — surface a recovery banner via
        // ReliabilityPreferences so the UI nudges them to open the app regularly.
        val budgetExhausted = timeSlots.size > MAX_EVENT_NOTIFICATIONS
        reliabilityPrefs.setIosNotificationBudgetExhausted(budgetExhausted)
        if (budgetExhausted) {
            AppLogger.w(
                TAG,
                "Notification budget exhausted: ${timeSlots.size} slots projected, " +
                    "$MAX_EVENT_NOTIFICATIONS schedulable. Surfacing recovery banner.",
            )
        }

        // Read privacy preference
        val visibility = notificationPrefs.lockScreenVisibility.first()
        val discreet = visibility != LockScreenVisibility.SHOW_WITH_NAMES

        // Check if the app has the critical alerts entitlement.
        // Apple is strict about granting this — if not authorized, fall back to timeSensitive.
        // Query once per reconcile (cached for all notifications in this batch).
        val canUseCritical = suspendCancellableCoroutine { cont ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                cont.resume(settings?.criticalAlertSetting == UNNotificationSettingEnabled)
            }
        }
        if (!canUseCritical) {
            AppLogger.d(TAG, "Critical alerts not authorized, falling back to timeSensitive")
        }

        // Check AlarmKit availability once per reconcile cycle
        val alarmKitReady = isAlarmKitAvailable() && isAlarmKitAuthorized()
        if (alarmKitReady) {
            AppLogger.d(TAG, "AlarmKit available and authorized — alarm-importance slots will use AlarmKit")
        }

        // Step 4: Schedule notifications + follow-ups, respecting budget.
        // Initial notifications are scheduled for the full 7-day window.
        // Follow-ups are only pre-scheduled within FOLLOWUP_WINDOW_MS (24h) to conserve budget.
        // AlarmKit alarms do NOT count against the notification budget.
        val followUpWindowEnd = now + FOLLOWUP_WINDOW_MS
        var scheduledCount = 0
        var followUpCount = 0
        var alarmKitCount = 0

        for ((slotTime, slotEvents) in timeSlots) {
            if (scheduledCount >= MAX_EVENT_NOTIFICATIONS) {
                AppLogger.w(
                    TAG,
                    "Reached event-notification ceiling ($MAX_EVENT_NOTIFICATIONS); " +
                        "$SNOOZE_RESERVED_SLOTS slots reserved for runtime snoozes",
                )
                break
            }

            // Skip snoozed slots — the snooze notification is already pending
            val existingSession = sessionManager.getSession(slotTime.toString())
            if (existingSession != null && existingSession.isSnoozed) {
                AppLogger.d(TAG, "Skipping snoozed slot $slotTime")
                continue
            }

            // Check if this slot has alarm-importance medications
            val hasAlarmMed = slotEvents.any { event ->
                val med = medicationDao.getByIdSync(event.medicationId)
                val label = med?.let { importanceLabelDao.getByIdSync(it.importanceLabelId) }
                label?.isAlarm == true
            }

            if (hasAlarmMed && alarmKitReady && slotTime > now) {
                // Route to AlarmKit — does NOT count against notification budget.
                // Past-pending alarm slots fall through to the UN path below:
                // AlarmKit cannot schedule a past fire time, but
                // scheduleGroupNotification's FollowUpConfig still carries
                // hasAlarm=true so the unfired tail of follow-up notifications
                // retains interruption level 4 (alarm).
                AppLogger.d(TAG, "Slot ${formatSlotTime(slotTime)}: using AlarmKit, hasAlarmMed=true")
                val followUpConfig = scheduleAlarmKitAlarmForSlot(slotTime, slotEvents, discreet)
                alarmKitCount++

                // Still schedule follow-ups as UNNotifications (they escalate independently)
                if (followUpConfig != null && slotTime <= followUpWindowEnd) {
                    val maxToSchedule = minOf(
                        followUpConfig.maxFollowUps,
                        MAX_EVENT_NOTIFICATIONS - scheduledCount,
                    )
                    for (i in 1..maxToSchedule) {
                        val followUpTime = slotTime + (i * followUpConfig.intervalMs)
                        if (followUpTime <= now) continue

                        scheduleFollowUpNotification(
                            center,
                            slotTime,
                            slotEvents,
                            i,
                            followUpConfig,
                            discreet,
                            canUseCritical,
                        )
                        scheduledCount++
                        followUpCount++

                        if (scheduledCount >= MAX_EVENT_NOTIFICATIONS) break
                    }
                }
            } else {
                // Standard path: schedule as UNNotification
                AppLogger.d(
                    TAG,
                    "Slot ${formatSlotTime(
                        slotTime,
                    )}: using UNNotification, ${slotEvents.size} meds. Budget remaining: ${MAX_EVENT_NOTIFICATIONS - scheduledCount}",
                )
                val followUpConfig = scheduleGroupNotification(
                    center,
                    slotTime,
                    slotEvents,
                    discreet,
                    canUseCritical,
                )
                scheduledCount++

                // Pre-schedule follow-ups for slots within the follow-up window
                if (followUpConfig != null && slotTime <= followUpWindowEnd) {
                    val maxToSchedule = minOf(
                        followUpConfig.maxFollowUps,
                        MAX_EVENT_NOTIFICATIONS - scheduledCount,
                    )
                    AppLogger.d(
                        TAG,
                        "Slot ${formatSlotTime(slotTime)}: scheduling $maxToSchedule follow-ups (within 24h window)",
                    )
                    for (i in 1..maxToSchedule) {
                        val followUpTime = slotTime + (i * followUpConfig.intervalMs)
                        if (followUpTime <= now) continue

                        scheduleFollowUpNotification(
                            center,
                            slotTime,
                            slotEvents,
                            i,
                            followUpConfig,
                            discreet,
                            canUseCritical,
                        )
                        scheduledCount++
                        followUpCount++

                        if (scheduledCount >= MAX_EVENT_NOTIFICATIONS) break
                    }
                } else if (followUpConfig != null) {
                    AppLogger.d(
                        TAG,
                        "Slot ${formatSlotTime(
                            slotTime,
                        )}: skipping ${followUpConfig.maxFollowUps} follow-ups (outside 24h window)",
                    )
                }
            }
        }

        // Step 5: Re-schedule snooze notifications for still-active snoozes.
        // The wipe in Step 1 removed them — we must restore them so the user
        // gets notified when the snooze expires. Filter each session's
        // scheduleIds against the current pending set so a snooze that expires
        // after the user has already logged some/all of its meds does not
        // surface those meds again (mis-trust → double dose risk).
        var restoredSnoozeCount = 0
        for (snoozeSession in activeSnoozedSessions) {
            val snoozeUntil = snoozeSession.snoozeUntilTime ?: continue
            val remainingMs = snoozeUntil - currentTimeMillis()
            if (remainingMs <= 0) continue

            val slotTime = snoozeSession.timeSlotKey.toLongOrNull() ?: continue

            val pendingForSlot = projector.getPendingEventsAtTime(slotTime)
                .map { it.scheduleId }
                .toSet()
            val stillPendingIds = computeStillPendingScheduleIds(
                snoozeSession.scheduleIds,
                pendingForSlot,
            )
            if (stillPendingIds.isEmpty()) {
                // All meds in this snoozed slot have been logged in-app during
                // the snooze period. Drop the session marker and cancel the
                // AlarmKit alarm (preserved in Step 1 because the slot was in
                // snoozedSlotKeys) so neither the UNNotification nor the
                // AlarmKit countdown re-surfaces already-taken meds.
                sessionManager.removeSession(snoozeSession.timeSlotKey)
                if (isAlarmKitAvailable()) {
                    cancelAlarmKitForSlot(slotTime)
                }
                continue
            }

            rescheduleSnoozeNotification(center, slotTime, remainingMs, stillPendingIds)
            restoredSnoozeCount++
        }

        AppLogger.i(
            TAG,
            "Reconciled: $scheduledCount notifications ($followUpCount follow-ups) + " +
                "$alarmKitCount AlarmKit alarms + $restoredSnoozeCount restored snooze notifications",
        )

        // Budget verification — log if event scheduling consumed >90% of its allocation.
        if (scheduledCount > (MAX_EVENT_NOTIFICATIONS * 9) / 10) {
            AppLogger.w(
                TAG,
                "Event-notification budget tight: $scheduledCount of $MAX_EVENT_NOTIFICATIONS used " +
                    "(snooze reserve: $SNOOZE_RESERVED_SLOTS)",
            )
        }
    }

    override suspend fun getDiagnosticSummary(): ReconcilerDiagnostic {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val now = currentTimeMillis()
        val windowEnd = now + LOOKAHEAD_MS

        // Get pending medication notifications
        val pendingRequests = suspendCancellableCoroutine<List<*>> { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                cont.resume(requests ?: emptyList<Any>())
            }
        }

        @Suppress("UNCHECKED_CAST")
        val medNotifications = (pendingRequests as List<UNNotificationRequest>)
            .filter { it.identifier.startsWith(NOTIFICATION_ID_PREFIX) }

        // Get delivered notifications
        val deliveredCount = suspendCancellableCoroutine { cont ->
            center.getDeliveredNotificationsWithCompletionHandler { notifications ->
                cont.resume((notifications ?: emptyList<Any>()).count())
            }
        }

        // Compare with pending events (count both UNNotifications and AlarmKit alarms)
        val pendingEvents = projector.projectEvents(now, windowEnd).filter { it.isPending }
        val alarmKitCount = alarmKitUUIDMap.size
        val totalScheduled = medNotifications.size + alarmKitCount

        val healthy = pendingEvents.isEmpty() || totalScheduled > 0
        val healthMessage = when {
            pendingEvents.isEmpty() -> "No pending events"
            totalScheduled == 0 -> "Warning: ${pendingEvents.size} pending events but no notifications scheduled"
            alarmKitCount > 0 -> "$totalScheduled scheduled (${medNotifications.size} notifications + $alarmKitCount AlarmKit) for ${pendingEvents.size} pending events"
            else -> "${medNotifications.size} notifications scheduled for ${pendingEvents.size} pending events"
        }

        val sessions = sessionManager.getAllSessions()

        // Timezone
        val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
        val nowInstant = kotlin.time.Instant.fromEpochMilliseconds(now)
        val localDt = nowInstant.toLocalDateTime(tz)
        val offsetSeconds = (
            (
                localDt.toInstant(
                    kotlinx.datetime.UtcOffset.ZERO,
                ).toEpochMilliseconds() - now
                ) / 1000
            ).toInt()

        val details = mutableMapOf(
            "pendingNotificationCount" to medNotifications.size.toString(),
            "deliveredNotificationCount" to deliveredCount.toString(),
            "pendingEventCount" to pendingEvents.size.toString(),
            "activeSessions" to sessions.size.toString(),
            "alarmKitAvailable" to isAlarmKitAvailable().toString(),
            "alarmKitScheduledCount" to alarmKitUUIDMap.size.toString(),
        )

        return ReconcilerDiagnostic(
            healthy = healthy,
            healthMessage = healthMessage,
            timezoneId = tz.id,
            utcOffsetSeconds = offsetSeconds,
            details = details,
        )
    }

    /**
     * Groups events by time slot. Events within [TIME_SLOT_TOLERANCE_MS] of each
     * other are considered the same slot. The slot key is the earliest event time.
     */
    private fun groupByTimeSlot(events: List<ProjectedEvent>): Map<Long, List<ProjectedEvent>> {
        val sorted = events.sortedBy { it.scheduledTime }
        val groups = mutableMapOf<Long, MutableList<ProjectedEvent>>()

        for (event in sorted) {
            // Find existing group within tolerance
            val existingKey = groups.keys.find { key ->
                kotlin.math.abs(event.scheduledTime - key) < TIME_SLOT_TOLERANCE_MS
            }

            if (existingKey != null) {
                groups[existingKey]!!.add(event)
            } else {
                groups[event.scheduledTime] = mutableListOf(event)
            }
        }

        return groups
    }

    private data class FollowUpConfig(
        val maxFollowUps: Int,
        val intervalMs: Long,
        val hasCritical: Boolean,
        val hasAlarm: Boolean,
        val criticalAfterFollowUp: Int?,
        val alarmAfterFollowUp: Int?,
        val medicationNames: List<String>,
    )

    /**
     * Schedules an AlarmKit alarm for a group of events at the same time slot.
     * Used when the slot contains alarm-importance medications and AlarmKit is available.
     *
     * AlarmKit alarms provide true system-level alarm behavior: screen wake,
     * Focus/silent bypass, Dynamic Island presence, and persistent Lock Screen UI.
     *
     * Returns FollowUpConfig if follow-ups should be scheduled, null otherwise.
     * AlarmKit alarms do NOT count against the 60-notification budget.
     */
    private suspend fun scheduleAlarmKitAlarmForSlot(
        slotTime: Long,
        events: List<ProjectedEvent>,
        discreet: Boolean,
    ): FollowUpConfig? {
        // Gather medication names and determine criticality
        val medicationNames = mutableListOf<String>()
        var hasCritical = false

        for (event in events) {
            val medication = medicationDao.getByIdSync(event.medicationId)
            if (medication != null) {
                medicationNames.add(medication.displayName ?: medication.name)
                val label = importanceLabelDao.getByIdSync(medication.importanceLabelId)
                if (label != null && label.isCritical) hasCritical = true
            }
        }

        if (medicationNames.isEmpty()) {
            AppLogger.w(TAG, "No valid medications found for AlarmKit slot $slotTime")
            return null
        }

        // Build alarm content — use same CMP resource strings as Android
        val count = medicationNames.size
        val timeStr = formatSlotTime(slotTime)
        val title = getPluralString(Res.plurals.notification_title_reminder, count)
        val body = if (discreet) {
            getPluralString(Res.plurals.notification_text_time_log_generic, count, timeStr, count)
        } else {
            getPluralString(
                Res.plurals.notification_text_time_log_named,
                count,
                timeStr,
                medicationNames.joinToString(", "),
            )
        }

        val scheduleIds = events.map { it.scheduleId }
        val uuid = NSUUID().UUIDString

        // Read snooze duration for AlarmKit's native countdown snooze
        val snoozeMinutes = notificationPrefs.snoozeIntervalMinutes.first()

        val success = scheduleAlarmKitAlarm(
            id = uuid,
            title = title,
            body = body,
            fireDateEpochMs = slotTime,
            slotTimeMs = slotTime,
            scheduleIds = scheduleIds.joinToString(","),
            medicationNames = medicationNames.joinToString(","),
            isCritical = hasCritical,
            snoozeDurationSeconds = snoozeMinutes * 60,
        )

        if (!success) {
            AppLogger.e(TAG, "AlarmKit scheduling failed for slot $slotTime, falling back to notification")
            // Fallback handled by caller — the slot won't have an alarm and won't be rescheduled
            // as a notification in this cycle. This is acceptable; next reconcile will retry.
            return null
        }

        // Create session for follow-up tracking (same as notification path)
        val maxFollowUps = computeMaxFollowUps(events)
        if (maxFollowUps > 0) {
            val followUpIntervalMs = computeFollowUpInterval(events)
            val criticalAfterFollowUp = computeCriticalAfterFollowUp(events)
            val alarmAfterFollowUp = computeAlarmAfterFollowUp(events)
            val session = NotificationSession(
                timeSlotKey = slotTime.toString(),
                scheduleIds = scheduleIds,
                notificationId = 0,
                maxFollowUps = maxFollowUps,
                followUpIntervalMs = followUpIntervalMs,
                nextFollowUpTime = slotTime + followUpIntervalMs,
                channelId = "alarm",
                hasCriticalMed = true,
                criticalAfterFollowUp = criticalAfterFollowUp,
                alarmAfterFollowUp = alarmAfterFollowUp,
                sessionType = SessionType.COMBINED,
                createdAt = currentTimeMillis(),
            )
            sessionManager.createSession(session)
            return FollowUpConfig(
                maxFollowUps = maxFollowUps,
                intervalMs = followUpIntervalMs,
                hasCritical = hasCritical,
                hasAlarm = true,
                criticalAfterFollowUp = criticalAfterFollowUp,
                alarmAfterFollowUp = alarmAfterFollowUp,
                medicationNames = medicationNames,
            )
        }
        return null
    }

    /**
     * Schedules a single notification for a group of events at the same time slot.
     * Returns FollowUpConfig if follow-ups should be scheduled, null otherwise.
     */
    private suspend fun scheduleGroupNotification(
        center: UNUserNotificationCenter,
        slotTime: Long,
        events: List<ProjectedEvent>,
        discreet: Boolean,
        canUseCritical: Boolean,
    ): FollowUpConfig? {
        // Gather medication names and determine criticality/alarm status
        val medicationNames = mutableListOf<String>()
        var hasCritical = false
        var hasAlarm = false

        for (event in events) {
            val medication = medicationDao.getByIdSync(event.medicationId)
            if (medication != null) {
                medicationNames.add(medication.displayName ?: medication.name)

                val label = importanceLabelDao.getByIdSync(medication.importanceLabelId)
                if (label != null) {
                    if (label.isAlarm) hasAlarm = true
                    if (label.isCritical) hasCritical = true
                }
            }
        }

        if (medicationNames.isEmpty()) {
            AppLogger.w(TAG, "No valid medications found for time slot $slotTime")
            return null
        }

        // Build notification content — use same CMP resource strings as Android
        val count = medicationNames.size
        val timeStr = formatSlotTime(slotTime)
        val content = UNMutableNotificationContent()
        content.setTitle(getPluralString(Res.plurals.notification_title_reminder, count))
        if (discreet) {
            content.setBody(getPluralString(Res.plurals.notification_text_time_log_generic, count, timeStr, count))
        } else {
            content.setBody(
                getPluralString(
                    Res.plurals.notification_text_time_log_named,
                    count,
                    timeStr,
                    medicationNames.joinToString(", "),
                ),
            )
        }

        content.setCategoryIdentifier(NOTIFICATION_CATEGORY_MEDICATION)
        content.setThreadIdentifier("hellomeds_$slotTime")

        // Set interruption level via Swift bridge (K/N can't reference UNNotificationInterruptionLevel).
        // Raw values: active=0, critical=1, passive=2, timeSensitive=3, alarm=4 (custom)
        // Note: The critical-alerts entitlement must be granted by Apple for critical/alarm to work.
        // Without it, canUseCritical is false and we gracefully fall back to timeSensitive.
        val allPlacebo = events.all { it.isPlacebo }
        if (allPlacebo) {
            // Placebo pills: use default active level, no escalation
            setInterruptionLevel(content, 0L)
            content.setSound(UNNotificationSound.defaultSound)
        } else if (hasAlarm && canUseCritical) {
            // Alarm: critical interruption + custom alarm sound via Swift bridge
            // Bridge level 4 uses criticalSoundNamed("alarm_sound.caf") which bypasses mute switch
            setInterruptionLevel(content, 4L)
        } else if (hasCritical && canUseCritical) {
            // Critical: bypasses Focus Mode AND mute switch
            setInterruptionLevel(content, 1L)
            content.setSound(UNNotificationSound.defaultCriticalSound)
        } else if (hasAlarm) {
            // Alarm without critical entitlement: timeSensitive + custom 30s alarm sound
            // soundNamed() respects mute switch (no bypass without entitlement)
            setInterruptionLevel(content, 3L)
            content.setSound(UNNotificationSound.soundNamed("alarm_sound.caf"))
        } else {
            // Time Sensitive: bypasses Focus Mode, stays on lock screen for 1 hour
            setInterruptionLevel(content, 3L)
            content.setSound(UNNotificationSound.defaultSound)
        }

        val scheduleIds = events.map { it.scheduleId }
        content.setUserInfo(
            mapOf<Any?, Any?>(
                "scheduledTime" to slotTime,
                "scheduleIds" to scheduleIds.joinToString(","),
                "type" to "medication_reminder",
                "isCritical" to hasCritical,
                "isAlarm" to hasAlarm,
            ),
        )

        // Create calendar-based trigger from the scheduled time
        val date = NSDate.dateWithTimeIntervalSince1970(slotTime / 1000.0)
        val calendar = NSCalendar.currentCalendar
        val components = calendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute,
            fromDate = date,
        )

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents = components,
            repeats = false,
        )

        // Build a unique identifier including schedule IDs for targeted removal
        val identifier =
            "${NOTIFICATION_ID_PREFIX}${slotTime}_${scheduleIds.sorted().joinToString("-")}"
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = trigger,
        )

        // Schedule the notification
        suspendCancellableCoroutine { cont ->
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    AppLogger.e(TAG, "Failed to schedule notification: ${error.localizedDescription}")
                } else {
                    AppLogger.d(
                        TAG,
                        "Scheduled notification $identifier for ${medicationNames.joinToString()}",
                    )
                }
                cont.resume(Unit)
            }
        }

        // Create session and return follow-up config
        val maxFollowUps = computeMaxFollowUps(events)
        if (maxFollowUps > 0) {
            val followUpIntervalMs = computeFollowUpInterval(events)
            val criticalAfterFollowUp = computeCriticalAfterFollowUp(events)
            val alarmAfterFollowUp = computeAlarmAfterFollowUp(events)
            val channelId = when {
                hasAlarm -> "alarm"
                hasCritical -> "critical"
                else -> "normal"
            }
            val session = NotificationSession(
                timeSlotKey = slotTime.toString(),
                scheduleIds = scheduleIds,
                notificationId = 0, // iOS uses string identifiers, not int IDs
                maxFollowUps = maxFollowUps,
                followUpIntervalMs = followUpIntervalMs,
                nextFollowUpTime = slotTime + followUpIntervalMs,
                channelId = channelId,
                hasCriticalMed = hasCritical || hasAlarm,
                criticalAfterFollowUp = criticalAfterFollowUp,
                alarmAfterFollowUp = alarmAfterFollowUp,
                sessionType = SessionType.COMBINED,
                createdAt = currentTimeMillis(),
            )
            sessionManager.createSession(session)
            return FollowUpConfig(
                maxFollowUps = maxFollowUps,
                intervalMs = followUpIntervalMs,
                hasCritical = hasCritical,
                hasAlarm = hasAlarm,
                criticalAfterFollowUp = criticalAfterFollowUp,
                alarmAfterFollowUp = alarmAfterFollowUp,
                medicationNames = medicationNames,
            )
        }
        return null
    }

    /**
     * Pre-schedules a follow-up notification for a time slot.
     * Content differs from initial: "Still need to take [med]" with follow-up numbering.
     * Escalates to critical on the final follow-up if criticalAfterFollowUp is configured.
     */
    private suspend fun scheduleFollowUpNotification(
        center: UNUserNotificationCenter,
        slotTime: Long,
        events: List<ProjectedEvent>,
        followUpNumber: Int,
        config: FollowUpConfig,
        discreet: Boolean,
        canUseCritical: Boolean,
    ) {
        val followUpTime = slotTime + (followUpNumber * config.intervalMs)
        val scheduleIds = events.map { it.scheduleId }

        val content = UNMutableNotificationContent()

        // Follow-up content — use same CMP resource strings as Android
        val count = config.medicationNames.size
        val timeStr = formatSlotTime(slotTime)
        val title = if (followUpNumber >= 2) {
            getPluralString(Res.plurals.notification_title_followup_numbered, count, followUpNumber)
        } else {
            getPluralString(Res.plurals.notification_title_followup, count)
        }
        content.setTitle(title)
        if (discreet) {
            content.setBody(getPluralString(Res.plurals.notification_text_time_log_generic, count, timeStr, count))
        } else {
            content.setBody(
                getPluralString(
                    Res.plurals.notification_text_time_log_named,
                    count,
                    timeStr,
                    config.medicationNames.joinToString(", "),
                ),
            )
        }

        content.setCategoryIdentifier(NOTIFICATION_CATEGORY_MEDICATION)
        content.setThreadIdentifier("hellomeds_$slotTime")

        // Two-stage escalation: alarm first (highest priority), then critical
        val shouldEscalateToAlarm = config.alarmAfterFollowUp != null &&
            followUpNumber >= config.alarmAfterFollowUp
        val shouldEscalateToCritical = config.criticalAfterFollowUp != null &&
            followUpNumber >= config.criticalAfterFollowUp
        val isAlarmNotif = config.hasAlarm || shouldEscalateToAlarm

        if (isAlarmNotif && canUseCritical) {
            setInterruptionLevel(content, 4L) // alarm (critical + custom sound via Swift bridge)
        } else if ((config.hasCritical || shouldEscalateToCritical) && canUseCritical) {
            setInterruptionLevel(content, 1L) // critical
            content.setSound(UNNotificationSound.defaultCriticalSound)
        } else if (isAlarmNotif) {
            // Alarm without critical entitlement: timeSensitive + custom 30s alarm sound
            setInterruptionLevel(content, 3L)
            content.setSound(UNNotificationSound.soundNamed("alarm_sound.caf"))
        } else {
            setInterruptionLevel(content, 3L) // timeSensitive
            content.setSound(UNNotificationSound.defaultSound)
        }

        content.setUserInfo(
            mapOf<Any?, Any?>(
                "scheduledTime" to slotTime,
                "scheduleIds" to scheduleIds.joinToString(","),
                "type" to "medication_reminder",
                "isCritical" to (config.hasCritical || shouldEscalateToCritical || isAlarmNotif),
                "isAlarm" to isAlarmNotif,
                "isFollowUp" to true,
                "followUpNumber" to followUpNumber,
            ),
        )

        // Calendar trigger for the follow-up time
        val date = NSDate.dateWithTimeIntervalSince1970(followUpTime / 1000.0)
        val calendar = NSCalendar.currentCalendar
        val components = calendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute,
            fromDate = date,
        )
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents = components,
            repeats = false,
        )

        val identifier =
            "${NOTIFICATION_ID_PREFIX}${slotTime}_${scheduleIds.sorted().joinToString("-")}_followup_$followUpNumber"
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = trigger,
        )

        suspendCancellableCoroutine { cont ->
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    AppLogger.e(TAG, "Failed to schedule follow-up #$followUpNumber: ${error.localizedDescription}")
                } else {
                    AppLogger.d(TAG, "Scheduled follow-up $identifier")
                }
                cont.resume(Unit)
            }
        }
    }

    // --- Follow-up config helpers (read from ImportanceLabel) ---

    private suspend fun computeMaxFollowUps(events: List<ProjectedEvent>): Int {
        var max = 0
        for (event in events) {
            val medication = medicationDao.getByIdSync(event.medicationId) ?: continue
            val label = importanceLabelDao.getByIdSync(medication.importanceLabelId) ?: continue
            if (label.hasFollowUps && label.followUpCount > max) {
                max = label.followUpCount
            }
        }
        return max
    }

    private suspend fun computeFollowUpInterval(events: List<ProjectedEvent>): Long {
        var intervalMinutes = 0
        for (event in events) {
            val medication = medicationDao.getByIdSync(event.medicationId) ?: continue
            val label = importanceLabelDao.getByIdSync(medication.importanceLabelId) ?: continue
            if (label.hasFollowUps && label.followUpIntervalMinutes > intervalMinutes) {
                intervalMinutes = label.followUpIntervalMinutes
            }
        }
        return intervalMinutes * 60 * 1000L
    }

    private suspend fun computeCriticalAfterFollowUp(events: List<ProjectedEvent>): Int? {
        for (event in events) {
            val medication = medicationDao.getByIdSync(event.medicationId) ?: continue
            val label = importanceLabelDao.getByIdSync(medication.importanceLabelId) ?: continue
            if (label.criticalAfterFollowUp != null) return label.criticalAfterFollowUp
        }
        return null
    }

    private suspend fun computeAlarmAfterFollowUp(events: List<ProjectedEvent>): Int? {
        for (event in events) {
            val medication = medicationDao.getByIdSync(event.medicationId) ?: continue
            val label = importanceLabelDao.getByIdSync(medication.importanceLabelId) ?: continue
            if (label.alarmAfterFollowUp != null) return label.alarmAfterFollowUp
        }
        return null
    }

    /**
     * Removes all pending snooze notifications. Called during full reconcile
     * since the wipe-and-reschedule cycle invalidates previous snooze state.
     */
    private suspend fun removeSnoozeNotifications() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        suspendCancellableCoroutine { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                @Suppress("UNCHECKED_CAST")
                val ids = (requests as? List<UNNotificationRequest>)
                    ?.filter { it.identifier.startsWith("snooze_") }
                    ?.map { it.identifier }
                    ?: emptyList()
                if (ids.isNotEmpty()) {
                    center.removePendingNotificationRequestsWithIdentifiers(ids)
                    AppLogger.d(TAG, "Removed ${ids.size} pending snooze notifications")
                }
                cont.resume(Unit)
            }
        }
    }

    /**
     * Re-schedules a snooze notification after reconcile wipe.
     * Uses the remaining snooze time as the trigger interval.
     */
    private fun rescheduleSnoozeNotification(
        center: UNUserNotificationCenter,
        slotTime: Long,
        remainingMs: Long,
        scheduleIds: List<Int>,
    ) {
        val content = UNMutableNotificationContent()
        content.setTitle("Snoozed")
        content.setBody("Medication reminder snoozed")
        content.setCategoryIdentifier(NOTIFICATION_CATEGORY_MEDICATION)
        content.setThreadIdentifier("hellomeds_$slotTime")
        setInterruptionLevel(content, 3L) // timeSensitive
        content.setSound(UNNotificationSound.defaultSound)
        content.setUserInfo(
            mapOf<Any?, Any?>(
                "scheduledTime" to slotTime,
                "scheduleIds" to scheduleIds.joinToString(","),
                "type" to "medication_reminder",
                "isCritical" to false,
                "isSnoozed" to true,
            ),
        )

        val intervalSeconds = maxOf(remainingMs / 1000.0, 1.0)
        val trigger = platform.UserNotifications.UNTimeIntervalNotificationTrigger
            .triggerWithTimeInterval(intervalSeconds, repeats = false)

        val now = currentTimeMillis()
        val identifier = "snooze_${slotTime}_$now"
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = trigger,
        )

        center.addNotificationRequest(request) { error ->
            if (error != null) {
                AppLogger.e(TAG, "Failed to restore snooze notification: ${error.localizedDescription}")
            } else {
                AppLogger.d(TAG, "Restored snooze $identifier (${intervalSeconds.toLong()}s remaining)")
            }
        }
    }

    /**
     * Removes all pending AND delivered medication notifications, awaiting completion of both.
     * Identifies medication notifications by the [NOTIFICATION_ID_PREFIX] prefix,
     * leaving snooze notifications ("snooze_") and other system notifications untouched.
     *
     * Both removal steps are fully awaited via suspendCancellableCoroutine. This prevents
     * a race condition where reconcile() adds new notifications before the wipe completes,
     * causing the OS to delete freshly-scheduled notifications.
     */
    private suspend fun removeAllMedicationNotifications() {
        val center = UNUserNotificationCenter.currentNotificationCenter()

        // Await pending notification removal (not yet fired)
        suspendCancellableCoroutine { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                @Suppress("UNCHECKED_CAST")
                val ids = (requests as? List<UNNotificationRequest>)
                    ?.filter { it.identifier.startsWith(NOTIFICATION_ID_PREFIX) }
                    ?.map { it.identifier }
                    ?: emptyList()
                if (ids.isNotEmpty()) {
                    center.removePendingNotificationRequestsWithIdentifiers(ids)
                    AppLogger.d(TAG, "Removed ${ids.size} pending medication notifications")
                }
                cont.resume(Unit)
            }
        }

        // Await delivered notification removal (visible on lock screen / notification center)
        suspendCancellableCoroutine { cont ->
            center.getDeliveredNotificationsWithCompletionHandler { delivered ->
                @Suppress("UNCHECKED_CAST")
                val ids = (delivered as? List<platform.UserNotifications.UNNotification>)
                    ?.filter { it.request.identifier.startsWith(NOTIFICATION_ID_PREFIX) }
                    ?.map { it.request.identifier }
                    ?: emptyList()
                if (ids.isNotEmpty()) {
                    center.removeDeliveredNotificationsWithIdentifiers(ids)
                    AppLogger.d(TAG, "Removed ${ids.size} delivered medication notifications")
                }
                cont.resume(Unit)
            }
        }
    }
}
