// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Expect helpers that wrap the M3 Expressive extensions on ListItemDefaults.
 *
 * On Android these delegate to the real [ListItemDefaults.segmentedShapes] and
 * [ListItemDefaults.SegmentedGap].  On iOS they fall back to rounded-corner
 * calculations that visually approximate segmented list styling.
 */

expect val SegmentedListGap: Dp

@Composable
expect fun segmentedListItemShapes(index: Int, count: Int): ListItemShapes
