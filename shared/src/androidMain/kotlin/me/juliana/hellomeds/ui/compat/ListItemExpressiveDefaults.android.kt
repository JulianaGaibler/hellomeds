// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * On Android, delegates to the real M3 Expressive [ListItemDefaults] extensions.
 */

actual val SegmentedListGap: Dp = ListItemDefaults.SegmentedGap

@Composable
actual fun segmentedListItemShapes(index: Int, count: Int): ListItemShapes =
    ListItemDefaults.segmentedShapes(index = index, count = count)
