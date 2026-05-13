// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.notifications_active_48px
import me.juliana.hellomeds.shared.onboarding_grant_permission
import me.juliana.hellomeds.shared.onboarding_notifications_description
import me.juliana.hellomeds.shared.onboarding_notifications_disable
import me.juliana.hellomeds.shared.onboarding_notifications_footer
import me.juliana.hellomeds.shared.onboarding_notifications_title
import me.juliana.hellomeds.ui.features.onboarding.components.PermissionOnboardingScreen
import me.juliana.hellomeds.ui.util.PermissionUtils
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS-specific notification permission screen for the onboarding flow.
 *
 * Uses UNUserNotificationCenter to request notification authorization.
 * Re-checks permission status when the app becomes active (e.g., user returns
 * from Settings).
 */
@Composable
fun IOSNotificationPermissionScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val notifPrefs = koinInject<NotificationPreferences>()

    var isGranted by remember { mutableStateOf(false) }
    var resumeCounter by remember { mutableStateOf(0) }

    // Query permission status inside a coroutine — result delivered on main thread.
    // Re-runs on initial composition and whenever resumeCounter changes (app resume).
    LaunchedEffect(resumeCounter) {
        val enabled = suspendCancellableCoroutine { cont ->
            UNUserNotificationCenter.currentNotificationCenter()
                .getNotificationSettingsWithCompletionHandler { settings ->
                    cont.resume(settings?.authorizationStatus == UNAuthorizationStatusAuthorized)
                }
        }
        PermissionUtils.cachedNotificationsEnabled = enabled
        isGranted = enabled
    }

    // Re-check when app becomes active (user may return from iOS Settings)
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            resumeCounter++
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }

    PermissionOnboardingScreen(
        icon = painterResource(Res.drawable.notifications_active_48px),
        title = stringResource(Res.string.onboarding_notifications_title),
        description = stringResource(Res.string.onboarding_notifications_description),
        primaryButtonText = stringResource(Res.string.onboarding_grant_permission),
        isPrimaryActionCompleted = isGranted,
        onPrimaryButtonClick = {
            if (!isGranted) {
                val baseOptions = UNAuthorizationOptionAlert or
                    UNAuthorizationOptionSound or
                    UNAuthorizationOptionBadge
                val timeSensitiveOption: ULong = 1UL shl 4
                val options = baseOptions or timeSensitiveOption

                UNUserNotificationCenter.currentNotificationCenter()
                    .requestAuthorizationWithOptions(options) { granted, _ ->
                        PermissionUtils.cachedNotificationsEnabled = granted
                        // Trigger re-check via the LaunchedEffect (safer than direct state set from ObjC callback)
                        resumeCounter++
                    }
            }
        },
        secondaryButtonText = stringResource(Res.string.onboarding_notifications_disable),
        onSecondaryButtonClick = {
            scope.launch {
                notifPrefs.setNotificationsEnabled(false)
            }
            onContinue()
        },
        footerInfo = stringResource(Res.string.onboarding_notifications_footer),
        onBackClick = onBack,
        onContinueClick = onContinue,
        isContinueEnabled = isGranted,
    )
}
