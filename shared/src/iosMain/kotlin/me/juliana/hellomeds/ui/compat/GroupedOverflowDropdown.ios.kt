// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * iOS fallback: standard [DropdownMenu] with dividers between groups.
 */
@Composable
actual fun GroupedOverflowDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    groups: List<@Composable ColumnScope.(dismiss: () -> Unit) -> Unit>,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        groups.forEachIndexed { index, groupContent ->
            groupContent(onDismissRequest)

            if (index < groups.lastIndex) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
