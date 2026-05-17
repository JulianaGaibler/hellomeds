// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.compose.ui.platform.testTag
import me.juliana.hellomeds.designsystem.testing.ScreenshotTestTags
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.filled_box_24px
import me.juliana.hellomeds.shared.filled_calendar_today_24
import me.juliana.hellomeds.shared.filled_pill_24px
import me.juliana.hellomeds.shared.outline_box_24px
import me.juliana.hellomeds.shared.outline_calendar_today_24
import me.juliana.hellomeds.shared.outline_pill_24px
import me.juliana.hellomeds.shared.screen_medication
import me.juliana.hellomeds.shared.screen_stock
import me.juliana.hellomeds.shared.screen_tracking
import me.juliana.hellomeds.ui.navigation3.entries.MedicationListScreenEntry
import me.juliana.hellomeds.ui.navigation3.entries.StockScreenEntry
import me.juliana.hellomeds.ui.navigation3.entries.TrackingScreenEntry
import me.juliana.hellomeds.ui.theme.ContrastLevel
import me.juliana.hellomeds.ui.theme.LocalContrastLevel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Adaptive main screen that shows the three main tabs (Tracking, Medication, Stock).
 *
 * This uses movableContentOf to preserve screen state when:
 * - Switching between tabs on mobile/tablet
 * - Resizing the window (mobile <-> tablet <-> desktop)
 * - Rotating the device
 *
 * Layout adapts to window size:
 * - Compact (< 600dp): Bottom navigation bar + AnimatedContent
 * - Medium (600-1023dp): Navigation rail + AnimatedContent
 * - Expanded (>= 1024dp): 1:1:1 horizontal split showing all three screens
 *
 * @param selectedTabIndex Current tab index (0=Tracking, 1=Medication, 2=Stock)
 * @param onTabSelected Callback when user selects a different tab
 * @param onNavigateToSettings Callback to navigate to Settings screen
 * @param onNavigateToAddMedication Callback to navigate to Add Medication flow
 * @param onNavigateToCamera Callback to navigate to Camera detection screen
 * @param onNavigateToMedicationDetail Callback to navigate to medication detail
 * @param onNavigateToEditSchedule Callback to navigate to edit schedule
 * @param onNavigateToEditLabel Callback to navigate to edit label
 */
