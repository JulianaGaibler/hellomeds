// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * iOS: build a Compose Path from the RoundedPolygon's cubic bezier data.
 */
actual fun RoundedPolygon.toComposePath(): Path {
    val path = Path()
    val cubics = this.cubics
    if (cubics.isEmpty()) return path

    // Each Cubic has 8 floats: anchor0.x, anchor0.y, ctrl0.x, ctrl0.y, ctrl1.x, ctrl1.y, anchor1.x, anchor1.y
    var first = true
    for (cubic in cubics) {
        if (first) {
            path.moveTo(cubic.anchor0X, cubic.anchor0Y)
            first = false
        }
        path.cubicTo(
            cubic.control0X,
            cubic.control0Y,
            cubic.control1X,
            cubic.control1Y,
            cubic.anchor1X,
            cubic.anchor1Y,
        )
    }
    path.close()
    return path
}

/**
 * iOS: build a Compose Path from the Morph's cubic bezier data at the given progress.
 */
actual fun Morph.toComposePath(progress: Float): Path {
    val path = Path()
    val cubics = this.asCubics(progress)

    var first = true
    for (cubic in cubics) {
        if (first) {
            path.moveTo(cubic.anchor0X, cubic.anchor0Y)
            first = false
        }
        path.cubicTo(
            cubic.control0X,
            cubic.control0Y,
            cubic.control1X,
            cubic.control1Y,
            cubic.anchor1X,
            cubic.anchor1Y,
        )
    }
    path.close()
    return path
}
