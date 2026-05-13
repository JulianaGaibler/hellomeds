// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

/**
 * Expect class wrapping M3 Expressive ButtonShapes.
 *
 * On Android this is a typealias to [androidx.compose.material3.ButtonShapes].
 * On iOS this is a lightweight holder used only so call-sites compile unchanged;
 * the iOS [ToggleButton] actual ignores the shapes and renders with standard M3.
 */
expect class ButtonShapes
