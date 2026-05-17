// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.model.enums.NotificationGroupingMode
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.preferences.OnboardingPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.depletion_notification_text
import me.juliana.hellomeds.shared.depletion_notification_title
import me.juliana.hellomeds.shared.low_stock_notification_text
import me.juliana.hellomeds.shared.low_stock_notification_title
import me.juliana.hellomeds.ui.viewmodel.DatabaseHealth
import me.juliana.hellomeds.ui.viewmodel.DetailedAlarmInfo
import me.juliana.hellomeds.ui.viewmodel.PreferencesInfo
import me.juliana.hellomeds.ui.viewmodel.TodayDoseInfo
import me.juliana.hellomeds.ui.viewmodel.TodayOverview
import me.juliana.hellomeds.ui.viewmodel.toHumanDescription
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSBundle
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Clock

class IOSDebugViewModel(
    private val projector: ScheduleProjector,
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val importanceLabelDao: ImportanceLabelDao,
    private val reconciler: ScheduleReconciler,
    private val notificationPrefs: NotificationPreferences,
    private val onboardingPrefs: OnboardingPreferences,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)

    // --- Today's Doses ---

    val todayOverview: StateFlow<TodayOverview> = combine(
        scheduleDao.getActive(),
        _refreshTrigger,
    ) { _, _ ->
        withContext(Dispatchers.Default) {
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(tz).date
            val startOfDay = today.atStartOfDayIn(tz).toEpochMilliseconds()
            val endOfDay = kotlinx.datetime.LocalDateTime(today, LocalTime(23, 59, 59, 999999999))
                .toInstant(tz).toEpochMilliseconds()
            val now = clock.now().toEpochMilliseconds()

            val diagnostic = projector.getDoseOverview(startOfDay, endOfDay, now)

            // Map diagnostic data to UI model (resolve real names for debug display)
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

    // --- Upcoming Schedules (next 7 days, pending only) ---

    val scheduledAlarms: StateFlow<List<DetailedAlarmInfo>> = combine(
        scheduleDao.getActive(),
        _refreshTrigger,
    ) { _, _ ->
        withContext(Dispatchers.Default) {
            val now = clock.now().toEpochMilliseconds()
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

    // --- iOS Notification Status ---

    val notificationStatus: StateFlow<IOSNotificationStatus> = combine(
        scheduleDao.getActive(),
        _refreshTrigger,
    ) { _, _ ->
        queryNotificationStatus()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IOSNotificationStatus(),
    )

    private suspend fun queryNotificationStatus(): IOSNotificationStatus {
        val center = UNUserNotificationCenter.currentNotificationCenter()

        // Query authorization status
        val settings = suspendCancellableCoroutine { cont ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                cont.resume(settings)
            }
        }

        val authStatus = when (settings?.authorizationStatus) {
            UNAuthorizationStatusAuthorized -> "Authorized"
            UNAuthorizationStatusDenied -> "Denied"
            UNAuthorizationStatusNotDetermined -> "Not Determined"
            UNAuthorizationStatusProvisional -> "Provisional"
            else -> "Unknown"
        }

        // Query pending notifications (detailed list for UI)
        val pendingRequests = suspendCancellableCoroutine<List<*>> { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                cont.resume(requests ?: emptyList<Any>())
            }
        }

        @Suppress("UNCHECKED_CAST")
        val medNotifications = (pendingRequests as List<UNNotificationRequest>)
            .filter { it.identifier.startsWith("med_") }
            .sortedBy {
                (it.content.userInfo["scheduledTime"] as? Long) ?: Long.MAX_VALUE
            }

        val pendingInfos = medNotifications.take(10).map { request ->
            val userInfo = request.content.userInfo
            PendingNotificationInfo(
                id = request.identifier,
                scheduledTime = (userInfo["scheduledTime"] as? Long) ?: 0L,
                medicationNames = request.content.title ?: "",
                isCritical = (userInfo["isCritical"] as? Boolean) ?: false,
            )
        }

        // Query delivered notifications
        val deliveredCount = suspendCancellableCoroutine { cont ->
            center.getDeliveredNotificationsWithCompletionHandler { notifications ->
                val medCount = (notifications ?: emptyList<Any>())
                    .count()
                cont.resume(medCount)
            }
        }

        // Use shared reconciler diagnostic for health check
        val diagnostic = reconciler.getDiagnosticSummary()

        return IOSNotificationStatus(
            authorizationStatus = authStatus,
            pendingCount = medNotifications.size,
            pendingNotifications = pendingInfos,
            deliveredCount = deliveredCount,
            healthy = diagnostic.healthy,
            healthMessage = diagnostic.healthMessage,
        )
    }

    // --- Preferences ---

    val preferences: StateFlow<PreferencesInfo> = combine(
        onboardingPrefs.onboardingCompleted,
        notificationPrefs.groupingMode,
        notificationPrefs.snoozeIntervalMinutes,
        notificationPrefs.lockScreenVisibility,
    ) { onboarding, grouping, snooze, lockScreen ->
        val version =
            NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"
        val build = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "?"

        PreferencesInfo(
            onboardingCompleted = onboarding,
            useExactAlarms = false, // N/A on iOS
            groupingMode = (grouping as NotificationGroupingMode).value,
            schedulingWindowHours = 168, // iOS uses 7-day window
            lastSchedulingTimestamp = 0L, // Not tracked on iOS
            snoozeIntervalMinutes = snooze as Int,
            lockScreenVisibility = (lockScreen as LockScreenVisibility).name,
            detectionMethod = "$version ($build)", // Repurpose for app version on iOS
            useDynamicColor = false, // N/A on iOS
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
        DatabaseHealth(
            activeMedicationCount = medications.size,
            activeScheduleCount = schedules.size,
            activeSessionCount = 0, // iOS doesn't track sessions
        )
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
     * Fires a low-stock notification using the first active medication's name,
     * matching the Android debug shortcut. iOS doesn't have a production
     * LowStockChecker yet (the binding in [iosPlatformModule] is a no-op stub),
     * so this posts a UNNotificationRequest directly. No threshold gating —
     * the real notifier (when it lands) won't be confused because debug
     * notifications use a fixed identifier that overwrites itself.
     */
    fun previewLowStockNotification() {
        viewModelScope.launch {
            val med = medicationDao.getActive().first().firstOrNull() ?: return@launch
            val name = med.displayName ?: med.name
            postDebugNotification(
                identifier = DEBUG_LOW_STOCK_IDENTIFIER,
                title = getString(Res.string.low_stock_notification_title, name),
                body = getString(Res.string.low_stock_notification_text),
            )
        }
    }

    /**
     * Fires a depletion-reminder notification. Mirrors [previewLowStockNotification].
     * Uses a sample `dosesSinceDepletion` of 33 — the same value the Android
     * debug screen uses (one past a typical 30-dose pack).
     */
    fun previewDepletionNotification() {
        viewModelScope.launch {
            val med = medicationDao.getActive().first().firstOrNull() ?: return@launch
            val name = med.displayName ?: med.name
            postDebugNotification(
                identifier = DEBUG_DEPLETION_IDENTIFIER,
                title = getString(Res.string.depletion_notification_title, name),
                body = getString(Res.string.depletion_notification_text, 33),
            )
        }
    }

    private suspend fun postDebugNotification(identifier: String, title: String, body: String) {
        val content = UNMutableNotificationContent()
        content.setTitle(title)
        content.setBody(body)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null, // deliver immediately
        )
        suspendCancellableCoroutine { cont ->
            UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { _ ->
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private companion object {
        // Fixed identifiers so re-firing the debug preview replaces the prior
        // one rather than stacking up in the notification tray.
        const val DEBUG_LOW_STOCK_IDENTIFIER = "hellomeds_debug_low_stock"
        const val DEBUG_DEPLETION_IDENTIFIER = "hellomeds_debug_depletion"
    }
}

// --- iOS-specific Data Classes ---

data class IOSNotificationStatus(
    val authorizationStatus: String = "Unknown",
    val pendingCount: Int = 0,
    val pendingNotifications: List<PendingNotificationInfo> = emptyList(),
    val deliveredCount: Int = 0,
    val healthy: Boolean = true,
    val healthMessage: String = "Checking...",
)

data class PendingNotificationInfo(
    val id: String,
    val scheduledTime: Long,
    val medicationNames: String,
    val isCritical: Boolean,
)