@Composable
fun AdaptiveMainScreen(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onNavigateToAddMedication: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToMedicationDetail: (medicationId: Int) -> Unit,
    onNavigateToEditSchedule: (medicationId: Int) -> Unit,
    onNavigateToEditLabel: (medicationId: Int) -> Unit,
    onNavigateToStockDetail: (medicationId: Int) -> Unit,
    modifier: Modifier = Modifier,
    notificationEventIds: IntArray? = null,
    notificationGroupingMode: String? = null,
    onNotificationHandled: () -> Unit = {},
) {
    // Auto-navigate to Tracking tab when notification data is present
    LaunchedEffect(notificationEventIds) {
        if (notificationEventIds != null && notificationEventIds.isNotEmpty()) {
            onTabSelected(0)
        }
    }

    // rememberUpdatedState keeps the lambda's reference to these values "live"
    // without recreating the movableContentOf (which would lose screen state).
    // Without this, the lambda captures the initial null and never updates.
    val currentNotificationEventIds by rememberUpdatedState(notificationEventIds)
    val currentNotificationGroupingMode by rememberUpdatedState(notificationGroupingMode)
    val currentOnNotificationHandled by rememberUpdatedState(onNotificationHandled)

    // movableContentOf preserves screen state (scroll position, ViewModels, etc.)
    // when moving between different parent layouts on resize.
    val trackingScreen = remember {
        movableContentOf {
            TrackingScreenEntry(
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSupport = onNavigateToSupport,
                notificationEventIds = currentNotificationEventIds,
                notificationGroupingMode = currentNotificationGroupingMode,
                onNotificationHandled = currentOnNotificationHandled,
            )
        }
    }

    val medicationScreen = remember {
        movableContentOf {
            MedicationListScreenEntry(
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSupport = onNavigateToSupport,
                onAddMedication = onNavigateToAddMedication,
                onAddWithCamera = onNavigateToCamera,
                onMedicationClick = onNavigateToMedicationDetail,
                onEditSchedule = onNavigateToEditSchedule,
                onEditLabel = onNavigateToEditLabel,
            )
        }
    }

    val stockScreen = remember {
        movableContentOf {
            StockScreenEntry(
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSupport = onNavigateToSupport,
                onNavigateToStockDetail = onNavigateToStockDetail,
            )
        }
    }

    // Use BoxWithConstraints for actual dp width measurement.
    // Note: WindowSizeClass.isWidthAtLeastBreakpoint() only reports bucketed breakpoint
    // values (0, 600, 840) — not the actual width — so custom thresholds like 1024
    // never trigger. BoxWithConstraints gives the real dp value.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 1024.dp
        val isMedium = maxWidth >= 600.dp && !isWide

        if (isWide) {
            // --- WIDE LAYOUT (Desktop/Large Tablet) ---
            // Show all three screens side-by-side as rounded cards with gaps
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.surfaceContainerHighest)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        shape = RoundedCornerShape(32.dp),
                        color = colorScheme.surface,
                    ) { trackingScreen() }

                    Surface(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        shape = RoundedCornerShape(32.dp),
                        color = colorScheme.surface,
                    ) { medicationScreen() }

                    Surface(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        shape = RoundedCornerShape(32.dp),
                        color = colorScheme.surface,
                    ) { stockScreen() }
                }
            }
        } else {
            // --- NARROW/MEDIUM LAYOUT (Mobile/Tablet) ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isMedium) {
                            colorScheme.surfaceContainerHighest
                        } else {
                            colorScheme.surfaceContainer
                        },
                    ),
            ) {
                val tabDestinations = TabDestination.entries

                if (isMedium) {
                    // --- MEDIUM: Navigation Rail + Content ---
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            tabDestinations.forEachIndexed { index, destination ->
                                val isSelected = selectedTabIndex == index
                                NavigationRailItem(
                                    selected = isSelected,
                                    onClick = { onTabSelected(index) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(
                                                if (isSelected) {
                                                    destination.selectedIconRes
                                                } else {
                                                    destination.unselectedIconRes
                                                },
                                            ),
                                            contentDescription = stringResource(destination.labelRes),
                                        )
                                    },
                                    label = { Text(stringResource(destination.labelRes)) },
                                    colors = NavigationRailItemDefaults.colors(
                                        indicatorColor = if (LocalContrastLevel.current == ContrastLevel.High) {
                                            colorScheme.secondaryContainer
                                        } else {
                                            colorScheme.surfaceContainerLow
                                        },
                                    ),
                                    modifier = Modifier.testTag(ScreenshotTestTags.navTab(destination.name)),
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars),
                            shape = RoundedCornerShape(topStart = 32.dp),
                            color = colorScheme.surface,
                        ) {
                            AnimatedContent(
                                targetState = selectedTabIndex,
                                label = "TabTransition",
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(200)) togetherWith
                                        fadeOut(animationSpec = tween(200))
                                },
                            ) { page ->
                                when (page) {
                                    0 -> trackingScreen()
                                    1 -> medicationScreen()
                                    2 -> stockScreen()
                                }
                            }
                        }
                    }
                } else {
                    // --- COMPACT: Bottom Navigation Bar + Content ---
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            NavigationBar(
                                containerColor = colorScheme.surfaceContainerHighest,
                            ) {
                                tabDestinations.forEachIndexed { index, destination ->
                                    val isSelected = selectedTabIndex == index
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { onTabSelected(index) },
                                        icon = {
                                            Icon(
                                                painter = painterResource(
                                                    if (isSelected) {
                                                        destination.selectedIconRes
                                                    } else {
                                                        destination.unselectedIconRes
                                                    },
                                                ),
                                                contentDescription = stringResource(destination.labelRes),
                                            )
                                        },
                                        label = { Text(stringResource(destination.labelRes)) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = if (LocalContrastLevel.current == ContrastLevel.High) {
                                                colorScheme.secondaryContainer
                                            } else {
                                                colorScheme.surfaceContainerLow
                                            },
                                        ),
                                        modifier = Modifier.testTag(ScreenshotTestTags.navTab(destination.name)),
                                    )
                                }
                            }
                        },
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            color = colorScheme.surface,
                        ) {
                            AnimatedContent(
                                targetState = selectedTabIndex,
                                label = "TabTransition",
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(200)) togetherWith
                                        fadeOut(animationSpec = tween(200))
                                },
                            ) { page ->
                                when (page) {
                                    0 -> trackingScreen()
                                    1 -> medicationScreen()
                                    2 -> stockScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab destinations for the main navigation.
 * These define the three main screens in the app.
 */
private enum class TabDestination(
    val labelRes: StringResource,
    val unselectedIconRes: DrawableResource,
    val selectedIconRes: DrawableResource,
) {
    TRACKING(
        labelRes = Res.string.screen_tracking,
        unselectedIconRes = Res.drawable.outline_calendar_today_24,
        selectedIconRes = Res.drawable.filled_calendar_today_24,
    ),
    MEDICATION(
        labelRes = Res.string.screen_medication,
        unselectedIconRes = Res.drawable.outline_pill_24px,
        selectedIconRes = Res.drawable.filled_pill_24px,
    ),
    STOCK(
        labelRes = Res.string.screen_stock,
        unselectedIconRes = Res.drawable.outline_box_24px,
        selectedIconRes = Res.drawable.filled_box_24px,
    ),
}
