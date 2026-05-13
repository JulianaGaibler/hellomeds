// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import me.juliana.hellomeds.R

/**
 * Converts MedicationColor enum to Compose Color for foreground/shape elements.
 *
 * Automatically adapts to light/dark theme via Android's resource qualifier system:
 * - Light mode: uses darker pastel shades (from values/colors.xml)
 * - Dark mode: uses lighter pastel shades (from values-night/colors.xml)
 *
 * This provides better contrast between foreground shapes and backgrounds.
 */
@Composable
@ReadOnlyComposable
fun MedicationColor.toForegroundColor(): Color = when (this) {
    MedicationColor.Rose -> colorResource(R.color.medication_foreground_rose)
    MedicationColor.Pink -> colorResource(R.color.medication_foreground_pink)
    MedicationColor.Red -> colorResource(R.color.medication_foreground_red)
    MedicationColor.Orange -> colorResource(R.color.medication_foreground_orange)
    MedicationColor.Yellow -> colorResource(R.color.medication_foreground_yellow)
    MedicationColor.Chartreuse -> colorResource(R.color.medication_foreground_chartreuse)
    MedicationColor.Green -> colorResource(R.color.medication_foreground_green)
    MedicationColor.Teal -> colorResource(R.color.medication_foreground_teal)
    MedicationColor.Cyan -> colorResource(R.color.medication_foreground_cyan)
    MedicationColor.Blue -> colorResource(R.color.medication_foreground_blue)
    MedicationColor.Indigo -> colorResource(R.color.medication_foreground_indigo)
    MedicationColor.Purple -> colorResource(R.color.medication_foreground_purple)
    MedicationColor.Monochrome -> colorResource(R.color.medication_foreground_monochrome)
}

/**
 * Converts MedicationColor enum to Compose Color for background elements.
 *
 * Automatically adapts to light/dark theme via Android's resource qualifier system:
 * - Light mode: uses lighter pastel shades (from values/colors.xml)
 * - Dark mode: uses darker pastel shades (from values-night/colors.xml)
 *
 * This provides better contrast with foreground shapes.
 */
@Composable
@ReadOnlyComposable
fun MedicationColor.toBackgroundColor(): Color = when (this) {
    MedicationColor.Rose -> colorResource(R.color.medication_background_rose)
    MedicationColor.Pink -> colorResource(R.color.medication_background_pink)
    MedicationColor.Red -> colorResource(R.color.medication_background_red)
    MedicationColor.Orange -> colorResource(R.color.medication_background_orange)
    MedicationColor.Yellow -> colorResource(R.color.medication_background_yellow)
    MedicationColor.Chartreuse -> colorResource(R.color.medication_background_chartreuse)
    MedicationColor.Green -> colorResource(R.color.medication_background_green)
    MedicationColor.Teal -> colorResource(R.color.medication_background_teal)
    MedicationColor.Cyan -> colorResource(R.color.medication_background_cyan)
    MedicationColor.Blue -> colorResource(R.color.medication_background_blue)
    MedicationColor.Indigo -> colorResource(R.color.medication_background_indigo)
    MedicationColor.Purple -> colorResource(R.color.medication_background_purple)
    MedicationColor.Monochrome -> colorResource(R.color.medication_background_monochrome)
}
