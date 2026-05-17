// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.interfaces.DepletionChecker
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.depletion_notification_text
import me.juliana.hellomeds.shared.depletion_notification_title
import me.juliana.hellomeds.shared.depletion_notification_title_discreet
import me.juliana.hellomeds.ui.util.PermissionUtils
import org.jetbrains.compose.resources.getString
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS counterpart of Android's `DepletionReminderNotifier`. Fires when a
 * medication's cumulative TAKEN-dose count since the most recent container
 * cycle anchor exceeds the configured `packagingQuantity * 1.1`. Notification
 * carries a "Mark Depleted" action button (see [IOSNotificationDelegate]).
 *
 * Mirrors the Android guard sequence exactly.
 */
class IOSDepletionReminderNotifier(
    private val medicationDao: MedicationDao,
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val notificationPrefs: NotificationPreferences,
) : DepletionChecker {

    override suspend fun checkAndNotify(medicationId: Int) = withContext(Dispatchers.Default) {
        try {
            if (!PermissionUtils.cachedNotificationsEnabled) {
                AppLogger.w(TAG, "Notifications not authorized — skipping depletion check")
                return@withContext
            }

            val medication = medicationDao.getByIdSync(medicationId) ?: return@withContext
            if (!medication.stockTrackingEnabled) return@withContext
            if (medication.trackingPrecision != TrackingPrecision.ESTIMATED) return@withContext

            val alertSent = medication.depletionAlertSent
            val identifier = "$DEPLETION_NOTIFICATION_ID_PREFIX$medicationId"

            // Feature disabled while alert was previously sent → clean up.
            if (!medication.depletionReminderEnabled && alertSent) {
                medicationDao.updateDepletionAlertSent(medicationId, false)
                cancelNotification(identifier)
                return@withContext
            }
            if (!medication.depletionReminderEnabled) return@withContext

            val packagingQuantity = medication.packagingQuantity ?: return@withContext

            // Most recent container-cycle anchor (any of INITIAL_STOCK / REFILL / CONTAINER_DEPLETED).
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

            val dosesSince = medicationHistoryDao.countTakenSince(medicationId, referenceTimestamp)
            val threshold = (packagingQuantity * 1.1).toInt()

            if (dosesSince > threshold && !alertSent) {
                val visibility = notificationPrefs.lockScreenVisibility.first()
                val discreet = visibility != LockScreenVisibility.SHOW_WITH_NAMES
                postDepletionNotification(medication, identifier, dosesSince, discreet)
                medicationDao.updateDepletionAlertSent(medicationId, true)
                AppLogger.i(
                    TAG,
                    "Depletion reminder fired: medicationId=$medicationId, " +
                        "doses=$dosesSince, threshold=$threshold",
                )
            } else if (dosesSince <= threshold && alertSent) {
                medicationDao.updateDepletionAlertSent(medicationId, false)
                cancelNotification(identifier)
                AppLogger.i(
                    TAG,
                    "Depletion reminder reset: medicationId=$medicationId, " +
                        "doses=$dosesSince, threshold=$threshold",
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Depletion check failed for medicationId=$medicationId", e)
        }
    }

    private suspend fun postDepletionNotification(
        medication: Medication,
        identifier: String,
        dosesSinceDepletion: Int,
        discreet: Boolean,
    ) {
        val name = medication.displayName ?: medication.name
        val title = if (discreet) {
            getString(Res.string.depletion_notification_title_discreet)
        } else {
            getString(Res.string.depletion_notification_title, name)
        }
        val body = getString(Res.string.depletion_notification_text, dosesSinceDepletion)

        val content = UNMutableNotificationContent()
        content.setTitle(title)
        content.setBody(body)
        content.setCategoryIdentifier(NOTIFICATION_CATEGORY_DEPLETION_REMINDER)
        content.setThreadIdentifier(DEPLETION_THREAD_IDENTIFIER)
        content.setUserInfo(
            mapOf<Any?, Any?>(
                "type" to NOTIFICATION_TYPE_DEPLETION_REMINDER,
                "medicationId" to medication.id.toString(),
            ),
        )

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null,
        )

        val center = UNUserNotificationCenter.currentNotificationCenter()
        suspendCancellableCoroutine { cont ->
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    AppLogger.e(
                        TAG,
                        "Failed to post depletion notification: ${error.localizedDescription}",
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
        const val TAG = "IOSDepletionNotif"
    }
}
