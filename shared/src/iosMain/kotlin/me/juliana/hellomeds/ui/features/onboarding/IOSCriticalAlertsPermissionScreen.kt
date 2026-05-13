// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.suspendCancellableCoroutine
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.notification_important_48px
import me.juliana.hellomeds.shared.onboarding_critical_alerts_description
import me.juliana.hellomeds.shared.onboarding_critical_alerts_footer
import me.juliana.hellomeds.shared.onboarding_critical_alerts_skip
import me.juliana.hellomeds.shared.onboarding_critical_alerts_title
import me.juliana.hellomeds.shared.onboarding_grant_permission
import me.juliana.hellomeds.ui.features.onboarding.components.PermissionOnboardingScreen
import me.juliana.hellomeds.ui.util.PermissionUtils
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNNotificationSettingEnabled
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS-specific critical alerts permission screen for the onboarding flow.
 *
 * Requests UNAuthorizationOptionCriticalAlert so the app can send alerts
 * that bypass silent mode and Do Not Disturb for critical medications.
 * Requires the com.apple.developer.usernotifications.critical-alerts entitlement.
 * Freely skippable — falls back to time-sensitive notifications.
 */
@Composable
fun IOSCriticalAlertsPermissionScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    var isGranted by remember { mutableStateOf(PlatformCapabilities.criticalAlertsAuthorized) }
    var hasAttempted by remember { mutableStateOf(false) }
    var resumeCounter by remember { mutableStateOf(0) }

    // Query critical alert status — re-runs on composition and on app resume.
    // If already granted (e.g. iOS bundled it with the notification dialog),
    // auto-advance to the next onboarding screen.
    LaunchedEffect(resumeCounter) {
        val authorized = suspendCancellableCoroutine { cont ->
            UNUserNotificationCenter.currentNotificationCenter()
                .getNotificationSettingsWithCompletionHandler { settings ->
                    cont.resume(settings?.criticalAlertSetting == UNNotificationSettingEnabled)
                }
        }
        PlatformCapabilities.criticalAlertsAuthorized = authorized
        isGranted = authorized
        if (authorized && resumeCounter == 0) {
            // Already granted before we even showed this screen — skip it
            onContinue()
        }
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
        icon = painterResource(Res.drawable.notification_important_48px),
        title = stringResource(Res.string.onboarding_critical_alerts_title),
        description = stringResource(Res.string.onboarding_critical_alerts_description),
        primaryButtonText = stringResource(Res.string.onboarding_grant_permission),
        isPrimaryActionCompleted = isGranted,
        onPrimaryButtonClick = {
            if (!isGranted) {
                if (hasAttempted) {
                    // Already tried once — iOS won't show the dialog again.
                    // Open Settings where the user can enable critical alerts manually.
                    PermissionUtils.openNotificationSettings(Unit)
                    return@PermissionOnboardingScreen
                }
                hasAttempted = true
                // Request critical alert authorization. iOS shows a system dialog
                // only the first time; subsequent calls return denied immediately.
                val baseOptions = UNAuthorizationOptionAlert or
                    UNAuthorizationOptionSound or
                    UNAuthorizationOptionBadge
                val timeSensitiveOption: ULong = 1UL shl 4
                val criticalAlertOption: ULong = 1UL shl 5
                val options = baseOptions or timeSensitiveOption or criticalAlertOption

                UNUserNotificationCenter.currentNotificationCenter()
                    .requestAuthorizationWithOptions(options) { _, _ ->
                        // Trigger re-check via LaunchedEffect.
                        // Note: on second tap (already denied), iOS returns immediately
                        // without showing a dialog. The re-check detects it's still denied,
                        // and the third tap will find hasAttempted=true → opens Settings.
                        resumeCounter++
                    }
            }
        },
        secondaryButtonText = stringResource(Res.string.onboarding_critical_alerts_skip),
        onSecondaryButtonClick = { onContinue() },
        footerInfo = stringResource(Res.string.onboarding_critical_alerts_footer),
        onBackClick = onBack,
        onContinueClick = onContinue,
        isContinueEnabled = true, // Freely skippable
    )
}
