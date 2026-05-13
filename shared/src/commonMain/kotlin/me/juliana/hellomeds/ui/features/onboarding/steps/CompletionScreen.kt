// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.onboarding_add_medication
import me.juliana.hellomeds.shared.onboarding_completion_subtitle
import me.juliana.hellomeds.shared.onboarding_completion_title
import me.juliana.hellomeds.shared.onboarding_later
import me.juliana.hellomeds.ui.features.onboarding.components.AddMedicationOptionsBottomSheet
import me.juliana.hellomeds.ui.features.onboarding.components.SplashOnboardingScreen
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 6: Onboarding Complete
 *
 * Final onboarding screen that congratulates the user and offers:
 * - "Add medication" button that shows bottom sheet with camera/manual options
 * - "Later" button to exit onboarding without adding medication
 */
@Composable
fun CompletionScreen(
    onComplete: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToManualAdd: () -> Unit,
    onNavigateToImport: () -> Unit,
) {
    var showAddMedicationOptions by remember { mutableStateOf(false) }

    SplashOnboardingScreen(
        title = stringResource(Res.string.onboarding_completion_title),
        subTitle = stringResource(Res.string.onboarding_completion_subtitle),
        primaryButtonText = stringResource(Res.string.onboarding_add_medication),
        onPrimaryButtonClick = {
            showAddMedicationOptions = true
        },
        secondaryButtonText = stringResource(Res.string.onboarding_later),
        onSecondaryButtonClick = onComplete,
    )

    // Show bottom sheet for medication add options
    if (showAddMedicationOptions) {
        AddMedicationOptionsBottomSheet(
            onDismiss = {
                showAddMedicationOptions = false
            },
            onCameraSelected = {
                showAddMedicationOptions = false
                onNavigateToCamera()
            },
            onManualSelected = {
                showAddMedicationOptions = false
                onNavigateToManualAdd()
            },
            onImportSelected = {
                showAddMedicationOptions = false
                onNavigateToImport()
            },
        )
    }
}
