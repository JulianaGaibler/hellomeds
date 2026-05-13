// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.util.AppLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * BroadcastReceiver that listens for SCHEDULE_EXACT_ALARM permission changes.
 *
 * On GRANT: trigger reconcile() so the next alarm "upgrades" from
 * setAndAllowWhileIdle (inexact) back to setAlarmClock (exact). Reconcile's
 * setAlarm() also clears the `exactAlarmsDisabled` flag.
 *
 * On REVOKE: trigger reconcile() so the next alarm gets re-scheduled via the
 * inexact fallback AND the `exactAlarmsDisabled` flag flips to true, lighting
 * up the recovery banner immediately. Without this, banners would only appear
 * after the next state mutation calls reconcile() through some other path —
 * which could be hours later.
 */
class AlarmPermissionReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val granted = alarmManager.canScheduleExactAlarms()
        Log.i(TAG, "Exact alarm permission state changed → granted=$granted, triggering reconcile")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reconciler: AlarmReconciler = get()
                reconciler.reconcile()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error reconciling after permission state change", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmPermissionRcvr"
    }
}
