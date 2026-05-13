// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.compose.runtime.Composable

/**
 * Provides platform-specific or app-module-only screen implementations
 * that are injected into the shared navigation graph.
 *
 * On Android, the app module provides real implementations for Settings, Debug,
 * Export/Import, and Notification Settings screens.
 *
 * On iOS, these default to empty composables (screens not yet available).
 */
data class NavigationScreenProviders(
    val debugScreen: @Composable (
        onNavigateBack: () -> Unit,
        onNavigateToOnboarding: (showAllSteps: Boolean) -> Unit,
    ) -> Unit = { _, _ -> },

    val cameraDetectionScreen: (
        @Composable (
            onNavigateBack: () -> Unit,
            onDetectionComplete: (me.juliana.hellomeds.domain.ml.MedicationDetectionResult) -> Unit,
        ) -> Unit
    )? = null,

    val notificationPermissionScreen: @Composable (
        onContinue: () -> Unit,
        onBack: () -> Unit,
    ) -> Unit = { _, _ -> },

    val alarmKitPermissionScreen: (
        @Composable (
            onContinue: () -> Unit,
            onBack: () -> Unit,
        ) -> Unit
    )? = null,

    val criticalAlertsPermissionScreen: (
        @Composable (
            onContinue: () -> Unit,
            onBack: () -> Unit,
        ) -> Unit
    )? = null,
)
