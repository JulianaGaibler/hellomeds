// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.juliana.hellomeds.designsystem.testing.ScreenshotTestTags
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_close_menu
import me.juliana.hellomeds.shared.accessibility_collapsed
import me.juliana.hellomeds.shared.accessibility_expanded
import me.juliana.hellomeds.shared.accessibility_loading
import me.juliana.hellomeds.shared.accessibility_move_down
import me.juliana.hellomeds.shared.accessibility_move_up
import me.juliana.hellomeds.shared.accessibility_toggle_menu
import me.juliana.hellomeds.shared.action_hide_archived
import me.juliana.hellomeds.shared.action_show_archived
import me.juliana.hellomeds.shared.add_medication_content_description
import me.juliana.hellomeds.shared.illustration_empty_no_medication
import me.juliana.hellomeds.shared.medication_add_manually
import me.juliana.hellomeds.shared.medication_add_with_camera
import me.juliana.hellomeds.shared.medication_archived
import me.juliana.hellomeds.shared.medication_empty_state
import me.juliana.hellomeds.shared.screen_medication
import me.juliana.hellomeds.ui.compat.ExpandableFabMenu
import me.juliana.hellomeds.ui.compat.FabMenuItem
import me.juliana.hellomeds.ui.compat.LoadingIndicator
import me.juliana.hellomeds.ui.compat.PlatformBackHandler
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.PermissionWarningBanners
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.common.EmptyState
import me.juliana.hellomeds.ui.components.common.TopAppBarWithMenu
import me.juliana.hellomeds.ui.components.medication.MedicationGridItem
import me.juliana.hellomeds.ui.util.LocalPermissionWarnings
import me.juliana.hellomeds.ui.util.PermissionWarning
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import me.juliana.hellomeds.ui.viewmodel.MedicationViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onAddMedication: () -> Unit,
    onAddWithCamera: () -> Unit,
    onMedicationClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    medicationViewModel: MedicationViewModel = koinViewModel(),
) {
    platformContext()
    LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val medications by medicationViewModel.activeMedications.collectAsStateWithLifecycle()
    val archivedMedications by medicationViewModel.archivedMedications.collectAsStateWithLifecycle()
    val hasLoaded by medicationViewModel.hasLoaded.collectAsStateWithLifecycle()

    val permissionState = LocalPermissionWarnings.current
    var dismissedWarnings by remember { mutableStateOf(emptySet<PermissionWarning>()) }

    val medicationsList = remember(medications) { medications.toMutableStateList() }

    LaunchedEffect(medications) {
        if (medicationsList != medications) {
            medicationsList.clear()
            medicationsList.addAll(medications)
        }
    }

    var showArchived by rememberSaveable { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    FocusRequester()

    PlatformBackHandler(fabMenuExpanded) { fabMenuExpanded = false }

    val lazyGridState = rememberLazyGridState()

    val warningBannerCount = if (permissionState.hasWarnings) 1 else 0
    val reorderableState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Offset to account for the conditional permission warning banner item
        val fromIndex = from.index - warningBannerCount
        val toIndex = to.index - warningBannerCount

        if (fromIndex in medicationsList.indices && toIndex in medicationsList.indices) {
            val item = medicationsList[fromIndex]
            medicationsList.removeAt(fromIndex)
            medicationsList.add(toIndex, item)

            // Persist reorder (debounced inside ViewModel)
            medicationViewModel.reorderMedications(medicationsList)
        }
    }

    // FAB Menu
    val hasCameraFeature = PlatformCapabilities.supportsCameraDetection()
    val addManuallyText = stringResource(Res.string.medication_add_manually)
    val addWithCameraText = stringResource(Res.string.medication_add_with_camera)
    val fabMenuItems = remember(addManuallyText, addWithCameraText, hasCameraFeature) {
        buildList {
            add(FabMenuItem(Icons.Default.Edit, addManuallyText))
            if (hasCameraFeature) {
                add(FabMenuItem(Icons.Default.Person, addWithCameraText))
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppScaffold(
            topBar = {
                TopAppBarWithMenu(
                    title = stringResource(Res.string.screen_medication),
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToSupport = onNavigateToSupport,
                    menuContent = { dismiss ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (showArchived) {
                                            Res.string.action_hide_archived
                                        } else {
                                            Res.string.action_show_archived
                                        },
                                    ),
                                )
                            },
                            onClick = {
                                dismiss()
                                showArchived = !showArchived
                            },
                        )
                    },
                )
            },
        ) { paddingValues ->
            // When there's nothing for the grid to render (no active meds and either the user
            // hasn't asked to see archived or archived is also empty), bypass the LazyVerticalGrid
            // so the empty/loading state can occupy the full viewport and center vertically —
            // LazyGridItemScope has no `fillParentMaxHeight` equivalent.
            val showFullScreenEmpty = medications.isEmpty() &&
                (!showArchived || archivedMedications.isEmpty())

            if (showFullScreenEmpty) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    if (permissionState.hasWarnings) {
                        PermissionWarningBanners(
                            state = permissionState,
                            dismissedWarnings = dismissedWarnings,
                            onDismiss = { dismissedWarnings = dismissedWarnings + it },
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!hasLoaded) {
                            val loadingDescription = stringResource(Res.string.accessibility_loading)
                            Box(
                                modifier = Modifier.semantics {
                                    contentDescription = loadingDescription
                                    liveRegion = LiveRegionMode.Polite
                                },
                            ) {
                                LoadingIndicator()
                            }
                        } else {
                            EmptyState(
                                title = stringResource(Res.string.medication_empty_state),
                                illustration = painterResource(Res.drawable.illustration_empty_no_medication),
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    state = lazyGridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 88.dp, // Extra space for FAB
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Permission warnings (centralized — includes DnD bypass check)
                    if (permissionState.hasWarnings) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PermissionWarningBanners(
                                state = permissionState,
                                dismissedWarnings = dismissedWarnings,
                                onDismiss = { dismissedWarnings = dismissedWarnings + it },
                            )
                        }
                    }

                    // Active medications with drag-and-drop reordering
                    itemsIndexed(medicationsList, key = { _, it -> it.id }) { currentIndex, medication ->
                        ReorderableItem(reorderableState, key = medication.id) { isDragging ->
                            // Visual feedback: scale up and reduce opacity when dragging
                            val scale = if (isDragging) 1.05f else 1f
                            val alpha = if (isDragging) 0.5f else 1f
                            val canMoveUp = currentIndex > 0
                            val canMoveDown = currentIndex < medicationsList.size - 1

                            // Accessibility actions for TalkBack users
                            val moveUpLabel = stringResource(Res.string.accessibility_move_up)
                            val moveDownLabel = stringResource(Res.string.accessibility_move_down)

                            MedicationGridItem(
                                medicationName = medication.name,
                                typeAndStrength = medication.typeAndStrength,
                                scheduleSummary = medication.scheduleSummary,
                                foregroundShape = medication.foregroundShape,
                                backgroundShape = medication.backgroundShape,
                                color1 = medication.color1,
                                onClick = { onMedicationClick(medication.id) },
                                modifier = Modifier
                                    .testTag(ScreenshotTestTags.medicationGridItem(medication.id))
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            // Haptic feedback when drag starts
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                    )
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    }
                                    .semantics {
                                        // Add custom accessibility actions for reordering
                                        val actions = mutableListOf<CustomAccessibilityAction>()

                                        if (canMoveUp) {
                                            actions.add(
                                                CustomAccessibilityAction(moveUpLabel) {
                                                    val item = medicationsList.removeAt(currentIndex)
                                                    medicationsList.add(currentIndex - 1, item)
                                                    medicationViewModel.reorderMedications(medicationsList)
                                                    true
                                                },
                                            )
                                        }

                                        if (canMoveDown) {
                                            actions.add(
                                                CustomAccessibilityAction(moveDownLabel) {
                                                    val item = medicationsList.removeAt(currentIndex)
                                                    medicationsList.add(currentIndex + 1, item)
                                                    medicationViewModel.reorderMedications(medicationsList)
                                                    true
                                                },
                                            )
                                        }

                                        if (actions.isNotEmpty()) {
                                            customActions = actions
                                        }
                                    },
                            )
                        }
                    }

                    // Archived section header (only show if archived medications exist)
                    if (showArchived && archivedMedications.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AnimatedVisibility(
                                visible = true,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Text(
                                    text = stringResource(Res.string.medication_archived),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                                )
                            }
                        }
                    }

                    // Archived medications
                    if (showArchived) {
                        items(archivedMedications, key = { it.id }) { medication ->
                            AnimatedVisibility(
                                visible = true,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                MedicationGridItem(
                                    medicationName = medication.name,
                                    typeAndStrength = medication.typeAndStrength,
                                    scheduleSummary = medication.scheduleSummary,
                                    foregroundShape = medication.foregroundShape,
                                    backgroundShape = medication.backgroundShape,
                                    color1 = medication.color1,
                                    onClick = { onMedicationClick(medication.id) },
                                    isArchived = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scrim to capture outside clicks when FAB menu is expanded
        if (fabMenuExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        fabMenuExpanded = false
                    },
                color = Color.Transparent,
            ) {}
        }

        // FAB Menu
        ExpandableFabMenu(
            modifier = Modifier.align(Alignment.BottomEnd),
            expanded = fabMenuExpanded,
            onExpandedChange = { expanded ->
                if (!hasCameraFeature) {
                    // Single action — skip expandable menu, add directly
                    onAddMedication()
                } else {
                    fabMenuExpanded = expanded
                }
            },
            items = fabMenuItems,
            onItemClick = { i ->
                fabMenuExpanded = false
                when (i) {
                    0 -> onAddMedication()
                    1 -> onAddWithCamera()
                }
            },
            tooltipText = stringResource(Res.string.add_medication_content_description),
            expandedLabel = stringResource(Res.string.accessibility_expanded),
            collapsedLabel = stringResource(Res.string.accessibility_collapsed),
            toggleMenuLabel = stringResource(Res.string.accessibility_toggle_menu),
            closeMenuLabel = stringResource(Res.string.accessibility_close_menu),
        )
    }
}
