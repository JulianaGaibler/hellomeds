// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

enum class MedicationBackgroundShape {
    CIRCLE,
    SQUARE,
    SLANTED,
    ARCH,
    PILL,
    DIAMOND,
    CLAMSHELL,
    PENTAGON,
    GEM,
    SUNNY,
    VERY_SUNNY,
    FOUR_SIDED_COOKIE,
    SEVEN_SIDED_COOKIE,
    TWELVE_SIDED_COOKIE,
    FOUR_LEAF_CLOVER,
    EIGHT_LEAF_CLOVER,
    SOFT_BURST,
    SOFT_BOOM,
    FLOWER,
    PUFFY_DIAMOND,
    BUN,
    ;

    companion object {
        private val map = entries.associateBy { it.name }
        fun fromNameOrDefault(name: String, default: MedicationBackgroundShape = CIRCLE) = map[name] ?: default
    }
}
