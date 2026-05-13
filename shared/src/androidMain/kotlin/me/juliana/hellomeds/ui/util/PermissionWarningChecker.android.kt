// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.juliana.hellomeds.ui.compat.platformContext

@Composable
actual fun rememberPermissionWarnings(): PermissionWarningState {
    val context = platformContext() as Context
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun checkAll() = PermissionWarningState(
        notificationsEnabled = notificationManager.areNotificationsEnabled(),
        exactAlarmsEnabled = PermissionUtils.canScheduleExactAlarms(context),
        fullScreenIntentEnabled = !PlatformCapabilities.supportsFullScreenIntentPermission() ||
            PermissionUtils.canUseFullScreenIntent(context),
        criticalChannelBypassesDnd = canCriticalChannelBypassDnd(context),
    )

    var state by remember { mutableStateOf(checkAll()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = checkAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return state
}
