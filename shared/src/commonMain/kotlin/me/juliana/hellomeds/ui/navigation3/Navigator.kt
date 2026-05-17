// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.navigation3.runtime.NavKey
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult

/**
 * Single root stack starts with [MainAppRoute]; overlays (Settings, Edit, etc.) are pushed on top
 * and completely replace the visible UI. Tab switches update [AppNavigationState.selectedTabIndex]
 * without touching the stack, and MainAppRoute's state is preserved while overlays cover it.
 */
class Navigator(val state: AppNavigationState) {

    fun openOverlay(route: NavKey) {
        state.rootBackStack.add(route)
    }

    /** Pops the top overlay. At size 1 we're at MainAppRoute and the system handles back/exit. */
    fun closeOverlay() {
        if (state.rootBackStack.size > 1) {
            state.rootBackStack.removeAt(state.rootBackStack.lastIndex)
        }
    }

    fun selectTab(index: Int) {
        state.selectedTabIndex = index
    }

    /** Stores detection data, then swaps the camera overlay for the Add Medication overlay. */
    fun onCameraDetectionComplete(detectionResult: MedicationDetectionResult? = null) {
        state.detectedMedicationData = detectionResult
        closeOverlay()
        openOverlay(AddMedicationRoute)
    }
}
