// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Draggable crop reticle overlay for the camera detection screen.
 * Manages internal bounds state to avoid stale closure issues with drag gestures.
 *
 * CRITICAL: Do NOT pass any rapidly-changing value as a key to pointerInput or remember,
 * or the gesture handler will restart mid-drag causing "twitching".
 */
@Composable
fun CropReticleOverlay(
    initialLeft: Float,
    initialTop: Float,
    initialRight: Float,
    initialBottom: Float,
    isEditable: Boolean = true,
    topInset: Float = 0f,
    onBoundsChanged: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val minSizePx = with(density) { 100.dp.toPx() }
    val cornerTouchPx = with(density) { 60.dp.toPx() }
    val cornerRadiusPx = with(density) { 40.dp.toPx() }
    val strokeWidthPx = with(density) { 12.dp.toPx() }
    val cornerLinePx = with(density) { 4.dp.toPx() }

    // Internal mutable state — NO key parameter!
    // Reinitialize only via LaunchedEffect when initial values change significantly
    var left by remember { mutableFloatStateOf(initialLeft) }
    var top by remember { mutableFloatStateOf(initialTop) }
    var right by remember { mutableFloatStateOf(initialRight) }
    var bottom by remember { mutableFloatStateOf(initialBottom) }
    var dragAction by remember { mutableStateOf<String?>(null) }

    // Update bounds when initial values change (e.g., new shutter press)
    LaunchedEffect(initialLeft, initialTop, initialRight, initialBottom) {
        left = initialLeft
        top = initialTop
        right = initialRight
        bottom = initialBottom
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            // ONLY key on isEditable — never on a version counter!
            .pointerInput(isEditable) {
                if (!isEditable) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        onDragStart()
                        dragAction = when {
                            offset.x < left + cornerTouchPx && offset.y < top + cornerTouchPx -> "TL"
                            offset.x > right - cornerTouchPx && offset.y < top + cornerTouchPx -> "TR"
                            offset.x < left + cornerTouchPx && offset.y > bottom - cornerTouchPx -> "BL"
                            offset.x > right - cornerTouchPx && offset.y > bottom - cornerTouchPx -> "BR"
                            offset.x in left..right && offset.y in top..bottom -> "MOVE"
                            else -> null
                        }
                    },
                    onDragEnd = {
                        dragAction = null
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragAction = null
                        onDragEnd()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val maxW = size.width.toFloat()
                        val maxH = size.height.toFloat()

                        when (dragAction) {
                            "TL" -> {
                                left = (left + dragAmount.x).coerceIn(0f, right - minSizePx)
                                top = (top + dragAmount.y).coerceIn(topInset, bottom - minSizePx)
                            }

                            "TR" -> {
                                right = (right + dragAmount.x).coerceIn(left + minSizePx, maxW)
                                top = (top + dragAmount.y).coerceIn(topInset, bottom - minSizePx)
                            }

                            "BL" -> {
                                left = (left + dragAmount.x).coerceIn(0f, right - minSizePx)
                                bottom = (bottom + dragAmount.y).coerceIn(top + minSizePx, maxH)
                            }

                            "BR" -> {
                                right = (right + dragAmount.x).coerceIn(left + minSizePx, maxW)
                                bottom = (bottom + dragAmount.y).coerceIn(top + minSizePx, maxH)
                            }

                            "MOVE" -> {
                                val w = right - left
                                val h = bottom - top
                                val newL = (left + dragAmount.x).coerceIn(0f, maxW - w)
                                val newT = (top + dragAmount.y).coerceIn(topInset, maxH - h)
                                left = newL
                                top = newT
                                right = newL + w
                                bottom = newT + h
                            }
                        }
                        onBoundsChanged(left, top, right, bottom)
                    },
                )
            },
    ) {
        // Dimmed overlay
        drawRect(Color.Black.copy(alpha = 0.7f), size = size)

        // Clear hole
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(cornerRadiusPx),
            blendMode = BlendMode.Clear,
        )

        // Corner handles
        val c = if (isEditable) Color.White else Color.Gray
        val r = cornerRadiusPx
        val sw = strokeWidthPx
        val cl = cornerLinePx
        val st = Stroke(width = sw)

        // Top-left
        drawArc(c, 180f, 90f, false, Offset(left, top), Size(r * 2, r * 2), style = st)
        drawLine(c, Offset(left + r, top), Offset(left + r + cl, top), sw, StrokeCap.Round)
        drawLine(c, Offset(left, top + r), Offset(left, top + r + cl), sw, StrokeCap.Round)

        // Top-right
        drawArc(c, 270f, 90f, false, Offset(right - r * 2, top), Size(r * 2, r * 2), style = st)
        drawLine(c, Offset(right - r, top), Offset(right - r - cl, top), sw, StrokeCap.Round)
        drawLine(c, Offset(right, top + r), Offset(right, top + r + cl), sw, StrokeCap.Round)

        // Bottom-left
        drawArc(c, 90f, 90f, false, Offset(left, bottom - r * 2), Size(r * 2, r * 2), style = st)
        drawLine(c, Offset(left, bottom - r), Offset(left, bottom - r - cl), sw, StrokeCap.Round)
        drawLine(c, Offset(left + r, bottom), Offset(left + r + cl, bottom), sw, StrokeCap.Round)

        // Bottom-right
        drawArc(
            c,
            0f,
            90f,
            false,
            Offset(right - r * 2, bottom - r * 2),
            Size(r * 2, r * 2),
            style = st,
        )
        drawLine(c, Offset(right, bottom - r), Offset(right, bottom - r - cl), sw, StrokeCap.Round)
        drawLine(c, Offset(right - r, bottom), Offset(right - r - cl, bottom), sw, StrokeCap.Round)

        // Debug: bounds info
        // drawRect(Color.Red, Offset(left, top), Size(right - left, bottom - top), style = Stroke(2f))
    }
}
