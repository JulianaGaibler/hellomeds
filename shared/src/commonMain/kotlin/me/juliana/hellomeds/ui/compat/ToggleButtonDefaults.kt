// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Expect object wrapping M3 Expressive ToggleButtonDefaults.
 *
 * On Android this delegates to [androidx.compose.material3.ToggleButtonDefaults].
 * On iOS it builds fallback [ToggleButtonColors] from MaterialTheme tokens.
 */
expect object ToggleButtonDefaults {
    @Composable
    fun toggleButtonColors(containerColor: Color = Color.Unspecified): ToggleButtonColors
}
