// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.stock.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_preview_fill
import me.juliana.hellomeds.ui.components.stock.preview.shapes.ContainerShapes
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Represents a dose line in the fill preview.
 */
data class DoseLine(
    val y: Float,
    val opacity: Float,
    val isPassed: Boolean,
)

/**
 * Loads a vector path from a CMP DrawableResource by extracting PathNode data
 * from the ImageVector tree and converting to a Compose Path.
 */
@Composable
fun rememberVectorPath(resource: DrawableResource): Path {
    val imageVector = vectorResource(resource)
    return remember(imageVector) {
        val parser = PathParser()
        fun walk(group: VectorGroup) {
            for (node in group) {
                when (node) {
                    is VectorPath -> parser.addPathNodes(node.pathData)
                    is VectorGroup -> walk(node)
                }
            }
        }
        walk(imageVector.root)
        parser.toPath()
    }
}

/**
 * Calculates dose lines for discrete (exact) tracking mode.
 */
fun calculateDiscreteLines(fillBounds: Rect, totalDoses: Int, currentDoses: Int): List<DoseLine> {
    if (totalDoses <= 1 || totalDoses > 30) return emptyList()

    val lines = mutableListOf<DoseLine>()
    val lineSpacing = fillBounds.height / totalDoses.toFloat()

    for (i in 1 until totalDoses) {
        val y = fillBounds.bottom - (i * lineSpacing)
        val dosesTaken = totalDoses - currentDoses
        val isPassed = i <= dosesTaken

        lines.add(DoseLine(y = y, opacity = 1.0f, isPassed = isPassed))
    }

    return lines
}

/**
 * Calculates dose lines for weight-based tracking mode.
 */
fun calculateWeightBasedLines(fillBounds: Rect, estimatedDoses: Double, currentPercentage: Float): List<DoseLine> {
    val totalLines = estimatedDoses.toInt().coerceIn(3, 30)
    if (totalLines <= 1 || totalLines > 25) return emptyList()

    val lines = mutableListOf<DoseLine>()
    val lineSpacing = fillBounds.height / totalLines.toFloat()

    for (i in 1 until totalLines) {
        val y = fillBounds.bottom - (i * lineSpacing)
        val progressPercentage = (i.toFloat() / totalLines) * 100f
        val isPassed = progressPercentage <= (100f - currentPercentage)

        lines.add(DoseLine(y = y, opacity = 1.0f, isPassed = isPassed))
    }

    return lines
}

/**
 * Calculates the fill clip rectangle for bottom-up fill based on percentage.
 */
fun calculateFillClipRect(fillBounds: Rect, fillPercentage: Float): Rect {
    val normalizedFill = (fillPercentage / 100f).coerceIn(0f, 1f)
    val fillHeight = fillBounds.height * normalizedFill

    return Rect(
        left = fillBounds.left,
        top = fillBounds.bottom - fillHeight,
        right = fillBounds.right,
        bottom = fillBounds.bottom,
    )
}

/**
 * Fill preview showing container with bottom-up fill and dose lines.
 * Supports both discrete and weight-based tracking modes.
 */
