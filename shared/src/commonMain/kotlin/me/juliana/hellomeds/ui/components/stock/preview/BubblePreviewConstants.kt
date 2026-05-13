// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.stock.preview

import androidx.compose.ui.unit.dp

/**
 * Constants for bubble preview layout and styling.
 * Used when displaying discrete medication stock as a grid of pills/tablets.
 */
object BubblePreviewConstants {
    // Grid padding
    val GRID_PADDING_START = 8.dp
    val GRID_PADDING_END = 8.dp
    val GRID_PADDING_TOP = 8.dp
    val GRID_PADDING_BOTTOM = 8.dp

    // Minimum padding on all sides of canvas
    val MIN_PADDING = 16.dp

    // Spacing between circles
    val CIRCLE_SPACING = 12.dp

    // Circle size constraints
    val MIN_CIRCLE_DIAMETER = 4.dp
    val MAX_CIRCLE_DIAMETER = 48.dp

    // Visual appearance
    const val REMAINING_ALPHA = 1.0f // Full opacity for remaining pills
    const val TAKEN_ALPHA = 1.0f // Full opacity for taken pills

    // Circle stroke/ring
    val CIRCLE_STROKE_WIDTH = 4.dp // White ring around each circle

    // Canvas aspect ratio (slightly taller than wide)
    const val BUBBLE_PREVIEW_ASPECT_RATIO = 1.2f

    // Package separator (when displaying multiple packages)
    val PACKAGE_SEPARATOR_HEIGHT = 12.dp
    val PACKAGE_SEPARATOR_PADDING = 4.dp
}
