// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Static reticle overlay shown during live scanning mode.
 * Displays a centered 300dp×300dp reticle with rounded corners.
 * Stroke width and alpha animate based on object detection state.
 */
@Composable
fun StaticReticleOverlay(strokeWidth: Float, alpha: Float = 1f, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val reticleSize = Size(300.dp.toPx(), 300.dp.toPx())
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val left = centerX - reticleSize.width / 2f
        val top = centerY - reticleSize.height / 2f
        val right = left + reticleSize.width
        val bottom = top + reticleSize.height

        val cornerLength = 40.dp.toPx()
        val cornerRadius = 40.dp.toPx()
        val strokeWidthPx = strokeWidth.dp.toPx()

        val color = Color.White.copy(alpha = alpha)

        // Top-left corner
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidthPx),
        )
        drawLine(
            color = color,
            start = Offset(left + cornerRadius, top),
            end = Offset(left + cornerLength, top),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(left, top + cornerRadius),
            end = Offset(left, top + cornerLength),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )

        // Top-right corner
        drawArc(
            color = color,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(right - cornerRadius * 2, top),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidthPx),
        )
        drawLine(
            color = color,
            start = Offset(right - cornerLength, top),
            end = Offset(right - cornerRadius, top),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(right, top + cornerRadius),
            end = Offset(right, top + cornerLength),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )

        // Bottom-left corner
        drawArc(
            color = color,
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left, bottom - cornerRadius * 2),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidthPx),
        )
        drawLine(
            color = color,
            start = Offset(left, bottom - cornerLength),
            end = Offset(left, bottom - cornerRadius),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(left + cornerRadius, bottom),
            end = Offset(left + cornerLength, bottom),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )

        // Bottom-right corner
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(right - cornerRadius * 2, bottom - cornerRadius * 2),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidthPx),
        )
        drawLine(
            color = color,
            start = Offset(right, bottom - cornerLength),
            end = Offset(right, bottom - cornerRadius),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(right - cornerLength, bottom),
            end = Offset(right - cornerRadius, bottom),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
    }
}
