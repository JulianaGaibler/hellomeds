// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Medication color variants for pill/medication visual representation.
 *
 * Each color variant has foreground and background colors for both light and dark themes.
 * These colors are used throughout the app to represent different medication types/categories.
 *
 * Available colors: Rose, Pink, Red, Orange, Yellow, Chartreuse, Green, Teal, Cyan, Blue, Indigo, Purple, Monochrome
 */
@Immutable
sealed class MedicationColor(
    val foregroundLight: Color,
    val backgroundLight: Color,
    val foregroundDark: Color,
    val backgroundDark: Color,
) {
    /**
     * Get the appropriate foreground color based on current theme
     */
    @Composable
    @ReadOnlyComposable
    fun foreground(isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme()): Color {
        return if (isDark) foregroundDark else foregroundLight
    }

    /**
     * Get the appropriate background color based on current theme
     */
    @Composable
    @ReadOnlyComposable
    fun background(isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme()): Color {
        return if (isDark) backgroundDark else backgroundLight
    }

    data object Rose : MedicationColor(
        foregroundLight = Color(0xFF8F4A56),
        backgroundLight = Color(0xFFFDA6B3),
        foregroundDark = Color(0xFFFFB2BD),
        backgroundDark = Color(0xFF72333F),
    )

    data object Pink : MedicationColor(
        foregroundLight = Color(0xFF8A4B68),
        backgroundLight = Color(0xFFFEB0D1),
        foregroundDark = Color(0xFFFEB0D1),
        backgroundDark = Color(0xFF6D3350),
    )

    data object Red : MedicationColor(
        foregroundLight = Color(0xFF914B43),
        backgroundLight = Color(0xFFFDA499),
        foregroundDark = Color(0xFFFFA79B),
        backgroundDark = Color(0xFF73342C),
    )

    data object Orange : MedicationColor(
        foregroundLight = Color(0xFF8D4F28),
        backgroundLight = Color(0xFFFFAE7F),
        foregroundDark = Color(0xFFFFB68C),
        backgroundDark = Color(0xFF6F3812),
    )

    data object Yellow : MedicationColor(
        foregroundLight = Color(0xFF795A0C),
        backgroundLight = Color(0xFFFFD57D),
        foregroundDark = Color(0xFFE9C16C),
        backgroundDark = Color(0xFF6D5000),
    )

    data object Chartreuse : MedicationColor(
        foregroundLight = Color(0xFF5C641E),
        backgroundLight = Color(0xFFDDE692),
        foregroundDark = Color(0xFFC3CD7C),
        backgroundDark = Color(0xFF434B06),
    )

    data object Green : MedicationColor(
        foregroundLight = Color(0xFF376A3E),
        backgroundLight = Color(0xFFB7F1B9),
        foregroundDark = Color(0xFF9CD49F),
        backgroundDark = Color(0xFF1D5128),
    )

    data object Teal : MedicationColor(
        foregroundLight = Color(0xFF006B60),
        backgroundLight = Color(0xFF9EF2E3),
        foregroundDark = Color(0xFF82D5C7),
        backgroundDark = Color(0xFF006B5F),
    )

    data object Cyan : MedicationColor(
        foregroundLight = Color(0xFF01687D),
        backgroundLight = Color(0xFF9AE5FD),
        foregroundDark = Color(0xFF86D1EA),
        backgroundDark = Color(0xFF00677C),
    )

    data object Blue : MedicationColor(
        foregroundLight = Color(0xFF485E92),
        backgroundLight = Color(0xFFADC3FE),
        foregroundDark = Color(0xFFB0C6FF),
        backgroundDark = Color(0xFF2E4578),
    )

    data object Indigo : MedicationColor(
        foregroundLight = Color(0xFF5B5992),
        backgroundLight = Color(0xFFC1BDFE),
        foregroundDark = Color(0xFFC5C0FF),
        backgroundDark = Color(0xFF444078),
    )

    data object Purple : MedicationColor(
        foregroundLight = Color(0xFF6B548D),
        backgroundLight = Color(0xFFD9BDFE),
        foregroundDark = Color(0xFFD6BBFB),
        backgroundDark = Color(0xFF523C73),
    )

    data object Monochrome : MedicationColor(
        foregroundLight = Color(0xFFFFFFFF),
        backgroundLight = Color(0xFFBDBDBD),
        foregroundDark = Color(0xFFFFFFFF),
        backgroundDark = Color(0xFFD4D4D4),
    )

    companion object {
        /**
         * All available medication colors
         */
        val all: List<MedicationColor> = listOf(
            Rose,
            Pink,
            Red,
            Orange,
            Yellow,
            Chartreuse,
            Green,
            Teal,
            Cyan,
            Blue,
            Indigo,
            Purple,
            Monochrome,
        )

        /**
         * Get color by name (case-insensitive)
         */
        fun fromName(name: String): MedicationColor? {
            return when (name.uppercase()) {
                "ROSE" -> Rose
                "PINK" -> Pink
                "RED" -> Red
                "ORANGE" -> Orange
                "YELLOW" -> Yellow
                "CHARTREUSE" -> Chartreuse
                "GREEN" -> Green
                "TEAL" -> Teal
                "CYAN" -> Cyan
                "BLUE" -> Blue
                "INDIGO" -> Indigo
                "PURPLE" -> Purple
                "MONOCHROME" -> Monochrome
                else -> null
            }
        }
    }
}
