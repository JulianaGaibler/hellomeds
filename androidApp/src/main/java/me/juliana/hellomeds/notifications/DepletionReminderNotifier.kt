// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import me.juliana.hellomeds.data.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.interfaces.DepletionChecker
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.preferences.NotificationPreferences

/**
 * Checks whether a medication's container usage has exceeded the expected
 * capacity (with a 10% margin) and fires a one-time notification when it does.
 *
 * The notification includes a "Mark Depleted" action button so the user can
 * log the container change directly from the notification.
 *
 * The notification is dismissed and the flag reset when the container cycle
 * resets (via container depletion, refill, or manual correction) or when
 * the depletion reminder is disabled.
 */
class DepletionReminderNotifier(
    private val context: Context,
    private val medicationDao: MedicationDao,
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val notificationBuilder: NotificationBuilder,
    private val notificationPrefs: NotificationPreferences,
) : DepletionChecker {

    /**
     * Check container usage for [medicationId] and fire or dismiss a depletion
     * reminder notification as appropriate.
     *
     * Safe to call from any coroutine context — switches to [Dispatchers.IO] internally.
     * Failures are logged but never propagated (callers wrap in try/catch anyway).
     */
    override suspend fun checkAndNotify(medicationId: Int) = withContext(Dispatchers.IO) {
        try {
            // Permission gate (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    AppLogger.w(TAG, "POST_NOTIFICATIONS not granted, skipping depletion check")
                    return@withContext
                }
            }

            val medication = medicationDao.getByIdSync(medicationId) ?: return@withContext
            if (!medication.stockTrackingEnabled) return@withContext
            if (medication.trackingPrecision != TrackingPrecision.ESTIMATED) return@withContext

            val alertSent = medication.depletionAlertSent
            val notificationId = NotificationIdGenerator.generateDepletionNotificationId(medicationId)
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            // Feature disabled while alert was previously sent → clean up
            if (!medication.depletionReminderEnabled && alertSent) {
                medicationDao.updateDepletionAlertSent(medicationId, false)
                notificationManager.cancel(notificationId)
                return@withContext
            }

            if (!medication.depletionReminderEnabled) return@withContext

            val packagingQuantity = medication.packagingQuantity ?: return@withContext

            // Find the most recent container cycle reference point
            val lastDepletion = stockAdjustmentDao.getLatestByTypeForMedication(
                medicationId,
                StockAdjustmentType.CONTAINER_DEPLETED.value,
            )
            val lastInitial = stockAdjustmentDao.getLatestByTypeForMedication(
                medicationId,
                StockAdjustmentType.INITIAL_STOCK.value,
            )
            val lastRefill = stockAdjustmentDao.getLatestByTypeForMedication(
                medicationId,
                StockAdjustmentType.REFILL.value,
            )
            val referenceTimestamp = maxOf(
                lastDepletion?.timestamp ?: 0L,
                lastInitial?.timestamp ?: 0L,
                lastRefill?.timestamp ?: 0L,
            )
            if (referenceTimestamp == 0L) return@withContext

            // Count TAKEN history records since reference timestamp
            val dosesSince = medicationHistoryDao.countTakenSince(medicationId, referenceTimestamp)
            val threshold = (packagingQuantity * 1.1).toInt()

            if (dosesSince > threshold && !alertSent) {
                // Usage exceeded container capacity → fire notification
                val visibility = notificationPrefs.lockScreenVisibility.first()
                val discreet = visibility != LockScreenVisibility.SHOW_WITH_NAMES
                val notification = notificationBuilder.buildDepletionReminderNotification(
                    medication,
                    dosesSince,
                    notificationId,
                    discreet,
                    visibility,
                )
                notificationManager.notify(notificationId, notification)
                medicationDao.updateDepletionAlertSent(medicationId, true)
                Log.i(
                    TAG,
                    "Depletion reminder fired: medicationId=$medicationId, doses=$dosesSince, threshold=$threshold",
                )
            } else if (dosesSince <= threshold && alertSent) {
                // Container cycle reset (doses dropped below threshold) → reset flag + dismiss
                medicationDao.updateDepletionAlertSent(medicationId, false)
                notificationManager.cancel(notificationId)
                Log.i(
                    TAG,
                    "Depletion reminder reset: medicationId=$medicationId, doses=$dosesSince, threshold=$threshold",
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Depletion check failed for medicationId=$medicationId", e)
        }
    }

    companion object {
        private const val TAG = "DepletionReminderNotif"
    }
}
