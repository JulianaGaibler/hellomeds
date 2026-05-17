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
import org.koin.mp.KoinPlatform
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateByAddingTimeInterval
import kotlin.time.Clock

/**
 * Schedules and handles iOS background tasks via BGTaskScheduler: a reconcile task
 * (BGAppRefreshTask, ~4h) and a history-cleanup task (BGProcessingTask, ~24h).
 *
 * Task identifiers must be listed in Info.plist under BGTaskSchedulerPermittedIdentifiers,
 * and [registerTasks] must run before the app finishes launching.
 */
class IOSBackgroundTaskManager(
    private val clock: Clock = Clock.System,
) {

    // Lazy: handlers fire post-first-unlock, so deferred DI avoids a Keychain crash
    // if eager resolution ran during a pre-unlock launch.
    private val reconciler: ScheduleReconciler by lazy { KoinPlatform.getKoin().get() }
    private val historyRepository: MedicationHistoryRepository by lazy { KoinPlatform.getKoin().get() }
    private val historyDao: MedicationHistoryDao by lazy { KoinPlatform.getKoin().get() }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentReconcileJob: Job? = null
    private var currentCleanupJob: Job? = null

    /** Must be called exactly once, before the app finishes launching — duplicate registration kills the app. */
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

    /** Safe to call multiple times — a new request for an already-queued identifier replaces the previous one. */
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

    private fun handleReconcileTask(task: BGTask) {
        AppLogger.i(TAG, "Reconcile task started")

        // Queue the next occurrence first so it survives a failure here.
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

        task.expirationHandler = {
            AppLogger.w(TAG, "Reconcile task expired, cancelling")
            job.cancel()
            task.setTaskCompletedWithSuccess(false)
        }
    }

    private fun handleCleanupTask(task: BGTask) {
        AppLogger.i(TAG, "Cleanup task started")

        scheduleCleanupTask()

        val job = scope.launch {
            try {
                val cutoff = clock.now().toEpochMilliseconds() - HISTORY_RETENTION_MS
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
