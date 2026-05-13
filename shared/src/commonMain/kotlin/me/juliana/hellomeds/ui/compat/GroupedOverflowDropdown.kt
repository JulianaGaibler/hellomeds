// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Dropdown menu that renders items in visually separated groups.
 *
 * On Android, uses M3 Expressive [DropdownMenuPopup] + [DropdownMenuGroup] with visual gaps.
 * On iOS, falls back to standard [DropdownMenu] with spacer gaps between groups.
 *
 * @param groups List of group content composables. Each receives a `dismiss` callback.
 *   Groups are rendered in order with visual gaps between them.
 */
@Composable
expect fun GroupedOverflowDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    groups: List<@Composable ColumnScope.(dismiss: () -> Unit) -> Unit>,
)
