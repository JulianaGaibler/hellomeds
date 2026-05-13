// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun systemContrastLevel(): ContrastLevel {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        return ContrastLevel.Standard
    }

    val context = LocalContext.current
    val uiModeManager = context.applicationContext
        .getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        ?: return ContrastLevel.Standard

    // Contrast changes trigger Activity recreation on Android 14+,
    // so reading the value at composition time is sufficient for live updates.
    val contrast = uiModeManager.contrast
    return when {
        contrast >= 0.66f -> ContrastLevel.High
        contrast >= 0.33f -> ContrastLevel.Medium
        else -> ContrastLevel.Standard
    }
}

@Composable
actual fun resolveColorScheme(darkTheme: Boolean, dynamicColor: Boolean, contrastLevel: ContrastLevel): ColorScheme {
    return when {
        // Dynamic colors handle contrast automatically on Android 14+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> when (contrastLevel) {
            ContrastLevel.High -> applyStableContainers(highContrastDarkColorScheme, true)
            ContrastLevel.Medium -> applyStableContainers(mediumContrastDarkColorScheme, true)
            ContrastLevel.Standard -> darkScheme
        }

        else -> when (contrastLevel) {
            ContrastLevel.High -> applyStableContainers(highContrastLightColorScheme, false)
            ContrastLevel.Medium -> applyStableContainers(mediumContrastLightColorScheme, false)
            ContrastLevel.Standard -> lightScheme
        }
    }
}
