// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Split button for top app bar actions: the leading button performs a primary action,
 * the trailing button toggles a dropdown menu.
 *
 * On Android, uses M3 Expressive [SplitButtonLayout].
 * On iOS, falls back to a Row with two styled buttons.
 */
@Composable
expect fun SplitMenuButton(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
)
