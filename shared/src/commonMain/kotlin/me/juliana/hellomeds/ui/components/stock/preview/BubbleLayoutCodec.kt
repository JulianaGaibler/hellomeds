// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.stock.preview

import kotlin.math.ceil

/**
 * In-memory representation of a user-customized bubble layout.
 *
 * @param columns Number of bubble columns; rows are derived from `packagingQuantity` and `columns`.
 * @param spacerIndices Geometric cell indices (LTR top→bottom, regardless of flow direction) that
 *   render as empty placeholder bubbles instead of pills. Indices are 0-based; valid range is
 *   `0 until (columns * ceil(packagingQuantity / columns))`.
 */
data class ManualLayout(
    val columns: Int,
    val spacerIndices: List<Int>,
) {
    /**
     * Semantic invariant: spacer count must exactly fill the wasted cells of a `columns × ceil(qty/cols)` grid,
     * and every spacer index must point at a real cell with no duplicates. Anything else means the layout was
     * authored against a different `packagingQuantity` and should be discarded.
     */
    fun isValidFor(packagingQuantity: Int): Boolean {
        if (columns !in MIN_COLUMNS..MAX_COLUMNS) return false
        if (packagingQuantity <= 0) return false
        val rows = ceil(packagingQuantity.toDouble() / columns).toInt()
        val cells = columns * rows
        if (spacerIndices.size != cells - packagingQuantity) return false
        if (spacerIndices.any { it !in 0 until cells }) return false
        return spacerIndices.toSet().size == spacerIndices.size
    }
}

/**
 * Serialisation for the `Medication.bubbleManualLayout` column.
 *
 * Format: `"{cols},{spacer1},{spacer2},..."` — first integer is column count (≥ 2), the rest are
 * spacer indices in any order. Stored as plain TEXT in SQLite (no TypeConverter needed).
 *
 * Decoding is total-safe: any malformed input — corrupted backup restore, future schema drift,
 * manual DB edits — returns `null` so the renderer falls back to auto layout rather than
 * crashing. Semantic validation lives in [ManualLayout.isValidFor], not in parsing.
 */
object BubbleLayoutCodec {
    fun encode(layout: ManualLayout): String = (listOf(layout.columns) + layout.spacerIndices).joinToString(",")

    fun decode(s: String): ManualLayout? {
        if (s.isEmpty()) return null
        val parts = s.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parts.isEmpty()) return null
        val cols = parts.first()
        if (cols < MIN_COLUMNS) return null
        return ManualLayout(columns = cols, spacerIndices = parts.drop(1))
    }
}

const val MIN_COLUMNS: Int = 2
const val MAX_COLUMNS: Int = 14
