// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIAccessibilityDarkerSystemColorsStatusDidChangeNotification

@Composable
actual fun systemContrastLevel(): ContrastLevel {
    return produceState(
        initialValue = if (UIAccessibilityDarkerSystemColorsEnabled()) {
            ContrastLevel.High
        } else {
            ContrastLevel.Standard
        },
    ) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIAccessibilityDarkerSystemColorsStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            value = if (UIAccessibilityDarkerSystemColorsEnabled()) {
                ContrastLevel.High
            } else {
                ContrastLevel.Standard
            }
        }
        awaitDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }.value
}

@Composable
actual fun resolveColorScheme(darkTheme: Boolean, dynamicColor: Boolean, contrastLevel: ContrastLevel): ColorScheme {
    return when {
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
