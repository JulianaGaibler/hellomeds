// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds

import android.app.Application
import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.util.DiagnosticLog
import me.juliana.hellomeds.data.di.dataModule
import me.juliana.hellomeds.di.appModule
import me.juliana.hellomeds.di.notificationModule
import me.juliana.hellomeds.di.viewModelModule
import me.juliana.hellomeds.notifications.NotificationChannels
import me.juliana.hellomeds.ui.di.sharedUiModule
import me.juliana.hellomeds.workers.AutoBackupWorker
import me.juliana.hellomeds.workers.CleanupWorker
import me.juliana.hellomeds.workers.NotificationSchedulerWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import java.util.Calendar
import java.util.concurrent.TimeUnit

@ExperimentalMaterial3Api
class HelloMedsApplication : Application(), Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Load SQLCipher native library before any database code runs
        System.loadLibrary("sqlcipher")

        // Enable file-backed diagnostic log persistence (before any service code runs)
        DiagnosticLog.configure(filesDir.resolve("diagnostic.log").absolutePath)
        // TODO(BETA_ROLLBACK): forced verbose for closed-beta diagnostic-report
        DiagnosticLog.verbose = true

        ComposeMaterial3Flags.isSnackbarStylingFixEnabled = true
        ComposeMaterial3Flags.isAnchoredDraggableComponentsInvalidationFixEnabled = true

        startKoin {
            androidLogger()
            androidContext(this@HelloMedsApplication)
            workManagerFactory()
            modules(
                dataModule,
                appModule,
                notificationModule,
                sharedUiModule,
                viewModelModule,
            )
        }

        // Off-main: channel creation resolves CMP string resources via suspend getString,
        // which would otherwise block the main thread inside Application.onCreate.
        // createNotificationChannels is idempotent, so deferring it is safe.
        applicationScope.launch {
            NotificationChannels.createChannels(this@HelloMedsApplication)
        }
        initializeWorkers()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun initializeWorkers() {
        val workManager = WorkManager.getInstance(this)

        // NotificationSchedulerWorker - safety-net every 4 hours
        val notificationSchedulerWork = PeriodicWorkRequestBuilder<NotificationSchedulerWorker>(
            4,
            TimeUnit.HOURS,
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "NotificationSchedulerWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            notificationSchedulerWork,
        )

        // CleanupWorker - daily at 3 AM
        val now = System.currentTimeMillis()
        val delayTo3AM = calculateDelayTo3AM(now)

        val cleanupWork = PeriodicWorkRequestBuilder<CleanupWorker>(
            1,
            TimeUnit.DAYS,
        ).setInitialDelay(delayTo3AM, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "CleanupWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupWork,
        )

        // AutoBackupWorker - daily at 4 AM (offset from cleanup at 3 AM)
        val delayTo4AM = calculateDelayTo3AM(now) + 60 * 60 * 1000L // 3 AM + 1 hour
        val autoBackupWork = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            1,
            TimeUnit.DAYS,
        ).setInitialDelay(delayTo4AM, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "AutoBackupWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            autoBackupWork,
        )
    }

    private fun calculateDelayTo3AM(now: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= now) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return calendar.timeInMillis - now
    }
}
