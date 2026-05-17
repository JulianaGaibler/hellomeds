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
 * Pre-resolved localized titles for the iOS notification action buttons.
 * Passed into [registerNotificationCategory] so that registration stays
 * synchronous at app launch — CMP's [org.jetbrains.compose.resources.getString]
 * is `suspend`, but the iOS notification system requires categories to be
 * registered before any notifications are scheduled.
 *
 * Resolve via `runBlocking(Dispatchers.Default) { getString(Res.string.X) }`
 * in the caller (see `MainViewController.setupNotifications`).
 */
data class NotificationActionStrings(
    val take: String,
    val skip: String,
    val snooze: String,
    val markDepleted: String,
)

/**
 * Registers the medication reminder and depletion reminder notification
 * categories with interactive actions. Actions appear as buttons on the
 * notification when the user long-presses or expands the notification on iOS.
 *
 * Must be called before any notifications are scheduled.
 */
fun registerNotificationCategory(strings: NotificationActionStrings) {
    val takeAction = UNNotificationAction.actionWithIdentifier(
        identifier = NOTIFICATION_ACTION_TAKE,
        title = strings.take,
        options = UNNotificationActionOptionNone,
    )

    val skipAction = UNNotificationAction.actionWithIdentifier(
        identifier = NOTIFICATION_ACTION_SKIP,
        title = strings.skip,
        options = UNNotificationActionOptionDestructive,
    )

    val snoozeAction = UNNotificationAction.actionWithIdentifier(
        identifier = NOTIFICATION_ACTION_SNOOZE,
        title = strings.snooze,
        options = UNNotificationActionOptionNone,
    )

    // Use explicit ObjC NSSet/NSArray bridging — Kotlin collection literals
    // may not bridge correctly to the ObjC types UNUserNotificationCenter expects.
    val medicationCategory = UNNotificationCategory.categoryWithIdentifier(
        identifier = NOTIFICATION_CATEGORY_MEDICATION,
        actions = listOf(takeAction, skipAction, snoozeAction),
        intentIdentifiers = listOf<String>(),
        options = UNNotificationCategoryOptionCustomDismissAction,
    )

    // Depletion-reminder category — single "Mark Depleted" action button.
    val markDepletedAction = UNNotificationAction.actionWithIdentifier(
        identifier = NOTIFICATION_ACTION_MARK_DEPLETED,
        title = strings.markDepleted,
        options = UNNotificationActionOptionNone,
    )
    val depletionCategory = UNNotificationCategory.categoryWithIdentifier(
        identifier = NOTIFICATION_CATEGORY_DEPLETION_REMINDER,
        actions = listOf(markDepletedAction),
        intentIdentifiers = listOf<String>(),
        options = UNNotificationCategoryOptionCustomDismissAction,
    )

    val center = UNUserNotificationCenter.currentNotificationCenter()

    // setNotificationCategories replaces the prior set on every call, so both
    // categories must be passed together.
    @Suppress("UNCHECKED_CAST")
    val categorySet = NSMutableSet().apply {
        addObject(medicationCategory)
        addObject(depletionCategory)
    } as Set<UNNotificationCategory>
    center.setNotificationCategories(categorySet)

    AppLogger.i(
        TAG,
        "Registered notification categories: medication (Take/Skip/Snooze) + depletion (Mark Depleted)",
    )
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
