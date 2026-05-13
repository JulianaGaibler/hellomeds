// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * On Android, delegates to the real M3 Expressive [ToggleButton].
 */
@Composable
actual fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shapes: ButtonShapes?,
    colors: ToggleButtonColors?,
    content: @Composable RowScope.() -> Unit,
) {
    if (shapes != null && colors != null) {
        androidx.compose.material3.ToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            shapes = shapes,
            colors = colors,
            content = content,
        )
    } else if (shapes != null) {
        androidx.compose.material3.ToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            shapes = shapes,
            content = content,
        )
    } else if (colors != null) {
        androidx.compose.material3.ToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            content = content,
        )
    } else {
        androidx.compose.material3.ToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}
