// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

/**
 * Defines whether a medication follows a cyclic dosing pattern (e.g., birth control blister packs).
 * The cycle acts as a mask over normal schedule frequency — active days generate events normally,
 * break days either suppress events or mark them as placebos.
 */
enum class CycleType {
    /** No cycle — standard medication scheduling. */
    NONE,

    /** Cyclic pattern with active and break periods (e.g., 21 days on / 7 days off). */
    CYCLIC,
}
