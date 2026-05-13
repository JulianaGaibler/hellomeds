// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.crypto.DatabaseKeyManager
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import me.juliana.hellomeds.data.preferences.CameraPreferences
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.DiagnosticLog
import kotlin.time.Clock
import kotlin.time.Instant

class BugReportService(
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val historyDao: MedicationHistoryDao,
    private val importanceLabelDao: ImportanceLabelDao,
    private val notificationPrefs: NotificationPreferences,
    private val autoBackupPrefs: AutoBackupPreferences,
    private val cameraPrefs: CameraPreferences,
    private val keyManager: DatabaseKeyManager,
    private val projector: ScheduleProjector,
) {
    suspend fun generateReport(
        diagnosticsProvider: PlatformDiagnosticsProvider,
        reconciler: ScheduleReconciler? = null,
    ): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val nowDt = now.toLocalDateTime(tz)
        val nowMs = now.toEpochMilliseconds()

        // Consistent medication aliases
        val aliases = mutableMapOf<Int, String>()
        var counter = 1
        fun alias(medId: Int) = aliases.getOrPut(medId) { "Medication ${counter++}" }

        sb.appendLine("HelloMeds Diagnostic Report")
        sb.appendLine("Generated: ${formatDateTime(nowDt)}")
        sb.appendLine("==================================")

        // DEVICE & APP
        try {
            val info = diagnosticsProvider.getDiagnostics()
            sb.appendLine()
            sb.appendLine("DEVICE & APP")
            sb.appendLine("  App version: ${info.appVersion}")
            sb.appendLine("  Platform: ${info.osVersion}")
            sb.appendLine("  Device: ${info.deviceModel}")
            sb.appendLine("  Locale: ${info.locale}")
            sb.appendLine("  Timezone: ${info.timezone}")
            if (info.appStandbyBucket != null) {
                sb.appendLine("  App standby bucket: ${info.appStandbyBucket}")
            }

            sb.appendLine()
            sb.appendLine("PERMISSIONS")
            sb.appendLine("  Notifications: ${if (info.notificationsGranted) "Granted" else "Denied"}")
            if (info.appStandbyBucket != null) {
                // Android-specific
                sb.appendLine("  Exact alarms: ${if (info.exactAlarmsGranted) "Granted" else "Not granted"}")
                sb.appendLine(
                    "  Battery optimization: ${if (info.batteryOptimizationIgnored) "Unrestricted" else "Optimized"}",
                )
                sb.appendLine("  Normal channel: ${if (info.normalChannelEnabled) "Enabled" else "Disabled"}")
                sb.appendLine("  Critical channel: ${if (info.criticalChannelEnabled) "Enabled" else "Disabled"}")
            }

            // Alarm state (Android)
            if (info.alarmRegistered || info.nextWakeupTime != null) {
                sb.appendLine()
                sb.appendLine("ALARM STATE")
                sb.appendLine("  Alarm registered: ${if (info.alarmRegistered) "Yes" else "No"}")
                if (info.nextWakeupTime != null) {
                    val wakeupDt = Instant.fromEpochMilliseconds(info.nextWakeupTime)
                        .toLocalDateTime(tz)
                    val diffMs = info.nextWakeupTime - nowMs
                    sb.appendLine("  Next wakeup: in ${formatDuration(diffMs)} (${formatTime(wakeupDt)})")
                    sb.appendLine("  Alarm type: ${info.alarmType}")
                }
            }

            // iOS notification state
            if (info.notificationAuthorizationStatus != "N/A") {
                sb.appendLine()
                sb.appendLine("NOTIFICATION STATE (iOS)")
                sb.appendLine("  Authorization: ${info.notificationAuthorizationStatus}")
                sb.appendLine("  Pending notifications: ${info.pendingNotificationCount}")
                sb.appendLine("  Delivered notifications: ${info.deliveredNotificationCount}")
            }
        } catch (e: Exception) {
            sb.appendLine()
            sb.appendLine("DEVICE & APP")
            sb.appendLine("  [ERROR: ${e.message}]")
        }

        // NOTIFICATION SETTINGS
        try {
            sb.appendLine()
            sb.appendLine("NOTIFICATION SETTINGS")
            sb.appendLine("  Enabled: ${notificationPrefs.notificationsEnabled.first()}")
            sb.appendLine("  Grouping: ${notificationPrefs.groupingMode.first()}")
            sb.appendLine("  Snooze interval: ${notificationPrefs.snoozeIntervalMinutes.first()} min")
            sb.appendLine("  Lock screen: ${notificationPrefs.lockScreenVisibility.first()}")
            sb.appendLine("  Scheduling window: ${notificationPrefs.schedulingWindowHours.first()}h")
            val lastSched = notificationPrefs.lastSchedulingTimestamp.first()
            sb.appendLine(
                "  Last scheduling: ${
                    if (lastSched > 0) {
                        formatRelative(
                            lastSched,
                            nowMs,
                        )
                    } else {
                        "Never"
                    }
                }",
            )
        } catch (e: Exception) {
            sb.appendLine()
            sb.appendLine("NOTIFICATION SETTINGS")
            sb.appendLine("  [ERROR: ${e.message}]")
        }

        // DATABASE
        try {
            val allMeds = medicationDao.getAll().first()
            val activeMeds = medicationDao.getActive().first()
            val activeSchedules = scheduleDao.getActive().first()
            val allHistory = historyDao.getAll().first()
            val labels = importanceLabelDao.getAll().first()

            sb.appendLine()
            sb.appendLine("DATABASE")
            sb.appendLine("  Medications: ${activeMeds.size} active, ${allMeds.size - activeMeds.size} archived")
            sb.appendLine("  Schedules: ${activeSchedules.size} active")
            sb.appendLine("  History records: ${allHistory.size}")
            sb.appendLine("  Encryption: ${if (keyManager.hasKey()) "Key present" else "Key missing"}")

            // TODAY'S DOSES
            runCatching {
                val today = nowDt.date
                val startOfDay = today.atStartOfDayIn(tz).toEpochMilliseconds()
                val endOfDay = kotlinx.datetime.LocalDateTime(today, LocalTime(23, 59, 59, 999999999))
                    .toInstant(tz).toEpochMilliseconds()
                val doseOverview = projector.getDoseOverview(startOfDay, endOfDay, nowMs)

                sb.appendLine()
                sb.appendLine("TODAY'S DOSES")
                sb.appendLine(
                    "  Total: ${doseOverview.totalCount}, Taken: ${doseOverview.takenCount}, Pending: ${doseOverview.pendingCount}, Skipped: ${doseOverview.skippedCount}, Overdue: ${doseOverview.overdueCount}",
                )
                doseOverview.doses.take(20).forEach { dose ->
                    val time = formatTime(Instant.fromEpochMilliseconds(dose.scheduledTime).toLocalDateTime(tz))
                    val overdue = if (dose.isOverdue) " (overdue)" else ""
                    sb.appendLine("  $time [${alias(dose.medicationId)}] ${dose.status}$overdue")
                }
                if (doseOverview.doses.size > 20) {
                    sb.appendLine("  ... and ${doseOverview.doses.size - 20} more")
                }
            }.onFailure { e ->
                sb.appendLine()
                sb.appendLine("TODAY'S DOSES")
                sb.appendLine("  [ERROR: ${e.message}]")
            }

            // UPCOMING EVENTS
            runCatching {
                val upcoming = projector.getUpcomingEventsDiagnostic(nowMs)
                sb.appendLine()
                sb.appendLine("UPCOMING EVENTS")
                sb.appendLine("  Timezone: ${upcoming.timezoneId} (UTC${formatUtcOffset(upcoming.utcOffsetSeconds)})")
                sb.appendLine("  Next 24h: ${upcoming.next24hCount} pending")
                sb.appendLine("  Next 7 days: ${upcoming.next7dCount} pending")
                if (upcoming.nextEventTime != null) {
                    val diffMs = upcoming.nextEventTime - nowMs
                    sb.appendLine("  Next event: in ${formatDuration(diffMs)} (${upcoming.nextEventTime})")
                } else {
                    sb.appendLine("  Next event: none")
                }
            }.onFailure { e ->
                sb.appendLine()
                sb.appendLine("UPCOMING EVENTS")
                sb.appendLine("  [ERROR: ${e.message}]")
            }

            // NOTIFICATION/ALARM HEALTH
            if (reconciler != null) {
                runCatching {
                    val diag = reconciler.getDiagnosticSummary()
                    sb.appendLine()
                    sb.appendLine("NOTIFICATION/ALARM HEALTH")
                    sb.appendLine("  Timezone: ${diag.timezoneId} (UTC${formatUtcOffset(diag.utcOffsetSeconds)})")
                    sb.appendLine("  Status: ${if (diag.healthy) "Healthy" else "UNHEALTHY"}")
                    sb.appendLine("  Message: ${diag.healthMessage}")
                    diag.details.forEach { (key, value) ->
                        sb.appendLine("  $key: $value")
                    }
                }.onFailure { e ->
                    sb.appendLine()
                    sb.appendLine("NOTIFICATION/ALARM HEALTH")
                    sb.appendLine("  [ERROR: ${e.message}]")
                }
            }

            // STOCK TRACKING
            runCatching {
                val stock = getStockDiagnostic(medicationDao)
                if (stock.trackedMedicationCount > 0) {
                    sb.appendLine()
                    sb.appendLine("STOCK TRACKING")
                    sb.appendLine("  Tracked medications: ${stock.trackedMedicationCount}")
                    if (stock.lowStockMedications.isEmpty()) {
                        sb.appendLine("  Low stock: none")
                    } else {
                        stock.lowStockMedications.forEach { med ->
                            sb.appendLine(
                                "  Low stock: ${alias(
                                    med.medicationId,
                                )} (${med.currentQuantity} remaining, threshold ${med.lowStockThreshold}, ${med.trackingPrecision})",
                            )
                        }
                    }
                }
            }.onFailure { e ->
                sb.appendLine()
                sb.appendLine("STOCK TRACKING")
                sb.appendLine("  [ERROR: ${e.message}]")
            }

            // MEDICATIONS & SCHEDULES (obfuscated names)
            if (allMeds.isNotEmpty()) {
                val labelsById = labels.associateBy { it.id }

                sb.appendLine()
                sb.appendLine("MEDICATIONS & SCHEDULES")
                allMeds.forEach { med ->
                    val medAlias = alias(med.id)
                    val labelName = labelsById[med.importanceLabelId]?.name ?: "Unknown"
                    val medSchedules = activeSchedules.filter { it.medicationId == med.id }
                    val archived = if (med.isArchived) " [archived]" else ""
                    sb.appendLine(
                        "  $medAlias: type=${med.type}, label=$labelName, schedules=${medSchedules.size}$archived",
                    )
                    medSchedules.forEach { sched ->
                        val freq = when (sched.frequencyType) {
                            me.juliana.hellomeds.data.model.enums.FrequencyType.INTERVAL ->
                                if (sched.frequencyValue == 1) "daily" else "every ${sched.frequencyValue} days"

                            me.juliana.hellomeds.data.model.enums.FrequencyType.DAYS_OF_WEEK ->
                                sched.daysOfWeek ?: "unknown days"
                        }
                        val schedArchived = if (sched.isArchived) " [archived]" else ""
                        sb.appendLine("    ${sched.timeOfDay} $freq, dose=${sched.dose}$schedArchived")
                    }
                }
            }

            // RECENT HISTORY (last 24h, obfuscated)
            val oneDayAgo = nowMs - 86400000L
            val recentHistory = allHistory.filter { it.createdAt > oneDayAgo }
            if (recentHistory.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("RECENT HISTORY (last 24h)")
                recentHistory.take(20).forEach { record ->
                    val medAlias = alias(record.medicationId)
                    val time = formatTime(Instant.fromEpochMilliseconds(record.createdAt).toLocalDateTime(tz))
                    val status = when (record.status) {
                        MedicationHistory.STATUS_TAKEN -> "TAKEN"
                        MedicationHistory.STATUS_SKIPPED -> "SKIPPED"
                        MedicationHistory.STATUS_AUTO_SKIPPED -> "AUTO_SKIPPED"
                        else -> "UNKNOWN(${record.status})"
                    }
                    sb.appendLine("  $time [$medAlias] $status")
                }
                if (recentHistory.size > 20) {
                    sb.appendLine("  ... and ${recentHistory.size - 20} more")
                }
            }

            // IMPORTANCE LABELS
            if (labels.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("IMPORTANCE LABELS")
                labels.forEach { label ->
                    val parts = mutableListOf<String>()
                    parts.add("remind=${label.shouldRemind}")
                    parts.add("critical=${label.isCritical}")
                    if (label.hasFollowUps) {
                        parts.add("followUps=${label.followUpCount}, interval=${label.followUpIntervalMinutes}min")
                    }
                    sb.appendLine("  ${label.name} (${parts.joinToString(", ")})")
                }
            }
        } catch (e: Exception) {
            sb.appendLine()
            sb.appendLine("DATABASE")
            sb.appendLine("  [DATABASE ERROR: ${e.message}]")
        }

        // AUTO-BACKUP
        try {
            sb.appendLine()
            sb.appendLine("AUTO-BACKUP")
            sb.appendLine("  Enabled: ${autoBackupPrefs.autoBackupEnabled.first()}")
            val lastBackup = autoBackupPrefs.lastBackupTimestamp.first()
            val status = autoBackupPrefs.lastBackupStatus.first()
            val medCount = autoBackupPrefs.lastBackupMedicationCount.first()
            val failures = autoBackupPrefs.consecutiveFailures.first()
            if (lastBackup > 0) {
                sb.appendLine(
                    "  Last backup: ${
                        formatRelative(
                            lastBackup,
                            nowMs,
                        )
                    } ($status, $medCount medications)",
                )
            } else {
                sb.appendLine("  Last backup: Never")
            }
            sb.appendLine("  Retention: ${autoBackupPrefs.backupRetentionCount.first()}")
            sb.appendLine("  Consecutive failures: $failures")
            val lastError = autoBackupPrefs.lastBackupErrorMessage.first()
            if (lastError != null) {
                sb.appendLine("  Last error: $lastError")
            }
        } catch (e: Exception) {
            sb.appendLine()
            sb.appendLine("AUTO-BACKUP")
            sb.appendLine("  [ERROR: ${e.message}]")
        }

        // CAMERA
        try {
            sb.appendLine()
            sb.appendLine("CAMERA")
            sb.appendLine("  Detection method: ${cameraPrefs.detectionMethod.first()}")
            sb.appendLine("  Consent given: ${cameraPrefs.hasConsented.first()}")
        } catch (e: Exception) {
            sb.appendLine()
            sb.appendLine("CAMERA")
            sb.appendLine("  [ERROR: ${e.message}]")
        }

        // RECENT LOG
        val twoDaysAgo = nowMs - 172800000L
        val logEntries = DiagnosticLog.getEntries(sinceTimestamp = twoDaysAgo)
        sb.appendLine()
        sb.appendLine("RECENT LOG (last 48h)")
        if (logEntries.isEmpty()) {
            sb.appendLine("  No log entries recorded")
        } else {
            logEntries.forEach { entry ->
                val entryDt = Instant.fromEpochMilliseconds(entry.timestamp).toLocalDateTime(tz)
                val levelTag = when (entry.level) {
                    DiagnosticLog.LogLevel.DEBUG -> "D"
                    DiagnosticLog.LogLevel.INFO -> "I"
                    DiagnosticLog.LogLevel.WARN -> "W"
                    DiagnosticLog.LogLevel.ERROR -> "E"
                }
                sb.appendLine("  ${formatDateTime(entryDt)} [$levelTag] ${entry.tag}: ${entry.message}")
            }
        }

        sb.toString()
    }

    private fun formatDateTime(dt: kotlinx.datetime.LocalDateTime): String {
        return "${dt.year.pad(4)}-${(dt.month.ordinal + 1).pad()}-${dt.day.pad()} " +
            "${dt.hour.pad()}:${dt.minute.pad()}:${dt.second.pad()}"
    }

    private fun formatTime(dt: kotlinx.datetime.LocalDateTime): String {
        return "${dt.hour.pad()}:${dt.minute.pad()}"
    }

    private fun Int.pad(length: Int = 2): String = toString().padStart(length, '0')

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "now"
        val minutes = (ms / 60000) % 60
        val hours = (ms / 3600000) % 24
        val days = ms / 86400000
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0 || (days == 0L && hours == 0L)) append("${minutes}m")
        }.trim()
    }

    private fun formatRelative(timestamp: Long, now: Long): String {
        val diff = now - timestamp
        if (diff < 0) return "in the future"
        return "${formatDuration(diff)} ago"
    }

    private fun formatUtcOffset(totalSeconds: Int): String {
        val sign = if (totalSeconds >= 0) "+" else "-"
        val absSeconds = kotlin.math.abs(totalSeconds)
        val hours = absSeconds / 3600
        val minutes = (absSeconds % 3600) / 60
        return if (minutes > 0) "$sign${hours.pad()}:${minutes.pad()}" else "$sign${hours.pad()}:00"
    }
}
