// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import me.juliana.hellomeds.util.camera.CoordinateTransformer

/**
 * Debug overlay that visualizes coordinate transformations.
 * Shows:
 * - Red rectangle: Visible bitmap area (what's shown on screen after FILL_CENTER crop)
 * - Green rectangle: Detected object bounding box
 * - Blue crosshair: Screen center
 * - Yellow text: Screen dimensions and scale factor
 */
@Composable
fun DebugOverlay(transformer: CoordinateTransformer?, detectedBox: Rect?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        transformer?.let { t ->
            // Draw visible bitmap bounds (red rectangle)
            val visibleTopLeft = t.bitmapToScreen(
                t.visibleBitmapLeft.toFloat(),
                t.visibleBitmapTop.toFloat(),
            )
            val visibleBottomRight = t.bitmapToScreen(
                t.visibleBitmapRight.toFloat(),
                t.visibleBitmapBottom.toFloat(),
            )

            drawRect(
                color = Color.Red,
                topLeft = Offset(visibleTopLeft.first, visibleTopLeft.second),
                size = Size(
                    visibleBottomRight.first - visibleTopLeft.first,
                    visibleBottomRight.second - visibleTopLeft.second,
                ),
                style = Stroke(width = 4f),
            )

            // Draw detected object box in bitmap space (green rectangle)
            detectedBox?.let { box ->
                val screenRect = t.bitmapRectToScreen(box)
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(screenRect.left, screenRect.top),
                    size = Size(screenRect.width(), screenRect.height()),
                    style = Stroke(width = 6f),
                )
            }

            // Draw screen center crosshair (blue)
            drawLine(
                color = Color.Blue,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = 2f,
            )
            drawLine(
                color = Color.Blue,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2f,
            )

            // Draw info text (yellow)
            val paint = Paint().apply {
                color = android.graphics.Color.YELLOW
                textSize = 40f
                isAntiAlias = true
            }

            drawContext.canvas.nativeCanvas.drawText(
                "Screen: ${size.width.toInt()}x${size.height.toInt()}",
                20f,
                60f,
                paint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Scale: ${"%.2f".format(t.scale)} (${t.scaleX.format(2)} x ${t.scaleY.format(2)})",
                20f,
                110f,
                paint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Offset: (${t.offsetX.format(1)}, ${t.offsetY.format(1)})",
                20f,
                160f,
                paint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Visible: [${t.visibleBitmapLeft},${t.visibleBitmapTop}-${t.visibleBitmapRight},${t.visibleBitmapBottom}]",
                20f,
                210f,
                paint,
            )
        }
    }
}
