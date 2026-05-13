// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.graph.gestures

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.model.StockDataPoint
import me.juliana.hellomeds.ui.components.graph.drawing.GraphCoordinateSystem

/**
 * Adds pan and snap gesture handling to the graph.
 *
 * @param enabled Whether gestures are enabled
 * @param onScroll Callback when scroll offset changes
 * @param onDragStart Callback when drag starts
 * @param onDragEnd Callback when drag ends (provides velocity for fling)
 */
fun Modifier.graphPanGestures(
    key: Any? = Unit,
    enabled: Boolean = true,
    onScroll: (Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: (velocity: Float) -> Unit = {},
): Modifier {
    if (!enabled) return this

    return this.then(
        Modifier.pointerInput(key) {
            val velocityTracker = VelocityTracker()

            detectHorizontalDragGestures(
                onDragStart = {
                    velocityTracker.resetTracking()
                    onDragStart()
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(
                        timeMillis = change.uptimeMillis,
                        position = change.position,
                    )
                    onScroll(dragAmount)
                },
                onDragEnd = {
                    val velocity = velocityTracker.calculateVelocity()
                    onDragEnd(velocity.x)
                },
                onDragCancel = {
                    onDragEnd(0f)
                },
            )
        },
    )
}

/**
 * State holder for graph gesture handling including fling and snap.
 */
class GraphGestureState(
    private val coordinateSystem: GraphCoordinateSystem,
    private val snapToPoints: Boolean,
    private val onCenteredPointChanged: ((StockDataPoint?) -> Unit)?,
    private val onLivePointChanged: ((StockDataPoint?) -> Unit)? = null,
) {
    private val scrollAnimatable = Animatable(0f)
    private val decayAnimationSpec: DecayAnimationSpec<Float> = exponentialDecay(
        frictionMultiplier = 3f,
        absVelocityThreshold = 0.5f,
    )

    init {
        val allTimestamps = coordinateSystem.lines.flatMap { it.dataPoints }.map { it.timestamp }
        if (allTimestamps.isNotEmpty() && coordinateSystem.drawableWidth > 0f) {
            val centerX = coordinateSystem.canvasWidth / 2f
            val timeRange =
                (coordinateSystem.visibleTimeRange.last - coordinateSystem.visibleTimeRange.first).toFloat()
            val wiggle = coordinateSystem.drawableWidth * 0.5f

            val firstX = coordinateSystem.padding.left +
                ((allTimestamps.min() - coordinateSystem.visibleTimeRange.first) / timeRange * coordinateSystem.drawableWidth)
            val maxBound = centerX - firstX + wiggle

            val lastX = coordinateSystem.padding.left +
                ((allTimestamps.max() - coordinateSystem.visibleTimeRange.first) / timeRange * coordinateSystem.drawableWidth)
            val minBound = centerX - lastX - wiggle

            if (minBound <= maxBound) {
                scrollAnimatable.updateBounds(lowerBound = minBound, upperBound = maxBound)
            }
        }
    }

    /**
     * Current scroll offset.
     */
    val scrollOffset: Float
        get() = scrollAnimatable.value

    /**
     * Build a coordinate system with the current scroll offset applied.
     * Used internally to ensure snap/find operations reflect the scrolled view.
     */
    private fun currentCoordinateSystem(): GraphCoordinateSystem {
        return coordinateSystem.copy(scrollOffset = scrollOffset)
    }

    /**
     * Handle immediate scroll without animation.
     */
    suspend fun scroll(delta: Float) {
        scrollAnimatable.snapTo(scrollAnimatable.value + delta)
        val centerX = coordinateSystem.canvasWidth / 2
        val nearest = currentCoordinateSystem().findNearestPoint(centerX)
        onLivePointChanged?.invoke(nearest?.first)
    }

    /**
     * Handle fling gesture with decay animation.
     */
    suspend fun fling(velocity: Float) {
        coroutineScope {
            launch {
                scrollAnimatable.animateDecay(
                    initialVelocity = velocity,
                    animationSpec = decayAnimationSpec,
                )
                // After fling completes, snap to nearest point if enabled
                if (snapToPoints) {
                    snapToNearest()
                }
            }
        }
    }

    /**
     * Snap to the nearest data point.
     */
    suspend fun snapToNearest() {
        val centerX = coordinateSystem.canvasWidth / 2
        val currentCS = currentCoordinateSystem()
        val nearestPoint = currentCS.findNearestPoint(centerX)

        if (nearestPoint != null) {
            val (point, _) = nearestPoint
            val pointX = currentCS.timeToX(point.timestamp)
            val offsetNeeded = centerX - pointX

            // Animate to snap position
            scrollAnimatable.animateTo(
                targetValue = scrollAnimatable.value + offsetNeeded,
                animationSpec = tween(durationMillis = 300),
            )

            onCenteredPointChanged?.invoke(point)
            onLivePointChanged?.invoke(point)
        } else {
            onCenteredPointChanged?.invoke(null)
            onLivePointChanged?.invoke(null)
        }
    }

    /**
     * Snap to a specific data point.
     */
    suspend fun snapToPoint(point: StockDataPoint) {
        val centerX = coordinateSystem.canvasWidth / 2
        val currentCS = currentCoordinateSystem()
        val pointX = currentCS.timeToX(point.timestamp)
        val offsetNeeded = centerX - pointX

        scrollAnimatable.animateTo(
            targetValue = scrollAnimatable.value + offsetNeeded,
            animationSpec = tween(durationMillis = 300),
        )

        onCenteredPointChanged?.invoke(point)
        onLivePointChanged?.invoke(point)
    }

    /**
     * Check if currently animating.
     */
    val isAnimating: Boolean
        get() = scrollAnimatable.isRunning

    /**
     * Stop any ongoing animation.
     */
    suspend fun stopAnimation() {
        scrollAnimatable.stop()
    }
}

/**
 * Remember a GraphGestureState across recompositions.
 *
 * Uses rememberUpdatedState for the callback to avoid stale lambda references
 * when the composable recomposes with a new callback instance.
 */
@Composable
fun rememberGraphGestureState(
    coordinateSystem: GraphCoordinateSystem,
    snapToPoints: Boolean = true,
    onCenteredPointChanged: ((StockDataPoint?) -> Unit)? = null,
    onLivePointChanged: ((StockDataPoint?) -> Unit)? = null,
): GraphGestureState {
    val currentCallback by rememberUpdatedState(onCenteredPointChanged)
    val currentLiveCallback by rememberUpdatedState(onLivePointChanged)
    return remember(coordinateSystem, snapToPoints) {
        GraphGestureState(
            coordinateSystem = coordinateSystem,
            snapToPoints = snapToPoints,
            onCenteredPointChanged = { currentCallback?.invoke(it) },
            onLivePointChanged = { currentLiveCallback?.invoke(it) },
        )
    }
}
