// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.components

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ethereal, dreamy animated background for onboarding screens.
 * Creates a soft, Material You-style effect with slowly rotating and breathing shapes.
 */
@Composable
fun OnboardingBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface) // Base color
            .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 60.dp else 0.dp), // The "Dreamy" effect
    ) {
        // Shape 1: Top Left - Primary Color (5-pointed star)
        FloatingShape(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedPolygonShape(sides = 5),
            size = 300.dp,
            offsetX = (-50).dp,
            offsetY = (-50).dp,
            animationDuration = 20000,
        )

        // Shape 2: Bottom Right - Tertiary Color (squircle)
        FloatingShape(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedPolygonShape(sides = 4),
            size = 350.dp,
            align = Alignment.BottomEnd,
            offsetX = 50.dp,
            offsetY = 50.dp,
            animationDuration = 25000,
            rotationDirection = -1f, // Counter-clockwise
        )

        // Shape 3: Center Left - Secondary Color (subtle circle)
        FloatingShape(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape,
            size = 200.dp,
            align = Alignment.CenterStart,
            offsetX = (-80).dp,
            offsetY = 100.dp,
            animationDuration = 30000,
            alpha = 0.5f,
        )
    }
}

@Composable
private fun BoxScope.FloatingShape(
    color: Color,
    shape: Shape,
    size: Dp,
    align: Alignment = Alignment.TopStart,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    animationDuration: Int,
    rotationDirection: Float = 1f, // 1f or -1f
    alpha: Float = 0.6f,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shape_anim")

    // Rotation Animation - very slow, subtle
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f * rotationDirection,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    // Breathing (Scale) Animation - creates organic "pulsing" effect
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDuration / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Box(
        modifier = Modifier
            .align(align)
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .rotate(rotation)
            .scale(scale)
            .alpha(alpha)
            .background(color, shape),
    )
}
