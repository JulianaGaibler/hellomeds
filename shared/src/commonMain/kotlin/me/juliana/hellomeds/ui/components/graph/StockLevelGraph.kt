// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.model.StockDataPoint
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_next_data_point
import me.juliana.hellomeds.shared.accessibility_previous_data_point
import me.juliana.hellomeds.shared.content_description_stock_graph_detail
import me.juliana.hellomeds.shared.stock_graph_changes
import me.juliana.hellomeds.ui.components.graph.drawing.GraphCoordinateSystem
import me.juliana.hellomeds.ui.components.graph.drawing.GraphPadding
import me.juliana.hellomeds.ui.components.graph.drawing.drawClippedContent
import me.juliana.hellomeds.ui.components.graph.drawing.drawContainerTransitions
import me.juliana.hellomeds.ui.components.graph.drawing.drawDataPointMarkers
import me.juliana.hellomeds.ui.components.graph.drawing.drawEventBubble
import me.juliana.hellomeds.ui.components.graph.drawing.drawFutureRegion
import me.juliana.hellomeds.ui.components.graph.drawing.drawGrid
import me.juliana.hellomeds.ui.components.graph.drawing.drawStepLine
import me.juliana.hellomeds.ui.components.graph.drawing.drawUncertaintyBand
import me.juliana.hellomeds.ui.components.graph.drawing.drawXAxisLabels
import me.juliana.hellomeds.ui.components.graph.drawing.drawYAxisLabels
import me.juliana.hellomeds.ui.components.graph.gestures.graphPanGestures
import me.juliana.hellomeds.ui.components.graph.gestures.rememberGraphGestureState
import me.juliana.hellomeds.ui.components.graph.models.GraphConfig
import me.juliana.hellomeds.ui.components.graph.models.StockLine
import me.juliana.hellomeds.ui.components.graph.util.TimeFormatter
import me.juliana.hellomeds.ui.components.graph.util.ValueFormatter
import me.juliana.hellomeds.ui.theme.ContrastLevel
import me.juliana.hellomeds.ui.theme.LocalContrastLevel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun StockLevelGraph(
    lines: List<StockLine>,
    config: GraphConfig = GraphConfig(),
    onCenteredPointChanged: ((StockDataPoint?) -> Unit)? = null,
    onLivePointChanged: ((StockDataPoint?) -> Unit)? = null,
    centeredDayEventCount: Int = 0,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    futureRegionColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
    bubbleColor: Color = MaterialTheme.colorScheme.tertiary,
    bubbleTextColor: Color = MaterialTheme.colorScheme.onTertiary,
    axisLabelColor: Color = if (LocalContrastLevel.current == ContrastLevel.High) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    },
    modifier: Modifier = Modifier,
) {
    val currentTime = remember { Clock.System.now().toEpochMilliseconds() }
    val visibleTimeRange = remember(config.zoomLevel) {
        val halfRange = config.zoomLevel.millisVisible / 2
        (currentTime - halfRange)..(currentTime + halfRange)
    }

    // Track canvas size
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Coordinate system (null until layout)
    val coordinateSystem = remember(canvasSize, lines, visibleTimeRange) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            GraphCoordinateSystem(
                canvasWidth = canvasSize.width.toFloat(),
                canvasHeight = canvasSize.height.toFloat(),
                lines = lines,
                visibleTimeRange = visibleTimeRange,
                padding = GraphPadding(),
            )
        } else {
            null
        }
    }

    // Gesture state
    val coroutineScope = rememberCoroutineScope()
    val gestureState = coordinateSystem?.let {
        rememberGraphGestureState(
            coordinateSystem = it,
            snapToPoints = config.snapToPoints,
            onCenteredPointChanged = onCenteredPointChanged,
            onLivePointChanged = onLivePointChanged,
        )
    }

    // Dynamic Y-axis: adjusts range to visible points during scroll
    val dynamicLines by remember(lines) {
        derivedStateOf {
            val offset = gestureState?.scrollOffset ?: 0f
            val drawableW = (canvasSize.width - 120f).coerceAtLeast(1f)
            val timeSpan = visibleTimeRange.last - visibleTimeRange.first
            if (timeSpan == 0L) return@derivedStateOf lines
            val timeShift = (offset / drawableW * timeSpan).toLong()
            val adjustedRange =
                (visibleTimeRange.first - timeShift)..(visibleTimeRange.last - timeShift)

            lines.map { line ->
                val visiblePoints = line.dataPoints.filter { it.timestamp in adjustedRange }
                if (visiblePoints.isEmpty()) return@map line

                val visMin = visiblePoints.minOf { it.value }
                val visMax = visiblePoints.maxOf { it.value }
                val yMin = if (visMin < 0) visMin * 1.1 else 0.0
                val yMax = maxOf(visMax * 1.1, line.yAxisRange.second)
                val safeMax = if (yMax > yMin) yMax else yMin + 1.0
                line.copy(yAxisRange = yMin to safeMax)
            }
        }
    }

    // Text measurement
    val textMeasurer = rememberTextMeasurer()
    val axisTextStyle = MaterialTheme.typography.labelSmall
    val bubbleTextStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)

    // Pre-measure bubble text
    val bubbleText = if (centeredDayEventCount > 0) {
        pluralStringResource(Res.plurals.stock_graph_changes, centeredDayEventCount, centeredDayEventCount)
    } else {
        null
    }
    val bubbleLayout = remember(bubbleText, bubbleTextStyle) {
        bubbleText?.let { textMeasurer.measure(it, bubbleTextStyle) }
    }

    // Hoisted Path objects
    val historicalPaths = remember(lines) { lines.map { Path() } }
    val futurePaths = remember(lines) { lines.map { Path() } }
    val bandPaths = remember(lines) { lines.map { Path() } }
    val graphClipPath = remember { Path() }
    val caretPath = remember { Path() }

    // Pre-measure Y-axis labels for first line
    val yLabelValues = remember(dynamicLines) {
        if (dynamicLines.isEmpty()) {
            emptyList()
        } else {
            val (min, max) = dynamicLines.first().yAxisRange
            ValueFormatter.calculateLabelValues(min, max, targetCount = 5)
        }
    }
    val yLabelLayouts = remember(yLabelValues, axisTextStyle) {
        yLabelValues.associateWith { value ->
            textMeasurer.measure(ValueFormatter.formatCompact(value, decimals = 0), axisTextStyle)
        }
    }

    // Pre-measure X-axis labels for a wide range (covers scrolling)
    val allTimestamps = remember(lines) { lines.flatMap { it.dataPoints }.map { it.timestamp } }
    val dataMinTime = remember(allTimestamps) { allTimestamps.minOrNull() ?: currentTime }
    val dataMaxTime = remember(allTimestamps) { allTimestamps.maxOrNull() ?: currentTime }
    val xLabelCache = remember(config.zoomLevel, axisTextStyle, dataMinTime, dataMaxTime) {
        val wideRange =
            (dataMinTime - config.zoomLevel.millisVisible)..(dataMaxTime + config.zoomLevel.millisVisible)
        val positions = TimeFormatter.calculateLabelPositions(wideRange, config.zoomLevel)
        positions.associateWith { timestamp ->
            textMeasurer.measure(TimeFormatter.format(timestamp, config.zoomLevel), axisTextStyle)
        }
    }

    val graphDescription = stringResource(Res.string.content_description_stock_graph_detail, lines.size)
    val nextPointLabel = stringResource(Res.string.accessibility_next_data_point)
    val prevPointLabel = stringResource(Res.string.accessibility_previous_data_point)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clearAndSetSemantics {
                    contentDescription = graphDescription
                    if (config.enablePanning && gestureState != null) {
                        val scrollStep = canvasSize.width.toFloat() * 0.3f
                        customActions = listOf(
                            CustomAccessibilityAction(nextPointLabel) {
                                coroutineScope.launch { gestureState.scroll(-scrollStep) }
                                true
                            },
                            CustomAccessibilityAction(prevPointLabel) {
                                coroutineScope.launch { gestureState.scroll(scrollStep) }
                                true
                            },
                        )
                    }
                }
                .onSizeChanged { canvasSize = it }
                .then(
                    if (config.enablePanning && gestureState != null) {
                        Modifier.graphPanGestures(
                            key = gestureState,
                            enabled = true,
                            onScroll = { delta ->
                                coroutineScope.launch { gestureState.scroll(delta) }
                            },
                            onDragEnd = { velocity ->
                                coroutineScope.launch { gestureState.fling(velocity) }
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            coordinateSystem ?: return@Canvas

            // Apply dynamic Y-ranges and scroll offset
            val cs = coordinateSystem.copy(
                lines = dynamicLines,
                scrollOffset = gestureState?.scrollOffset ?: 0f,
            )

            // Future region background (behind everything)
            drawFutureRegion(cs, currentTime, futureRegionColor)

            // Clipped content area (rounded corners)
            drawClippedContent(cs, graphClipPath) {
                // Grid
                if (config.showGrid) {
                    drawGrid(cs, config.zoomLevel)
                }

                // Uncertainty bands (behind lines)
                dynamicLines.forEachIndexed { lineIndex, line ->
                    if (line.lowerBoundPoints.isNotEmpty() && line.upperBoundPoints.isNotEmpty()) {
                        val resolvedColor = if (line.color == Color.Unspecified) lineColor else line.color
                        drawUncertaintyBand(
                            cs,
                            line.lowerBoundPoints,
                            line.upperBoundPoints,
                            lineIndex,
                            bandPaths[lineIndex],
                            resolvedColor.copy(alpha = 0.12f),
                        )
                    }
                }

                // Lines (step-style)
                dynamicLines.forEachIndexed { lineIndex, line ->
                    val resolvedColor = if (line.color == Color.Unspecified) lineColor else line.color

                    // Historical line
                    if (line.historicalPoints.isNotEmpty()) {
                        drawStepLine(
                            cs,
                            line.historicalPoints,
                            lineIndex,
                            historicalPaths[lineIndex],
                            resolvedColor,
                            isFuture = false,
                        )
                    }

                    // Future line (bridged from last historical point)
                    if (line.futurePoints.isNotEmpty()) {
                        val bridgedFuture = buildList {
                            line.historicalPoints.lastOrNull()?.let { add(it) }
                            addAll(line.futurePoints)
                        }
                        drawStepLine(
                            cs,
                            bridgedFuture,
                            lineIndex,
                            futurePaths[lineIndex],
                            resolvedColor,
                            isFuture = true,
                        )

                        // Container transition markers
                        drawContainerTransitions(cs, line, resolvedColor)
                    }
                }

                // Data point markers (on top of all lines)
                dynamicLines.forEachIndexed { lineIndex, line ->
                    val resolvedColor = if (line.color == Color.Unspecified) lineColor else line.color
                    drawDataPointMarkers(cs, line.dataPoints, lineIndex, resolvedColor)
                }
            }

            // Axis labels (outside clip so always visible)
            if (config.showYAxis) {
                drawYAxisLabels(cs, yLabelLayouts, 0, axisLabelColor)
            }
            drawXAxisLabels(cs, config.zoomLevel, xLabelCache, axisLabelColor)

            // Event bubble above centered point
            if (centeredDayEventCount > 0 && bubbleLayout != null) {
                val centerX = cs.canvasWidth / 2f
                val nearest = cs.findNearestPoint(centerX)
                if (nearest != null) {
                    val (point, lineIdx) = nearest
                    drawEventBubble(cs, point, lineIdx, bubbleLayout, bubbleColor, bubbleTextColor, caretPath)
                }
            }
        }

        // Legend (Compose, not Canvas)
        if (config.showLegend && lines.size > 1) {
            GraphLegend(lines = lines, defaultColor = lineColor)
        }
    }
}

@Composable
private fun GraphLegend(lines: List<StockLine>, defaultColor: Color) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { line ->
            val color = if (line.color == Color.Unspecified) defaultColor else line.color
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = line.medicationName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
