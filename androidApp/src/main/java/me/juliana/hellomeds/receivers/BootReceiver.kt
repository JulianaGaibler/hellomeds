// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.notifications.AlarmReconciler
import me.juliana.hellomeds.workers.NotificationSchedulerWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Receiver that reschedules alarms after device reboot.
 *
 * Calls reconciler.processMissedDoses() then reconciler.reconcile() immediately via
 * goAsync() to ensure (a) any dose whose alarm fired during the reboot window gets a
 * notification, and (b) the next alarm is restored milliseconds after boot
 * (WorkManager may delay 10+ minutes). Also enqueues the safety-net worker for
 * auto-skip and session cleanup.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Check if device has been unlocked (Direct Boot guard)
        // Database encryption key is inaccessible before first unlock
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) {
            AppLogger.w(TAG, "Device not yet unlocked after reboot, deferring reconciliation to worker")
            return
        }

        AppLogger.d(TAG, "Device rebooted, rescheduling notifications")

        // Immediate reconciliation via goAsync()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reconciler: AlarmReconciler = get()
                // Catch-up FIRST: surface any dose that should have fired during the
                // reboot/locked window. findNextPendingEvent() in reconcile() is
                // forward-only and would otherwise skip these silently.
                reconciler.processMissedDoses()
                reconciler.reconcile()
                AppLogger.i(TAG, "Immediate post-boot reconciliation complete")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in immediate post-boot reconciliation", e)
            } finally {
                pendingResult.finish()
            }
        }

        // Also enqueue safety-net worker for auto-skip + session cleanup
        val workRequest = OneTimeWorkRequestBuilder<NotificationSchedulerWorker>()
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "NotificationSchedulerWorker",
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
