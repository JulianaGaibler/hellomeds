// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.notifications.AlarmReconciler
import me.juliana.hellomeds.notifications.NotificationSessionManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Daily cleanup worker that runs at 3 AM.
 * Cleans stale notification sessions and ensures alarm is set.
 */
class CleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        return try {
            AppLogger.i(TAG, "Starting cleanup")

            val sessionManager: NotificationSessionManager = get()
            val reconciler: AlarmReconciler = get()

            // Clean up stale sessions
            sessionManager.clearStaleSessions()

            // Ensure alarm is set
            reconciler.reconcile()

            AppLogger.i(TAG, "Cleanup complete")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error in cleanup", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "Cleanup"
    }
}
