// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

/**
 * Order in which pills in a blister-pack-style bubble visualization are consumed.
 * Both options start at the bottom-right bubble (that's the always-first pill taken in a blister);
 * the difference is which neighbour is taken next.
 *
 * Enum value names ([LTR_TOP_BOTTOM], [RTL_BOTTOM_TOP]) are retained for DB stability — they were
 * already shipped in the schema with `DEFAULT 'LTR_TOP_BOTTOM'`. Only the semantics and user-facing
 * labels changed when the design switched to "always start bottom-right." Treat the names as
 * opaque identifiers; consult the [BubbleStockPreview] sequence builder for actual behaviour.
 *
 * Pinned to the bubble canvas under a forced [androidx.compose.ui.unit.LayoutDirection.Ltr]
 * composition local so the visual interpretation is independent of system locale RTL.
 */
enum class BubbleFlowDirection {
    /** Column sweep: start bottom-right, next pill is the one *above*; finish each column before
     *  moving one column to the left. Default. User-facing label: "Column by column". */
    LTR_TOP_BOTTOM,

    /** Row sweep: start bottom-right, next pill is the one *to the left*; finish each row before
     *  moving up to the next row. User-facing label: "Row by row". */
    RTL_BOTTOM_TOP,
}
