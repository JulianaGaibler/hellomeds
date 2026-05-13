// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.ui.graphics.Color

/**
 * iOS fallback for [ToggleButtonColors].
 * Stores basic color overrides for the fallback toggle button rendering.
 */
actual class ToggleButtonColors(
    val containerColor: Color = Color.Unspecified,
    val checkedContainerColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
    val checkedContentColor: Color = Color.Unspecified,
)