@Composable
fun FillStockPreview(medication: Medication, currentStock: Double, modifier: Modifier = Modifier) {
    val containerShape = remember(medication.medicationContainer) {
        ContainerShapes.forContainer(medication.medicationContainer)
    }

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val primary = MaterialTheme.colorScheme.primary

    // Calculate fill percentage based on tracking precision
    val fillPercentage: Float = run {
        val total = medication.packagingQuantity?.toFloat() ?: 100f
        if (total > 0f) {
            ((currentStock.toFloat() / total) * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
    }

    // Calculate dose lines
    val doseLines =
        remember(medication.trackingPrecision, currentStock, medication.packagingQuantity) {
            when (medication.trackingPrecision) {
                TrackingPrecision.EXACT -> calculateDiscreteLines(
                    containerShape.fillMaskBounds,
                    medication.packagingQuantity?.toInt() ?: 0,
                    currentStock.toInt(),
                )

                TrackingPrecision.ESTIMATED -> {
                    val estimatedDoses = medication.packagingQuantity ?: 20.0
                    calculateWeightBasedLines(
                        containerShape.fillMaskBounds,
                        estimatedDoses,
                        fillPercentage,
                    )
                }

                null -> emptyList()
            }
        }

    val fillClipRect = remember(fillPercentage) {
        calculateFillClipRect(containerShape.fillMaskBounds, fillPercentage)
    }

    // Load paths from vector drawables
    val fillMaskPath = rememberVectorPath(containerShape.fillMaskDrawable)
    val decorationPath = rememberVectorPath(containerShape.decorationDrawable)
    val behindDecorationPath = containerShape.behindDecorationDrawable?.let {
        rememberVectorPath(it)
    }

    val fillDescription = stringResource(Res.string.stock_preview_fill, fillPercentage.toInt())

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(
                ratio = containerShape.aspectRatio,
                matchHeightConstraintsFirst = true,
            )
            .clearAndSetSemantics {
                contentDescription = fillDescription
            },
    ) {
        // Calculate scaling to fit canvas
        val scaleX = size.width / containerShape.viewportWidth
        val scaleY = size.height / containerShape.viewportHeight

        // Scale paths
        val scaledFillMask = Path().apply {
            addPath(fillMaskPath)
            transform(Matrix().apply { scale(scaleX, scaleY) })
        }

        val scaledDecoration = Path().apply {
            addPath(decorationPath)
            transform(Matrix().apply { scale(scaleX, scaleY) })
        }

        val scaledBehindDecoration = behindDecorationPath?.let { path ->
            Path().apply {
                addPath(path)
                transform(Matrix().apply { scale(scaleX, scaleY) })
            }
        }

        // Scale bounds
        val scaledFillBounds = Rect(
            left = containerShape.fillMaskBounds.left * scaleX,
            top = containerShape.fillMaskBounds.top * scaleY,
            right = containerShape.fillMaskBounds.right * scaleX,
            bottom = containerShape.fillMaskBounds.bottom * scaleY,
        )

        val scaledFillClip = Rect(
            left = fillClipRect.left * scaleX,
            top = fillClipRect.top * scaleY,
            right = fillClipRect.right * scaleX,
            bottom = fillClipRect.bottom * scaleY,
        )

        // Layer 0: Draw behind-decoration (e.g. inhaler cap that sits under the canister body)
        scaledBehindDecoration?.let { path ->
            drawPath(
                path = path,
                color = onPrimaryContainer,
            )
        }

        // Layer 1: Draw container background
        drawPath(
            path = scaledFillMask,
            color = primaryContainer,
        )

        // Layer 2: Draw fill level (clipped to container shape)
        clipPath(scaledFillMask) {
            if (medication.trackingPrecision == TrackingPrecision.ESTIMATED) {
                // ESTIMATED mode: gradient fill with stepped opacity bands
                val totalDoses = (medication.packagingQuantity ?: 20.0).toInt().coerceAtLeast(2)
                val doseHeight = scaledFillBounds.height / totalDoses.toFloat()

                val gradientFraction = (0.5f - (totalDoses - 8f) / 80f).coerceIn(0.25f, 0.5f)
                val gradientDoses = (totalDoses * gradientFraction).toInt().coerceAtLeast(2)

                val fillLineY = scaledFillClip.top
                val gradientBottomY = fillLineY + (gradientDoses / 2f) * doseHeight

                // Solid fill below gradient zone
                if (gradientBottomY < scaledFillBounds.bottom) {
                    drawRect(
                        color = onPrimaryContainer,
                        topLeft = Offset(scaledFillBounds.left, gradientBottomY),
                        size = Size(
                            width = scaledFillBounds.width,
                            height = (scaledFillBounds.bottom - gradientBottomY).coerceAtLeast(0f),
                        ),
                    )
                }

                // Gradient bands
                val maxOpacityLevels = 4
                for (level in (maxOpacityLevels - 1) downTo 0) {
                    val alpha = (level + 1).toFloat() / maxOpacityLevels
                    val firstI = (0 until gradientDoses).firstOrNull { i ->
                        ((1f - i.toFloat() / gradientDoses) * maxOpacityLevels)
                            .toInt().coerceIn(0, maxOpacityLevels - 1) == level
                    } ?: continue
                    val lastI = (0 until gradientDoses).last { i ->
                        ((1f - i.toFloat() / gradientDoses) * maxOpacityLevels)
                            .toInt().coerceIn(0, maxOpacityLevels - 1) == level
                    }
                    val blockTop = (gradientBottomY - (lastI + 1) * doseHeight)
                        .coerceAtLeast(scaledFillBounds.top)
                    val blockBottom = (gradientBottomY - firstI * doseHeight)
                        .coerceAtMost(scaledFillBounds.bottom)
                    if (blockBottom > blockTop) {
                        drawRect(
                            color = onPrimaryContainer,
                            alpha = alpha,
                            topLeft = Offset(scaledFillBounds.left, blockTop),
                            size = Size(scaledFillBounds.width, blockBottom - blockTop),
                        )
                    }
                }
            } else {
                // EXACT mode: solid fill
                drawRect(
                    color = onPrimaryContainer,
                    topLeft = Offset(scaledFillClip.left, scaledFillClip.top),
                    size = Size(
                        width = scaledFillClip.width,
                        height = scaledFillClip.height,
                    ),
                )
            }
        }

        // Layer 3: Draw dose lines
        val dashLen = 4.dp.toPx()
        val gapLen = 3.dp.toPx()
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, gapLen))

        clipPath(scaledFillMask) {
            doseLines.forEach { line ->
                val scaledY = line.y * scaleY
                val isAboveFill = scaledY < scaledFillClip.top

                val lineColor = if (isAboveFill) onPrimaryContainer else primary

                drawLine(
                    color = lineColor,
                    start = Offset(scaledFillBounds.left, scaledY),
                    end = Offset(scaledFillBounds.right, scaledY),
                    strokeWidth = 1.dp.toPx(),
                    alpha = line.opacity,
                    cap = StrokeCap.Round,
                    pathEffect = dashEffect,
                )
            }
        }

        // Layer 4: Draw decoration (always visible on top)
        drawPath(
            path = scaledDecoration,
            color = onPrimaryContainer,
        )
    }
}
