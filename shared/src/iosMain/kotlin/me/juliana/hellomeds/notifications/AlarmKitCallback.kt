// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlinx.coroutines.suspendCancellableCoroutine
import me.juliana.hellomeds.data.util.AppLogger
import kotlin.coroutines.resume

/**
 * Callback bridge for Apple AlarmKit (iOS 26+).
 *
 * AlarmKit is a Swift-only framework that provides system-level alarms (screen wake,
 * Focus/silent bypass, Dynamic Island presence). Since K/N can't import AlarmKit directly,
 * we use the same callback bridge pattern as [FoundationModelCallback]:
 *
 * 1. Swift registers callbacks during app init via [registerAlarmKitBridge]
 * 2. Kotlin calls scheduling/cancellation functions which invoke the registered callbacks
 * 3. Swift executes the AlarmKit API and returns results via completion handlers
 *
 * All file-level variables (no class) to avoid K/N class initialization issues.
 */

private const val TAG = "AlarmKitCallback"

// --- Bridge state ---

private var alarmKitAvailable: Boolean = false

// --- Scheduling callbacks (Kotlin → Swift) ---

/**
 * Schedule an AlarmKit alarm.
 * Params: (id: String, title: String, body: String, fireDateEpochMs: Long,
 *          slotTimeMs: Long, scheduleIds: String, medicationNames: String,
 *          isCritical: Boolean, snoozeDurationSeconds: Int, completion: (Boolean) -> Unit)
 */
private var scheduleAlarmCallback:
    ((String, String, String, Long, Long, String, String, Boolean, Int, (Boolean) -> Unit) -> Unit)? = null

/** Cancel an alarm by ID (removes it from the schedule entirely). */
private var cancelAlarmCallback: ((String) -> Unit)? = null

/** Stop an alarm by ID (dismisses a ringing alarm). */
private var stopAlarmCallback: ((String) -> Unit)? = null

/** Cancel all HelloMeds AlarmKit alarms. Completion called when done. */
private var cancelAllAlarmsCallback: ((() -> Unit) -> Unit)? = null

// --- Authorization callbacks ---

/** Request AlarmKit authorization. Completion returns true if granted. */
private var requestAuthCallback: (((Boolean) -> Unit) -> Unit)? = null

/** Check current AlarmKit authorization status. Completion returns true if authorized. */
private var checkAuthCallback: (((Boolean) -> Unit) -> Unit)? = null

// --- In-memory mapping: timeSlotKey → AlarmKit UUID string ---
// Used by IOSNotificationSessionManager to cancel specific alarms when the user
// acts on a regular UNNotification follow-up (not via AlarmKit intent).
internal val alarmKitUUIDMap = mutableMapOf<String, String>()

/**
 * Called from Swift during app initialization to register AlarmKit callbacks.
 * Must be called synchronously before any scheduling occurs.
 *
 * When AlarmKit is unavailable (iOS < 26), pass isAvailable=false and null callbacks.
 */
fun registerAlarmKitBridge(
    isAvailable: Boolean,
    scheduleAlarm: ((String, String, String, Long, Long, String, String, Boolean, Int, (Boolean) -> Unit) -> Unit)?,
    cancelAlarm: ((String) -> Unit)?,
    stopAlarm: ((String) -> Unit)?,
    cancelAllAlarms: ((() -> Unit) -> Unit)?,
    requestAuth: (((Boolean) -> Unit) -> Unit)?,
    checkAuth: (((Boolean) -> Unit) -> Unit)?,
) {
    alarmKitAvailable = isAvailable
    scheduleAlarmCallback = scheduleAlarm
    cancelAlarmCallback = cancelAlarm
    stopAlarmCallback = stopAlarm
    cancelAllAlarmsCallback = cancelAllAlarms
    requestAuthCallback = requestAuth
    checkAuthCallback = checkAuth

    if (isAvailable) {
        AppLogger.i(TAG, "AlarmKit bridge registered (available)")
    } else {
        AppLogger.d(TAG, "AlarmKit bridge registered (unavailable — iOS < 26)")
    }
}

/**
 * Returns true if AlarmKit is available on this device (iOS 26+).
 */
fun isAlarmKitAvailable(): Boolean = alarmKitAvailable

/**
 * Schedules an AlarmKit alarm for a medication time slot.
 *
 * @param id Unique alarm identifier (UUID string)
 * @param title Alarm title text
 * @param body Alarm body text (medication names or generic message)
 * @param fireDateEpochMs When the alarm should fire (epoch milliseconds)
 * @param slotTimeMs Original time slot key (epoch milliseconds)
 * @param scheduleIds Comma-separated schedule IDs for this slot
 * @param medicationNames Comma-separated medication names
 * @param isCritical Whether any medication in this slot is critical
 * @return true if successfully scheduled, false on failure or if bridge unavailable
 */
