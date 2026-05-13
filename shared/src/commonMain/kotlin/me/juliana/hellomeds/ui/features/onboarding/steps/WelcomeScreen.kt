// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.steps

import androidx.compose.runtime.Composable
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.onboarding_get_started
import me.juliana.hellomeds.shared.onboarding_welcome_title
import me.juliana.hellomeds.ui.features.onboarding.components.SplashOnboardingScreen
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 1: Welcome/Splash Screen
 *
 * First onboarding screen that welcomes the user with the app name
 * and a "Get Started" button to begin the onboarding flow.
 */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    SplashOnboardingScreen(
        title = stringResource(Res.string.onboarding_welcome_title),
        primaryButtonText = stringResource(Res.string.onboarding_get_started),
        onPrimaryButtonClick = onContinue,
    )
}
