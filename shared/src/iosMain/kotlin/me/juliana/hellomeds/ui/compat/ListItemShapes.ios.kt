// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.ui.graphics.Shape

/**
 * iOS fallback for [ListItemShapes].
 * A simple data class holding shape references for segmented list item styling.
 */
actual class ListItemShapes actual constructor(
    actual val shape: Shape,
    val selectedShape: Shape,
    val pressedShape: Shape,
    val focusedShape: Shape,
    val hoveredShape: Shape,
    val draggedShape: Shape,
)
