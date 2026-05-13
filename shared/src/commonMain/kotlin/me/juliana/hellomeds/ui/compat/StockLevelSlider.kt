// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific slider for adjusting stock fill level.
 *
 * Android: [VerticalSlider] from M3 Expressive (bottom=0, top=max).
 * iOS: horizontal [Slider] fallback (left=0, right=max).
 */
@Composable
expect fun StockLevelSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
)
