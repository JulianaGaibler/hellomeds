// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.ui.graphics.Shape

/**
 * Expect class wrapping M3 Expressive ListItemShapes.
 *
 * On Android this is a typealias to the real [androidx.compose.material3.ListItemShapes].
 * On iOS this is a simple data class holding shape references.
 *
 * Only [shape] is accessed at call-sites (for clipping); the other properties exist
 * so that the constructor call-sites compile unchanged.
 */
expect class ListItemShapes(
    shape: Shape,
    selectedShape: Shape,
    pressedShape: Shape,
    focusedShape: Shape,
    hoveredShape: Shape,
    draggedShape: Shape,
) {
    val shape: Shape
}
