// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

@Composable
actual fun rememberPermissionWarnings(): PermissionWarningState {
    fun checkAll() = PermissionWarningState(
        notificationsEnabled = PermissionUtils.areNotificationsEnabled(Unit),
        // AlarmKit: only warn if available (iOS 26+) and not authorized
        alarmKitEnabled = !me.juliana.hellomeds.notifications.isAlarmKitAvailable() ||
            PlatformCapabilities.alarmKitAuthorized,
        // Critical alerts: check cached authorization
        criticalAlertsEnabled = PlatformCapabilities.criticalAlertsAuthorized,
        // Android-only fields default to true
    )

    var state by remember { mutableStateOf(checkAll()) }

    // Re-check when app returns to foreground (user may have changed settings in iOS Settings)
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            state = checkAll()
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }

    return state
}
