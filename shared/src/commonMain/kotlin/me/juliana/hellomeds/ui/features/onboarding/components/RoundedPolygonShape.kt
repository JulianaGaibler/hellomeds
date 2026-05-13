// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

/**
 * Creates a rounded polygon shape (Star/Flower-like) common in Material You designs.
 * This shape creates the soft, organic "blob" effect seen in modern Android UI.
 */
class RoundedPolygonShape(
    private val sides: Int = 4,
    private val curvature: Float = 0.5f, // 0.0 = sharp, 1.0 = circle-ish
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(createPath(size, sides, curvature))
    }

    private fun createPath(size: Size, sides: Int, curvature: Float): Path {
        val path = Path()
        val radius = size.minDimension / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        val angleStep = (2 * kotlin.math.PI) / sides

        // Generate polygon points
        val points = mutableListOf<Offset>()
        for (i in 0 until sides) {
            val theta = i * angleStep - (kotlin.math.PI / 2) // Start at top
            val x = centerX + radius * cos(theta).toFloat()
            val y = centerY + radius * sin(theta).toFloat()
            points.add(Offset(x, y))
        }

        // Draw path with smooth curves between points
        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            // Create control point for smooth curve
            val cp1x = p0.x + (p1.x - p0.x) * curvature
            val cp1y = p0.y + (p1.y - p0.y) * curvature
            path.quadraticTo(cp1x, cp1y, p1.x, p1.y)
        }

        // Close the loop smoothly
        val pLast = points.last()
        val pFirst = points.first()
        val cpX = pLast.x + (pFirst.x - pLast.x) * curvature
        val cpY = pLast.y + (pFirst.y - pLast.y) * curvature
        path.quadraticTo(cpX, cpY, pFirst.x, pFirst.y)

        path.close()
        return path
    }
}
