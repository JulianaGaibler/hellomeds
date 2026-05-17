// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.repository.MedicationHistoryRepository
import me.juliana.hellomeds.data.repository.StockTrackingRepository
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger
import kotlin.time.Clock
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.notification_text_time_log_generic
import me.juliana.hellomeds.shared.notification_text_time_log_named
import me.juliana.hellomeds.shared.notification_title_snoozed
import org.jetbrains.compose.resources.getPluralString
import org.koin.mp.KoinPlatform
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSString
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

private const val DELEGATE_TAG = "IOSNotificationDelegate"

/**
 * Handles notification interactions on iOS:
 * - User tapping a notification (default action)
 * - User choosing Take/Skip/Snooze from notification actions
 * - Foreground presentation policy
 *
 * Registered as UNUserNotificationCenter.delegate during app initialization.
 * Uses Koin to resolve dependencies lazily when actions are received.
 *
 * Note: Kotlin/Native does not support companion objects with fields in NSObject subclasses,
 * so constants are declared at file level.
 */
class IOSNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {

    // Lazy resolution: this delegate is registered as UNUserNotificationCenter.delegate
    // during cold launch (pre-UI), but its callback methods only fire later when the
    // user interacts with a notification — which always happens after first unlock.
    // Eager constructor injection would pull AppDatabase through these deps, and a
    // foreground launch attempt while the Keychain is locked would crash. Lazy
    // delegates push DB resolution to action-handling time.
    // Note: reconciler was injected via the old constructor but never referenced — repository
    // methods (markAsTaken / markAsSkipped) trigger reconcile internally, so we drop it here.
    private val projector: ScheduleProjector by lazy { KoinPlatform.getKoin().get() }
    private val historyRepo: MedicationHistoryRepository by lazy { KoinPlatform.getKoin().get() }
    private val notificationPrefs: NotificationPreferences by lazy { KoinPlatform.getKoin().get() }
    private val sessionManager: IOSNotificationSessionManager by lazy { KoinPlatform.getKoin().get() }
    private val medicationDao: MedicationDao by lazy { KoinPlatform.getKoin().get() }
    private val stockTrackingRepo: StockTrackingRepository by lazy { KoinPlatform.getKoin().get() }

    // Property rather than constructor parameter because this class is an NSObject
    // subclass with a parameterless constructor (delegate is created at cold launch
    // before Koin builds the AppDatabase — see comment above on lazy deps).
    private val clock: Clock = Clock.System

    private val scope = CoroutineScope(Dispatchers.Main)

    // Background-launched notification actions (e.g. Mark Depleted from a dead-state
    // app) need a scope that won't be torn down by transient UI lifecycle changes.
    // Default dispatcher because Kotlin/Native's `Dispatchers.IO` is internal —
    // Default is the closest publicly-available pool. Supervised so a failure
    // here can't poison the medication-reminder scope above.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Called when the user interacts with a notification (tap, or action button).
     * Parses the event info from userInfo and delegates to action handlers.
     */
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val response = didReceiveNotificationResponse
        val actionId = response.actionIdentifier
        val userInfo = response.notification.request.content.userInfo

        // Extract event metadata from notification userInfo
        val type = userInfo["type"] as? String

        when (type) {
            NOTIFICATION_TYPE_DEPLETION_REMINDER -> {
                handleDepletionResponse(actionId, userInfo, withCompletionHandler)
                return
            }
            NOTIFICATION_TYPE_LOW_STOCK -> {
                // No interactive actions on the low-stock notification. Default
                // tap just opens the app; nothing to do here beyond completing.
                AppLogger.d(DELEGATE_TAG, "Low-stock notification tapped — opening app")
                withCompletionHandler()
                return
            }
            NOTIFICATION_TYPE_MEDICATION_REMINDER -> {
                // Fall through to the medication-reminder handling below.
            }
            else -> {
                AppLogger.d(DELEGATE_TAG, "Ignoring notification action with unknown type: $type")
                withCompletionHandler()
                return
            }
        }

        val scheduledTime = (userInfo["scheduledTime"] as? Long)
            ?: (userInfo["scheduledTime"] as? Number)?.toLong()
        val scheduleIdsStr = userInfo["scheduleIds"] as? String

        if (scheduledTime == null || scheduleIdsStr == null) {
            AppLogger.e(DELEGATE_TAG, "Missing event metadata in notification userInfo")
            withCompletionHandler()
            return
        }

        val scheduleIds = scheduleIdsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (scheduleIds.isEmpty()) {
            AppLogger.e(DELEGATE_TAG, "No valid schedule IDs parsed from: $scheduleIdsStr")
            withCompletionHandler()
            return
        }

        val isCritical = (userInfo["isCritical"] as? Boolean) ?: false
        val isAlarm = (userInfo["isAlarm"] as? Boolean) ?: false

