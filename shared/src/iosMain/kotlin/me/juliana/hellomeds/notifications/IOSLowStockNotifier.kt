// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.interfaces.LowStockChecker
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.util.StockThresholdCalculator
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.low_stock_notification_text
import me.juliana.hellomeds.shared.low_stock_notification_title
import me.juliana.hellomeds.shared.low_stock_notification_title_discreet
import me.juliana.hellomeds.ui.util.PermissionUtils
import org.jetbrains.compose.resources.getString
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS counterpart of Android's `LowStockNotifier`. Fires a UNNotification when
 * a medication's effective stock crosses below its configured low-stock
 * threshold, and cancels the notification when stock recovers above the
 * threshold or the threshold is removed.
 *
 * Mirrors the Android guard sequence exactly so behavior is platform-parity.
 */
class IOSLowStockNotifier(
    private val medicationDao: MedicationDao,
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val notificationPrefs: NotificationPreferences,
) : LowStockChecker {

    override suspend fun checkAndNotify(medicationId: Int) = withContext(Dispatchers.Default) {
        try {
            if (!PermissionUtils.cachedNotificationsEnabled) {
                AppLogger.w(TAG, "Notifications not authorized — skipping low stock check")
                return@withContext
            }

            val medication = medicationDao.getByIdSync(medicationId) ?: return@withContext
            if (!medication.stockTrackingEnabled) return@withContext

            val threshold = medication.lowStockThreshold
            val alertSent = medication.lowStockAlertSent
            val identifier = "$LOW_STOCK_NOTIFICATION_ID_PREFIX$medicationId"

            // Threshold removed while alert was previously sent → clean up.
            if (threshold == null && alertSent) {
                medicationDao.updateLowStockAlertSent(medicationId, false)
                cancelNotification(identifier)
                return@withContext
            }

            if (threshold == null) return@withContext

            val effectiveStock =
                StockThresholdCalculator.calculateEffectiveStock(medication, stockAdjustmentDao)

            if (effectiveStock <= threshold && !alertSent) {
                val visibility = notificationPrefs.lockScreenVisibility.first()
                val discreet = visibility != LockScreenVisibility.SHOW_WITH_NAMES
                postLowStockNotification(medication, identifier, discreet)
                medicationDao.updateLowStockAlertSent(medicationId, true)
                AppLogger.i(
                    TAG,
                    "Low stock notification fired: medicationId=$medicationId, " +
                        "stock=$effectiveStock, threshold=$threshold",
                )
            } else if (effectiveStock > threshold && alertSent) {
                medicationDao.updateLowStockAlertSent(medicationId, false)
                cancelNotification(identifier)
                AppLogger.i(
                    TAG,
                    "Low stock recovered: medicationId=$medicationId, " +
                        "stock=$effectiveStock, threshold=$threshold",
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Low stock check failed for medicationId=$medicationId", e)
        }
    }

    private suspend fun postLowStockNotification(medication: Medication, identifier: String, discreet: Boolean) {
        val name = medication.displayName ?: medication.name
        val title = if (discreet) {
            getString(Res.string.low_stock_notification_title_discreet)
        } else {
            getString(Res.string.low_stock_notification_title, name)
        }
        val body = getString(Res.string.low_stock_notification_text)

        val content = UNMutableNotificationContent()
        content.setTitle(title)
        content.setBody(body)
        content.setThreadIdentifier(LOW_STOCK_THREAD_IDENTIFIER)
        // Encode medicationId as a String to avoid NSNumber/Long bridging ambiguity
        // when the delegate reads userInfo back (see IOSNotificationDelegate).
        content.setUserInfo(
            mapOf<Any?, Any?>(
                "type" to NOTIFICATION_TYPE_LOW_STOCK,
                "medicationId" to medication.id.toString(),
            ),
        )

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null, // deliver immediately
        )

        val center = UNUserNotificationCenter.currentNotificationCenter()
        suspendCancellableCoroutine { cont ->
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    AppLogger.e(
                        TAG,
                        "Failed to post low stock notification: ${error.localizedDescription}",
                    )
                }
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun cancelNotification(identifier: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val ids = listOf(identifier)
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    private companion object {
        const val TAG = "IOSLowStockNotifier"
    }
}
