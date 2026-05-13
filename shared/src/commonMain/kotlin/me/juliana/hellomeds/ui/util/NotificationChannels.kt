// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

/**
 * Notification channel IDs shared across platforms.
 * On Android, these correspond to real notification channel IDs.
 * On iOS, they are used as logical identifiers.
 */
object NotificationChannels {
    const val NORMAL_CHANNEL_ID: String = "medication_reminders"
    const val CRITICAL_CHANNEL_ID: String = "medication_critical"
    const val ALARM_CHANNEL_ID: String = "medication_alarm"
    const val STOCK_ALERTS_CHANNEL_ID: String = "stock_alerts"
    const val DEPLETION_REMINDERS_CHANNEL_ID: String = "depletion_reminders"
}

/**
 * Check if the critical notification channel can bypass Do Not Disturb.
 * @param context Platform context (android.content.Context on Android, Unit on iOS)
 */
expect fun canCriticalChannelBypassDnd(context: Any): Boolean
