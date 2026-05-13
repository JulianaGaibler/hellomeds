// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import me.juliana.hellomeds.data.util.AppLogger
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter

private const val TAG = "NotificationPermChecker"

/**
 * Fires the async UNUserNotificationCenter query and updates
 * [PermissionUtils.cachedNotificationsEnabled]. Does NOT update Compose state
 * directly — the caller polls the cache after a short delay.
 */
private fun refreshCachedStatus() {
    UNUserNotificationCenter.currentNotificationCenter()
        .getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            val enabled = status == UNAuthorizationStatusAuthorized ||
                status == UNAuthorizationStatusProvisional
            AppLogger.d(TAG, "Authorization status raw=$status → enabled=$enabled")
            PermissionUtils.cachedNotificationsEnabled = enabled
        }
}

@Composable
actual fun isNotificationPermissionGranted(): Boolean {
    // Read the @Volatile cached value. The LaunchedEffect below triggers
    // re-reads by bumping checkCounter after the async query completes.
    var checkCounter by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(PermissionUtils.cachedNotificationsEnabled) }

    // Fire async query, wait for it to update the cache, then read it.
    // This avoids K/N ObjC block → Compose MutableState mutation issues.
    LaunchedEffect(checkCounter) {
        refreshCachedStatus()
        // The callback updates cachedNotificationsEnabled asynchronously.
        // A short delay ensures we read after the callback has run.
        delay(200)
        granted = PermissionUtils.cachedNotificationsEnabled
    }

    // Bump counter on app resume to re-query
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            checkCounter++
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }

    return granted
}
