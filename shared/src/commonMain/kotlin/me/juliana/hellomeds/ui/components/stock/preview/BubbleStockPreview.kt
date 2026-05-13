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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_preview_bubble
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Grid dimensions for bubble preview.
 *
 * @param rows Number of rows in the grid
 * @param columns Number of columns in the grid
 */
data class GridDimensions(
    val rows: Int,
    val columns: Int,
)

/**
 * Calculates optimal grid dimensions based on total quantity.
 * Prefers wider layouts (more columns than rows) for better visual balance.
 *
 * @param totalQuantity Total number of pills to display
 * @return Grid dimensions with rows and columns
 */
fun calculateGridDimensions(totalQuantity: Int): GridDimensions {
    if (totalQuantity <= 0) return GridDimensions(0, 0)

    // Small quantities fit in two rows
    if (totalQuantity <= 10) {
        return GridDimensions(
            rows = 2,
            columns = ceil(totalQuantity.toDouble() / 2).toInt(),
        )
    }

    // Target aspect ratio: prefer slightly wider layouts (1.3 = 30% wider than tall)
    val targetAspectRatio = 1.3f

    // Calculate columns based on target aspect ratio
    // Formula: columns ≈ sqrt(totalQuantity * aspectRatio)
    val idealColumns = ceil(sqrt(totalQuantity.toDouble() * targetAspectRatio)).toInt()

    // Clamp to reasonable range (2-10 columns)
    var columns = idealColumns.coerceIn(2, 10)
    var rows = ceil(totalQuantity.toDouble() / columns).toInt()

    // Avoid single pill in last row
    val pillsInLastRow = totalQuantity % columns
    if (pillsInLastRow == 1 && totalQuantity > 1) {
        // Reduce columns to avoid single pill
        columns = (columns - 1).coerceAtLeast(2)
        rows = ceil(totalQuantity.toDouble() / columns).toInt()
    }

    return GridDimensions(rows = rows, columns = columns)
}

/**
 * Calculates the optimal circle diameter for pills based on quantity and available space.
 * Uses logarithmic scaling to keep circles readable for large quantities.
 *
 * @param totalQuantity Total number of pills
 * @param canvasWidth Available canvas width in pixels
 * @param canvasHeight Available canvas height in pixels
 * @param gridDimensions Pre-calculated grid dimensions
 * @param minDiameter Minimum circle diameter in pixels
 * @param maxDiameter Maximum circle diameter in pixels
 * @param spacing Spacing between circles in pixels
 * @param paddingStart Left padding in pixels
 * @param paddingEnd Right padding in pixels
 * @param paddingTop Top padding in pixels
 * @param paddingBottom Bottom padding in pixels
 * @return Optimal circle diameter in pixels
 */
fun calculateCircleDiameter(
    totalQuantity: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    gridDimensions: GridDimensions,
    minDiameter: Float,
    maxDiameter: Float,
    spacing: Float,
    paddingStart: Float,
    paddingEnd: Float,
    paddingTop: Float,
    paddingBottom: Float,
): Float {
    if (gridDimensions.columns == 0 || gridDimensions.rows == 0) return minDiameter

    // Calculate available space
    val availableWidth = canvasWidth - paddingStart - paddingEnd
    val availableHeight = canvasHeight - paddingTop - paddingBottom

    // Calculate maximum diameter that fits in the grid
    val horizontalSpace = availableWidth / gridDimensions.columns
    val verticalSpace = availableHeight / gridDimensions.rows

    val maxFitDiameter = min(horizontalSpace, verticalSpace) - spacing

    // Clamp to min/max bounds
    return maxFitDiameter.coerceIn(minDiameter, maxDiameter)
}

/**
 * Bubble preview showing pills/tablets in a grid.
 * Taken pills are shown darker, remaining pills are lighter.
 * Wrapped in a rounded container with padding.
 *
 * @param totalQuantity Total number of pills in the package
 * @param remainingQuantity Number of pills remaining (not taken)
 * @param modifier Modifier for the container
 */
