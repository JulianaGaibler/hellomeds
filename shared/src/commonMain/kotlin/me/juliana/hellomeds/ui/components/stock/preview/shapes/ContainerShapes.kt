// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.stock.preview.shapes

import androidx.compose.ui.geometry.Rect
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_decoration_bottle_cap
import me.juliana.hellomeds.shared.stock_decoration_canister_cap
import me.juliana.hellomeds.shared.stock_decoration_dispenser_pump
import me.juliana.hellomeds.shared.stock_decoration_inhaler_cap
import me.juliana.hellomeds.shared.stock_decoration_inhaler_mouthpiece
import me.juliana.hellomeds.shared.stock_decoration_jar_lid
import me.juliana.hellomeds.shared.stock_decoration_tube_cap
import me.juliana.hellomeds.shared.stock_fill_mask_bottle
import me.juliana.hellomeds.shared.stock_fill_mask_canister
import me.juliana.hellomeds.shared.stock_fill_mask_dispenser
import me.juliana.hellomeds.shared.stock_fill_mask_inhaler
import me.juliana.hellomeds.shared.stock_fill_mask_jar
import me.juliana.hellomeds.shared.stock_fill_mask_tube
import me.juliana.hellomeds.ui.components.stock.preview.ContainerShapeDefinition

/**
 * Pre-defined container shapes for stock preview visualization.
 * Each container has a fill mask and decoration layer that are pre-aligned.
 */
object ContainerShapes {

    /**
     * Bottle container shape (for weight-based tracking).
     * Viewport: 168x232
     */
    val BOTTLE = ContainerShapeDefinition(
        type = MedicationContainer.BOTTLE,
        fillMaskDrawable = Res.drawable.stock_fill_mask_bottle,
        fillMaskBounds = Rect(
            left = 8f,
            top = 32f,
            right = 160f,
            bottom = 224f,
        ),
        decorationDrawable = Res.drawable.stock_decoration_bottle_cap,
        viewportWidth = 168f,
        viewportHeight = 232f,
        aspectRatio = 168f / 232f,
    )

    /**
     * Jar container shape.
     * Viewport: 160x112
     */
    val JAR = ContainerShapeDefinition(
        type = MedicationContainer.JAR,
        fillMaskDrawable = Res.drawable.stock_fill_mask_jar,
        fillMaskBounds = Rect(
            left = 15.9f,
            top = 24f,
            right = 143.9f,
            bottom = 104f,
        ),
        decorationDrawable = Res.drawable.stock_decoration_jar_lid,
        viewportWidth = 160f,
        viewportHeight = 112f,
        aspectRatio = 160f / 112f,
    )

    /**
     * Tube container shape.
     * Viewport: 144x248
     */
    val TUBE = ContainerShapeDefinition(
        type = MedicationContainer.TUBE,
        fillMaskDrawable = Res.drawable.stock_fill_mask_tube,
        fillMaskBounds = Rect(
            left = 9f,
            top = 48f,
            right = 135f,
            bottom = 240f,
        ),
        decorationDrawable = Res.drawable.stock_decoration_tube_cap,
        viewportWidth = 144f,
        viewportHeight = 248f,
        aspectRatio = 144f / 248f,
    )

    /**
     * Dispenser container shape.
     * Viewport: 144x256
     */
    val DISPENSER = ContainerShapeDefinition(
        type = MedicationContainer.DISPENSER,
        fillMaskDrawable = Res.drawable.stock_fill_mask_dispenser,
        fillMaskBounds = Rect(
            left = 8f,
            top = 56f,
            right = 136f,
            bottom = 248f,
        ),
        decorationDrawable = Res.drawable.stock_decoration_dispenser_pump,
        viewportWidth = 144f,
        viewportHeight = 256f,
        aspectRatio = 144f / 256f,
    )

    /**
     * Canister container shape.
     * Viewport: 144x232
     */
    val CANISTER = ContainerShapeDefinition(
        type = MedicationContainer.CANISTER,
        fillMaskDrawable = Res.drawable.stock_fill_mask_canister,
        fillMaskBounds = Rect(
            left = 8f,
            top = 32f,
            right = 136f,
            bottom = 224f,
        ),
        decorationDrawable = Res.drawable.stock_decoration_canister_cap,
        viewportWidth = 144f,
        viewportHeight = 232f,
        aspectRatio = 144f / 232f,
    )

    /**
     * Inhaler container shape (L-shaped).
     * Viewport: 165x194
     */
    val INHALER = ContainerShapeDefinition(
        type = MedicationContainer.INHALER,
        fillMaskDrawable = Res.drawable.stock_fill_mask_inhaler,
        fillMaskBounds = Rect(
            left = 21f,
            top = 17f,
            right = 156f,
            bottom = 186f,
        ),
        decorationDrawable = Res.drawable.stock_decoration_inhaler_mouthpiece,
        // Cap sits behind the canister body so the fill level renders over it.
        behindDecorationDrawable = Res.drawable.stock_decoration_inhaler_cap,
        viewportWidth = 165f,
        viewportHeight = 194f,
        aspectRatio = 165f / 194f,
    )

    /**
     * Gets the container shape definition for a given medication container type.
     *
     * @param container The medication container type
     * @return The container shape definition, or BOTTLE as fallback
     */
    fun forContainer(container: MedicationContainer?): ContainerShapeDefinition {
        return when (container) {
            MedicationContainer.BOTTLE -> BOTTLE
            MedicationContainer.JAR -> JAR
            MedicationContainer.TUBE -> TUBE
            MedicationContainer.DISPENSER -> DISPENSER
            MedicationContainer.CANISTER -> CANISTER
            MedicationContainer.INHALER -> INHALER
            else -> BOTTLE // Default fallback
        }
    }
}