        // Protect DB writes with a UIBackgroundTask. If the app is backgrounded/terminated,
        // iOS gives very limited execution time. Without this, iOS may kill the process before
        // withCompletionHandler() is called, penalizing the app and losing the action.
        var taskId = UIBackgroundTaskInvalid
        taskId = UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler {
            // OS is killing us — call completion handler to avoid system penalty
            withCompletionHandler()
            UIApplication.sharedApplication.endBackgroundTask(taskId)
        }

        scope.launch {
            try {
                when (actionId) {
                    NOTIFICATION_ACTION_TAKE -> handleTaken(scheduledTime, scheduleIds)
                    NOTIFICATION_ACTION_SKIP -> handleSkipped(scheduledTime, scheduleIds)
                    NOTIFICATION_ACTION_SNOOZE -> handleSnooze(scheduledTime, scheduleIds, isCritical, isAlarm)
                    // Default action (user tapped the notification) — open app
                    // Alarm notifications show fullscreen alarm screen; others show log sheet
                    else -> {
                        if (isAlarm) {
                            AppLogger.d(
                                DELEGATE_TAG,
                                "Alarm notification tapped — deep linking to alarm screen for ${scheduleIds.size} schedules",
                            )
                            // Look up medication names for the alarm screen display
                            val events = projector.getPendingEventsAtTime(scheduledTime)
                            val medNames = events
                                .filter { it.scheduleId in scheduleIds }
                                .mapNotNull { event ->
                                    medicationDao.getByIdSync(event.medicationId)
                                        ?.let { it.displayName ?: it.name }
                                }
                            NotificationDeepLinkState.setAlarmData(medNames, scheduledTime)
                        } else {
                            AppLogger.d(
                                DELEGATE_TAG,
                                "Notification tapped — deep linking to log sheet for ${scheduleIds.size} schedules",
                            )
                        }
                        NotificationDeepLinkState.setPending(scheduleIds.toIntArray(), isAlarm = isAlarm)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(DELEGATE_TAG, "Error processing notification action: $actionId", e)
            } finally {
                withCompletionHandler()
                if (taskId != UIBackgroundTaskInvalid) {
                    UIApplication.sharedApplication.endBackgroundTask(taskId)
                }
            }
        }
    }

    /**
     * Called when a notification arrives while the app is in the foreground.
     * Suppresses stale follow-ups (user already acted in-app) and shows valid ones.
     */
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        val userInfo = willPresentNotification.request.content.userInfo
        val type = userInfo["type"] as? String
        val scheduledTime = (userInfo["scheduledTime"] as? Long)
            ?: (userInfo["scheduledTime"] as? Number)?.toLong()
        val isFollowUp = (userInfo["isFollowUp"] as? Boolean) ?: false

        // For medication reminders in foreground: suppress if stale, replace if follow-up
        if (type == "medication_reminder" && scheduledTime != null) {
            val scheduleIdsStr = userInfo["scheduleIds"] as? String ?: ""
            val scheduleIds = scheduleIdsStr.split(",").mapNotNull { it.trim().toIntOrNull() }

            scope.launch {
                val events = findMatchingEvents(scheduledTime, scheduleIds)
                val stillPending = events.any { it.isPending }

                if (!stillPending) {
                    // All events completed — suppress and clean up remaining follow-ups
                    AppLogger.d(DELEGATE_TAG, "Suppressing stale notification for slot $scheduledTime")
                    withCompletionHandler(0UL)
                    sessionManager.cancelFollowUpsForSlot(scheduledTime)
                    return@launch
                }

                // For alarm notifications in foreground: show fullscreen alarm overlay
                // instead of a banner notification
                val isAlarmNotif = (userInfo["isAlarm"] as? Boolean) ?: false
                if (isAlarmNotif) {
                    AppLogger.d(DELEGATE_TAG, "Alarm notification in foreground — showing alarm overlay")
                    withCompletionHandler(0UL) // suppress banner
                    val medNames = events
                        .filter { it.scheduleId in scheduleIds }
                        .mapNotNull { event ->
                            medicationDao.getByIdSync(event.medicationId)
                                ?.let { it.displayName ?: it.name }
                        }
                    NotificationDeepLinkState.setAlarmData(medNames, scheduledTime)
                    NotificationDeepLinkState.setPending(scheduleIds.toIntArray(), isAlarm = true)
                    return@launch
                }

                // Remove older delivered notifications for this slot before showing new one.
                // This prevents stacking: the follow-up replaces the initial (foreground only).
                if (isFollowUp) {
                    sessionManager.removeDeliveredForSlot(scheduledTime)
                }

                withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
            }
            return
        }

        // Non-follow-up or non-medication: show normally
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
    }

