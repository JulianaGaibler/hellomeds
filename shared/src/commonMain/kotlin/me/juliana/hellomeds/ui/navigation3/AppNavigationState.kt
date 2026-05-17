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
 * Single root stack: starts with [initialRoute] (MainAppRoute, or OnboardingRoute on first launch).
 * The three main tabs are held in local state within MainAppRoute, not the stack. Overlay routes pushed
 * on top fully replace the visible UI, hiding the navigation bars defined inside MainAppRoute.
 */
@Composable
fun rememberAppNavigationState(initialRoute: NavKey = MainAppRoute): AppNavigationState {
    val rootBackStack = rememberPlatformNavBackStack(initialRoute)
    val selectedTabIndex = rememberSaveable { mutableIntStateOf(0) }
    val detectedMedicationData = remember { mutableStateOf<MedicationDetectionResult?>(null) }
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

data class MedicationCompletionData(
    val medicationId: Long,
    val medicationName: String,
    val state: AddMedicationState,
)

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

    val isOverlayVisible: Boolean
        get() = rootBackStack.size > 1
}

@Composable
fun AppNavigationState.toRootEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): SnapshotStateList<NavEntry<NavKey>> {
    // SaveableStateHolderDecorator preserves MainAppRoute state (scroll, tab) while overlays cover it.
    val decorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
    )

    val decoratedEntries = rememberDecoratedNavEntries(
        backStack = rootBackStack,
        entryDecorators = decorators,
        entryProvider = entryProvider,
    )

    return decoratedEntries.toMutableStateList()
}
