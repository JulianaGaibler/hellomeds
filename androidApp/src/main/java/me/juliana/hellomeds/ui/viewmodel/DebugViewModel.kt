// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.BuildConfig
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.model.NotificationSession
import me.juliana.hellomeds.data.model.enums.DetectionMethod
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.model.enums.NotificationGroupingMode
import me.juliana.hellomeds.data.preferences.AppearancePreferences
import me.juliana.hellomeds.data.preferences.CameraPreferences
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.preferences.OnboardingPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.notifications.AlarmReconciler
import me.juliana.hellomeds.notifications.NotificationBuilder
import me.juliana.hellomeds.notifications.NotificationChannels
import me.juliana.hellomeds.notifications.NotificationSessionManager
import kotlin.time.Clock

class DebugViewModel(
    private val context: Context,
    private val projector: ScheduleProjector,
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val importanceLabelDao: ImportanceLabelDao,
    private val reconciler: AlarmReconciler,
    private val sessionManager: NotificationSessionManager,
    private val alarmManager: AlarmManager,
    private val notificationPrefs: NotificationPreferences,
    private val cameraPrefs: CameraPreferences,
    private val appearancePrefs: AppearancePreferences,
    private val onboardingPrefs: OnboardingPreferences,
    private val notificationBuilder: NotificationBuilder,
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)

    val todayOverview: StateFlow<TodayOverview> = combine(
        scheduleDao.getActive(),
        _refreshTrigger,
    ) { _, _ ->
        withContext(Dispatchers.IO) {
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(tz).date
            val startOfDay = today.atStartOfDayIn(tz).toEpochMilliseconds()
            val endOfDay = kotlinx.datetime.LocalDateTime(today, LocalTime(23, 59, 59, 999999999))
                .toInstant(tz).toEpochMilliseconds()
            val now = System.currentTimeMillis()

            val diagnostic = projector.getDoseOverview(startOfDay, endOfDay, now)

            // Resolve real medication and schedule names for the debug display.
            val events = projector.projectEvents(startOfDay, endOfDay).sortedBy { it.scheduledTime }
            val doses = events.map { event ->
                val medication = medicationDao.getByIdSync(event.medicationId)
                val schedule = scheduleDao.getById(event.scheduleId).first()
                TodayDoseInfo(
                    event = event,
                    medicationName = medication?.displayName ?: medication?.name ?: "Unknown",
                    dose = event.dose,
                    strengthUnit = medication?.strengthUnit?.value,
                    scheduleDescription = schedule?.toHumanDescription() ?: "Unknown schedule",
                    isOverdue = event.isPending && event.scheduledTime < now,
                )
            }

            TodayOverview(
                doses = doses,
                totalCount = diagnostic.totalCount,
                takenCount = diagnostic.takenCount,
                skippedCount = diagnostic.skippedCount,
                pendingCount = diagnostic.pendingCount,
                overdueCount = diagnostic.overdueCount,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayOverview(),
    )

    val scheduledAlarms: StateFlow<List<DetailedAlarmInfo>> = combine(
        scheduleDao.getActive(),
        _refreshTrigger,
    ) { _, _ ->
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val sevenDaysLater = now + (7 * 24 * 60 * 60 * 1000L)
            val events = projector.projectEvents(now, sevenDaysLater)
                .filter { it.isPending }
                .sortedBy { it.scheduledTime }

            events.map { event ->
                val medication = medicationDao.getByIdSync(event.medicationId)
                val schedule = scheduleDao.getById(event.scheduleId).first()
                val importanceLabel = medication?.importanceLabelId?.let { labelId ->
                    importanceLabelDao.getByIdSync(labelId)
                }

                DetailedAlarmInfo(
                    event = event,
                    medication = medication,
                    schedule = schedule,
                    importanceLabel = importanceLabel,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val reconcilerStatus: StateFlow<ReconcilerStatus> = combine(
        scheduleDao.getActive(),
        _refreshTrigger,
    ) { _, _ ->
        withContext(Dispatchers.IO) {
            val diagnostic = reconciler.getDiagnosticSummary()
            val now = System.currentTimeMillis()
            val nextWakeup = reconciler.computeNextWakeupTime()

            val wakeupReason = when (diagnostic.details["wakeupReason"]) {
                "FOLLOW_UP" -> WakeupReason.FOLLOW_UP
                "SNOOZE" -> WakeupReason.SNOOZE
                "SCHEDULED_EVENT" -> WakeupReason.SCHEDULED_EVENT
                else -> WakeupReason.NONE
            }
            val alarmType = when (diagnostic.details["alarmType"]) {
                "SET_ALARM_CLOCK" -> AlarmType.SET_ALARM_CLOCK
                "SET_EXACT" -> AlarmType.SET_EXACT
                "SET_INEXACT" -> AlarmType.SET_INEXACT
                else -> AlarmType.NONE
            }

            // Resolve real medication names for each session.
            val sessions = sessionManager.getAllSessions()
            val sessionDetails = sessions.map { session ->
                val medNames = session.scheduleIds.mapNotNull { scheduleId ->
                    val schedule = scheduleDao.getById(scheduleId).first()
                    schedule?.let { medicationDao.getByIdSync(it.medicationId) }
                        ?.let { it.displayName ?: it.name }
                }
                SessionDetail(session = session, medicationNames = medNames)
            }

            ReconcilerStatus(
                alarmExists = diagnostic.details["alarmExists"]?.toBoolean() ?: false,
                nextWakeupTime = nextWakeup,
                alarmType = alarmType,
                wakeupReason = wakeupReason,
                sessionDetails = sessionDetails,
                alarmHealthy = diagnostic.healthy,
                alarmHealthMessage = diagnostic.healthMessage,
                totalSessionCount = diagnostic.details["totalSessions"]?.toIntOrNull() ?: 0,
                activeFollowUpCount = diagnostic.details["activeFollowUps"]?.toIntOrNull() ?: 0,
                snoozedCount = diagnostic.details["snoozedSessions"]?.toIntOrNull() ?: 0,
                catchUpEventCount = diagnostic.details["catchUpEventCount"]?.toIntOrNull() ?: 0,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReconcilerStatus(),
    )

    val systemStatus: StateFlow<SystemStatus> = _refreshTrigger.combine(
        flow { emit(Unit) },
    ) { _, _ ->
        val powerManager = context.getSystemService(PowerManager::class.java)

        SystemStatus(
            notificationsEnabled = NotificationChannels.areNotificationsEnabled(context),
            normalChannelEnabled = NotificationChannels.isChannelEnabled(
                context,
                NotificationChannels.NORMAL_CHANNEL_ID,
            ),
            criticalChannelEnabled = NotificationChannels.isChannelEnabled(
                context,
                NotificationChannels.CRITICAL_CHANNEL_ID,
            ),
            exactAlarmsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            },
            batteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            },
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            buildType = if (BuildConfig.DEBUG) "debug" else "release",
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SystemStatus(),
    )

    // --- Preferences ---

    val preferences: StateFlow<PreferencesInfo> = combine(
        onboardingPrefs.onboardingCompleted,
        notificationPrefs.useExactAlarms,
        notificationPrefs.groupingMode,
        notificationPrefs.schedulingWindowHours,
        notificationPrefs.lastSchedulingTimestamp,
        notificationPrefs.snoozeIntervalMinutes,
        notificationPrefs.lockScreenVisibility,
        cameraPrefs.detectionMethod,
        appearancePrefs.useDynamicColor,
    ) { flows: Array<Any?> ->
        PreferencesInfo(
            onboardingCompleted = flows[0] as Boolean,
            useExactAlarms = flows[1] as Boolean,
            groupingMode = (flows[2] as NotificationGroupingMode).value,
            schedulingWindowHours = flows[3] as Int,
            lastSchedulingTimestamp = flows[4] as Long,
            snoozeIntervalMinutes = flows[5] as Int,
            lockScreenVisibility = (flows[6] as LockScreenVisibility).name,
            detectionMethod = (flows[7] as DetectionMethod).name,
            useDynamicColor = flows[8] as Boolean,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreferencesInfo(),
    )

    // --- Database Health ---

    val databaseHealth: StateFlow<DatabaseHealth> = combine(
        medicationDao.getActive(),
        scheduleDao.getActive(),
        _refreshTrigger,
    ) { medications, schedules, _ ->
        withContext(Dispatchers.IO) {
            val sessionCount = sessionManager.getAllSessions().size
            DatabaseHealth(
                activeMedicationCount = medications.size,
                activeScheduleCount = schedules.size,
                activeSessionCount = sessionCount,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DatabaseHealth(),
    )

    // --- Actions ---

    fun forceReconcile() {
        viewModelScope.launch {
            reconciler.reconcile()
            _refreshTrigger.value++
        }
    }

    /**
     * Fire a low-stock notification using the first active medication's data,
     * bypassing the threshold + permission gates that the real
     * [me.juliana.hellomeds.notifications.LowStockNotifier] enforces. Pure UI
     * preview — does NOT toggle [lowStockAlertSent], so the real notifier will
     * still fire normally once stock actually crosses the threshold.
     */
    fun previewLowStockNotification(): Unit = postPreviewNotification(
        notificationId = DEBUG_LOW_STOCK_NOTIFICATION_ID,
        buildFromMedication = { med, visibility ->
            notificationBuilder.buildLowStockNotification(
                medication = med,
                notificationId = DEBUG_LOW_STOCK_NOTIFICATION_ID,
                discreet = false,
                lockScreenVisibility = visibility,
            )
        },
    )

    /**
     * Fire a depletion-reminder notification. See [previewLowStockNotification].
     * Uses a sample `dosesSinceDepletion` of 33 (the threshold for a 30-dose pack).
     */
    fun previewDepletionNotification(): Unit = postPreviewNotification(
        notificationId = DEBUG_DEPLETION_NOTIFICATION_ID,
        buildFromMedication = { med, visibility ->
            notificationBuilder.buildDepletionReminderNotification(
                medication = med,
                dosesSinceDepletion = 33,
                notificationId = DEBUG_DEPLETION_NOTIFICATION_ID,
                discreet = false,
                lockScreenVisibility = visibility,
            )
        },
    )

    private fun postPreviewNotification(
        notificationId: Int,
        buildFromMedication: (
            med: me.juliana.hellomeds.data.database.entities.Medication,
            visibility: LockScreenVisibility,
        ) -> android.app.Notification,
    ) {
        viewModelScope.launch {
            val med = medicationDao.getActive().first().firstOrNull() ?: run {
                me.juliana.hellomeds.data.util.AppLogger.w(
                    "DebugViewModel",
                    "No active medication available — preview notification skipped",
                )
                return@launch
            }
            val visibility = notificationPrefs.lockScreenVisibility.first()
            val notification = buildFromMedication(med, visibility)
            val notifMgr = context.getSystemService(android.app.NotificationManager::class.java)
            notifMgr.notify(notificationId, notification)
        }
    }

    companion object {
        // Hardcoded debug-only notification IDs to avoid colliding with the
        // real medication-based IDs that the production notifiers generate.
        private const val DEBUG_LOW_STOCK_NOTIFICATION_ID = 0x7DEB10
        private const val DEBUG_DEPLETION_NOTIFICATION_ID = 0x7DEB20
    }
}

// --- Android-only Data Classes ---

enum class AlarmType { NONE, SET_ALARM_CLOCK, SET_EXACT, SET_INEXACT }
enum class WakeupReason { NONE, SCHEDULED_EVENT, FOLLOW_UP, SNOOZE }

data class SessionDetail(
    val session: NotificationSession,
    val medicationNames: List<String>,
)

data class ReconcilerStatus(
    val alarmExists: Boolean = false,
    val nextWakeupTime: Long? = null,
    val alarmType: AlarmType = AlarmType.NONE,
    val wakeupReason: WakeupReason = WakeupReason.NONE,
    val sessionDetails: List<SessionDetail> = emptyList(),
    val alarmHealthy: Boolean = true,
    val alarmHealthMessage: String = "Alarm system healthy",
    val totalSessionCount: Int = 0,
    val activeFollowUpCount: Int = 0,
    val snoozedCount: Int = 0,
    val catchUpEventCount: Int = 0,
)

data class SystemStatus(
    val notificationsEnabled: Boolean = false,
    val normalChannelEnabled: Boolean = false,
    val criticalChannelEnabled: Boolean = false,
    val exactAlarmsGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val appVersion: String = "",
    val buildType: String = "",
)
