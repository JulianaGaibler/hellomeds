// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS fallback for M3 Expressive segmented list helpers.
 */

actual val SegmentedListGap: Dp = 2.dp

@Composable
actual fun segmentedListItemShapes(index: Int, count: Int): ListItemShapes {
    val topRadius = if (index == 0) 16.dp else 4.dp
    val bottomRadius = if (index == count - 1) 16.dp else 4.dp
    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
    return ListItemShapes(
        shape = shape,
        selectedShape = shape,
        pressedShape = shape,
        focusedShape = shape,
        hoveredShape = shape,
        draggedShape = shape,
    )
}
