// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.juliana.hellomeds.data.util.DiagnosticLog
import me.juliana.hellomeds.data.di.dataModule
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.repository.MedicationHistoryRepository
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.notifications.IOSNotificationSessionManager
import me.juliana.hellomeds.notifications.NotificationActionStrings
import me.juliana.hellomeds.notifications.NotificationDeepLinkState
import me.juliana.hellomeds.notifications.NotificationHandlerHolder
import me.juliana.hellomeds.notifications.createIosNotificationModule
import me.juliana.hellomeds.notifications.isAlarmKitAuthorized
import me.juliana.hellomeds.notifications.isAlarmKitAvailable
import me.juliana.hellomeds.notifications.registerNotificationCategory
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.depletion_notification_action_mark_depleted
import me.juliana.hellomeds.shared.notification_action_skipped
import me.juliana.hellomeds.shared.notification_action_snooze
import me.juliana.hellomeds.shared.notification_action_taken
import org.jetbrains.compose.resources.getString
import me.juliana.hellomeds.notifications.startAlarmSound
import me.juliana.hellomeds.notifications.stopAlarmSound
import me.juliana.hellomeds.ui.HelloMedsApp
import me.juliana.hellomeds.ui.di.iosPlatformModule
import me.juliana.hellomeds.ui.di.sharedUiModule
import me.juliana.hellomeds.ui.features.alarm.ReminderAlarmScreen
import me.juliana.hellomeds.ui.features.camera.IOSCameraDetectionScreen
import me.juliana.hellomeds.ui.features.onboarding.IOSAlarmKitPermissionScreen
import me.juliana.hellomeds.ui.features.onboarding.IOSCriticalAlertsPermissionScreen
import me.juliana.hellomeds.ui.features.onboarding.IOSNotificationPermissionScreen
import me.juliana.hellomeds.ui.features.settings.IOSDebugScreen
import me.juliana.hellomeds.ui.navigation3.NavigationScreenProviders
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import me.juliana.hellomeds.ui.util.PermissionUtils
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import me.juliana.hellomeds.ui.util.updateScreenPrivacyState
import me.juliana.hellomeds.workers.IOSBackgroundTaskManager
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIViewController
import platform.UserNotifications.UNUserNotificationCenter

