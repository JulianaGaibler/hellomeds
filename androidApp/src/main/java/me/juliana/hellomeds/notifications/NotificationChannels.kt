// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.provider.Settings
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.notification_channel_alarm_description
import me.juliana.hellomeds.shared.notification_channel_alarm_name
import me.juliana.hellomeds.shared.notification_channel_critical_description
import me.juliana.hellomeds.shared.notification_channel_critical_name
import me.juliana.hellomeds.shared.notification_channel_depletion_description
import me.juliana.hellomeds.shared.notification_channel_depletion_name
import me.juliana.hellomeds.shared.notification_channel_normal_description
import me.juliana.hellomeds.shared.notification_channel_normal_name
import me.juliana.hellomeds.shared.notification_channel_stock_alerts_description
import me.juliana.hellomeds.shared.notification_channel_stock_alerts_name
import org.jetbrains.compose.resources.getString

object NotificationChannels {
    const val NORMAL_CHANNEL_ID = "medication_reminders"
    const val CRITICAL_CHANNEL_ID = "medication_critical"
    const val ALARM_CHANNEL_ID = "medication_alarm"
    const val STOCK_ALERTS_CHANNEL_ID = "stock_alerts"
    const val DEPLETION_REMINDERS_CHANNEL_ID = "depletion_reminders"

    suspend fun createChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        val normalName = getString(Res.string.notification_channel_normal_name)
        val normalDescription = getString(Res.string.notification_channel_normal_description)
        val criticalName = getString(Res.string.notification_channel_critical_name)
        val criticalDescription = getString(Res.string.notification_channel_critical_description)
        val alarmName = getString(Res.string.notification_channel_alarm_name)
        val alarmDescription = getString(Res.string.notification_channel_alarm_description)
        val stockAlertsName = getString(Res.string.notification_channel_stock_alerts_name)
        val stockAlertsDescription = getString(Res.string.notification_channel_stock_alerts_description)
        val depletionName = getString(Res.string.notification_channel_depletion_name)
        val depletionDescription = getString(Res.string.notification_channel_depletion_description)

        // Normal priority channel
        val normalChannel = NotificationChannel(
            NORMAL_CHANNEL_ID,
            normalName,
            NotificationManager.IMPORTANCE_HIGH, // High for heads-up display
        ).apply {
            description = normalDescription
            enableVibration(true)
            enableLights(true)
        }

        // Critical priority channel - for important medication reminders.
        // Bypasses Do Not Disturb via setBypassDnd(true). Uses USAGE_NOTIFICATION
        // so Android shows notification sounds (not alarm sounds) in channel settings.
        val criticalChannel = NotificationChannel(
            CRITICAL_CHANNEL_ID,
            criticalName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = criticalDescription
            setBypassDnd(true)

            val notificationAudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, notificationAudioAttributes)

            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 500, 500)
            enableLights(true)
        }

        // Alarm channel - for fullscreen alarm-type medication reminders.
        // SILENT channel: AlarmActivity handles looping audio + vibration via MediaPlayer + Vibrator.
        // If the channel also plays sound, both fire simultaneously = echo bug.
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            alarmName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = alarmDescription
            setBypassDnd(true)
            setSound(null, null)
            enableVibration(false)
            enableLights(true)
        }

        // Stock alerts channel - for low stock warnings and measurement reminders
        val stockAlertsChannel = NotificationChannel(
            STOCK_ALERTS_CHANNEL_ID,
            stockAlertsName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = stockAlertsDescription
            enableVibration(true)
            enableLights(true)
        }

        // Depletion reminders channel - heuristic-based container depletion alerts
        val depletionRemindersChannel = NotificationChannel(
            DEPLETION_REMINDERS_CHANNEL_ID,
            depletionName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = depletionDescription
            enableVibration(true)
            enableLights(true)
        }

        notificationManager.createNotificationChannels(
            listOf(
                normalChannel,
                criticalChannel,
                alarmChannel,
                stockAlertsChannel,
                depletionRemindersChannel,
            ),
        )
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        return notificationManager.areNotificationsEnabled()
    }

    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = notificationManager.getNotificationChannel(channelId)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Check if the critical notification channel can bypass Do Not Disturb
     */
    fun canCriticalChannelBypassDnd(context: Context): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = notificationManager.getNotificationChannel(CRITICAL_CHANNEL_ID)
        return channel?.canBypassDnd() == true
    }
}
