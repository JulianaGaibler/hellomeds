// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * On Android, delegates to the real M3 Expressive [ButtonGroupDefaults].
 */
actual object ButtonGroupDefaults {
    actual val ConnectedSpaceBetween: Dp =
        androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween

    @Composable
    actual fun connectedLeadingButtonShapes(): ButtonShapes =
        androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShapes()

    @Composable
    actual fun connectedMiddleButtonShapes(): ButtonShapes =
        androidx.compose.material3.ButtonGroupDefaults.connectedMiddleButtonShapes()

    @Composable
    actual fun connectedTrailingButtonShapes(): ButtonShapes =
        androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShapes()
}
