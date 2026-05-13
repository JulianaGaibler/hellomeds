// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.steps

import androidx.compose.runtime.Composable
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.back_hand_48px
import me.juliana.hellomeds.shared.onboarding_quickstart_step1_body
import me.juliana.hellomeds.shared.onboarding_quickstart_step1_title
import me.juliana.hellomeds.shared.onboarding_quickstart_step2_body
import me.juliana.hellomeds.shared.onboarding_quickstart_step2_title
import me.juliana.hellomeds.shared.onboarding_quickstart_step3_body
import me.juliana.hellomeds.shared.onboarding_quickstart_step3_title
import me.juliana.hellomeds.shared.onboarding_quickstart_title
import me.juliana.hellomeds.ui.features.onboarding.components.StepsOnboardingScreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 2: Quick Start Guide
 *
 * Provides a 3-step overview of how to use the app:
 * 1. Add medication
 * 2. Set label
 * 3. Add schedule
 */
@Composable
fun QuickStartScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    val steps = listOf(
        stringResource(Res.string.onboarding_quickstart_step1_title) to
            stringResource(Res.string.onboarding_quickstart_step1_body),
        stringResource(Res.string.onboarding_quickstart_step2_title) to
            stringResource(Res.string.onboarding_quickstart_step2_body),
        stringResource(Res.string.onboarding_quickstart_step3_title) to
            stringResource(Res.string.onboarding_quickstart_step3_body),
    )

    StepsOnboardingScreen(
        icon = painterResource(Res.drawable.back_hand_48px),
        title = stringResource(Res.string.onboarding_quickstart_title),
        steps = steps,
        onContinueClick = onContinue,
        onBackClick = onBack,
    )
}
