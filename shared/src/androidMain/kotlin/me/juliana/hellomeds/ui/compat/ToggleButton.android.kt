// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.ToggleButton as M3ToggleButton
import androidx.compose.material3.ToggleButtonDefaults as M3ToggleButtonDefaults

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
    contentPadding: PaddingValues?,
    content: @Composable RowScope.() -> Unit,
) {
    M3ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        shapes = shapes ?: M3ToggleButtonDefaults.shapes(),
        colors = colors ?: M3ToggleButtonDefaults.toggleButtonColors(),
        contentPadding = contentPadding ?: ButtonDefaults.ContentPadding,
        content = content,
    )
}
