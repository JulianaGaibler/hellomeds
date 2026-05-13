// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Wrapper for overlay screens that adds subtle rounded corners.
 *
 * **Purpose:**
 * On full-screen phones, 16dp corners are barely noticeable (hidden by status bars
 * or device hardware corners). However, during the predictive back shrink animation,
 * these rounded corners become visible and create a polished "card" appearance.
 *
 * **Visual Effect:**
 * - At rest: Corners blend with device hardware or status bar
 * - During back gesture: Screen shrinks revealing elegant rounded corners
 * - Gives overlays a "floating card" appearance during transitions
 *
 * **Usage:**
 * ```kotlin
 * entry<SettingsRoute> {
 *   OverlayScreenWrapper {
 *     SettingsScreen(...)
 *   }
 * }
 * ```
 *
 * @param cornerRadius Corner radius in dp (default 16dp for subtle effect)
 * @param content The overlay screen composable
 */
@Composable
fun OverlayScreenWrapper(cornerRadius: Float = 16f, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            // Clip to rounded corners
            // At full size: barely noticeable
            // During scale animation: creates polished card appearance
            .clip(RoundedCornerShape(cornerRadius.dp)),
        color = MaterialTheme.colorScheme.background,
    ) {
        content()
    }
}

/**
 * Alternative wrapper with more pronounced corners for tablet/desktop.
 *
 * Uses 28dp corners which are more appropriate for larger screens where
 * the screen doesn't fill the entire display edge-to-edge.
 */
@Composable
fun OverlayScreenWrapperLarge(content: @Composable () -> Unit) {
    OverlayScreenWrapper(cornerRadius = 28f, content = content)
}

/**
 * No-corner version for screens that should remain square.
 * Useful for camera or immersive experiences.
 */
@Composable
fun OverlayScreenWrapperSquare(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        content()
    }
}
