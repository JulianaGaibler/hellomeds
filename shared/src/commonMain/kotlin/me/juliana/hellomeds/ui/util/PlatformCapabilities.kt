// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

/**
 * Platform-specific capability checks.
 * Used by shared Settings screen to conditionally show platform-specific options.
 */
expect object PlatformCapabilities {
    /** Whether the platform supports dynamic/Material You colors (Android 12+). */
    fun supportsDynamicColor(): Boolean

    /** Whether the exact alarm toggle should be shown (Android < TIRAMISU). */
    fun supportsExactAlarmToggle(): Boolean

    /** Whether the camera/ML detection feature is available (false for F-Droid builds). */
    fun supportsCameraDetection(): Boolean

    /** Whether to show ML detection method picker (Android: Gemini choice, iOS: no). */
    fun showMlDetectionMethodPicker(): Boolean

    /** Whether the platform supports per-app lock screen visibility control. */
    fun supportsLockScreenVisibilityControl(): Boolean

    /** Whether the platform has notification channels (Android). */
    fun supportsNotificationChannels(): Boolean

    /** Whether battery optimization settings are relevant (Android). */
    fun supportsBatteryOptimization(): Boolean

    /** App version string for display (e.g. "1.0.0 (1)"). */
    fun appVersionString(): String

    /** Whether auto-backup needs a user-selected folder (Android SAF). iOS uses iCloud. */
    fun supportsAutoBackupFolderPicker(): Boolean

    /** Whether Full Screen Intent permission management is relevant (Android 14+). */
    fun supportsFullScreenIntentPermission(): Boolean

    /** Whether the app can schedule critical alerts (iOS: requires Apple entitlement). */
    fun canScheduleCriticalAlerts(): Boolean

    /** Whether the platform supports native system-level alarms (AlarmKit on iOS 26+, AlarmManager on Android). */
    fun isNativeAlarmSupported(): Boolean

    /** Whether native alarm authorization has been granted (iOS: AlarmKit auth, Android: always true). */
    fun isAlarmKitAuthorized(): Boolean

    /** Whether this is a debug build. Used to show developer tools. */
    fun isDebugBuild(): Boolean

    /**
     * Whether the app needs to surface the medical-disclaimer onboarding step
     * for store compliance. True on iOS (App Store guidelines 5.1.1 / 5.5
     * require a disclaimer for health-related apps); false on Android
     * (Google Play accepts the in-app privacy-policy link).
     */
    fun requiresAppStoreDisclaimer(): Boolean

    /** Trigger notification alarm reconciliation after settings change. */
    fun triggerNotificationReconciliation(context: Any)
}
