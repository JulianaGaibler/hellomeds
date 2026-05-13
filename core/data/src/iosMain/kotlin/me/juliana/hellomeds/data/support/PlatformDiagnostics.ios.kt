// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.support

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT
import platform.UIKit.UIDevice
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

class IOSDiagnosticsProvider : PlatformDiagnosticsProvider {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getDiagnostics(): PlatformDiagnosticInfo {
        val tz = NSTimeZone.localTimeZone
        val offsetSeconds = tz.secondsFromGMT.toInt()
        val offsetHours = offsetSeconds / 3600
        val offsetMinutes = kotlin.math.abs(offsetSeconds % 3600) / 60
        val sign = if (offsetSeconds >= 0) "+" else "-"
        val absHours = kotlin.math.abs(offsetHours).toString().padStart(2, '0')
        val mins = offsetMinutes.toString().padStart(2, '0')
        val offsetStr = "$sign$absHours:$mins"

        val device = UIDevice.currentDevice
        val info = NSBundle.mainBundle.infoDictionary ?: emptyMap<Any?, Any?>()
        val version = info["CFBundleShortVersionString"] as? String ?: "?"
        val build = info["CFBundleVersion"] as? String ?: "?"

        // Query UNUserNotificationCenter directly (no callback bridge needed)
        val center = UNUserNotificationCenter.currentNotificationCenter()

        val authStatus = suspendCancellableCoroutine { cont ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                val statusName = when (settings?.authorizationStatus) {
                    UNAuthorizationStatusAuthorized -> "Authorized"
                    UNAuthorizationStatusDenied -> "Denied"
                    UNAuthorizationStatusNotDetermined -> "NotDetermined"
                    UNAuthorizationStatusProvisional -> "Provisional"
                    else -> "Unknown"
                }
                cont.resume(statusName)
            }
        }

        val pendingCount = suspendCancellableCoroutine { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                @Suppress("UNCHECKED_CAST")
                cont.resume((requests as? List<UNNotificationRequest>)?.size ?: 0)
            }
        }

        val deliveredCount = suspendCancellableCoroutine { cont ->
            center.getDeliveredNotificationsWithCompletionHandler { delivered ->
                cont.resume((delivered as? List<*>)?.size ?: 0)
            }
        }

        return PlatformDiagnosticInfo(
            deviceModel = "${device.model} (${device.systemName})",
            osVersion = "${device.systemName} ${device.systemVersion}",
            locale = NSLocale.currentLocale.languageCode,
            timezone = "${tz.name} (UTC$offsetStr)",
            appVersion = "$version ($build)",
            notificationsGranted = authStatus == "Authorized" || authStatus == "Provisional",
            notificationAuthorizationStatus = authStatus,
            pendingNotificationCount = pendingCount,
            deliveredNotificationCount = deliveredCount,
        )
    }
}
