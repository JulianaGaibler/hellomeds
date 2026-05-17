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
 * Subtle rounded corners on overlay screens. At rest the corners hide behind status bars or device
 * hardware curvature; during the predictive-back shrink they're revealed, giving the overlay a
 * floating-card look.
 */
@Composable
fun OverlayScreenWrapper(cornerRadius: Float = 16f, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius.dp)),
        color = MaterialTheme.colorScheme.background,
    ) {
        content()
    }
}

/** Larger 28dp corners for tablet/desktop where the screen isn't flush to the device edge. */
@Composable
fun OverlayScreenWrapperLarge(content: @Composable () -> Unit) {
    OverlayScreenWrapper(cornerRadius = 28f, content = content)
}

/** Square-cornered variant for camera or other immersive screens. */
@Composable
fun OverlayScreenWrapperSquare(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        content()
    }
}
