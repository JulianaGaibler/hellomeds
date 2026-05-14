// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.navigation3.runtime.NavKey
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult

/**
 * Handles navigation events for Single Root Stack Architecture.
 *
 * Navigation Rules:
 * - Root stack starts with [MainAppRoute]
 * - Overlay screens (Settings, Edit, etc.) are pushed on top of MainAppRoute
 * - Tab switching updates selectedTabIndex (does NOT modify navigation stack)
 * - When overlays are on top, MainAppRoute is stopped but state is preserved
 */
class Navigator(val state: AppNavigationState) {

    /**
     * Open an overlay screen (Settings, Edit, Camera, etc.).
     * Pushes the route onto the root stack, completely replacing the visible UI.
     */
    fun openOverlay(route: NavKey) {
        state.rootBackStack.add(route)
    }

    /**
     * Close the current overlay screen.
     * Pops the top route from the root stack.
     *
     * If stack is [MainAppRoute, SettingsRoute], this removes SettingsRoute -> shows MainAppRoute
     * If stack is [MainAppRoute], system handles back (app exit)
     */
    fun closeOverlay() {
        if (state.rootBackStack.size > 1) {
            // Remove current overlay
            state.rootBackStack.removeAt(state.rootBackStack.lastIndex)
        }
        // If rootBackStack.size == 1, we're at MainAppRoute - system handles app exit
    }

    /**
     * Switch tabs within MainAppRoute (does not affect navigation stack).
     * This updates local state only - no navigation transition occurs.
     */
    fun selectTab(index: Int) {
        state.selectedTabIndex = index
    }

    /**
     * Special handler for camera detection completion.
     * Stores detection data, closes camera overlay, then opens Add Medication overlay.
     */
    fun onCameraDetectionComplete(detectionResult: MedicationDetectionResult? = null) {
        state.detectedMedicationData = detectionResult
        closeOverlay() // Close camera
        openOverlay(AddMedicationRoute) // Open add medication
    }
}
