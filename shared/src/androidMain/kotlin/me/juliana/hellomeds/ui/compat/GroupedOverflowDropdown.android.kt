// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * On Android, uses M3 Expressive [DropdownMenuPopup] + [DropdownMenuGroup] with visual gaps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun GroupedOverflowDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    groups: List<@Composable ColumnScope.(dismiss: () -> Unit) -> Unit>,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        groups.forEachIndexed { index, groupContent ->
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index, groups.size),
            ) {
                groupContent(onDismissRequest)
            }

            if (index < groups.lastIndex) {
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            }
        }
    }
}
