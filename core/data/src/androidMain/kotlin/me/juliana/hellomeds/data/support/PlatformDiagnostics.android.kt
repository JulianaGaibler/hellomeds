// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.support

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.util.Locale
import java.util.TimeZone

class AndroidDiagnosticsProvider(
    private val context: Context,
) : PlatformDiagnosticsProvider {

    private val appVersion: String by lazy {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName} (${pInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    override suspend fun getDiagnostics(): PlatformDiagnosticInfo {
        val tz = TimeZone.getDefault()
        val offsetMs = tz.rawOffset + tz.dstSavings
        val offsetHours = offsetMs / 3600000
        val offsetMinutes = (Math.abs(offsetMs) % 3600000) / 60000
        val offsetStr =
            "%s%02d:%02d".format(if (offsetMs >= 0) "+" else "-", Math.abs(offsetHours), offsetMinutes)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val standbyBucket = try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            when (usm.appStandbyBucket) {
                5 -> "EXEMPTED"
                10 -> "ACTIVE"
                20 -> "WORKING_SET"
                30 -> "FREQUENT"
                40 -> "RARE"
                45 -> "RESTRICTED"
                50 -> "NEVER"
                else -> "UNKNOWN (${usm.appStandbyBucket})"
            }
        } catch (e: Exception) {
            "Unavailable"
        }

        val normalChannel = notificationManager.getNotificationChannel("medication_reminders")
        val criticalChannel =
            notificationManager.getNotificationChannel("critical_medication_reminders")

        val nextAlarm = alarmManager.nextAlarmClock
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        return PlatformDiagnosticInfo(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            osVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            locale = Locale.getDefault().toLanguageTag(),
            timezone = "${tz.id} (UTC$offsetStr)",
            appVersion = appVersion,
            notificationsGranted = notificationManager.areNotificationsEnabled(),
            exactAlarmsGranted = canScheduleExact,
            batteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            appStandbyBucket = standbyBucket,
            alarmRegistered = nextAlarm != null,
            nextWakeupTime = nextAlarm?.triggerTime,
            alarmType = if (nextAlarm != null) "AlarmClock" else "None",
            normalChannelEnabled = normalChannel?.importance != NotificationManager.IMPORTANCE_NONE,
            criticalChannelEnabled = criticalChannel?.importance != NotificationManager.IMPORTANCE_NONE,
        )
    }
}
