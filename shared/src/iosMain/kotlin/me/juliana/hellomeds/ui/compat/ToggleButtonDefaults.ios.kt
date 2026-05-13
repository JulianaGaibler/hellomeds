// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * iOS fallback for [ToggleButtonDefaults].
 */
actual object ToggleButtonDefaults {
    @Composable
    actual fun toggleButtonColors(containerColor: Color): ToggleButtonColors =
        ToggleButtonColors(containerColor = containerColor)
}
