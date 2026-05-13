// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the "Cookie12Sided" (Top Right) and "Pill" (Bottom Left) background shapes
 * for the onboarding screens with subtle animations.
 *
 * This modifier is screen-size independent and uses mathematical functions to
 * create smooth, scalable shapes that match the design specifications.
 *
 * Animations:
 * - Cookie rotates slowly (full rotation every 60 seconds)
 * - Pill translates up and down slowly (±5% range, 8 seconds per cycle)
 *
 * @param tertiaryColor Color for the top-right Cookie12Sided shape
 * @param primaryColor Color for the bottom-left Pill shape
 */
@Composable
fun Modifier.onboardingBackgroundShapes(tertiaryColor: Color, primaryColor: Color): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding_shapes")

    // Very slow rotation for the cookie (360° over 2 minutes)
    val cookieRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(120000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cookie_rotation",
    )

    // Slow up/down translation for the pill (±2.5% over 16 seconds with gentle ease)
    val pillYOffset by infiniteTransition.animateFloat(
        initialValue = -0.025f,
        targetValue = 0.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pill_translation",
    )

    // Pre-allocate path for reuse — rebuilt only when canvas size changes
    val cookiePath = remember { Path() }
    var lastCookieRadius by remember { mutableFloatStateOf(0f) }

    return this.drawBehind {
        val width = size.width
        val height = size.height
        val cookieRadius = width / 2

        // Rebuild cookie path only when size changes (not every frame)
        if (cookieRadius != lastCookieRadius) {
            lastCookieRadius = cookieRadius
            cookiePath.rewind()
            val points = 12
            for (i in 0..360) {
                val theta = i.toDouble() * kotlin.math.PI / 180.0
                val r = cookieRadius + (cookieRadius * 0.05f) * sin(points * theta)
                val x = (cookieRadius + r * cos(theta)).toFloat()
                val y = (cookieRadius + r * sin(theta)).toFloat()
                if (i == 0) cookiePath.moveTo(x, y) else cookiePath.lineTo(x, y)
            }
            cookiePath.close()
        }

        // Draw cookie with pre-built path — only rotation changes per frame
        translate(left = width * 0.25f, top = -height * 0.20f) {
            rotate(degrees = cookieRotation, pivot = Offset(cookieRadius, cookieRadius)) {
                drawPath(
                    path = cookiePath,
                    color = tertiaryColor,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // Pill (already cheap — just a roundRect, no path generation needed)
        val pillWidth = width * 1.4f
        val pillHeight = pillWidth * 1.2f
        val pillOffsetX = width * 0.35f
        val pillOffsetY = height * 0.85f + (height * pillYOffset)

        translate(left = pillOffsetX, top = pillOffsetY) {
            rotate(degrees = -65f, pivot = Offset.Zero) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(-pillWidth / 2, -pillHeight / 2),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillWidth / 2),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}
