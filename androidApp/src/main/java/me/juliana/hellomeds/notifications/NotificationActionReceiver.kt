// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import me.juliana.hellomeds.data.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * BroadcastReceiver that handles notification button actions.
 * Actions: TAKEN, SKIPPED, SNOOZED
 *
 * Uses goAsync() + coroutine for immediate action processing (no WorkManager delay).
 * Instantly cancels the notification for ALL actions, then processes the business logic.
 * For COMBINED sessions with remaining meds, the handler re-posts an updated notification.
 * Mutex deduplication in the handler prevents duplicate actions from rapid taps.
 */
class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("action") ?: run {
            AppLogger.e(TAG, "No action in intent")
            return
        }
        val notificationId = intent.getIntExtra("notificationId", -1)

        // INSTANT UI FEEDBACK: cancel notification for ALL actions
        if (notificationId != -1) {
            val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifMgr.cancel(notificationId)
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val handler: NotificationActionHandler = get()

                if (action == "CONTAINER_DEPLETED") {
                    // Depletion reminder action — uses medicationId instead of scheduleIds
                    val medicationId = intent.getIntExtra("medicationId", -1)
                    if (medicationId == -1) {
                        AppLogger.e(TAG, "No medicationId in CONTAINER_DEPLETED intent")
                        return@launch
                    }
                    Log.d(
                        TAG,
                        "Action: $action, medicationId: $medicationId, notificationId: $notificationId",
                    )
                    handler.processDepletionAction(medicationId)
                } else {
                    // Standard medication actions — TAKEN, SKIPPED, SNOOZED
                    val scheduleIds = intent.getIntArrayExtra("scheduleIds")?.toList() ?: run {
                        AppLogger.e(TAG, "No schedule IDs in intent")
                        return@launch
                    }
                    val scheduledTime = intent.getLongExtra("scheduledTime", 0L)
                    if (scheduledTime == 0L) return@launch
                    Log.d(
                        TAG,
                        "Action: $action, schedules: $scheduleIds, scheduledTime: $scheduledTime, notificationId: $notificationId",
                    )
                    handler.processAction(context, action, scheduleIds, scheduledTime, notificationId)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error processing action $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotificationAction"
    }
}
