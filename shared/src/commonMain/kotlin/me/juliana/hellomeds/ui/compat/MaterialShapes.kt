// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.graphics.shapes.RoundedPolygon

/**
 * Expect object wrapping M3 Expressive MaterialShapes.
 *
 * On Android this is a typealias to [androidx.compose.material3.MaterialShapes].
 * On iOS this provides approximate [RoundedPolygon] equivalents built from the
 * graphics-shapes KMP library.
 */
expect object MaterialShapes {
    val Circle: RoundedPolygon
    val Square: RoundedPolygon
    val Slanted: RoundedPolygon
    val Arch: RoundedPolygon
    val Pill: RoundedPolygon
    val Diamond: RoundedPolygon
    val ClamShell: RoundedPolygon
    val Pentagon: RoundedPolygon
    val Gem: RoundedPolygon
    val Sunny: RoundedPolygon
    val VerySunny: RoundedPolygon
    val Cookie4Sided: RoundedPolygon
    val Cookie7Sided: RoundedPolygon
    val Cookie12Sided: RoundedPolygon
    val Clover4Leaf: RoundedPolygon
    val Clover8Leaf: RoundedPolygon
    val SoftBurst: RoundedPolygon
    val SoftBoom: RoundedPolygon
    val Flower: RoundedPolygon
    val PuffyDiamond: RoundedPolygon
    val Bun: RoundedPolygon
}
