// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.ui.compat.rememberPlatformNavBackStack
import me.juliana.hellomeds.ui.features.medication.AddMedicationState

/**
 * Create app navigation state with single root stack architecture.
 *
 * Architecture:
 * - Root Stack: Starts with [initialRoute] (usually MainAppRoute, or OnboardingRoute on first launch)
 * - Tab Management: 3 tabs (Tracking, Medication, Stock) managed by local state within MainAppRoute
 *
 * When overlay routes (Settings, Edit, etc.) are pushed, they completely replace the visible UI,
 * naturally hiding navigation bars that are defined inside MainAppRoute.
 *
 * @param initialRoute The route to start with (OnboardingRoute on first launch, MainAppRoute thereafter)
 */
@Composable
fun rememberAppNavigationState(initialRoute: NavKey = MainAppRoute): AppNavigationState {
    // Single root backstack initialized with provided initial route
    val rootBackStack = rememberPlatformNavBackStack(initialRoute)

    // Tab selection state (0=Tracking, 1=Medication, 2=Stock)
    // This is independent of the navigation stack - tabs switch without navigation
    val selectedTabIndex = rememberSaveable { mutableIntStateOf(0) } // Default to Tracking tab

    // State for camera detection data
    val detectedMedicationData = remember { mutableStateOf<MedicationDetectionResult?>(null) }

    // State for medication added completion dialog
    val completionDialogData = remember { mutableStateOf<MedicationCompletionData?>(null) }

    return remember(rootBackStack, selectedTabIndex, detectedMedicationData, completionDialogData) {
        AppNavigationState(
            rootBackStack = rootBackStack,
            _selectedTabIndex = selectedTabIndex,
            _detectedMedicationData = detectedMedicationData,
            _completionDialogData = completionDialogData,
        )
    }
}

/**
 * Data class to hold medication completion dialog information
 */
data class MedicationCompletionData(
    val medicationId: Long,
    val medicationName: String,
    val state: AddMedicationState,
)

/**
 * State holder for Single Root Stack Navigation.
 *
 * @param rootBackStack - the root navigation stack starting with MainAppRoute
 * @param _selectedTabIndex - internal state for current tab index (0=Tracking, 1=Medication, 2=Stock)
 * @param _detectedMedicationData - internal state for camera detection data to pass to add medication flow
 * @param _completionDialogData - internal state for medication added completion dialog
 */
class AppNavigationState(
    val rootBackStack: NavBackStack<NavKey>,
    private val _selectedTabIndex: MutableState<Int>,
    private val _detectedMedicationData: MutableState<MedicationDetectionResult?>,
    private val _completionDialogData: MutableState<MedicationCompletionData?>,
) {
    var selectedTabIndex: Int
        get() = _selectedTabIndex.value
        set(value) {
            _selectedTabIndex.value = value
        }

    var detectedMedicationData: MedicationDetectionResult?
        get() = _detectedMedicationData.value
        set(value) {
            _detectedMedicationData.value = value
        }

    var completionDialogData: MedicationCompletionData?
        get() = _completionDialogData.value
        set(value) {
            _completionDialogData.value = value
        }

    /**
     * Check if any overlay is currently visible on top of MainAppRoute.
     * True when stack is [MainAppRoute, ...overlay routes]
     */
    val isOverlayVisible: Boolean
        get() = rootBackStack.size > 1 // More than just MainAppRoute means overlays are visible
}

/**
 * Convert root navigation stack into NavEntries for NavDisplay.
 *
 * This includes BOTH MainAppRoute and any overlay routes on top of it.
 * The SaveableStateHolderDecorator ensures that when MainAppRoute is stopped
 * (hidden by an overlay), its state (scroll positions, tab selection) is preserved.
 *
 * Navigation 3 is designed to work with NavKey (the interface).
 * The entryProvider checks specific types internally via the entry<T> blocks.
 */
@Composable
fun AppNavigationState.toRootEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): SnapshotStateList<NavEntry<NavKey>> {
    val decorators = listOf(
        // CRITICAL: This preserves MainAppRoute state when overlays are on top
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
    )

    val decoratedEntries = rememberDecoratedNavEntries(
        backStack = rootBackStack,
        entryDecorators = decorators,
        entryProvider = entryProvider,
    )

    // Return all decorated entries from the root stack
    return decoratedEntries.toMutableStateList()
}
