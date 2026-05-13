// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Applies dynamic corner rounding during predictive back gestures.
 *
 * This modifier creates the Android 14+ predictive back visual effect where:
 * - Screens get rounded corners during the back swipe gesture
 * - Corner radius lerps from 0dp -> 32dp based on gesture progress
 * - On devices with hardware rounded corners > 12dp, uses a fixed 32dp radius
 * - On devices with square corners, animates the corners during the gesture
 *
 * @param progress Gesture progress (0.0 = no gesture, 1.0 = fully swiped back)
 *                 In practice, this comes from the transition state during animations
 *
 * Usage in screen composables:
 * ```kotlin
 * Box(
 *   modifier = Modifier
 *     .fillMaxSize()
 *     .predictiveBackCorners(transitionProgress)
 * ) {
 *   // Screen content
 * }
 * ```
 */
@Composable
fun Modifier.predictiveBackCorners(progress: Float = 0f): Modifier {
    val density = LocalDensity.current

    // Get hardware display cutout (includes corner radius on modern devices)
    val displayCutout = WindowInsets.displayCutout

    // Calculate hardware corner radius
    // Display cutout top inset often indicates rounded corners on modern devices
    val hardwareCornerRadius = remember(displayCutout) {
        with(density) {
            // If device has significant display cutout, it likely has rounded corners
            // Modern devices (Pixel, Samsung) typically have 32-48dp corner radius
            val topInset = displayCutout.getTop(density)
            if (topInset > 12.dp.toPx()) {
                32.dp // Match or approximate hardware corners
            } else {
                0.dp // Square display
            }
        }
    }

    return if (hardwareCornerRadius > 12.dp) {
        // Device has rounded hardware corners - use fixed radius
        this.clip(RoundedCornerShape(32.dp))
    } else {
        // Device has square corners - animate radius based on gesture progress
        val animatedRadius = lerp(0f, 32f, progress).dp

        this.graphicsLayer {
            clip = true
            shape = RoundedCornerShape(animatedRadius)
        }
    }
}

/**
 * Alternative version that always animates corners regardless of hardware.
 * Useful for overlay screens that should always show rounded corners during transitions.
 */
@Composable
fun Modifier.animatedBackCorners(progress: Float = 0f): Modifier {
    val animatedRadius = lerp(0f, 32f, progress).dp

    return this.graphicsLayer {
        clip = true
        shape = RoundedCornerShape(animatedRadius)
    }
}

/**
 * Fixed corner radius for screens that should always have rounded corners.
 * Useful for modal overlays that don't participate in gesture animations.
 */
fun Modifier.fixedBackCorners(radius: Float = 32f): Modifier {
    return this.clip(RoundedCornerShape(radius.dp))
}
