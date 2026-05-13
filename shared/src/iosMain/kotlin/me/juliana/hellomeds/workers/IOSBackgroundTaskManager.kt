// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.juliana.hellomeds.workers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.repository.MedicationHistoryRepository
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.util.currentTimeMillis
import org.koin.mp.KoinPlatform
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateByAddingTimeInterval

/**
 * Manages iOS background tasks via BGTaskScheduler.
 *
 * This is a pure Kotlin class (NOT an NSObject subclass) so it is safe
 * to register directly in Koin without wrapper indirection.
 *
 * Provides two background tasks mirroring Android's WorkManager workers:
 *
 * 1. **Reconcile task** (BGAppRefreshTask, ~4 hours):
 *    - Re-schedules upcoming notification batch via ScheduleReconciler
 *    - Auto-skips overdue events via MedicationHistoryRepository
 *
 * 2. **Cleanup task** (BGProcessingTask, ~24 hours):
 *    - Removes old history records (>90 days)
 *
 * IMPORTANT:
 * - Task registration (registerTasks) MUST be called before the app finishes
 *   launching. In practice, call it from MainViewController before Compose setup.
 * - BGTaskScheduler methods must be called on the main thread.
 * - Task identifiers must be listed in Info.plist under
 *   BGTaskSchedulerPermittedIdentifiers.
 */
class IOSBackgroundTaskManager {

    // Lazy resolution: registerTasks() is called from MainViewController during cold launch,
    // but the BGTask handlers only fire later, when the OS schedules them. Resolving these
    // collaborators eagerly would pull AppDatabase through their constructors, which on iOS
    // requires the Keychain to be unlocked — and a foreground launch attempted in the
    // post-reboot pre-first-unlock window would crash. Lazy delegates push resolution to
    // task-fire time, which always runs after the device has been unlocked at least once.
    private val reconciler: ScheduleReconciler by lazy { KoinPlatform.getKoin().get() }
    private val historyRepository: MedicationHistoryRepository by lazy { KoinPlatform.getKoin().get() }
    private val historyDao: MedicationHistoryDao by lazy { KoinPlatform.getKoin().get() }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentReconcileJob: Job? = null
    private var currentCleanupJob: Job? = null

    /**
     * Registers BGTask launch handlers for both task identifiers.
     *
     * Must be called exactly once, before the app finishes launching.
     * Calling this multiple times for the same identifier will cause the
     * system to kill the app.
     */
    fun registerTasks() {
        val scheduler = BGTaskScheduler.sharedScheduler

        val reconcileRegistered = scheduler.registerForTaskWithIdentifier(
            identifier = RECONCILE_TASK_ID,
            usingQueue = null,
        ) { task ->
            handleReconcileTask(task!!)
        }

        if (reconcileRegistered) {
            AppLogger.i(TAG, "Registered reconcile background task: $RECONCILE_TASK_ID")
        } else {
            AppLogger.e(
                TAG,
                "Failed to register reconcile task - check Info.plist BGTaskSchedulerPermittedIdentifiers",
            )
        }

        val cleanupRegistered = scheduler.registerForTaskWithIdentifier(
            identifier = CLEANUP_TASK_ID,
            usingQueue = null,
        ) { task ->
            handleCleanupTask(task!!)
        }

        if (cleanupRegistered) {
            AppLogger.i(TAG, "Registered cleanup background task: $CLEANUP_TASK_ID")
        } else {
            AppLogger.e(
                TAG,
                "Failed to register cleanup task - check Info.plist BGTaskSchedulerPermittedIdentifiers",
            )
        }
    }

    /**
     * Schedules the periodic reconciliation task.
     * Uses BGAppRefreshTaskRequest with a 4-hour earliest begin date.
     *
     * Safe to call multiple times — submitting a request for an already-queued
     * task replaces the previous request.
     */
    fun scheduleReconcileTask() {
        val request = BGAppRefreshTaskRequest(identifier = RECONCILE_TASK_ID)
        request.earliestBeginDate = NSDate().dateByAddingTimeInterval(RECONCILE_INTERVAL_SECONDS)

        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
            AppLogger.i(
                TAG,
                "Scheduled reconcile task (earliest in ${RECONCILE_INTERVAL_SECONDS / 3600.0}h)",
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to schedule reconcile task: ${e.message}")
        }
    }

