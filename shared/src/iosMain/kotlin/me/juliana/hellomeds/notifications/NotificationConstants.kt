// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

// Standalone constants with no class dependencies, safe at file level.
// They stay out of the IOSScheduleReconciler companion object because referencing it
// initializes IOSScheduleReconciler and its iOS framework imports at file load time,
// which throws FileFailedToInitializeException when Koin accesses iosPlatformModule.

const val NOTIFICATION_CATEGORY_MEDICATION = "MEDICATION_REMINDER"
const val NOTIFICATION_CATEGORY_DEPLETION_REMINDER = "DEPLETION_REMINDER"
const val NOTIFICATION_ACTION_TAKE = "ACTION_TAKE"
const val NOTIFICATION_ACTION_SKIP = "ACTION_SKIP"
const val NOTIFICATION_ACTION_SNOOZE = "ACTION_SNOOZE"
const val NOTIFICATION_ACTION_MARK_DEPLETED = "ACTION_MARK_DEPLETED"
const val NOTIFICATION_ID_PREFIX = "med_"

/** userInfo "type" values written by iOS notifiers and dispatched by IOSNotificationDelegate. */
const val NOTIFICATION_TYPE_MEDICATION_REMINDER = "medication_reminder"
const val NOTIFICATION_TYPE_LOW_STOCK = "low_stock"
const val NOTIFICATION_TYPE_DEPLETION_REMINDER = "depletion_reminder"

/** Stable identifier prefixes for stock-related notifications — `<prefix><medicationId>`. */
const val LOW_STOCK_NOTIFICATION_ID_PREFIX = "hellomeds_low_stock_"
const val DEPLETION_NOTIFICATION_ID_PREFIX = "hellomeds_depletion_"

/** Thread identifiers so iOS Notification Center groups by alert type. */
const val LOW_STOCK_THREAD_IDENTIFIER = "hellomeds_low_stock"
const val DEPLETION_THREAD_IDENTIFIER = "hellomeds_depletion"

/**
 * iOS hard-caps an app at 64 pending local notifications (system enforces, no warning).
 * We reserve [SNOOZE_RESERVED_SLOTS] to absorb runtime snooze additions without
 * stealing slots from already-scheduled events.
 */
const val MAX_SCHEDULED_NOTIFICATIONS = 60

/** Reserved budget for runtime-added snooze notifications. */
const val SNOOZE_RESERVED_SLOTS = 5

/**
 * Effective ceiling for projected events + their pre-scheduled follow-ups.
 * When the projected slot count exceeds this number, we set
 * `ReliabilityPreferences.iosNotificationBudgetExhausted = true` so the UI
 * surfaces a "too many reminders — open the app every 2 days" banner.
 */
const val MAX_EVENT_NOTIFICATIONS = MAX_SCHEDULED_NOTIFICATIONS - SNOOZE_RESERVED_SLOTS

const val SCHEDULING_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Beyond [SCHEDULING_WINDOW_MS], leftover event budget is filled with plain reminders up
 * to this horizon. Without it a user who never opens the app, and whose BGAppRefresh never
 * runs, goes silent after a week.
 */
const val EXTENDED_SCHEDULING_WINDOW_MS = 90L * 24 * 60 * 60 * 1000

/**
 * Identifier of the "open the app to keep receiving reminders" notification queued after
 * the last scheduled reminder. Shares [NOTIFICATION_ID_PREFIX] so every reconcile wipes
 * and re-queues it, which means it only fires if no reconcile happened before it was due.
 * Uses one of the 4 slots between [MAX_SCHEDULED_NOTIFICATIONS] and the iOS cap of 64.
 */
const val REOPEN_APP_NOTIFICATION_ID = "${NOTIFICATION_ID_PREFIX}reopen_app"

/** Delay after the last queued reminder before the reopen-app notification fires. */
const val REOPEN_APP_DELAY_MS = 30L * 60 * 1000

/** Follow-ups are only pre-scheduled within this window to conserve notification budget. */
const val FOLLOWUP_WINDOW_MS = 24L * 60 * 60 * 1000
