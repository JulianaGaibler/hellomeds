// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.ui.theme.MedicationColor

internal data class MedicationIconPreset(
    val foregroundShape: MedicationForegroundShape,
    val backgroundShape: MedicationBackgroundShape,
    val color: MedicationColor,
)

internal val MedicationIconPresets: List<MedicationIconPreset> = listOf(
    MedicationIconPreset(
        MedicationForegroundShape.CAPSULE_PILL,
        MedicationBackgroundShape.FOUR_LEAF_CLOVER,
        MedicationColor.Blue,
    ),
    MedicationIconPreset(
        MedicationForegroundShape.ROUND_PILL_NOTCH,
        MedicationBackgroundShape.BUN,
        MedicationColor.Rose,
    ),
    MedicationIconPreset(
        MedicationForegroundShape.DIAMOND_PILL_TALL_NOTCH,
        MedicationBackgroundShape.PUFFY_DIAMOND,
        MedicationColor.Yellow,
    ),
    MedicationIconPreset(
        MedicationForegroundShape.DOUBLE_CIRCLE_PILL,
        MedicationBackgroundShape.CLAMSHELL,
        MedicationColor.Green,
    ),
    MedicationIconPreset(
        MedicationForegroundShape.PILL_BOTTLE,
        MedicationBackgroundShape.GEM,
        MedicationColor.Purple,
    ),
    MedicationIconPreset(
        MedicationForegroundShape.BLISTER_PACK_GRID,
        MedicationBackgroundShape.SOFT_BOOM,
        MedicationColor.Orange,
    ),
    MedicationIconPreset(
        MedicationForegroundShape.TUBE,
        MedicationBackgroundShape.PILL,
        MedicationColor.Teal,
    ),
    MedicationIconPreset(
        MedicationForegroundShape.DISPENSER,
        MedicationBackgroundShape.FLOWER,
        MedicationColor.Red,
    ),
    MedicationIconPreset(
        MedicationForegroundShape.PATCH,
        MedicationBackgroundShape.SEVEN_SIDED_COOKIE,
        MedicationColor.Indigo,
    ),
)
