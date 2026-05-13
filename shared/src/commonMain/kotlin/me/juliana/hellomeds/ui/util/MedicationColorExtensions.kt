// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import me.juliana.hellomeds.ui.theme.MedicationColor

/**
 * Convenience extension to get the theme-aware foreground color.
 * Delegates to MedicationColor.foreground() which already handles light/dark.
 */
@Composable
@ReadOnlyComposable
fun MedicationColor.toForegroundColor(): Color = foreground()

/**
 * Convenience extension to get the theme-aware background color.
 * Delegates to MedicationColor.background() which already handles light/dark.
 */
@Composable
@ReadOnlyComposable
fun MedicationColor.toBackgroundColor(): Color = background()
