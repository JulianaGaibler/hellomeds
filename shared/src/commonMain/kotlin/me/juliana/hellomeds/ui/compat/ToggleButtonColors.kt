// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

/**
 * Expect class wrapping M3 Expressive ToggleButtonColors.
 *
 * On Android this is a typealias to [androidx.compose.material3.ToggleButtonColors].
 * On iOS this is a lightweight holder used only so call-sites compile unchanged;
 * the iOS [ToggleButton] actual reads the stored colors for fallback rendering.
 */
expect class ToggleButtonColors
