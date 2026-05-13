// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import android.app.NotificationManager
import android.content.Context

actual fun canCriticalChannelBypassDnd(context: Any): Boolean {
    val ctx = context as Context
    val notificationManager = ctx.getSystemService(NotificationManager::class.java)
    val channel = notificationManager.getNotificationChannel(NotificationChannels.CRITICAL_CHANNEL_ID)
    return channel?.canBypassDnd() == true
}
