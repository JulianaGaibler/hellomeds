// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

/**
 * Types of permission warnings the app can display.
 * Ordered by priority (most critical first) for display in warning banners.
 */
enum class PermissionWarning {
    /** System notifications are completely disabled for the app. */
    NOTIFICATIONS_DISABLED,

    /** Exact alarm scheduling permission is not granted (Android 12+). */
    EXACT_ALARMS_DISABLED,

    /** Full Screen Intent permission not granted — screen won't wake for reminders (Android 14+). */
    FULL_SCREEN_INTENT_DISABLED,

    /** Critical notification channel cannot bypass Do Not Disturb. */
    CRITICAL_CHANNEL_DND_BLOCKED,

    /** AlarmKit not authorized (iOS 26+). */
    ALARMKIT_DISABLED,

    /** Critical alerts not authorized (iOS). */
    CRITICAL_ALERTS_DISABLED,
}

/**
 * Snapshot of all permission states relevant to medication reminders.
 *
 * On iOS, Android-only fields default to `true` (no warning).
 * The [warnings] list is derived automatically — only active issues appear.
 *
 * Note: Battery optimization is intentionally excluded — it's a last-resort option
 * that users should seek out in notification settings, not be prompted about.
 */
data class PermissionWarningState(
    val notificationsEnabled: Boolean = true,
    val exactAlarmsEnabled: Boolean = true,
    val fullScreenIntentEnabled: Boolean = true,
    val criticalChannelBypassesDnd: Boolean = true,
    val alarmKitEnabled: Boolean = true,
    val criticalAlertsEnabled: Boolean = true,
) {
    val warnings: List<PermissionWarning> = buildList {
        if (!notificationsEnabled) add(PermissionWarning.NOTIFICATIONS_DISABLED)
        if (!exactAlarmsEnabled) add(PermissionWarning.EXACT_ALARMS_DISABLED)
        if (!fullScreenIntentEnabled) add(PermissionWarning.FULL_SCREEN_INTENT_DISABLED)
        if (!criticalChannelBypassesDnd) add(PermissionWarning.CRITICAL_CHANNEL_DND_BLOCKED)
        if (!alarmKitEnabled) add(PermissionWarning.ALARMKIT_DISABLED)
        if (!criticalAlertsEnabled) add(PermissionWarning.CRITICAL_ALERTS_DISABLED)
    }
    val hasWarnings: Boolean = warnings.isNotEmpty()

    companion object {
        /**
         * Filters raw permission state to only show warnings relevant to the user's medications.
         *
         * Key behavior:
         * - AlarmKit warning: only if user has alarm-label medications
         * - Critical alerts / DnD warning: if user has critical-label medications, OR
         *   alarm-label medications when AlarmKit is unavailable/unauthorized (alarms fall
         *   back to critical notifications on iOS < 26 or when AlarmKit is denied)
         */
        fun filterByMedications(
            raw: PermissionWarningState,
            hasCriticalMeds: Boolean,
            hasAlarmMeds: Boolean,
            isAlarmKitAuthorized: Boolean,
        ): PermissionWarningState {
            val needsCriticalAlerts = hasCriticalMeds ||
                (hasAlarmMeds && !isAlarmKitAuthorized)
            return raw.copy(
                criticalChannelBypassesDnd = if (needsCriticalAlerts) raw.criticalChannelBypassesDnd else true,
                criticalAlertsEnabled = if (needsCriticalAlerts) raw.criticalAlertsEnabled else true,
                alarmKitEnabled = if (hasAlarmMeds) raw.alarmKitEnabled else true,
            )
        }
    }
}
