// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Expect object wrapping M3 Expressive ButtonGroupDefaults.
 *
 * On Android this delegates to [androidx.compose.material3.ButtonGroupDefaults].
 * On iOS it returns fallback [ButtonShapes] based on standard rounded corners.
 */
expect object ButtonGroupDefaults {
    val ConnectedSpaceBetween: Dp

    @Composable
    fun connectedLeadingButtonShapes(): ButtonShapes

    @Composable
    fun connectedMiddleButtonShapes(): ButtonShapes

    @Composable
    fun connectedTrailingButtonShapes(): ButtonShapes
}
