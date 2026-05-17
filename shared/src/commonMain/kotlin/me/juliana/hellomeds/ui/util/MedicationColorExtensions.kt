// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import me.juliana.hellomeds.ui.theme.MedicationColor

@Composable
@ReadOnlyComposable
fun MedicationColor.toForegroundColor(): Color = foreground()

@Composable
@ReadOnlyComposable
fun MedicationColor.toBackgroundColor(): Color = background()
