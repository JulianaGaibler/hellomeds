// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.sp

private fun TextStyle.withHyphenation() = copy(hyphens = Hyphens.Auto)

val Typography = Typography().let { default ->
    Typography(
        displayLarge = default.displayLarge.withHyphenation(),
        displayMedium = default.displayMedium.withHyphenation(),
        displaySmall = default.displaySmall.withHyphenation(),
        headlineLarge = default.headlineLarge.withHyphenation(),
        headlineMedium = default.headlineMedium.withHyphenation(),
        headlineSmall = default.headlineSmall.withHyphenation(),
        titleLarge = default.titleLarge.withHyphenation(),
        titleMedium = default.titleMedium.withHyphenation(),
        titleSmall = default.titleSmall.withHyphenation(),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
            hyphens = Hyphens.Auto,
        ),
        bodyMedium = default.bodyMedium.withHyphenation(),
        bodySmall = default.bodySmall.withHyphenation(),
        labelLarge = default.labelLarge.withHyphenation(),
        labelMedium = default.labelMedium.withHyphenation(),
        labelSmall = default.labelSmall.withHyphenation(),
    )
}
