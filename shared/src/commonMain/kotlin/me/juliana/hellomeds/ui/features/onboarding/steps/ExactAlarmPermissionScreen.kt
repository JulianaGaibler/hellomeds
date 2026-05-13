// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.alarm_48px
import me.juliana.hellomeds.shared.onboarding_exact_alarms_description
import me.juliana.hellomeds.shared.onboarding_exact_alarms_footer
import me.juliana.hellomeds.shared.onboarding_exact_alarms_title
import me.juliana.hellomeds.shared.onboarding_exact_alarms_use_imprecise
import me.juliana.hellomeds.shared.onboarding_grant_permission
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.features.onboarding.components.PermissionOnboardingScreen
import me.juliana.hellomeds.ui.util.PermissionUtils
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Screen 4: Exact Alarms Permission (Precise Timing)
 *
 * Requests exact alarm permission for time-sensitive notifications.
 * Users can either:
 * - Grant permission (opens system settings)
 * - Use imprecise notifications (sets preference to false, reschedules alarms)
 */
@Composable
fun ExactAlarmPermissionScreen(onContinue: () -> Unit, onBack: () -> Unit, onRescheduleAlarms: () -> Unit = {}) {
    val context = platformContext()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val notifPrefs = koinInject<NotificationPreferences>()

    var isGranted by remember {
        mutableStateOf(PermissionUtils.canScheduleExactAlarms(context))
    }

    // Observe lifecycle to update permission state when user returns from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGranted = PermissionUtils.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    PermissionOnboardingScreen(
        icon = painterResource(Res.drawable.alarm_48px),
        title = stringResource(Res.string.onboarding_exact_alarms_title),
        description = stringResource(Res.string.onboarding_exact_alarms_description),
        primaryButtonText = stringResource(Res.string.onboarding_grant_permission),
        onPrimaryButtonClick = {
            PermissionUtils.openExactAlarmSettings(context)
        },
        isPrimaryActionCompleted = isGranted,
        secondaryButtonText = stringResource(Res.string.onboarding_exact_alarms_use_imprecise),
        onSecondaryButtonClick = {
            scope.launch {
                // Disable exact alarms feature
                notifPrefs.setUseExactAlarms(false)
                // Reschedule alarms as inexact (platform-specific)
                onRescheduleAlarms()
            }
            onContinue()
        },
        footerInfo = stringResource(Res.string.onboarding_exact_alarms_footer),
        onBackClick = onBack,
        onContinueClick = onContinue,
        isContinueEnabled = isGranted,
    )
}
