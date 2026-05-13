// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.graph.drawing

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.StockDataPoint
import me.juliana.hellomeds.data.model.StockEvent
import me.juliana.hellomeds.ui.components.graph.models.StockLine
import me.juliana.hellomeds.ui.components.graph.util.TimeFormatter

// ── Future Region Background ─────────────────────────────────────────

fun DrawScope.drawFutureRegion(cs: GraphCoordinateSystem, currentTime: Long, color: Color) {
    val currentX = cs.timeToX(currentTime)
    val rightEdge = cs.canvasWidth - cs.padding.right
    val clampedX = currentX.coerceAtLeast(cs.padding.left)
    val futureWidth = rightEdge - clampedX

    if (futureWidth > 0) {
        drawRoundRect(
            color = color,
            topLeft = Offset(clampedX, cs.padding.top),
            size = Size(futureWidth, cs.drawableHeight),
            cornerRadius = CornerRadius(24.dp.toPx()),
        )
    }
}

// ── Grid Lines ───────────────────────────────────────────────────────

fun DrawScope.drawGrid(
    cs: GraphCoordinateSystem,
    zoomLevel: me.juliana.hellomeds.ui.components.graph.models.ZoomLevel,
) {
    val gridColor = Color.Gray.copy(alpha = 0.2f)

    // Horizontal grid lines (evenly spaced)
    val numLines = 5
    for (i in 0..numLines) {
        val y = cs.padding.top + (cs.drawableHeight * i / numLines)
        drawLine(
            color = gridColor,
            start = Offset(cs.padding.left, y),
            end = Offset(cs.canvasWidth - cs.padding.right, y),
            strokeWidth = 1f,
        )
    }

    // Vertical grid lines — time-aligned, scrolling with content
    val gridBuffer = cs.drawableWidth * 0.15f
    val gridVisibleRange =
        cs.xToTime(cs.padding.left - gridBuffer)..cs.xToTime(cs.canvasWidth - cs.padding.right + gridBuffer)
    val labelPositions = TimeFormatter.calculateLabelPositions(gridVisibleRange, zoomLevel)
    for (timestamp in labelPositions) {
        val x = cs.timeToX(timestamp)
        drawLine(
            color = gridColor,
            start = Offset(x, cs.padding.top),
            end = Offset(x, cs.canvasHeight - cs.padding.bottom),
            strokeWidth = 1f,
        )
    }
}

// ── Uncertainty Band (step-style) ────────────────────────────────────

fun DrawScope.drawUncertaintyBand(
    cs: GraphCoordinateSystem,
    lowerPoints: List<StockDataPoint>,
    upperPoints: List<StockDataPoint>,
    lineIndex: Int,
    bandPath: Path,
    color: Color,
) {
    if (lowerPoints.size < 2 || upperPoints.size < 2) return

    bandPath.reset()

    // Forward along upper bound (step-style)
    val firstUpper = upperPoints.first()
    bandPath.moveTo(
        cs.timeToX(firstUpper.timestamp),
        cs.valueToY(firstUpper.value, lineIndex),
    )
    for (i in 1 until upperPoints.size) {
        val prev = upperPoints[i - 1]
        val curr = upperPoints[i]
        val currX = cs.timeToX(curr.timestamp)
        val currY = cs.valueToY(curr.value, lineIndex)
        val prevY = cs.valueToY(prev.value, lineIndex)
        bandPath.lineTo(currX, prevY)
        bandPath.lineTo(currX, currY)
    }

    // Backward along lower bound (step-style)
    for (i in lowerPoints.size - 1 downTo 0) {
        val point = lowerPoints[i]
        val x = cs.timeToX(point.timestamp)
        val y = cs.valueToY(point.value, lineIndex)
        if (i < lowerPoints.size - 1) {
            val nextPoint = lowerPoints[i + 1]
            val nextY = cs.valueToY(nextPoint.value, lineIndex)
            bandPath.lineTo(x, nextY)
        }
        bandPath.lineTo(x, y)
    }

    bandPath.close()
    drawPath(bandPath, color)
}

// ── Step Line ────────────────────────────────────────────────────────

fun DrawScope.drawStepLine(
    cs: GraphCoordinateSystem,
    points: List<StockDataPoint>,
    lineIndex: Int,
    linePath: Path,
    color: Color,
    isFuture: Boolean,
) {
    if (points.isEmpty()) return

    linePath.reset()

    points.forEachIndexed { index, point ->
        val x = cs.timeToX(point.timestamp)
        val y = cs.valueToY(point.value, lineIndex)

        if (index == 0) {
            linePath.moveTo(x, y)
        } else {
            val prevPoint = points[index - 1]
            val prevY = cs.valueToY(prevPoint.value, lineIndex)
            // Step: horizontal first (maintains previous value), then vertical
            linePath.lineTo(x, prevY)
            linePath.lineTo(x, y)
        }
    }

    val pathEffect = if (isFuture) {
        PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    } else {
        null
    }

    drawPath(
        path = linePath,
        color = color,
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = pathEffect,
            cap = StrokeCap.Round,
        ),
    )
}

// ── Container Transition Markers ─────────────────────────────────────

fun DrawScope.drawContainerTransitions(cs: GraphCoordinateSystem, line: StockLine, color: Color) {
    val transitionColor = color.copy(alpha = 0.4f)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))

    line.futurePoints
        .filter { it.event is StockEvent.ContainerSwitch }
        .forEach { point ->
            val x = cs.timeToX(point.timestamp)
            drawLine(
                color = transitionColor,
                start = Offset(x, cs.padding.top),
                end = Offset(x, cs.canvasHeight - cs.padding.bottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect,
            )
        }
}

