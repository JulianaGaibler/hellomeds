// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.juliana.hellomeds.ui.util

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.coroutines.resume

actual object PlatformCapabilities {
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    actual fun isDebugBuild(): Boolean = Platform.isDebugBinary

    actual fun supportsDynamicColor(): Boolean = false

    actual fun supportsExactAlarmToggle(): Boolean = false

    actual fun supportsCameraDetection(): Boolean = true // iOS uses Apple Intelligence
    actual fun showMlDetectionMethodPicker(): Boolean = false
    actual fun supportsLockScreenVisibilityControl(): Boolean = false
    actual fun supportsNonDismissableNotifications(): Boolean = false
    actual fun supportsNotificationChannels(): Boolean = false
    actual fun supportsBatteryOptimization(): Boolean = false
    actual fun supportsAutoBackupFolderPicker(): Boolean = true
    actual fun supportsBackupDestinationModeChoice(): Boolean = true

    /**
     * Uses [UIApplicationOpenSettingsURLString], the only deep link Apple sanctions for
     * third-party apps (App Review 2.5.1). The user lands on this app's settings page and
     * has to tap back to reach Apple Account → iCloud.
     */
    actual suspend fun openSystemSettingsForICloud(): Boolean {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(url)) return false
        return suspendCancellableCoroutine { cont ->
            app.openURL(url, options = emptyMap<Any?, Any>()) { success ->
                cont.resume(success)
            }
        }
    }
    actual fun supportsFullScreenIntentPermission(): Boolean = false

    // Set from Swift bridge at startup (UNNotificationSettings.criticalAlertSetting)
    var criticalAlertsAuthorized: Boolean = false
    actual fun canScheduleCriticalAlerts(): Boolean = criticalAlertsAuthorized

    // Set from AlarmKit bridge at startup (AlarmManager.authorizationState)
    var alarmKitAuthorized: Boolean = false
    actual fun isNativeAlarmSupported(): Boolean = me.juliana.hellomeds.notifications.isAlarmKitAvailable()
    actual fun isAlarmKitAuthorized(): Boolean = alarmKitAuthorized
    actual fun requiresAppStoreDisclaimer(): Boolean = true // App Store guidelines 5.1.1 / 5.5

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
