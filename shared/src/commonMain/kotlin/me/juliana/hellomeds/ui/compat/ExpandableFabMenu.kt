// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A menu item for [ExpandableFabMenu].
 */
data class FabMenuItem(
    val icon: ImageVector,
    val label: String,
)

/**
 * Cross-platform expandable FAB menu.
 *
 * On Android this uses the real M3 Expressive [FloatingActionButtonMenu],
 * [ToggleFloatingActionButton], and [FloatingActionButtonMenuItem].
 *
 * On iOS this renders as a simple FAB that shows a dropdown menu when tapped.
 */
@Composable
expect fun ExpandableFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<FabMenuItem>,
    onItemClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    tooltipText: String = "",
    expandedLabel: String = "",
    collapsedLabel: String = "",
    toggleMenuLabel: String = "",
    closeMenuLabel: String = "",
)