private var koinInitialized = false

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController {
    if (!koinInitialized) {
        // Enable file-backed diagnostic log persistence (before any service code runs)
        val cachesDir = NSFileManager.defaultManager.URLForDirectory(
            directory = NSCachesDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        cachesDir?.path?.let { DiagnosticLog.configure("$it/diagnostic.log") }
        DiagnosticLog.verbose = kotlin.native.Platform.isDebugBinary

        // Core DI — no iOS notification classes referenced in iosPlatformModule
        startKoin {
            modules(
                dataModule,
                iosPlatformModule,
                sharedUiModule,
            )
        }
        koinInitialized = true

        // Load notification module separately (safe — references iOS notification
        // classes only inside Koin lambdas, after startKoin has completed)
        KoinPlatform.getKoin().loadModules(listOf(createIosNotificationModule()), allowOverride = true)

        // Set up notification system (category registration, delegate, etc.)
        setupNotifications()

        // Register and schedule background tasks (BGTaskScheduler).
        // Registration must happen before the app finishes launching.
        setupBackgroundTasks()
    }

    // Accessibility tree syncs lazily on demand since CMP 1.8.0 — no manual config needed.
    return ComposeUIViewController {
        HelloMedsTheme {
            // Observe screen privacy preference and push state to Swift bridge
            val koin = KoinPlatform.getKoin()
            val appearancePrefs = koin.get<me.juliana.hellomeds.data.preferences.AppearancePreferences>()
            val screenPrivacy by appearancePrefs.screenPrivacy.collectAsState(false)
            LaunchedEffect(screenPrivacy) {
                updateScreenPrivacyState(screenPrivacy)
            }

            // Refresh the 7-day notification batch every time the app becomes active.
            // iOS BGAppRefreshTask is aggressively throttled — if the user doesn't open the
            // app for days, iOS may stop running background tasks entirely. Reconciling on
            // resume guarantees notifications are replenished whenever the user opens the app.
            //
            // This lives inside the Compose body (rather than setupNotifications()) so that
            // ScheduleReconciler resolution — which transitively constructs AppDatabase —
            // is deferred until SwiftUI actually realizes this view. The user cannot reach
            // this point pre-first-unlock, so the Keychain is guaranteed to be readable.
            val resumeScope = rememberCoroutineScope()
            DisposableEffect(Unit) {
                val reconciler = KoinPlatform.getKoin().get<ScheduleReconciler>()
                val observer = NSNotificationCenter.defaultCenter.addObserverForName(
                    name = UIApplicationDidBecomeActiveNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                ) { _ ->
                    resumeScope.launch { reconciler.reconcile() }
                }
                onDispose {
                    observer?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
                }
            }

            val screenProviders = NavigationScreenProviders(
                debugScreen = { onBack, onNavigateToOnboarding ->
                    IOSDebugScreen(
                        onNavigateBack = onBack,
                        onNavigateToOnboarding = onNavigateToOnboarding,
                    )
                },
                cameraDetectionScreen = { onBack, onDetectionComplete ->
                    IOSCameraDetectionScreen(
                        onNavigateBack = onBack,
                        onDetectionComplete = onDetectionComplete,
                    )
                },
                notificationPermissionScreen = { onContinue, onBack ->
                    IOSNotificationPermissionScreen(
                        onContinue = onContinue,
                        onBack = onBack,
                    )
                },
                alarmKitPermissionScreen = if (isAlarmKitAvailable()) {
                    { onContinue, onBack ->
                        IOSAlarmKitPermissionScreen(
                            onContinue = onContinue,
                            onBack = onBack,
                        )
                    }
                } else {
                    null
                },
                criticalAlertsPermissionScreen = { onContinue, onBack ->
                    IOSCriticalAlertsPermissionScreen(
                        onContinue = onContinue,
                        onBack = onBack,
                    )
                },
            )

            // Deep link: when user taps a notification, IOSNotificationDelegate stores
            // the scheduleIds in NotificationDeepLinkState. This flows through to
            // TrackingScreen which auto-opens the log bottom sheet.
            val notificationEventIds by NotificationDeepLinkState.pendingEventIds.collectAsState()
            val isAlarmNotification by NotificationDeepLinkState.isAlarmNotification.collectAsState()
            val alarmMedicationNames by NotificationDeepLinkState.alarmMedicationNames.collectAsState()
            val alarmScheduledTime by NotificationDeepLinkState.alarmScheduledTime.collectAsState()

            Box(modifier = Modifier.fillMaxSize()) {
                HelloMedsApp(
                    screenProviders = screenProviders,
                    // Don't pass notification IDs to the log sheet when it's an alarm — the alarm
                    // overlay handles it. For non-alarm taps, pass through normally.
                    notificationEventIds = if (isAlarmNotification) null else notificationEventIds,
                    onNotificationHandled = { NotificationDeepLinkState.consume() },
                )

                // Alarm overlay: fullscreen alarm screen shown on top when alarm notification is tapped.
                // Starts looping audio + vibration, stops on any action.
                if (isAlarmNotification && notificationEventIds != null) {
                    // Start alarm audio/vibration when shown, stop when dismissed
                    DisposableEffect(Unit) {
                        startAlarmSound()
                        onDispose { stopAlarmSound() }
                    }

                    val timeText = if (alarmScheduledTime > 0) {
                        val date = platform.Foundation.NSDate.dateWithTimeIntervalSince1970(alarmScheduledTime / 1000.0)
                        val formatter = platform.Foundation.NSDateFormatter()
                        formatter.dateFormat = "HH:mm"
                        formatter.stringFromDate(date)
                    } else {
                        ""
                    }

                    val scope = CoroutineScope(Dispatchers.Main)
                    val koin = KoinPlatform.getKoin()
                    val notifPrefs = koin.get<me.juliana.hellomeds.data.preferences.NotificationPreferences>()
                    val lockVisibility by notifPrefs.lockScreenVisibility.collectAsState(
                        initial = me.juliana.hellomeds.data.model.enums.LockScreenVisibility.SHOW_WITH_NAMES,
                    )
                    val isDiscreet = lockVisibility != me.juliana.hellomeds.data.model.enums.LockScreenVisibility.SHOW_WITH_NAMES

                    ReminderAlarmScreen(
                        medicationNames = alarmMedicationNames,
                        timeText = timeText,
                        discreet = isDiscreet,
                        onTaken = {
                            stopAlarmSound()
                            scope.launch {
                                handleAlarmAction(koin, alarmScheduledTime, notificationEventIds!!, isTaken = true)
                                NotificationDeepLinkState.consume()
                            }
                        },
                        onSkipped = {
                            stopAlarmSound()
                            scope.launch {
                                handleAlarmAction(koin, alarmScheduledTime, notificationEventIds!!, isTaken = false)
                                NotificationDeepLinkState.consume()
                            }
                        },
                        onSnooze = {
                            stopAlarmSound()
                            scope.launch {
                                handleAlarmSnooze(koin, alarmScheduledTime, notificationEventIds!!)
                                NotificationDeepLinkState.consume()
                            }
                        },
                        onDismiss = {
                            stopAlarmSound()
                            NotificationDeepLinkState.consume()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Initializes the iOS notification system:
 * 1. Registers notification category with Take/Skip/Snooze actions
 * 2. Sets the notification delegate for action handling + foreground presentation
 *
 * Note: Permission is now requested during the onboarding flow
 * via IOSNotificationPermissionScreen, not at app startup.
 */
private fun setupNotifications() {
    // Pre-resolve localized action-button titles via CMP resources. Registration
    // itself must be synchronous (iOS requires the category to exist before any
    // notification using it is scheduled), but CMP's `getString` is `suspend`.
    // `runBlocking(Dispatchers.Default)` parks the Main thread on Default so a
    // potential internal Main dispatch inside `getString` can't deadlock us.
    val actionStrings = runBlocking(Dispatchers.Default) {
        NotificationActionStrings(
            take = getString(Res.string.notification_action_taken),
            skip = getString(Res.string.notification_action_skipped),
            snooze = getString(Res.string.notification_action_snooze),
            markDepleted = getString(Res.string.depletion_notification_action_mark_depleted),
        )
    }

    // Register the notification category (must happen before scheduling)
    registerNotificationCategory(actionStrings)

    // Query actual notification authorization status early so the cache is warm
    // before any UI reads it. The composable isNotificationPermissionGranted() also
    // queries, but it only runs when the Settings screen is composed.
    UNUserNotificationCenter.currentNotificationCenter()
        .getNotificationSettingsWithCompletionHandler { settings ->
            PermissionUtils.cachedNotificationsEnabled =
                settings?.authorizationStatus == platform.UserNotifications.UNAuthorizationStatusAuthorized ||
                settings?.authorizationStatus == platform.UserNotifications.UNAuthorizationStatusProvisional
            // Cache critical alert authorization for PlatformCapabilities
            PlatformCapabilities.criticalAlertsAuthorized =
                settings?.criticalAlertSetting == platform.UserNotifications.UNNotificationSettingEnabled

            // Cache AlarmKit authorization status (iOS 26+)
            if (isAlarmKitAvailable()) {
                CoroutineScope(Dispatchers.Main).launch {
                    PlatformCapabilities.alarmKitAuthorized = isAlarmKitAuthorized()
                }
            }
        }

    // Set the notification delegate for handling user actions.
    // Safe at cold launch: the delegate's DB-touching collaborators are resolved lazily
    // (see IOSNotificationDelegate), so no AppDatabase construction happens here.
    // Retrieve via NotificationHandlerHolder (pure Kotlin wrapper — Koin can't reflect on NSObject subclasses).
    val holder = KoinPlatform.getKoin().get<NotificationHandlerHolder>()
    UNUserNotificationCenter.currentNotificationCenter().delegate = holder.delegate

    // The "reconcile on resume" observer used to live here, but it eagerly resolved
    // ScheduleReconciler — which transitively constructs AppDatabase, which on iOS
    // requires the Keychain to be unlocked. A foreground launch attempt while the
    // device is post-reboot pre-first-unlock would crash here. The observer has been
    // moved into a DisposableEffect inside the Compose body, which only runs after
    // SwiftUI realizes the WindowGroup — i.e., after first unlock.
}

/**
 * Registers and schedules iOS background tasks via BGTaskScheduler.
 *
 * Two tasks mirror Android's WorkManager workers:
 * 1. Reconcile (every ~4h): re-schedules notifications + auto-skips overdue events
 * 2. Cleanup (every ~24h): removes old history records + re-reconciles
 *
 * Registration MUST happen before the app finishes launching (iOS requirement).
 * Scheduling is safe to call multiple times — duplicate requests replace previous ones.
 */
private fun setupBackgroundTasks() {
    val taskManager = KoinPlatform.getKoin().get<IOSBackgroundTaskManager>()
    taskManager.registerTasks()
    taskManager.scheduleReconcileTask()
    taskManager.scheduleCleanupTask()
}

/**
 * Handles Take/Skip actions from the alarm screen overlay.
 * Marks all matching events as taken or skipped, then cleans up sessions and reconciles.
 */
private suspend fun handleAlarmAction(
    koin: org.koin.core.Koin,
    scheduledTime: Long,
    scheduleIds: IntArray,
    isTaken: Boolean,
) {
    val projector = koin.get<ScheduleProjector>()
    val historyRepo = koin.get<MedicationHistoryRepository>()
    val sessionManager = koin.get<IOSNotificationSessionManager>()
    val reconciler = koin.get<ScheduleReconciler>()

    val events = projector.getPendingEventsAtTime(scheduledTime)
    val now = me.juliana.hellomeds.data.util.currentTimeMillis()

    for (event in events) {
        if (event.scheduleId in scheduleIds && event.isPending) {
            if (isTaken) {
                historyRepo.markAsTaken(event, event.dose, now)
            } else {
                historyRepo.markAsSkipped(event)
            }
        }
    }

    sessionManager.cancelFollowUpsForSlot(scheduledTime)
    sessionManager.cancelSnoozeForSlot(scheduledTime)
    sessionManager.removeDeliveredForSlot(scheduledTime)

    val session = sessionManager.getSession(scheduledTime.toString())
    if (session != null) {
        sessionManager.removeSession(session.timeSlotKey)
    }

    reconciler.reconcile()
}

/**
 * Handles Snooze action from the alarm screen overlay.
 * Delegates to the session manager's snooze logic and reconciles.
 */
private suspend fun handleAlarmSnooze(koin: org.koin.core.Koin, scheduledTime: Long, scheduleIds: IntArray) {
    val sessionManager = koin.get<IOSNotificationSessionManager>()
    val notifPrefs = koin.get<me.juliana.hellomeds.data.preferences.NotificationPreferences>()
    val reconciler = koin.get<ScheduleReconciler>()

    val snoozeMinutes = notifPrefs.snoozeIntervalMinutes.first()
    val snoozeUntil = me.juliana.hellomeds.data.util.currentTimeMillis() + (snoozeMinutes * 60_000L)

    sessionManager.cancelFollowUpsForSlot(scheduledTime)
    sessionManager.removeDeliveredForSlot(scheduledTime)

    val session = sessionManager.getSession(scheduledTime.toString())
    if (session != null) {
        sessionManager.snoozeSession(session.timeSlotKey, snoozeUntil)
    }

    reconciler.reconcile()
}
