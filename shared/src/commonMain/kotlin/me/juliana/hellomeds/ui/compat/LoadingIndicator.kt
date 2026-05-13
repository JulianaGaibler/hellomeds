// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Expect composable wrapping M3 Expressive LoadingIndicator.
 *
 * On Android this delegates to [androidx.compose.material3.LoadingIndicator].
 * On iOS this falls back to a standard [CircularProgressIndicator].
 */
@Composable
expect fun LoadingIndicator(modifier: Modifier = Modifier, color: Color = Color.Unspecified)
