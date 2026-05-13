// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import me.juliana.hellomeds.notifications.requestAlarmKitAuthorization
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.alarm_48px
import me.juliana.hellomeds.shared.onboarding_alarmkit_description
import me.juliana.hellomeds.shared.onboarding_alarmkit_footer
import me.juliana.hellomeds.shared.onboarding_alarmkit_skip
import me.juliana.hellomeds.shared.onboarding_alarmkit_title
import me.juliana.hellomeds.shared.onboarding_grant_permission
import me.juliana.hellomeds.ui.features.onboarding.components.PermissionOnboardingScreen
import me.juliana.hellomeds.ui.util.PermissionUtils
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/**
 * iOS-specific AlarmKit permission screen for the onboarding flow (iOS 26+).
 *
 * Requests AlarmKit authorization so the app can schedule system-level alarms
 * that wake the screen and bypass Focus mode for important medications.
 * Freely skippable — falls back to time-sensitive notifications.
 */
@Composable
fun IOSAlarmKitPermissionScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var isGranted by remember { mutableStateOf(PlatformCapabilities.alarmKitAuthorized) }
    var hasAttempted by remember { mutableStateOf(false) }

    // Re-check when app becomes active (user may return from iOS Settings)
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            isGranted = PlatformCapabilities.alarmKitAuthorized
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }

    PermissionOnboardingScreen(
        icon = painterResource(Res.drawable.alarm_48px),
        title = stringResource(Res.string.onboarding_alarmkit_title),
        description = stringResource(Res.string.onboarding_alarmkit_description),
        primaryButtonText = stringResource(Res.string.onboarding_grant_permission),
        isPrimaryActionCompleted = isGranted,
        onPrimaryButtonClick = {
            if (!isGranted) {
                if (hasAttempted) {
                    // Already denied once — iOS won't show the dialog again.
                    // Open Settings where the user can enable it manually.
                    PermissionUtils.openNotificationSettings(Unit)
                } else {
                    hasAttempted = true
                    scope.launch {
                        val granted = requestAlarmKitAuthorization()
                        PlatformCapabilities.alarmKitAuthorized = granted
                        isGranted = granted
                    }
                }
            }
        },
        secondaryButtonText = stringResource(Res.string.onboarding_alarmkit_skip),
        onSecondaryButtonClick = { onContinue() },
        footerInfo = stringResource(Res.string.onboarding_alarmkit_footer),
        onBackClick = onBack,
        onContinueClick = onContinue,
        isContinueEnabled = true, // Freely skippable
    )
}
