// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * On Android, delegates to the real M3 Expressive [ToggleButtonDefaults].
 */
actual object ToggleButtonDefaults {
    @Composable
    actual fun toggleButtonColors(containerColor: Color): ToggleButtonColors =
        if (containerColor != Color.Unspecified) {
            androidx.compose.material3.ToggleButtonDefaults.toggleButtonColors(
                containerColor = containerColor,
            )
        } else {
            androidx.compose.material3.ToggleButtonDefaults.toggleButtonColors()
        }
}
