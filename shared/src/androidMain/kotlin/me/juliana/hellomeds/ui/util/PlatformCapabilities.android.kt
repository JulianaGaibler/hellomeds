// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import android.os.Build

actual object PlatformCapabilities {
    // Set by the app module at startup based on BuildConfig.DEBUG
    var debugBuild: Boolean = false
    actual fun isDebugBuild(): Boolean = debugBuild

    actual fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    actual fun supportsExactAlarmToggle(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    // Set by the app module at startup based on build flavor
    var cameraDetectionSupported: Boolean = true
    actual fun supportsCameraDetection(): Boolean = cameraDetectionSupported

    actual fun showMlDetectionMethodPicker(): Boolean = true
    actual fun supportsLockScreenVisibilityControl(): Boolean = true
    actual fun supportsNotificationChannels(): Boolean = true
    actual fun supportsBatteryOptimization(): Boolean = true
    actual fun supportsAutoBackupFolderPicker(): Boolean = true
    actual fun supportsFullScreenIntentPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    actual fun canScheduleCriticalAlerts(): Boolean = false // Android uses its own alarm system
    actual fun isNativeAlarmSupported(): Boolean = true // Android always has AlarmManager
    actual fun isAlarmKitAuthorized(): Boolean = true // Android always has AlarmManager — no separate auth

    // Set by the app module at startup
    var versionString: String = "Unknown"
    var reconciliationTrigger: (context: Any) -> Unit = {}

    actual fun appVersionString(): String = versionString

    actual fun triggerNotificationReconciliation(context: Any) {
        reconciliationTrigger(context)
    }
}
