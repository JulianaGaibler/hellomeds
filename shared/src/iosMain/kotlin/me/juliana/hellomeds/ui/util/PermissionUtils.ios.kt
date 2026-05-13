// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.concurrent.Volatile

actual object PermissionUtils {
    /**
     * Cached notification authorization status.
     * Updated by NotificationPermissionChecker on app launch and resume.
     * Defaults to false so onboarding shows the permission screen on fresh installs.
     * Note: Avoid setting this to true by default, as it prevents the app from
     * identifying the need to request permissions from the user.
     */
    @Volatile
    var cachedNotificationsEnabled: Boolean = false

    actual fun areNotificationsEnabled(context: Any): Boolean = cachedNotificationsEnabled

    /**
     * Exact alarms are not an iOS concept; iOS handles scheduling internally.
     */
    actual fun canScheduleExactAlarms(context: Any): Boolean = true

    actual fun openExactAlarmSettings(context: Any) {
        /* no-op on iOS */
    }

    /**
     * Opens the iOS Settings app to the app's notification settings page.
     */
    actual fun openNotificationSettings(context: Any) {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = mapOf<Any?, Any>(), completionHandler = null)
    }

    actual fun openChannelSettings(context: Any, channelId: String) {
        // iOS doesn't have notification channels; open general notification settings
        openNotificationSettings(context)
    }

    actual fun isNotificationChannelEnabled(context: Any, channelId: String): Boolean = true
    actual fun isBatteryOptimizationIgnored(context: Any): Boolean = true
    actual fun openBatteryOptimizationSettings(context: Any) { /* no-op on iOS */ }
    actual fun canUseFullScreenIntent(context: Any): Boolean = true
    actual fun openFullScreenIntentSettings(context: Any) { /* no-op on iOS */ }
}
