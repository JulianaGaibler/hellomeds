// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.repository.MedicationHistoryRepository
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger
import org.koin.mp.KoinPlatform
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.time.Clock

private const val TAG = "AlarmKitActionBridge"

/**
 * Action bridge invoked by AlarmKit LiveActivityIntents (Swift → Kotlin).
 *
 * LiveActivityIntents run in the main app process, so we have full access to Koin DI,
 * Room database, and all KMP infrastructure. This bridge reuses the same action logic
 * as [IOSNotificationDelegate.handleTaken] and [MainViewController.handleAlarmAction].
 *
 * All functions are file-level (no class) to avoid K/N class initialization issues
 * and to match the callback bridge pattern used elsewhere.
 */

private val scope = CoroutineScope(Dispatchers.Main)

/**
 * Wrap a suspending DB+reconcile action in a UIBackgroundTask so iOS does not
 * suspend the process mid-write. LiveActivityIntents wake the app briefly and
 * the OS reclaims us aggressively; without this, taps from Dynamic Island can
 * lose their write between the markAsTaken transaction and the reconciler
 * cycle. Matches the protection IOSNotificationDelegate uses for the same
 * class of action.
 */
private fun launchWithBackgroundTask(name: String, block: suspend () -> Unit) {
    var taskId = UIBackgroundTaskInvalid
    taskId = UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler {
        AppLogger.w(TAG, "$name background task expired before completion")
        if (taskId != UIBackgroundTaskInvalid) {
            UIApplication.sharedApplication.endBackgroundTask(taskId)
            taskId = UIBackgroundTaskInvalid
        }
    }
    scope.launch {
        try {
            block()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error in $name", e)
        } finally {
            if (taskId != UIBackgroundTaskInvalid) {
                UIApplication.sharedApplication.endBackgroundTask(taskId)
                taskId = UIBackgroundTaskInvalid
            }
        }
    }
}

/**
 * Called from [TakeMedicationIntent] when the user taps "Taken" on an AlarmKit alarm.
 *
 * Marks all medications at the given time slot as taken, cancels pending follow-up
 * UNNotifications and snooze notifications, cleans up the session, and triggers
 * reconciliation to replenish the notification schedule.
 *
 * @param slotTimeMs The scheduled time slot in epoch milliseconds
 * @param scheduleIds Comma-separated schedule IDs for this slot
 */
fun handleAlarmTaken(slotTimeMs: Long, scheduleIds: String) {
    val ids = scheduleIds.split(",").mapNotNull { it.trim().toIntOrNull() }
    if (ids.isEmpty()) {
        AppLogger.e(TAG, "handleAlarmTaken: no valid schedule IDs from '$scheduleIds'")
        return
    }

    AppLogger.d(TAG, "handleAlarmTaken: slot=$slotTimeMs, schedules=$ids")

    launchWithBackgroundTask("handleAlarmTaken") {
        val koin = KoinPlatform.getKoin()
        val projector = koin.get<ScheduleProjector>()
        val historyRepo = koin.get<MedicationHistoryRepository>()
        val sessionManager = koin.get<IOSNotificationSessionManager>()
        val reconciler = koin.get<ScheduleReconciler>()

        val events = projector.getPendingEventsAtTime(slotTimeMs)
        val now = Clock.System.now().toEpochMilliseconds()

        for (event in events) {
            if (event.scheduleId in ids && event.isPending) {
                historyRepo.markAsTaken(event, event.dose, now)
                AppLogger.d(TAG, "Marked schedule=${event.scheduleId} as taken")
            }
        }

        // Cancel pending follow-ups + snooze + delivered notifications
        sessionManager.cancelFollowUpsForSlot(slotTimeMs)
        sessionManager.cancelSnoozeForSlot(slotTimeMs)
        sessionManager.removeDeliveredForSlot(slotTimeMs)

        // Also cancel/stop any AlarmKit alarm for this slot
        cancelAlarmKitForSlot(slotTimeMs)

        // Clean up session
        val session = sessionManager.getSession(slotTimeMs.toString())
        if (session != null) {
            sessionManager.removeSession(session.timeSlotKey)
        }

        // Reconcile is also triggered by markAsTaken via the repository,
        // but call explicitly to ensure follow-up budget is replenished
        reconciler.reconcile()
    }
}

/**
 * Called from [OpenMedicationIntent] when the user taps "Open" on an AlarmKit alarm.
 *
 * Deep links to the alarm UI (ReminderAlarmScreen) via [NotificationDeepLinkState],
 * the same mechanism used for regular notification taps.
 *
 * @param slotTimeMs The scheduled time slot in epoch milliseconds
 * @param scheduleIds Comma-separated schedule IDs for this slot
 */
