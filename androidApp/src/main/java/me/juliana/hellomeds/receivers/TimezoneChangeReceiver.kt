// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import me.juliana.hellomeds.data.util.AppLogger
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.juliana.hellomeds.notifications.AlarmReconciler
import me.juliana.hellomeds.workers.NotificationSchedulerWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Receiver that reschedules alarms after timezone or manual clock changes.
 *
 * When the device timezone or clock changes, all scheduled alarm times may shift
 * relative to wall-clock time. This receiver triggers immediate alarm reconciliation
 * via goAsync() + coroutine, plus a safety-net WorkManager task for auto-skip
 * and session cleanup.
 */
class TimezoneChangeReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED &&
            intent.action != Intent.ACTION_TIME_CHANGED
        ) {
            return
        }

        Log.d(TAG, "Time/timezone changed (${intent.action}), rescheduling notifications")

        // Immediate reconciliation via goAsync()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reconciler: AlarmReconciler = get()
                reconciler.reconcile()
                Log.i(TAG, "Immediate post-time-change reconciliation complete")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in post-time-change reconciliation", e)
            } finally {
                pendingResult.finish()
            }
        }

        // Safety-net worker for auto-skip + session cleanup
        val workRequest = OneTimeWorkRequestBuilder<NotificationSchedulerWorker>()
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "NotificationSchedulerWorker-TimeChange",
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
    }

    companion object {
        private const val TAG = "TimezoneChangeReceiver"
    }
}
