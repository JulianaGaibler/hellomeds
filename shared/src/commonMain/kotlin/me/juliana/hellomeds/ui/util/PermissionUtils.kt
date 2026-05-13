// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

/**
 * Cross-platform permission utility functions.
 * On Android: delegates to system permission APIs using Context.
 * On iOS: returns sensible defaults.
 *
 * The Context parameter is typed as Any to avoid a hard dependency on android.content.Context
 * in commonMain. Android callers pass platformContext(); iOS callers pass Unit.
 */
expect object PermissionUtils {
    fun areNotificationsEnabled(context: Any): Boolean
    fun canScheduleExactAlarms(context: Any): Boolean
    fun openExactAlarmSettings(context: Any)
    fun openNotificationSettings(context: Any)
    fun openChannelSettings(context: Any, channelId: String)
    fun isNotificationChannelEnabled(context: Any, channelId: String): Boolean
    fun isBatteryOptimizationIgnored(context: Any): Boolean
    fun openBatteryOptimizationSettings(context: Any)
    fun canUseFullScreenIntent(context: Any): Boolean
    fun openFullScreenIntentSettings(context: Any)
}
