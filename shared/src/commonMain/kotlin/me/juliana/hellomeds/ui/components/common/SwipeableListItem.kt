// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_action_mark_skipped
import me.juliana.hellomeds.shared.accessibility_action_mark_taken
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
fun SwipeableListItem(
    key: Any? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    leftSwipeIcon: Painter? = null,
    rightSwipeIcon: Painter? = null,
    leftSwipeBackgroundColor: Color = Color.Transparent,
    rightSwipeBackgroundColor: Color = Color.Transparent,
    leftSwipeIconTint: Color = Color.White,
    rightSwipeIconTint: Color = Color.White,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onSwipeLeft == null && onSwipeRight == null) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        LocalHapticFeedback.current
        val dragOffset = remember { Animatable(0f) }
        var rawDragOffset by remember { mutableFloatStateOf(0f) }
        val maxDragDistance = with(density) { 20.dp.toPx() }

        Box(
            modifier = modifier
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { rawDragOffset = 0f },
                        onDragEnd = {
                            rawDragOffset = 0f
                            scope.launch {
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                rawDragOffset += dragAmount
                                val absRawOffset = abs(rawDragOffset)
                                val resistedOffset = if (absRawOffset > 0) {
                                    val normalizedDrag = absRawOffset / maxDragDistance
                                    val resistance = normalizedDrag / (1f + normalizedDrag)
                                    maxDragDistance * resistance * sign(rawDragOffset)
                                } else {
                                    0f
                                }
                                dragOffset.snapTo(resistedOffset)
                            }
                        },
                    )
                },
        ) {
            content()
        }
        return
    }

    androidx.compose.runtime.key(key) {
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
        val dismissState = remember {
            SwipeToDismissBoxState(
                initialValue = SwipeToDismissBoxValue.Settled,
                positionalThreshold = positionalThreshold,
            )
        }

        var containerWidth by remember { mutableFloatStateOf(1f) }
        var isProcessing by remember { mutableStateOf(false) }
        var lastActionTime by remember { mutableStateOf(0L) }

        val isPastThreshold by remember(dismissState) {
            derivedStateOf {
                val offset = try {
                    dismissState.requireOffset()
                } catch (_: Exception) {
                    0f
                }
                val thresholdPx = containerWidth * 0.4f
                val isRightSwipe = offset > 0 && onSwipeRight != null
                val isLeftSwipe = offset < 0 && onSwipeLeft != null
                (isRightSwipe || isLeftSwipe) && abs(offset) >= thresholdPx
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { isPastThreshold }
                .distinctUntilChanged()
                .drop(1)
                .collect { _ ->
                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
        }

        val markTakenLabel = stringResource(Res.string.accessibility_action_mark_taken)
        val markSkippedLabel = stringResource(Res.string.accessibility_action_mark_skipped)

        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier
                .onSizeChanged { size -> containerWidth = size.width.toFloat() }
                .semantics {
                    customActions = buildList {
                        if (onSwipeRight != null) {
                            add(
                                CustomAccessibilityAction(label = markTakenLabel, action = {
                                    onSwipeRight.invoke()
                                    scope.launch { dismissState.reset() }
                                    true
                                }),
                            )
                        }
                        if (onSwipeLeft != null) {
                            add(
                                CustomAccessibilityAction(label = markSkippedLabel, action = {
                                    onSwipeLeft.invoke()
                                    scope.launch { dismissState.reset() }
                                    true
                                }),
                            )
                        }
                    }
                },
            backgroundContent = {
                val offset = try {
                    dismissState.requireOffset()
                } catch (_: Exception) {
                    0f
                }

                val progress = if (containerWidth > 0f) {
                    (abs(offset) / containerWidth).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (offset > 0f && progress > 0f && onSwipeRight != null && rightSwipeIcon != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .padding(vertical = 2.dp)
                                .background(
                                    color = rightSwipeBackgroundColor,
                                    shape = MaterialTheme.shapes.extraLarge,
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = rightSwipeIcon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = rightSwipeIconTint,
                            )
                        }
                    }

                    if (offset < 0f && progress > 0f && onSwipeLeft != null && leftSwipeIcon != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .padding(vertical = 2.dp)
                                .background(
                                    color = leftSwipeBackgroundColor,
                                    shape = MaterialTheme.shapes.extraLarge,
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = leftSwipeIcon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = leftSwipeIconTint,
                            )
                        }
                    }
                }
            },
            onDismiss = { direction ->
                val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
                val timeSinceLastAction = currentTime - lastActionTime

                if (isProcessing || timeSinceLastAction < 500) {
                    return@SwipeToDismissBox
                }

                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        isProcessing = true
                        lastActionTime = currentTime
                        onSwipeRight?.invoke()
                        scope.launch {
                            dismissState.reset()
                            isProcessing = false
                        }
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        isProcessing = true
                        lastActionTime = currentTime
                        onSwipeLeft?.invoke()
                        scope.launch {
                            dismissState.reset()
                            isProcessing = false
                        }
                    }

                    SwipeToDismissBoxValue.Settled -> {
                        /* no action */
                    }
                }
            },
        ) {
            content()
        }
    }
}
