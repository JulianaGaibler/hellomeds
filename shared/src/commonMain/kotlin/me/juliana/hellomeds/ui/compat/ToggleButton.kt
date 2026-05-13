// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Expect composable wrapping M3 Expressive ToggleButton.
 *
 * On Android this delegates to [androidx.compose.material3.ToggleButton].
 * On iOS this renders as a [FilledTonalButton]/[OutlinedButton] pair toggled
 * by [checked] state, providing a close visual equivalent.
 *
 * Pass `null` for [shapes] or [colors] to use platform defaults.
 */
@Composable
expect fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ButtonShapes? = null,
    colors: ToggleButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
)
