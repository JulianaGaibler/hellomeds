// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.graphics.shapes.RoundedPolygon

/**
 * On Android, delegates each property to the real M3 Expressive [MaterialShapes][androidx.compose.material3.MaterialShapes].
 */
actual object MaterialShapes {
    actual val Circle: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Circle
    actual val Square: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Square
    actual val Slanted: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Slanted
    actual val Arch: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Arch
    actual val Pill: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Pill
    actual val Diamond: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Diamond
    actual val ClamShell: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.ClamShell
    actual val Pentagon: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Pentagon
    actual val Gem: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Gem
    actual val Sunny: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Sunny
    actual val VerySunny: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.VerySunny
    actual val Cookie4Sided: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Cookie4Sided
    actual val Cookie7Sided: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Cookie7Sided
    actual val Cookie12Sided: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Cookie12Sided
    actual val Clover4Leaf: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Clover4Leaf
    actual val Clover8Leaf: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Clover8Leaf
    actual val SoftBurst: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.SoftBurst
    actual val SoftBoom: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.SoftBoom
    actual val Flower: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Flower
    actual val PuffyDiamond: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.PuffyDiamond
    actual val Bun: RoundedPolygon get() = androidx.compose.material3.MaterialShapes.Bun
}
