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
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.interfaces.LowStockChecker
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.util.StockThresholdCalculator

/**
 * Checks whether a medication's stock has crossed below the user-defined
 * low stock threshold and fires a one-time notification when it does.
 *
 * The notification is dismissed and the flag reset when stock recovers
 * above the threshold or when the threshold is removed.
 */
class LowStockNotifier(
    private val context: Context,
    private val medicationDao: MedicationDao,
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val notificationBuilder: NotificationBuilder,
    private val notificationPrefs: NotificationPreferences,
) : LowStockChecker {

    /**
     * Check stock level for [medicationId] and fire or dismiss a low stock
     * notification as appropriate.
     *
     * Safe to call from any coroutine context — switches to [Dispatchers.IO] internally.
     * Failures are logged but never propagated (callers wrap in try/catch anyway).
     */
    override suspend fun checkAndNotify(medicationId: Int) = withContext(Dispatchers.IO) {
        try {
            // Permission gate (Android 13+): don't set the flag if user can't see the notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    AppLogger.w(TAG, "POST_NOTIFICATIONS not granted, skipping low stock check")
                    return@withContext
                }
            }

            val medication = medicationDao.getByIdSync(medicationId) ?: return@withContext
            if (!medication.stockTrackingEnabled) return@withContext

            val threshold = medication.lowStockThreshold
            val alertSent = medication.lowStockAlertSent
            val notificationId = NotificationIdGenerator.generateLowStockNotificationId(medicationId)
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            // Threshold removed while alert was previously sent → clean up
            if (threshold == null && alertSent) {
                medicationDao.updateLowStockAlertSent(medicationId, false)
                notificationManager.cancel(notificationId)
                return@withContext
            }

            if (threshold == null) return@withContext

            val effectiveStock =
                StockThresholdCalculator.calculateEffectiveStock(medication, stockAdjustmentDao)

            if (effectiveStock <= threshold && !alertSent) {
                // Stock crossed below threshold → fire notification
                val visibility = notificationPrefs.lockScreenVisibility.first()
                val discreet = visibility != LockScreenVisibility.SHOW_WITH_NAMES
                val notification =
                    notificationBuilder.buildLowStockNotification(medication, notificationId, discreet, visibility)
                notificationManager.notify(notificationId, notification)
                medicationDao.updateLowStockAlertSent(medicationId, true)
                Log.i(
                    TAG,
                    "Low stock notification fired: medicationId=$medicationId, stock=$effectiveStock, threshold=$threshold",
                )
            } else if (effectiveStock > threshold && alertSent) {
                // Stock recovered above threshold → reset flag + dismiss
                medicationDao.updateLowStockAlertSent(medicationId, false)
                notificationManager.cancel(notificationId)
                Log.i(
                    TAG,
                    "Low stock recovered: medicationId=$medicationId, stock=$effectiveStock, threshold=$threshold",
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Low stock check failed for medicationId=$medicationId", e)
        }
    }

    companion object {
        private const val TAG = "LowStockNotifier"
    }
}
