// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.alarm_48px
import me.juliana.hellomeds.shared.onboarding_fsi_description
import me.juliana.hellomeds.shared.onboarding_fsi_enable
import me.juliana.hellomeds.shared.onboarding_fsi_skip
import me.juliana.hellomeds.shared.onboarding_fsi_title
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.features.onboarding.components.PermissionOnboardingScreen
import me.juliana.hellomeds.ui.util.PermissionUtils
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Onboarding screen for Full Screen Intent permission (Android 14+).
 *
 * Explains that FSI allows the screen to wake for medication reminders.
 * Skippable — FSI degrades gracefully to standard heads-up notifications.
 */
@Composable
fun FullScreenIntentPermissionScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    val context = platformContext()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isGranted by remember {
        mutableStateOf(PermissionUtils.canUseFullScreenIntent(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGranted = PermissionUtils.canUseFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PermissionOnboardingScreen(
        icon = painterResource(Res.drawable.alarm_48px),
        title = stringResource(Res.string.onboarding_fsi_title),
        description = stringResource(Res.string.onboarding_fsi_description),
        primaryButtonText = stringResource(Res.string.onboarding_fsi_enable),
        onPrimaryButtonClick = { PermissionUtils.openFullScreenIntentSettings(context) },
        isPrimaryActionCompleted = isGranted,
        secondaryButtonText = stringResource(Res.string.onboarding_fsi_skip),
        onSecondaryButtonClick = { onContinue() },
        footerInfo = "",
        onBackClick = onBack,
        onContinueClick = onContinue,
        isContinueEnabled = isGranted,
    )
}
