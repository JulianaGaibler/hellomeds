// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.stock.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.enums.BubbleFlowDirection
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_preview_bubble
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Geometric grid layout for the bubble preview.
 *
 * @param rows Number of rows in the grid.
 * @param columns Number of columns in the grid.
 * @param spacerIndices Cells (LTR top→bottom geometric order) that render as empty placeholders
 *   instead of pill bubbles. Empty for full grids.
 */
data class GridLayout(
    val rows: Int,
    val columns: Int,
    val spacerIndices: List<Int> = emptyList(),
)

/**
 * Choose columns/rows for [totalQuantity] pills and place any wasted cells as spacers symmetrically
 * across the last row. Replaces the old visual-only `rowOffset` centering: spacers are now real grid
 * cells, so the renderer just iterates and skips them.
 *
 * Spacer placement rules (last row only — `cells - qty` is always < cols by construction):
 * - 1 → rightmost cell of last row
 * - 2 → leftmost + rightmost
 * - 3 → leftmost + center + rightmost
 * - 4+ → alternate outside→in (L, R, L+1, R-1, ...)
 */
fun autoLayout(totalQuantity: Int): GridLayout {
    if (totalQuantity <= 0) return GridLayout(0, 0)

    val columns: Int
    val rows: Int
    if (totalQuantity <= 10) {
        columns = ceil(totalQuantity.toDouble() / 2).toInt()
        rows = 2
    } else {
        // Aspect ratio target 1.3 — prefer slightly wider than tall.
        val idealColumns = ceil(sqrt(totalQuantity.toDouble() * 1.3f)).toInt()
        var cols = idealColumns.coerceIn(MIN_COLUMNS, 10)
        var r = ceil(totalQuantity.toDouble() / cols).toInt()
        // A single orphan in the last row reads as a typo — drop one column to absorb it.
        val orphan = totalQuantity % cols
        if (orphan == 1 && totalQuantity > 1) {
            cols = (cols - 1).coerceAtLeast(MIN_COLUMNS)
            r = ceil(totalQuantity.toDouble() / cols).toInt()
        }
        columns = cols
        rows = r
    }

    val cells = rows * columns
    val numSpacers = cells - totalQuantity
    val spacerIndices = if (numSpacers == 0) {
        emptyList()
    } else {
        val base = (rows - 1) * columns
        pickSymmetricLastRow(columns, numSpacers).map { base + it }
    }
    return GridLayout(rows, columns, spacerIndices)
}

/**
 * Pick [k] indices from `[0, cols)` to act as spacers in the last row. See [autoLayout] for the rules.
 */
private fun pickSymmetricLastRow(cols: Int, k: Int): List<Int> {
    if (k <= 0) return emptyList()
    if (k == 1) return listOf(cols - 1) // Plan: single spacer goes to the rightmost cell.
    val picks = mutableListOf<Int>()
    var left = 0
    var right = cols - 1
    while (picks.size < k && left <= right) {
        picks.add(left)
        if (picks.size < k) picks.add(right)
        left++
        right--
    }
    return picks.sorted()
}

/**
 * Bubble preview showing pills in a grid. Taken pills are hidden, remaining pills shown opaque (exact)
 * or with a gradient band at the boundary (estimated). Spacer cells render as dashed-outline
 * placeholders.
 *
 * @param layoutOverride When non-null, overrides the auto-derived layout. The caller is responsible
 *   for validating the override against [totalQuantity]; an invalid override produces wrong-looking
 *   bubbles but never crashes.
 * @param flowDirection Order in which pills are consumed. Both options empty bubbles starting from
 *   the bottom-right; they differ on the second-pill direction (column-sweep upward vs row-sweep
 *   leftward). See [BubbleFlowDirection].
 */
