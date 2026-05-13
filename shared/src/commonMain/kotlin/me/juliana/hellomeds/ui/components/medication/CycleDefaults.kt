// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.medication

import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.cycle_preset_21_7
import me.juliana.hellomeds.shared.cycle_preset_21_7_description
import me.juliana.hellomeds.shared.cycle_preset_24_4
import me.juliana.hellomeds.shared.cycle_preset_24_4_description
import me.juliana.hellomeds.shared.cycle_preset_continuous
import me.juliana.hellomeds.shared.cycle_preset_continuous_description
import org.jetbrains.compose.resources.StringResource

/**
 * A preset cycle configuration (e.g., 21 active / 7 break).
 */
data class CyclePreset(
    val active: Int,
    val breakDays: Int,
    val labelRes: StringResource,
    val descriptionRes: StringResource,
)

/**
 * Shared cycle preset definitions used in the wizard step and edit screen.
 */
val cyclePresets = listOf(
    CyclePreset(21, 7, Res.string.cycle_preset_21_7, Res.string.cycle_preset_21_7_description),
    CyclePreset(24, 4, Res.string.cycle_preset_24_4, Res.string.cycle_preset_24_4_description),
    CyclePreset(28, 0, Res.string.cycle_preset_continuous, Res.string.cycle_preset_continuous_description),
)
