// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Shared countdown overlay shown during the grace period after pressing the shutter.
 * Displays a big animated number (3→2→1) that scales in/out on each tick.
 * Used on both Android and iOS camera screens.
 */
@Composable
fun CountdownOverlay(secondsRemaining: Int, modifier: Modifier = Modifier) {
    if (secondsRemaining > 0) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = secondsRemaining,
                transitionSpec = {
                    (scaleIn(initialScale = 1.5f) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 0.5f) + fadeOut())
                },
            ) { count ->
                Text(
                    text = count.toString(),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}
