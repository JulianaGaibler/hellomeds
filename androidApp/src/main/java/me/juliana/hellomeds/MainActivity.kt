// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.preferences.AppearancePreferences
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.preferences.OnboardingPreferences
import me.juliana.hellomeds.flavor.CameraFeature
import me.juliana.hellomeds.ui.HelloMedsApp
import me.juliana.hellomeds.ui.features.onboarding.OnboardingUtils
import me.juliana.hellomeds.ui.features.onboarding.PermissionRevokedDialog
import me.juliana.hellomeds.ui.features.onboarding.RevokedPermission
import me.juliana.hellomeds.ui.features.onboarding.steps.NotificationPermissionScreen
import me.juliana.hellomeds.ui.features.settings.DebugScreen
import me.juliana.hellomeds.ui.navigation3.NavigationScreenProviders
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import me.juliana.hellomeds.ui.util.IncomingBackupHandler
import me.juliana.hellomeds.util.PermissionUtils
import me.juliana.hellomeds.workers.NotificationSchedulerWorker
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private var notificationEventIds: IntArray? by mutableStateOf(null)
    private var notificationGroupingMode: String? by mutableStateOf(null)
    private var stockDetailMedicationId: Int? by mutableStateOf(null)

    // Permission revocation tracking
    private var revokedPermissionToShow: RevokedPermission? by mutableStateOf(null)

    private val appearancePrefs: AppearancePreferences by inject()
    private val onboardingPrefs: OnboardingPreferences by inject()
    private val notifPrefs: NotificationPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check for notification intent
        handleNotificationIntent(intent)

        // Register platform capabilities for shared settings screen
        me.juliana.hellomeds.ui.util.PlatformCapabilities.versionString =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        me.juliana.hellomeds.ui.util.PlatformCapabilities.reconciliationTrigger = { ctx ->
            val context = ctx as android.content.Context
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<NotificationSchedulerWorker>()
                .setInitialDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "NotificationSchedulerWorker",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest,
            )
        }
        CameraFeature.registerMlStatusChecker(this@MainActivity)
        me.juliana.hellomeds.ui.util.PlatformCapabilities.cameraDetectionSupported =
            CameraFeature.isAvailable
        me.juliana.hellomeds.ui.util.PlatformCapabilities.debugBuild = BuildConfig.DEBUG

        // Observe screen privacy preference and update FLAG_SECURE accordingly
        lifecycleScope.launch {
            appearancePrefs.screenPrivacy.collect { enabled ->
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            val useDynamicColor by appearancePrefs.useDynamicColor.collectAsState(initial = true)
            HelloMedsTheme(dynamicColor = useDynamicColor) {
                // Provide real Android screen implementations for app-only screens
                val screenProviders = NavigationScreenProviders(
                    debugScreen = { onBack, onNavigateToOnboarding ->
                        DebugScreen(
                            onNavigateBack = onBack,
                            onNavigateToOnboarding = onNavigateToOnboarding,
                        )
                    },
                    cameraDetectionScreen = CameraFeature.getCameraScreen(),
                    notificationPermissionScreen = { onContinue, onBack ->
                        NotificationPermissionScreen(
                            onContinue = onContinue,
                            onBack = onBack,
                        )
                    },
                )

                HelloMedsApp(
                    screenProviders = screenProviders,
                    notificationEventIds = notificationEventIds,
                    notificationGroupingMode = notificationGroupingMode,
                    onNotificationHandled = {
                        // Clear the notification data after it's been handled
                        notificationEventIds = null
                        notificationGroupingMode = null
                    },
                    stockDetailMedicationId = stockDetailMedicationId,
                    onStockDetailHandled = {
                        stockDetailMedicationId = null
                    },
                )

                // Show permission revocation dialog if needed
                revokedPermissionToShow?.let { permission ->
                    PermissionRevokedDialog(
                        permission = permission,
                        onGrantPermission = {
                            // Open appropriate settings based on permission type
                            when (permission) {
                                RevokedPermission.NOTIFICATIONS ->
                                    PermissionUtils.openNotificationSettings(this)

                                RevokedPermission.EXACT_ALARMS ->
                                    PermissionUtils.openExactAlarmSettings(this)
                            }
                            revokedPermissionToShow = null
                        },
                        onDisableFeature = when (permission) {
                            // For permissions with feature toggles, provide disable callback
                            RevokedPermission.NOTIFICATIONS -> {
                                {
                                    lifecycleScope.launch {
                                        // Disable notifications feature
                                        notifPrefs.setNotificationsEnabled(false)
                                        // No need to explicitly cancel - alarms won't be scheduled when notifications disabled
                                    }
                                    revokedPermissionToShow = null
                                }
                            }

                            RevokedPermission.EXACT_ALARMS -> {
                                {
                                    lifecycleScope.launch {
                                        // Disable exact alarms feature
                                        notifPrefs.setUseExactAlarms(false)
                                        // Reschedule alarms as inexact
                                        val workManager = WorkManager.getInstance(this@MainActivity)
                                        workManager.enqueue(
                                            androidx.work.OneTimeWorkRequestBuilder<NotificationSchedulerWorker>()
                                                .build(),
                                        )
                                    }
                                    revokedPermissionToShow = null
                                }
                            }
                        },
                        onDismiss = {
                            revokedPermissionToShow = null
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Check for revoked permissions
        lifecycleScope.launch {
            val onboardingCompleted = onboardingPrefs.onboardingCompleted.first()

            // Detect revoked permissions
            val revokedPermissions = OnboardingUtils.detectRevokedPermissions(
                context = this@MainActivity,
                onboardingCompleted = onboardingCompleted,
            )

            // Show dialog for the first revoked permission
            // (Only show one at a time to avoid overwhelming the user)
            if (revokedPermissions.isNotEmpty()) {
                revokedPermissionToShow = revokedPermissions.first()
            }
        }

        // Safety-net: restore alarm chain on app resume.
        // If the OS killed the app, cleared alarms (system update, battery saver),
        // or the user force-stopped, the chain breaks until the 4-hour worker fires.
        // This heals it immediately when the user opens the app.
        AppLogger.i("MainActivity", "App opened, triggering safety-net reconciliation")
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<NotificationSchedulerWorker>()
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "NotificationSchedulerWorker-Resume",
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle notification intent when app is already running
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("OPEN_LOG_SHEET", false) == true) {
            notificationEventIds = intent.getIntArrayExtra("NOTIFICATION_SCHEDULE_IDS")
        }
        if (intent?.getBooleanExtra("OPEN_STOCK_DETAIL", false) == true) {
            stockDetailMedicationId = intent.getIntExtra("STOCK_DETAIL_MEDICATION_ID", -1)
                .takeIf { it != -1 }
        }
        // Handle .hmbackup file opened from another app
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                IncomingBackupHandler.setPendingImportReader {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            // Stat the URI before reading — reject anything that would OOM the app.
                            // Malicious or accidental gigabyte content URIs should fail loudly,
                            // not crash the activity.
                            val size = contentResolver.query(
                                uri,
                                arrayOf(android.provider.OpenableColumns.SIZE),
                                null,
                                null,
                                null,
                            )?.use { cursor ->
                                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                                    cursor.getLong(0)
                                } else {
                                    -1L
                                }
                            } ?: -1L
                            if (size > MAX_IMPORT_FILE_BYTES) {
                                AppLogger.w(
                                    "BackupImport",
                                    "Rejecting incoming backup: $size bytes exceeds cap of $MAX_IMPORT_FILE_BYTES",
                                )
                                return@withContext null
                            }
                            contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?.takeIf { it.isNotEmpty() }
                                ?: contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                    java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
                                }
                        } catch (e: Exception) {
                            AppLogger.e("BackupImport", "Failed to read backup file", e)
                            null
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Cap on incoming backup file size to prevent OOM from oversized URIs. */
        private const val MAX_IMPORT_FILE_BYTES = 50L * 1024L * 1024L // 50 MB
    }
}