suspend fun scheduleAlarmKitAlarm(
    id: String,
    title: String,
    body: String,
    fireDateEpochMs: Long,
    slotTimeMs: Long,
    scheduleIds: String,
    medicationNames: String,
    isCritical: Boolean,
    snoozeDurationSeconds: Int,
): Boolean {
    val callback = scheduleAlarmCallback ?: return false
    if (!alarmKitAvailable) return false

    // Track the mapping for cancellation from notification delegate
    alarmKitUUIDMap[slotTimeMs.toString()] = id

    return suspendCancellableCoroutine { cont ->
        callback(id, title, body, fireDateEpochMs, slotTimeMs, scheduleIds, medicationNames, isCritical, snoozeDurationSeconds) { success ->
            if (success) {
                AppLogger.d(TAG, "Scheduled AlarmKit alarm $id for slot $slotTimeMs")
            } else {
                AppLogger.e(TAG, "Failed to schedule AlarmKit alarm $id")
                alarmKitUUIDMap.remove(slotTimeMs.toString())
            }
            cont.resume(success)
        }
    }
}

/**
 * Cancels a specific AlarmKit alarm by ID (removes it from the schedule).
 * Non-blocking — fire-and-forget.
 */
fun cancelAlarmKitAlarm(id: String) {
    val callback = cancelAlarmCallback ?: return
    if (!alarmKitAvailable) return
    callback(id)
    AppLogger.d(TAG, "Cancelled AlarmKit alarm $id")
}

/**
 * Stops a specific AlarmKit alarm by ID (dismisses a ringing alarm).
 * Non-blocking — fire-and-forget.
 */
fun stopAlarmKitAlarm(id: String) {
    val callback = stopAlarmCallback ?: return
    if (!alarmKitAvailable) return
    callback(id)
    AppLogger.d(TAG, "Stopped AlarmKit alarm $id")
}

/**
 * Cancels all HelloMeds AlarmKit alarms. Used during reconcile wipe.
 * Suspends until all alarms are cancelled.
 */
suspend fun cancelAllAlarmKitAlarms() {
    val callback = cancelAllAlarmsCallback ?: return
    if (!alarmKitAvailable) return

    alarmKitUUIDMap.clear()

    suspendCancellableCoroutine { cont ->
        callback {
            AppLogger.d(TAG, "Cancelled all AlarmKit alarms")
            cont.resume(Unit)
        }
    }
}

/**
 * Cancels all AlarmKit alarms EXCEPT those for snoozed time slots.
 * Preserves the native AlarmKit countdown (Dynamic Island / Lock Screen) for active snoozes.
 *
 * @param snoozedSlotKeys Set of time slot keys (epoch ms strings) that are currently snoozed
 */
fun cancelNonSnoozedAlarmKitAlarms(snoozedSlotKeys: Set<String>) {
    if (!alarmKitAvailable) return

    val toCancel = alarmKitUUIDMap.entries
        .filter { it.key !in snoozedSlotKeys }
        .toList()

    for ((key, uuid) in toCancel) {
        cancelAlarmKitAlarm(uuid)
        alarmKitUUIDMap.remove(key)
    }

    AppLogger.d(
        TAG,
        "Cancelled ${toCancel.size} non-snoozed AlarmKit alarms, preserved ${alarmKitUUIDMap.size} snoozed",
    )
}

/**
 * Requests AlarmKit authorization from the user.
 * @return true if authorization was granted
 */
suspend fun requestAlarmKitAuthorization(): Boolean {
    val callback = requestAuthCallback ?: return false
    if (!alarmKitAvailable) return false

    return suspendCancellableCoroutine { cont ->
        callback { granted ->
            AppLogger.i(TAG, "AlarmKit authorization ${if (granted) "granted" else "denied"}")
            cont.resume(granted)
        }
    }
}

/**
 * Checks the current AlarmKit authorization status.
 * @return true if authorized
 */
suspend fun isAlarmKitAuthorized(): Boolean {
    val callback = checkAuthCallback ?: return false
    if (!alarmKitAvailable) return false

    return suspendCancellableCoroutine { cont ->
        callback { authorized ->
            cont.resume(authorized)
        }
    }
}

/**
 * Cancels the AlarmKit alarm for a specific time slot, if one exists.
 * Used when the user acts on a regular UNNotification (follow-up) for a slot
 * that also has an AlarmKit alarm scheduled.
 */
fun cancelAlarmKitForSlot(slotTimeMs: Long) {
    val uuid = alarmKitUUIDMap.remove(slotTimeMs.toString()) ?: return
    cancelAlarmKitAlarm(uuid)
}

/**
 * Stops the AlarmKit alarm for a specific time slot, if one is ringing.
 * Used when the user acts on a regular UNNotification while an AlarmKit alarm is active.
 */
fun stopAlarmKitForSlot(slotTimeMs: Long) {
    val uuid = alarmKitUUIDMap[slotTimeMs.toString()] ?: return
    stopAlarmKitAlarm(uuid)
}
