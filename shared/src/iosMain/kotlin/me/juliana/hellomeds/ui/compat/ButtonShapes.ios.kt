// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape

/**
 * iOS fallback for [ButtonShapes].
 * Stores the primary shape; the iOS [ToggleButton] uses it for clipping.
 */
actual class ButtonShapes(
    val shape: Shape = RoundedCornerShape(50),
    val pressedShape: Shape = shape,
    val hoveredShape: Shape = shape,
    val focusedShape: Shape = shape,
    val checkedShape: Shape = shape,
)
