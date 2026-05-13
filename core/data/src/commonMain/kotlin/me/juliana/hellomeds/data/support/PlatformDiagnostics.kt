// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.support

data class PlatformDiagnosticInfo(
    val deviceModel: String,
    val osVersion: String,
    val locale: String,
    val timezone: String,
    val appVersion: String,
    // Permissions
    val notificationsGranted: Boolean,
    val exactAlarmsGranted: Boolean = true,
    val batteryOptimizationIgnored: Boolean = true,
    val appStandbyBucket: String? = null,
    // Android alarm state
    val alarmRegistered: Boolean = false,
    val nextWakeupTime: Long? = null,
    val alarmType: String = "N/A",
    val normalChannelEnabled: Boolean = true,
    val criticalChannelEnabled: Boolean = true,
    // iOS notification state
    val notificationAuthorizationStatus: String = "N/A",
    val pendingNotificationCount: Int = 0,
    val deliveredNotificationCount: Int = 0,
)

interface PlatformDiagnosticsProvider {
    suspend fun getDiagnostics(): PlatformDiagnosticInfo
}
