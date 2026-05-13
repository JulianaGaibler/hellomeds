// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * On Android, delegates to the real M3 Expressive [LoadingIndicator].
 */
@Composable
actual fun LoadingIndicator(modifier: Modifier, color: Color) {
    if (color != Color.Unspecified) {
        androidx.compose.material3.LoadingIndicator(
            modifier = modifier,
            color = color,
        )
    } else {
        androidx.compose.material3.LoadingIndicator(
            modifier = modifier,
        )
    }
}
