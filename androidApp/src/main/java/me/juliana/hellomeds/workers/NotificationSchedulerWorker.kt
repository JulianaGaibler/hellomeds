// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.repository.MedicationHistoryRepository
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.notifications.AlarmReconciler
import me.juliana.hellomeds.notifications.NotificationSessionManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Safety-net worker that runs every 4 hours.
 * Ensures the single reconciler alarm stays set even if chaining breaks.
 *
 * Responsibilities:
 * 1. Auto-skip missed events
 * 2. Clear stale notification sessions
 * 3. Call reconciler.reconcile() to ensure alarm is set
 */
class NotificationSchedulerWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        val prefs: NotificationPreferences = get()

        if (!prefs.notificationsEnabled.first()) {
            AppLogger.d(TAG, "Notifications disabled, skipping reconciliation")
            return Result.success()
        }

        return try {
            AppLogger.i(TAG, "Safety-net reconciliation starting")

            val historyRepo: MedicationHistoryRepository = get()
            val sessionManager: NotificationSessionManager = get()
            val reconciler: AlarmReconciler = get()

            val sessionsBefore = sessionManager.getAllSessions().size

            // Auto-skip missed events
            historyRepo.autoSkipMissedEvents()

            // Clean up stale sessions (>48h old)
            sessionManager.clearStaleSessions()

            val sessionsAfter = sessionManager.getAllSessions().size
            val staleCleared = sessionsBefore - sessionsAfter

            // Ensure alarm is set for next event
            reconciler.reconcile()

            prefs.setLastSchedulingTimestamp(System.currentTimeMillis())
            AppLogger.i(TAG, "Safety-net complete: staleSessions=$staleCleared, remainingSessions=$sessionsAfter")

            Result.success()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error in safety-net reconciliation", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NotificationScheduler"
    }
}
