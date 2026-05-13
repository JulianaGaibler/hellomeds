// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.stock.preview

import androidx.compose.ui.geometry.Rect
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import org.jetbrains.compose.resources.DrawableResource

/**
 * Defines a container shape with fill mask and decoration layers.
 *
 * The fill mask defines the area that fills from bottom-up based on stock level.
 * The decoration layer contains always-visible elements like caps, lids, or pumps.
 * Both vectors share the same viewport dimensions and align perfectly when stacked.
 *
 * @param type The medication container type
 * @param fillMaskDrawable Drawable resource for the fill mask (container body)
 * @param fillMaskBounds Bounding rectangle within the viewport for fill calculations
 * @param decorationDrawable Drawable resource for decorations (caps, lids, pumps)
 * @param viewportWidth Viewport width (unitless, e.g., 144)
 * @param viewportHeight Viewport height (unitless, e.g., 256)
 * @param aspectRatio Width/height ratio for canvas sizing
 */
data class ContainerShapeDefinition(
    val type: MedicationContainer,
    val fillMaskDrawable: DrawableResource,
    val fillMaskBounds: Rect,
    val decorationDrawable: DrawableResource,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val aspectRatio: Float,
)
