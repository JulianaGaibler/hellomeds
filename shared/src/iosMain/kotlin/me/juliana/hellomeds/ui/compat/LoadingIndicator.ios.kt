// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * iOS fallback for [LoadingIndicator].
 * Renders as a standard [CircularProgressIndicator].
 */
@Composable
actual fun LoadingIndicator(modifier: Modifier, color: Color) {
    CircularProgressIndicator(
        modifier = modifier,
        color = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.primary,
    )
}
