// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

actual object PermissionUtils {
    actual fun areNotificationsEnabled(context: Any): Boolean {
        val ctx = context as Context
        val notificationManager = ctx.getSystemService(NotificationManager::class.java)
        return notificationManager.areNotificationsEnabled()
    }

    actual fun canScheduleExactAlarms(context: Any): Boolean {
        val ctx = context as Context
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = ctx.getSystemService(AlarmManager::class.java)
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    actual fun openExactAlarmSettings(context: Any) {
        val ctx = context as Context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
        }
    }

    actual fun openNotificationSettings(context: Any) {
        val ctx = context as Context
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(intent)
    }

    actual fun openChannelSettings(context: Any, channelId: String) {
        val ctx = context as Context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        } else {
            openNotificationSettings(context)
        }
    }

    actual fun isNotificationChannelEnabled(context: Any, channelId: String): Boolean {
        val ctx = context as Context
        val notificationManager = ctx.getSystemService(NotificationManager::class.java)
        val channel = notificationManager.getNotificationChannel(channelId)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    actual fun isBatteryOptimizationIgnored(context: Any): Boolean {
        val ctx = context as Context
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = ctx.getSystemService(PowerManager::class.java)
            powerManager.isIgnoringBatteryOptimizations(ctx.packageName)
        } else {
            true
        }
    }

    actual fun openBatteryOptimizationSettings(context: Any) {
        val ctx = context as Context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        }
    }

    actual fun canUseFullScreenIntent(context: Any): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val ctx = context as Context
            val notificationManager = ctx.getSystemService(NotificationManager::class.java)
            notificationManager.canUseFullScreenIntent()
        } else {
            true // Auto-granted on Android < 14
        }
    }

    actual fun openFullScreenIntentSettings(context: Any) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val ctx = context as Context
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = android.net.Uri.fromParts("package", ctx.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
            } catch (_: Exception) {
                // Some Android 14 builds don't handle the package URI — fall back to App Info
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", ctx.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(fallback)
            }
        }
    }
}
