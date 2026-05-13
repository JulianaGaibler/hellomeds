// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import platform.Foundation.NSBundle

actual object PlatformCapabilities {
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    actual fun isDebugBuild(): Boolean = kotlin.native.Platform.isDebugBinary

    actual fun supportsDynamicColor(): Boolean = false

    actual fun supportsExactAlarmToggle(): Boolean = false

    actual fun supportsCameraDetection(): Boolean = true // iOS uses Apple Intelligence
    actual fun showMlDetectionMethodPicker(): Boolean = false
    actual fun supportsLockScreenVisibilityControl(): Boolean = false
    actual fun supportsNotificationChannels(): Boolean = false
    actual fun supportsBatteryOptimization(): Boolean = false
    actual fun supportsAutoBackupFolderPicker(): Boolean = false
    actual fun supportsFullScreenIntentPermission(): Boolean = false

    // Set from Swift bridge at startup (UNNotificationSettings.criticalAlertSetting)
    var criticalAlertsAuthorized: Boolean = false
    actual fun canScheduleCriticalAlerts(): Boolean = criticalAlertsAuthorized

    // Set from AlarmKit bridge at startup (AlarmManager.authorizationState)
    var alarmKitAuthorized: Boolean = false
    actual fun isNativeAlarmSupported(): Boolean = me.juliana.hellomeds.notifications.isAlarmKitAvailable()
    actual fun isAlarmKitAuthorized(): Boolean = alarmKitAuthorized

    actual fun appVersionString(): String {
        val info = NSBundle.mainBundle.infoDictionary ?: return "Unknown"
        val version = info["CFBundleShortVersionString"] as? String ?: "?"
        val build = info["CFBundleVersion"] as? String ?: "?"
        return "$version ($build)"
    }

    actual fun triggerNotificationReconciliation(context: Any) {
        // iOS auto-reconciles via IOSScheduleReconciler — no manual trigger needed
    }
}
