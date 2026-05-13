// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlinx.coroutines.suspendCancellableCoroutine
import me.juliana.hellomeds.data.util.AppLogger
import platform.Foundation.NSMutableSet
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionDestructive
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionCustomDismissAction
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

// Top-level functions instead of an `object` to avoid Kotlin/Native class initialization
// at file load time, which could trigger iOS framework APIs before frameworks are ready.

private const val TAG = "IOSNotificationSetup"

/**
 * Registers the medication reminder notification category with interactive actions.
 * Actions appear as buttons on the notification when the user long-presses or
 * expands the notification on iOS.
 *
 * Must be called before any notifications are scheduled.
 */
fun registerNotificationCategory() {
    val takeAction = UNNotificationAction.actionWithIdentifier(
        identifier = NOTIFICATION_ACTION_TAKE,
        title = "Take",
        options = UNNotificationActionOptionNone,
    )

    val skipAction = UNNotificationAction.actionWithIdentifier(
        identifier = NOTIFICATION_ACTION_SKIP,
        title = "Skip",
        options = UNNotificationActionOptionDestructive,
    )

    val snoozeAction = UNNotificationAction.actionWithIdentifier(
        identifier = NOTIFICATION_ACTION_SNOOZE,
        title = "Snooze",
        options = UNNotificationActionOptionNone,
    )

    // Use explicit ObjC NSSet/NSArray bridging — Kotlin collection literals
    // may not bridge correctly to the ObjC types UNUserNotificationCenter expects.
    val category = UNNotificationCategory.categoryWithIdentifier(
        identifier = NOTIFICATION_CATEGORY_MEDICATION,
        actions = listOf(takeAction, skipAction, snoozeAction),
        intentIdentifiers = listOf<String>(),
        options = UNNotificationCategoryOptionCustomDismissAction,
    )

    val center = UNUserNotificationCenter.currentNotificationCenter()

    @Suppress("UNCHECKED_CAST")
    val categorySet = NSMutableSet().apply { addObject(category) } as Set<UNNotificationCategory>
    center.setNotificationCategories(categorySet)

    AppLogger.i(TAG, "Registered notification category with Take/Skip/Snooze actions")
}

/**
 * Requests notification authorization from the user.
 *
 * Requests:
 * - Alert, Sound, Badge (standard)
 * - Time Sensitive (iOS 15+, bypasses Focus/DND for medication reminders)
 * - Critical Alert (requires Apple entitlement, bypasses even silent mode for critical meds)
 *
 * Returns true if authorization was granted, false otherwise.
 */
suspend fun requestNotificationPermission(): Boolean {
    val center = UNUserNotificationCenter.currentNotificationCenter()

    // Standard options
    val baseOptions = UNAuthorizationOptionAlert or
        UNAuthorizationOptionSound or
        UNAuthorizationOptionBadge

    // UNAuthorizationOptionTimeSensitive (1 shl 4 = 16) — bypasses Focus Modes
    // UNAuthorizationOptionCriticalAlert (1 shl 5 = 32) — bypasses mute switch
    // Using raw values because direct K/N enum references cause FileFailedToInitializeException.
    // Note: CriticalAlert requires com.apple.developer.usernotifications.critical-alerts
    // entitlement approved by Apple.
    val timeSensitiveOption: ULong = 1UL shl 4
    val criticalAlertOption: ULong = 1UL shl 5
    val options = baseOptions or timeSensitiveOption or criticalAlertOption

    return suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(options) { granted, error ->
            if (error != null) {
                AppLogger.e(TAG, "Notification permission error: ${error.localizedDescription}")
            }
            if (granted) {
                AppLogger.i(TAG, "Notification permission granted")
            } else {
                AppLogger.w(TAG, "Notification permission denied")
            }
            cont.resume(granted)
        }
    }
}
