// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS fallback for [ButtonGroupDefaults].
 * Connected button shapes with leading/middle/trailing corners for unchecked state,
 * and fully rounded pill shape for checked state (matching M3 Expressive).
 */
actual object ButtonGroupDefaults {
    actual val ConnectedSpaceBetween: Dp = 2.dp

    private val checkedShape = RoundedCornerShape(50)

    @Composable
    actual fun connectedLeadingButtonShapes(): ButtonShapes = ButtonShapes(
        shape = RoundedCornerShape(
            topStart = 50.dp,
            bottomStart = 50.dp,
            topEnd = 4.dp,
            bottomEnd = 4.dp,
        ),
        checkedShape = checkedShape,
    )

    @Composable
    actual fun connectedMiddleButtonShapes(): ButtonShapes = ButtonShapes(
        shape = RoundedCornerShape(4.dp),
        checkedShape = checkedShape,
    )

    @Composable
    actual fun connectedTrailingButtonShapes(): ButtonShapes = ButtonShapes(
        shape = RoundedCornerShape(
            topStart = 4.dp,
            bottomStart = 4.dp,
            topEnd = 50.dp,
            bottomEnd = 50.dp,
        ),
        checkedShape = checkedShape,
    )
}