@Composable
fun BubbleStockPreview(
    totalQuantity: Int,
    remainingQuantity: Int,
    isEstimated: Boolean = false,
    layoutOverride: GridLayout? = null,
    flowDirection: BubbleFlowDirection = BubbleFlowDirection.LTR_TOP_BOTTOM,
    modifier: Modifier = Modifier,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    val minPadding = with(density) { BubblePreviewConstants.MIN_PADDING.toPx() }
    val spacing = with(density) { BubblePreviewConstants.CIRCLE_SPACING.toPx() }
    val minDiameter = with(density) { BubblePreviewConstants.MIN_CIRCLE_DIAMETER.toPx() }
    val maxDiameter = with(density) { BubblePreviewConstants.MAX_CIRCLE_DIAMETER.toPx() }

    // wrapContentSize lets the Surface shrink to fit the Canvas instead of stretching to the parent's
    // full width. Combined with the Canvas modifier chain below, this preserves the grid's true
    // aspect ratio across portrait, landscape, phone, and tablet form factors.
    Surface(
        modifier = modifier.wrapContentSize(Alignment.Center),
        shape = RoundedCornerShape(16.dp),
        color = primaryContainer,
    ) {
        val layout = layoutOverride ?: autoLayout(totalQuantity)
        val cellCount = layout.rows * layout.columns

        val estimatedGridWidth =
            layout.columns * maxDiameter + (layout.columns - 1).coerceAtLeast(0) * spacing
        val estimatedGridHeight =
            layout.rows * maxDiameter + (layout.rows - 1).coerceAtLeast(0) * spacing
        val estimatedCanvasWidth = estimatedGridWidth + 2 * minPadding
        val estimatedCanvasHeight = estimatedGridHeight + 2 * minPadding
        val dynamicAspectRatio =
            if (estimatedCanvasHeight > 0f) estimatedCanvasWidth / estimatedCanvasHeight else 1f

        val bubbleDescription = stringResource(Res.string.stock_preview_bubble, remainingQuantity, totalQuantity)

        // Pin the canvas to LTR so bubbleFlowDirection is the sole source of direction logic,
        // independent of system locale (Arabic/Hebrew would otherwise flip the coordinate axes).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // Modifier chain rationale: widthIn/heightIn cap the Canvas at its natural size so the
            // bubble doesn't inflate on tablets; fillMaxWidth is intentionally absent so aspectRatio
            // can shrink the width when height is the binding constraint (landscape).
            Canvas(
                modifier = Modifier
                    .widthIn(max = with(density) { estimatedCanvasWidth.toDp() })
                    .heightIn(max = with(density) { estimatedCanvasHeight.toDp() })
                    .aspectRatio(dynamicAspectRatio)
                    .clearAndSetSemantics {
                        contentDescription = bubbleDescription
                    },
            ) {
                if (totalQuantity <= 0 || cellCount <= 0) return@Canvas

                val circleDiameter = calculateCircleDiameter(
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    rows = layout.rows,
                    columns = layout.columns,
                    minDiameter = minDiameter,
                    maxDiameter = maxDiameter,
                    spacing = spacing,
                    padding = minPadding,
                )

                val totalGridWidth =
                    layout.columns * circleDiameter + (layout.columns - 1).coerceAtLeast(0) * spacing
                val totalGridHeight =
                    layout.rows * circleDiameter + (layout.rows - 1).coerceAtLeast(0) * spacing
                val dynamicPaddingX = ((size.width - totalGridWidth) / 2).coerceAtLeast(minPadding)
                val dynamicPaddingY = ((size.height - totalGridHeight) / 2).coerceAtLeast(minPadding)

                val spacerSet = layout.spacerIndices.toSet()
                val pillCellsInTakingOrder = buildPillTakingOrder(layout, flowDirection, spacerSet)
                val takenCount = (totalQuantity - remainingQuantity).coerceIn(0, totalQuantity)

                val gradientBottom: Int
                val gradientTop: Int
                val maxOpacityLevels = 4
                if (isEstimated) {
                    val gradientFraction =
                        (0.5f - (totalQuantity - 8f) / 80f).coerceIn(0.25f, 0.5f)
                    val gradientDoses =
                        (totalQuantity * gradientFraction).toInt().coerceAtLeast(2)
                    gradientBottom = (takenCount - gradientDoses / 2).coerceAtLeast(0)
                    gradientTop = (gradientBottom + gradientDoses).coerceAtMost(totalQuantity)
                } else {
                    gradientBottom = 0
                    gradientTop = 0
                }

                for (cellIdx in 0 until cellCount) {
                    val row = cellIdx / layout.columns
                    val col = cellIdx % layout.columns
                    val centerX = dynamicPaddingX + col * (circleDiameter + spacing) + circleDiameter / 2
                    val centerY = dynamicPaddingY + row * (circleDiameter + spacing) + circleDiameter / 2
                    val center = Offset(centerX, centerY)

                    // Spacer cells render nothing in the read-only preview — the gap itself reads as
                    // "deliberately blank" without an outline cluttering the final layout. The editor
                    // grid still draws a dashed outline so users can see and drag the empty slots.
                    if (cellIdx in spacerSet) continue

                    // Pouch (filled background circle) — always drawn for pill cells.
                    drawCircle(
                        color = surfaceContainerColor,
                        radius = circleDiameter / 2,
                        center = center,
                    )

                    val ordinal = pillCellsInTakingOrder[cellIdx]
                    val pillAlpha: Float = if (isEstimated) {
                        when {
                            ordinal < gradientBottom -> 0f // Definitely taken
                            ordinal >= gradientTop -> 1f // Definitely present
                            else -> {
                                val gradientSize = (gradientTop - gradientBottom).coerceAtLeast(1)
                                val t = (ordinal - gradientBottom).toFloat() / gradientSize
                                val level = (t * maxOpacityLevels).toInt()
                                    .coerceIn(0, maxOpacityLevels - 1)
                                (level + 1).toFloat() / maxOpacityLevels
                            }
                        }
                    } else {
                        if (ordinal >= takenCount) 1f else 0f
                    }

                    if (pillAlpha > 0f) {
                        drawCircle(
                            color = primaryColor,
                            radius = circleDiameter * 0.35f,
                            center = center,
                            alpha = pillAlpha,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Per-cell ordinal in the consumption sequence — the Nth-to-be-taken pill has ordinal N. Spacers
 * get a sentinel (-1) but they're never read because the renderer skips them.
 *
 * Both flow directions start at the bottom-right cell (that's the universal first pill taken in a
 * blister); they differ only in which neighbour is taken second.
 */
private fun buildPillTakingOrder(
    layout: GridLayout,
    flowDirection: BubbleFlowDirection,
    spacerSet: Set<Int>,
): IntArray {
    val cellCount = layout.rows * layout.columns
    val ordinals = IntArray(cellCount) { -1 }
    val cellsInFlow: Sequence<Int> = when (flowDirection) {
        // Column sweep: bottom→top inside a column, then jump one column to the left.
        BubbleFlowDirection.LTR_TOP_BOTTOM -> sequence {
            for (c in (layout.columns - 1) downTo 0) {
                for (r in (layout.rows - 1) downTo 0) {
                    yield(r * layout.columns + c)
                }
            }
        }
        // Row sweep: right→left inside a row, then jump one row up.
        BubbleFlowDirection.RTL_BOTTOM_TOP -> sequence {
            for (r in (layout.rows - 1) downTo 0) {
                for (c in (layout.columns - 1) downTo 0) {
                    yield(r * layout.columns + c)
                }
            }
        }
    }
    var ordinal = 0
    for (cellIdx in cellsInFlow) {
        if (cellIdx in spacerSet) continue
        ordinals[cellIdx] = ordinal++
    }
    return ordinals
}

/**
 * Optimal pill diameter for the available canvas — divides the usable space (canvas minus padding)
 * across the grid, then clamps to [minDiameter]..[maxDiameter]. The min clamp keeps very dense grids
 * legible on small screens; the max clamp prevents sparse grids from looking like balloons on tablets.
 */
fun calculateCircleDiameter(
    canvasWidth: Float,
    canvasHeight: Float,
    rows: Int,
    columns: Int,
    minDiameter: Float,
    maxDiameter: Float,
    spacing: Float,
    padding: Float,
): Float {
    if (columns == 0 || rows == 0) return minDiameter
    val availableWidth = canvasWidth - 2 * padding
    val availableHeight = canvasHeight - 2 * padding
    val horizontalSpace = availableWidth / columns
    val verticalSpace = availableHeight / rows
    val maxFit = min(horizontalSpace, verticalSpace) - spacing
    return maxFit.coerceIn(minDiameter, maxDiameter)
}

@Composable
private fun BubblePreview_Small() {
    BubbleStockPreview(
        totalQuantity = 10,
        remainingQuantity = 7,
    )
}

@Composable
private fun BubblePreview_Medium() {
    BubbleStockPreview(
        totalQuantity = 30,
        remainingQuantity = 18,
    )
}

@Composable
private fun BubblePreview_Large() {
    BubbleStockPreview(
        totalQuantity = 90,
        remainingQuantity = 45,
    )
}

@Composable
private fun BubblePreview_AlmostEmpty() {
    BubbleStockPreview(
        totalQuantity = 30,
        remainingQuantity = 3,
    )
}

@Composable
private fun BubblePreview_Full() {
    BubbleStockPreview(
        totalQuantity = 30,
        remainingQuantity = 30,
    )
}

@Composable
private fun BubblePreview_25Pills_5Remaining() {
    BubbleStockPreview(
        totalQuantity = 25,
        remainingQuantity = 5,
    )
}

@Composable
private fun BubblePreview_ManualLayout() {
    BubbleStockPreview(
        totalQuantity = 10,
        remainingQuantity = 7,
        layoutOverride = GridLayout(rows = 3, columns = 4, spacerIndices = listOf(5, 6)),
    )
}

@Composable
private fun BubblePreview_RtlBottomTop() {
    BubbleStockPreview(
        totalQuantity = 10,
        remainingQuantity = 5,
        flowDirection = BubbleFlowDirection.RTL_BOTTOM_TOP,
    )
}