    /**
     * Marks all events at the given time slot as taken.
     * Also cancels any pre-scheduled follow-ups and cleans up the session.
     */
    /**
     * Routes depletion-reminder notification responses. The only interactive
     * action is "Mark Depleted"; the default tap just opens the app.
     *
     * Runs the DB write on [backgroundScope] (IO dispatcher, supervised) so
     * that a background-launched action survives transient UI lifecycle
     * changes that would cancel [scope].
     */
    private fun handleDepletionResponse(actionId: String, userInfo: Map<Any?, *>, withCompletionHandler: () -> Unit) {
        // Defensive double-cast: NSDictionary bridging may surface the value
        // as NSString rather than a Kotlin String depending on how the entry
        // was inserted on the originating side.
        val medIdString = (userInfo["medicationId"] as? String)
            ?: (userInfo["medicationId"] as? NSString)?.toString()
        val medicationId = medIdString?.toIntOrNull()

        if (actionId != NOTIFICATION_ACTION_MARK_DEPLETED) {
            // Default tap or unknown action — just open the app.
            AppLogger.d(DELEGATE_TAG, "Depletion notification tapped (action=$actionId)")
            withCompletionHandler()
            return
        }

        if (medicationId == null) {
            AppLogger.e(DELEGATE_TAG, "Mark Depleted action missing medicationId in userInfo")
            withCompletionHandler()
            return
        }

        var taskId = UIBackgroundTaskInvalid
        taskId = UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler {
            withCompletionHandler()
            UIApplication.sharedApplication.endBackgroundTask(taskId)
        }

        backgroundScope.launch {
            try {
                stockTrackingRepo.recordContainerDepleted(medicationId)
                AppLogger.i(DELEGATE_TAG, "Recorded CONTAINER_DEPLETED for medicationId=$medicationId")
            } catch (e: Exception) {
                AppLogger.e(DELEGATE_TAG, "Failed to record container depleted for $medicationId", e)
            } finally {
                withCompletionHandler()
                if (taskId != UIBackgroundTaskInvalid) {
                    UIApplication.sharedApplication.endBackgroundTask(taskId)
                }
            }
        }
    }

    private suspend fun handleTaken(scheduledTime: Long, scheduleIds: List<Int>) {
        AppLogger.d(DELEGATE_TAG, "Processing TAKE for ${scheduleIds.size} schedules at $scheduledTime")

        val events = findMatchingEvents(scheduledTime, scheduleIds)
        val now = clock.now().toEpochMilliseconds()

        for (event in events) {
            if (event.isPending) {
                historyRepo.markAsTaken(event, event.dose, now)
                AppLogger.d(DELEGATE_TAG, "Marked schedule=${event.scheduleId} as taken")
            }
        }

        // Cancel pending follow-ups + snooze for this slot
        sessionManager.cancelFollowUpsForSlot(scheduledTime)
        sessionManager.cancelSnoozeForSlot(scheduledTime)
        sessionManager.removeDeliveredForSlot(scheduledTime)

        // Also cancel/stop any AlarmKit alarm for this slot
        cancelAlarmKitForSlot(scheduledTime)
        stopAlarmKitForSlot(scheduledTime)

        // Clean up session
        val session = sessionManager.getSessionForSchedule(scheduledTime, scheduleIds.first())
        if (session != null) {
            sessionManager.removeSession(session.timeSlotKey)
        }

        // Reconcile will be called by markAsTaken via the repository
    }

    /**
     * Marks all events at the given time slot as skipped.
     * Also cancels any pre-scheduled follow-ups and cleans up the session.
     */
    private suspend fun handleSkipped(scheduledTime: Long, scheduleIds: List<Int>) {
        AppLogger.d(DELEGATE_TAG, "Processing SKIP for ${scheduleIds.size} schedules at $scheduledTime")

        val events = findMatchingEvents(scheduledTime, scheduleIds)

        for (event in events) {
            if (event.isPending) {
                historyRepo.markAsSkipped(event)
                AppLogger.d(DELEGATE_TAG, "Marked schedule=${event.scheduleId} as skipped")
            }
        }

        // Cancel pending follow-ups + snooze for this slot
        sessionManager.cancelFollowUpsForSlot(scheduledTime)
        sessionManager.cancelSnoozeForSlot(scheduledTime)
        sessionManager.removeDeliveredForSlot(scheduledTime)

        // Also cancel/stop any AlarmKit alarm for this slot
        cancelAlarmKitForSlot(scheduledTime)
        stopAlarmKitForSlot(scheduledTime)

        // Clean up session
        val session = sessionManager.getSessionForSchedule(scheduledTime, scheduleIds.first())
        if (session != null) {
            sessionManager.removeSession(session.timeSlotKey)
        }

        // Reconcile will be called by markAsSkipped via the repository
    }

