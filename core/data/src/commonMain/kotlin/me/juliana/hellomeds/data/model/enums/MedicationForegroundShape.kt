// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

enum class MedicationForegroundShape {
    // Common pill shapes
    CAPSULE_PILL,
    TABLET_PILL,
    TABLET_PILL_NOTCH,
    ROUND_PILL,
    ROUND_PILL_NOTCH,
    OVAL_PILL,
    OVAL_PILL_NOTCH,
    RECTANGLE_PILL,
    RECTANGLE_PILL_NOTCH,
    SQUARE_PILL,
    DIAMOND_PILL,
    DIAMOND_PILL_NOTCH,
    DIAMOND_PILL_TALL,
    DIAMOND_PILL_TALL_NOTCH,
    PENTAGON_PILL,
    TRIANGLE_PILL,
    DOUBLE_CIRCLE_PILL,
    EFFERVESCENT_TABLET,

    // Containers & packaging
    PILL_BOTTLE,
    JAR,
    BLISTER_PACK_GRID,
    TUBE,
    DROPPER_BOTTLE,
    DISPENSER,

    // Liquids & powders
    LIQUID,
    MEASUREMENT_CUP,
    POWDER_SACHET,

    // Delivery devices & equipment
    SYRINGE,
    INJECTION_PEN,
    INHALER,
    PATCH,
    SUPPOSITORY,
    CONTRACEPTIVE_RING,
    IUD_T_SHAPE,
    ;

    companion object {
        private val map = entries.associateBy { it.name }
        fun fromNameOrDefault(name: String, default: MedicationForegroundShape = ROUND_PILL) = map[name] ?: default
    }
}