    /**
     * Schedules the daily cleanup task.
     * Uses BGProcessingTaskRequest with a 24-hour earliest begin date.
     * Does not require network or external power.
     */
    fun scheduleCleanupTask() {
        val request = BGProcessingTaskRequest(identifier = CLEANUP_TASK_ID).apply {
            earliestBeginDate = NSDate().dateByAddingTimeInterval(CLEANUP_INTERVAL_SECONDS)
            requiresNetworkConnectivity = false
            requiresExternalPower = false
        }

        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
            AppLogger.i(TAG, "Scheduled cleanup task (earliest in ${CLEANUP_INTERVAL_SECONDS / 3600.0}h)")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to schedule cleanup task: ${e.message}")
        }
    }

    /**
     * Handles the reconcile background task when the system launches it.
     *
     * 1. Sets an expiration handler to cancel the coroutine if time runs out
     * 2. Runs reconciliation + auto-skip in a coroutine
     * 3. Reports success/failure to the system
     * 4. Schedules the next occurrence
     */
    private fun handleReconcileTask(task: BGTask) {
        AppLogger.i(TAG, "Reconcile task started")

        // Schedule the next occurrence immediately so it's queued even if this one fails
        scheduleReconcileTask()

        val job = scope.launch {
            try {
                reconciler.reconcile()
                historyRepository.autoSkipMissedEvents()
                AppLogger.i(TAG, "Reconcile task completed successfully")
                task.setTaskCompletedWithSuccess(true)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Reconcile task failed: ${e.message}")
                task.setTaskCompletedWithSuccess(false)
            }
        }
        currentReconcileJob = job

        // If the system needs to terminate us early, cancel the coroutine
        task.expirationHandler = {
            AppLogger.w(TAG, "Reconcile task expired, cancelling")
            job.cancel()
            task.setTaskCompletedWithSuccess(false)
        }
    }

    /**
     * Handles the cleanup background task when the system launches it.
     *
     * 1. Deletes history records older than 90 days
     * 2. Runs reconciliation to ensure notification schedule is current
     */
    private fun handleCleanupTask(task: BGTask) {
        AppLogger.i(TAG, "Cleanup task started")

        // Schedule the next occurrence immediately
        scheduleCleanupTask()

        val job = scope.launch {
            try {
                // Delete history records older than 90 days
                val cutoff = currentTimeMillis() - HISTORY_RETENTION_MS
                val deletedCount = historyDao.deleteOlderThan(cutoff)
                if (deletedCount > 0) {
                    AppLogger.i(TAG, "Cleanup: deleted $deletedCount old history records")
                }

                // Re-reconcile to keep notification schedule fresh
                reconciler.reconcile()

                AppLogger.i(TAG, "Cleanup task completed successfully")
                task.setTaskCompletedWithSuccess(true)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Cleanup task failed: ${e.message}")
                task.setTaskCompletedWithSuccess(false)
            }
        }
        currentCleanupJob = job

        task.expirationHandler = {
            AppLogger.w(TAG, "Cleanup task expired, cancelling")
            job.cancel()
            task.setTaskCompletedWithSuccess(false)
        }
    }

    companion object {
        private const val TAG = "IOSBackgroundTaskMgr"

        /** BGAppRefreshTask identifier for periodic reconciliation. */
        const val RECONCILE_TASK_ID = "me.juliana.hellomeds.reconcile"

        /** BGProcessingTask identifier for daily cleanup. */
        const val CLEANUP_TASK_ID = "me.juliana.hellomeds.cleanup"

        /** Reconcile interval: 4 hours in seconds. */
        private const val RECONCILE_INTERVAL_SECONDS = 4.0 * 60 * 60

        /** Cleanup interval: 24 hours in seconds. */
        private const val CLEANUP_INTERVAL_SECONDS = 24.0 * 60 * 60

        /** History retention period: 90 days in milliseconds. */
        private const val HISTORY_RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    }
}