    /**
     * Snoozes by cancelling pending follow-ups, recording snooze state in the session,
     * and scheduling a single snooze notification with medication names.
     */
    private suspend fun handleSnooze(
        scheduledTime: Long,
        scheduleIds: List<Int>,
        isCritical: Boolean,
        isAlarm: Boolean = false,
    ) {
        val snoozeMinutes = notificationPrefs.snoozeIntervalMinutes.first()
        val now = clock.now().toEpochMilliseconds()
        val snoozeUntil = now + (snoozeMinutes * 60 * 1000L)

        AppLogger.d(
            DELEGATE_TAG,
            "Snoozing ${scheduleIds.size} schedules for $snoozeMinutes minutes (critical=$isCritical)",
        )

        // Cancel all pending follow-ups for this slot — snooze takes priority
        sessionManager.cancelFollowUpsForSlot(scheduledTime)

        // Update session with snooze state
        val session = sessionManager.getSessionForSchedule(scheduledTime, scheduleIds.first())
        if (session != null) {
            sessionManager.snoozeSession(session.timeSlotKey, snoozeUntil)
        }

        val events = findMatchingEvents(scheduledTime, scheduleIds)
        if (events.isEmpty()) {
            AppLogger.w(DELEGATE_TAG, "No matching events found for snooze")
            return
        }

        // Gather medication names for the snooze notification
        val medNames = events.mapNotNull { event ->
            medicationDao.getByIdSync(event.medicationId)
        }.map { it.displayName ?: it.name }

        // Build snooze notification — use same CMP resource strings as Android
        val count = medNames.size
        val formatter = NSDateFormatter().apply { dateFormat = "HH:mm" }
        val timeStr = formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(scheduledTime / 1000.0))
        val visibility = notificationPrefs.lockScreenVisibility.first()
        val discreet = visibility != LockScreenVisibility.SHOW_WITH_NAMES

        val center = UNUserNotificationCenter.currentNotificationCenter()
        val content = UNMutableNotificationContent()

        content.setTitle(getPluralString(Res.plurals.notification_title_snoozed, count))
        if (discreet) {
            content.setBody(getPluralString(Res.plurals.notification_text_time_log_generic, count, timeStr, count))
        } else {
            content.setBody(
                getPluralString(
                    Res.plurals.notification_text_time_log_named,
                    count,
                    timeStr,
                    medNames.joinToString(", "),
                ),
            )
        }

        content.setCategoryIdentifier(NOTIFICATION_CATEGORY_MEDICATION)
        content.setThreadIdentifier("hellomeds_$scheduledTime")

        if (isAlarm && isCritical) {
            // Alarm with critical entitlement: critical + custom alarm sound
            setInterruptionLevel(content, 4L)
        } else if (isCritical) {
            setInterruptionLevel(content, 1L) // critical
            content.setSound(UNNotificationSound.defaultCriticalSound)
        } else if (isAlarm) {
            // Alarm without critical entitlement: timeSensitive + custom alarm sound
            setInterruptionLevel(content, 3L)
            content.setSound(UNNotificationSound.soundNamed("alarm_sound.caf"))
        } else {
            setInterruptionLevel(content, 3L) // timeSensitive
            content.setSound(UNNotificationSound.defaultSound)
        }

        content.setUserInfo(
            mapOf<Any?, Any?>(
                "scheduledTime" to scheduledTime,
                "scheduleIds" to scheduleIds.joinToString(","),
                "type" to "medication_reminder",
                "isCritical" to isCritical,
                "isSnoozed" to true,
            ),
        )

        val intervalSeconds = (snoozeMinutes * 60).toDouble()
        val trigger = UNTimeIntervalNotificationTrigger
            .triggerWithTimeInterval(intervalSeconds, repeats = false)

        // Unique snooze ID — distinct from follow-up IDs to avoid accidental cleanup
        val identifier = "snooze_${scheduledTime}_$now"
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = trigger,
        )

        center.addNotificationRequest(request) { error ->
            if (error != null) {
                AppLogger.e(DELEGATE_TAG, "Failed to schedule snooze: ${error.localizedDescription}")
            } else {
                AppLogger.d(DELEGATE_TAG, "Scheduled snooze $identifier in $snoozeMinutes minutes")
            }
        }
    }

    /**
     * Finds projected events matching the given time slot and schedule IDs.
     */
    private suspend fun findMatchingEvents(scheduledTime: Long, scheduleIds: List<Int>): List<ProjectedEvent> {
        val tolerance = 60_000L
        val events = projector.projectEvents(scheduledTime - tolerance, scheduledTime + tolerance)
        return events.filter { event ->
            event.scheduleId in scheduleIds &&
                kotlin.math.abs(event.scheduledTime - scheduledTime) < tolerance
        }
    }
}

// setInterruptionLevel is imported from IOSScheduleReconciler.kt via the shared bridge