// ── Data Point Markers (white outer + colored inner) ─────────────────

fun DrawScope.drawDataPointMarkers(
    cs: GraphCoordinateSystem,
    points: List<StockDataPoint>,
    lineIndex: Int,
    color: Color,
) {
    points.forEach { point ->
        val x = cs.timeToX(point.timestamp)
        val y = cs.valueToY(point.value, lineIndex)

        // White outer circle
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = Offset(x, y),
        )
        // Colored inner circle
        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = Offset(x, y),
        )
    }
}

// ── X-Axis Labels (scroll-aware) ─────────────────────────────────────

fun DrawScope.drawXAxisLabels(
    cs: GraphCoordinateSystem,
    zoomLevel: me.juliana.hellomeds.ui.components.graph.models.ZoomLevel,
    labelLayouts: Map<Long, TextLayoutResult>,
    color: Color,
) {
    val labelY = cs.canvasHeight - 8f

    // Compute scroll-adjusted visible range with buffer
    val labelBuffer = cs.drawableWidth * 0.15f
    val actualVisibleRange =
        cs.xToTime(cs.padding.left - labelBuffer)..cs.xToTime(cs.canvasWidth - cs.padding.right + labelBuffer)
    val labelPositions = TimeFormatter.calculateLabelPositions(actualVisibleRange, zoomLevel)

    for (timestamp in labelPositions) {
        val x = cs.timeToX(timestamp)
        val layout = labelLayouts[timestamp] ?: continue
        drawText(
            textLayoutResult = layout,
            color = color,
            topLeft = Offset(x - layout.size.width / 2f, labelY - layout.size.height / 2f),
        )
    }
}

// ── Y-Axis Labels ────────────────────────────────────────────────────

fun DrawScope.drawYAxisLabels(
    cs: GraphCoordinateSystem,
    labelLayouts: Map<Double, TextLayoutResult>,
    lineIndex: Int,
    color: Color,
    rightSide: Boolean = false,
) {
    labelLayouts.forEach { (value, layout) ->
        val y = cs.valueToY(value, lineIndex)
        val textY = y - layout.size.height / 2f
        if (textY >= cs.padding.top - layout.size.height &&
            textY <= cs.canvasHeight - cs.padding.bottom
        ) {
            val x = if (rightSide) {
                cs.canvasWidth - cs.padding.right + 8f
            } else {
                cs.padding.left - layout.size.width - 8f
            }
            drawText(
                textLayoutResult = layout,
                color = color,
                topLeft = Offset(x, textY),
            )
        }
    }
}

// ── Event Bubble ("N changes") ───────────────────────────────────────

fun DrawScope.drawEventBubble(
    cs: GraphCoordinateSystem,
    point: StockDataPoint,
    lineIndex: Int,
    bubbleLayout: TextLayoutResult,
    bubbleColor: Color,
    bubbleTextColor: Color,
    caretPath: Path,
) {
    val x = cs.timeToX(point.timestamp)
    val y = cs.valueToY(point.value, lineIndex)

    val padH = 12.dp.toPx()
    val padV = 6.dp.toPx()
    val bubbleW = bubbleLayout.size.width + padH * 2
    val bubbleH = bubbleLayout.size.height + padV * 2
    val cornerRadius = 14.dp.toPx()
    val caretHeight = 6.dp.toPx()
    val gap = 8.dp.toPx()

    // Position above point by default
    var pillY = y - bubbleH - gap - caretHeight
    val caretBelow = pillY >= cs.padding.top

    // If too close to top, flip below the point
    if (!caretBelow) {
        pillY = y + gap
    }

    val pillLeft = x - bubbleW / 2f

    // Pill background
    drawRoundRect(
        color = bubbleColor,
        topLeft = Offset(pillLeft, pillY),
        size = Size(bubbleW, bubbleH),
        cornerRadius = CornerRadius(cornerRadius),
    )

    // Caret triangle (reuse hoisted path to avoid per-frame allocation)
    caretPath.rewind()
    if (caretBelow) {
        caretPath.moveTo(x - 5.dp.toPx(), pillY + bubbleH)
        caretPath.lineTo(x, pillY + bubbleH + caretHeight)
        caretPath.lineTo(x + 5.dp.toPx(), pillY + bubbleH)
    } else {
        caretPath.moveTo(x - 5.dp.toPx(), pillY)
        caretPath.lineTo(x, pillY - caretHeight)
        caretPath.lineTo(x + 5.dp.toPx(), pillY)
    }
    caretPath.close()
    drawPath(caretPath, bubbleColor)

    // Text
    drawText(
        textLayoutResult = bubbleLayout,
        color = bubbleTextColor,
        topLeft = Offset(pillLeft + padH, pillY + padV),
    )
}

// ── Clipped Content Drawing ──────────────────────────────────────────

fun DrawScope.drawClippedContent(cs: GraphCoordinateSystem, reusablePath: Path, block: DrawScope.() -> Unit) {
    reusablePath.rewind()
    reusablePath.addRoundRect(
        RoundRect(
            left = cs.padding.left,
            top = cs.padding.top,
            right = cs.canvasWidth - cs.padding.right,
            bottom = cs.canvasHeight - cs.padding.bottom,
            cornerRadius = CornerRadius(24.dp.toPx()),
        ),
    )
    clipPath(reusablePath) {
        block()
    }
}