fun handleAlarmOpenApp(slotTimeMs: Long, scheduleIds: String) {
    val ids = scheduleIds.split(",").mapNotNull { it.trim().toIntOrNull() }
    if (ids.isEmpty()) {
        AppLogger.e(TAG, "handleAlarmOpenApp: no valid schedule IDs from '$scheduleIds'")
        return
    }

    AppLogger.d(TAG, "handleAlarmOpenApp: slot=$slotTimeMs, schedules=$ids")

    // Open app with the log scheduled medication bottom sheet (same as tapping a regular notification).
    // AlarmKit already shows its own native alarm screen — no need for our alarm overlay.
    NotificationDeepLinkState.setPending(ids.toIntArray(), isAlarm = false)
}

/**
 * Called from [SnoozeMedicationIntent] when the user taps "Snooze" on an AlarmKit alarm.
 *
 * Snoozes all medications at the given time slot, cancels pending follow-ups,
 * and triggers reconciliation.
 *
 * @param slotTimeMs The scheduled time slot in epoch milliseconds
 * @param scheduleIds Comma-separated schedule IDs for this slot
 */
fun handleAlarmSnooze(slotTimeMs: Long, scheduleIds: String) {
    val ids = scheduleIds.split(",").mapNotNull { it.trim().toIntOrNull() }
    if (ids.isEmpty()) {
        AppLogger.e(TAG, "handleAlarmSnooze: no valid schedule IDs from '$scheduleIds'")
        return
    }

    AppLogger.d(TAG, "handleAlarmSnooze: slot=$slotTimeMs, schedules=$ids")

    launchWithBackgroundTask("handleAlarmSnooze") {
        val koin = KoinPlatform.getKoin()
        val sessionManager = koin.get<IOSNotificationSessionManager>()
        val notifPrefs = koin.get<me.juliana.hellomeds.data.preferences.NotificationPreferences>()

        val snoozeMinutes = notifPrefs.snoozeIntervalMinutes.first()
        val snoozeUntil = Clock.System.now().toEpochMilliseconds() + (snoozeMinutes * 60_000L)

        // Cancel follow-up UNNotifications and clear delivered — snooze takes priority
        sessionManager.cancelFollowUpsForSlot(slotTimeMs)
        sessionManager.removeDeliveredForSlot(slotTimeMs)

        // Do NOT cancel the AlarmKit alarm — its native countdown manages the
        // Dynamic Island / Lock Screen presence and will re-fire after the duration.
        // Do NOT call reconcile — it would wipe the countdown via cancelAllAlarmKitAlarms.

        val session = sessionManager.getSession(slotTimeMs.toString())
        if (session != null) {
            sessionManager.snoozeSession(session.timeSlotKey, snoozeUntil)
        }
    }
}

/**
 * Called from [SkipMedicationIntent] when the user taps "Skipped" on an AlarmKit alarm
 * (available in Dynamic Island expanded view).
 *
 * Marks all medications at the given time slot as skipped, cancels pending follow-ups,
 * and triggers reconciliation.
 *
 * @param slotTimeMs The scheduled time slot in epoch milliseconds
 * @param scheduleIds Comma-separated schedule IDs for this slot
 */
fun handleAlarmSkipped(slotTimeMs: Long, scheduleIds: String) {
    val ids = scheduleIds.split(",").mapNotNull { it.trim().toIntOrNull() }
    if (ids.isEmpty()) {
        AppLogger.e(TAG, "handleAlarmSkipped: no valid schedule IDs from '$scheduleIds'")
        return
    }

    AppLogger.d(TAG, "handleAlarmSkipped: slot=$slotTimeMs, schedules=$ids")

    launchWithBackgroundTask("handleAlarmSkipped") {
        val koin = KoinPlatform.getKoin()
        val projector = koin.get<ScheduleProjector>()
        val historyRepo = koin.get<MedicationHistoryRepository>()
        val sessionManager = koin.get<IOSNotificationSessionManager>()
        val reconciler = koin.get<ScheduleReconciler>()

        val events = projector.getPendingEventsAtTime(slotTimeMs)

        for (event in events) {
            if (event.scheduleId in ids && event.isPending) {
                historyRepo.markAsSkipped(event)
                AppLogger.d(TAG, "Marked schedule=${event.scheduleId} as skipped")
            }
        }

        sessionManager.cancelFollowUpsForSlot(slotTimeMs)
        sessionManager.cancelSnoozeForSlot(slotTimeMs)
        sessionManager.removeDeliveredForSlot(slotTimeMs)
        cancelAlarmKitForSlot(slotTimeMs)

        val session = sessionManager.getSession(slotTimeMs.toString())
        if (session != null) {
            sessionManager.removeSession(session.timeSlotKey)
        }

        reconciler.reconcile()
    }
}