@Composable
fun BubbleStockPreview(
    totalQuantity: Int,
    remainingQuantity: Int,
    isEstimated: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    // Convert dp values to pixels
    with(density) { BubblePreviewConstants.GRID_PADDING_START.toPx() }
    with(density) { BubblePreviewConstants.GRID_PADDING_END.toPx() }
    with(density) { BubblePreviewConstants.GRID_PADDING_TOP.toPx() }
    with(density) { BubblePreviewConstants.GRID_PADDING_BOTTOM.toPx() }
    val minPadding = with(density) { BubblePreviewConstants.MIN_PADDING.toPx() }
    val spacing = with(density) { BubblePreviewConstants.CIRCLE_SPACING.toPx() }
    val minDiameter = with(density) { BubblePreviewConstants.MIN_CIRCLE_DIAMETER.toPx() }
    val maxDiameter = with(density) { BubblePreviewConstants.MAX_CIRCLE_DIAMETER.toPx() }

    // wrapContentSize lets the Surface shrink to fit the Canvas instead of stretching to the
    // parent's full width. Combined with the Canvas modifier chain below, this preserves the
    // grid's true aspect ratio across portrait, landscape, phone, and tablet form factors.
    Surface(
        modifier = modifier.wrapContentSize(Alignment.Center),
        shape = RoundedCornerShape(16.dp),
        color = primaryContainer,
    ) {
        // Calculate grid dimensions first
        val gridDimensions = calculateGridDimensions(totalQuantity)

        // Calculate estimated grid size (using max diameter as estimate)
        val estimatedGridWidth =
            gridDimensions.columns * maxDiameter + (gridDimensions.columns - 1) * spacing
        val estimatedGridHeight =
            gridDimensions.rows * maxDiameter + (gridDimensions.rows - 1) * spacing

        // Calculate canvas size with equal padding on all sides
        val estimatedCanvasWidth = estimatedGridWidth + 2 * minPadding
        val estimatedCanvasHeight = estimatedGridHeight + 2 * minPadding

        // Aspect ratio derived from canvas dimensions (keeps internal padding equal)
        val dynamicAspectRatio = estimatedCanvasWidth / estimatedCanvasHeight

        val bubbleDescription = stringResource(Res.string.stock_preview_bubble, remainingQuantity, totalQuantity)

        // Modifier chain rationale (from a senior Compose review):
        // 1. widthIn / heightIn cap the Canvas to its natural grid size; on screens larger than
        //    that the bubble does not artificially inflate.
        // 2. fillMaxWidth is intentionally absent — locking minWidth=maxWidth would prevent
        //    aspectRatio from shrinking the width when height is the binding constraint
        //    (e.g. landscape), which is what produced the wide-with-empty-padding bug.
        // 3. aspectRatio uses the default matchHeightConstraintsFirst = false so Compose
        //    automatically picks the binding axis: width-bound on portrait phones, height-bound
        //    on short landscapes.
        Canvas(
            modifier = Modifier
                .widthIn(max = with(density) { estimatedCanvasWidth.toDp() })
                .heightIn(max = with(density) { estimatedCanvasHeight.toDp() })
                .aspectRatio(dynamicAspectRatio)
                .clearAndSetSemantics {
                    contentDescription = bubbleDescription
                },
        ) {
            if (totalQuantity <= 0) return@Canvas

            // Calculate circle diameter using minimum padding
            val circleDiameter = calculateCircleDiameter(
                totalQuantity = totalQuantity,
                canvasWidth = size.width,
                canvasHeight = size.height,
                gridDimensions = gridDimensions,
                minDiameter = minDiameter,
                maxDiameter = maxDiameter,
                spacing = spacing,
                paddingStart = minPadding,
                paddingEnd = minPadding,
                paddingTop = minPadding,
                paddingBottom = minPadding,
            )

            // Calculate total grid size
            val totalGridWidth =
                gridDimensions.columns * circleDiameter + (gridDimensions.columns - 1) * spacing
            val totalGridHeight =
                gridDimensions.rows * circleDiameter + (gridDimensions.rows - 1) * spacing

            // Calculate dynamic padding to ensure equal spacing on all sides
            val dynamicPaddingX = ((size.width - totalGridWidth) / 2).coerceAtLeast(minPadding)
            val dynamicPaddingY = ((size.height - totalGridHeight) / 2).coerceAtLeast(minPadding)

            // Estimated mode: precompute gradient band (same logic as FillStockPreview)
            val gradientBottom: Int
            val gradientTop: Int
            val maxOpacityLevels = 4
            if (isEstimated) {
                val gradientFraction =
                    (0.5f - (totalQuantity - 8f) / 80f).coerceIn(0.25f, 0.5f)
                val gradientDoses =
                    (totalQuantity * gradientFraction).toInt().coerceAtLeast(2)
                gradientBottom = (remainingQuantity - gradientDoses / 2).coerceAtLeast(0)
                gradientTop = (gradientBottom + gradientDoses).coerceAtMost(totalQuantity)
            } else {
                gradientBottom = 0
                gradientTop = 0
            }

            // Draw each pill as a circle
            for (index in 0 until totalQuantity) {
                val row = index / gridDimensions.columns
                val col = index % gridDimensions.columns

                // Calculate pills in this row to center incomplete rows
                val pillsInRow = min(gridDimensions.columns, totalQuantity - row * gridDimensions.columns)
                val isIncompleteRow = pillsInRow < gridDimensions.columns

                // Calculate offset to center incomplete rows
                val rowOffset = if (isIncompleteRow) {
                    ((gridDimensions.columns - pillsInRow) * (circleDiameter + spacing)) / 2
                } else {
                    0f
                }

                val centerX =
                    dynamicPaddingX + rowOffset + col * (circleDiameter + spacing) + circleDiameter / 2
                val centerY = dynamicPaddingY + row * (circleDiameter + spacing) + circleDiameter / 2

                // Draw pouch (larger filled circle)
                drawCircle(
                    color = surfaceContainerColor,
                    radius = circleDiameter / 2,
                    center = Offset(centerX, centerY),
                )

                // Determine pill visibility
                // fillIndex: 0 = bottom-right (first filled), totalQuantity-1 = top-left (last filled)
                val fillIndex = totalQuantity - 1 - index
                val pillAlpha: Float

                if (isEstimated) {
                    pillAlpha = when {
                        fillIndex < gradientBottom -> 1.0f // Definitely present
                        fillIndex >= gradientTop -> 0f // Definitely gone
                        else -> {
                            // Gradient zone: stepped opacity bands
                            val gradientSize = gradientTop - gradientBottom
                            val t = (fillIndex - gradientBottom).toFloat() / gradientSize
                            val level = ((1f - t) * maxOpacityLevels).toInt()
                                .coerceIn(0, maxOpacityLevels - 1)
                            (level + 1).toFloat() / maxOpacityLevels
                        }
                    }
                } else {
                    pillAlpha = if (index < remainingQuantity) 1.0f else 0f
                }

                // Draw pill (smaller filled circle) when present
                if (pillAlpha > 0f) {
                    drawCircle(
                        color = primaryColor,
                        radius = circleDiameter * 0.35f,
                        center = Offset(centerX, centerY),
                        alpha = pillAlpha,
                    )
                }
            }
        }
    }
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
