// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

@file:Suppress("MagicNumber")

package me.juliana.hellomeds.ui.compat

import androidx.compose.ui.geometry.Offset
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ── Helper types and functions (ported from M3 MaterialShapes.kt) ────

private data class PointNRound(
    val o: Offset,
    val r: CornerRounding = CornerRounding.Unrounded,
)

private fun Offset.rotateDegrees(angle: Float, center: Offset = Offset.Zero): Offset {
    val a = angle / 360f * 2 * PI.toFloat()
    val off = this - center
    return Offset(off.x * cos(a) - off.y * sin(a), off.x * sin(a) + off.y * cos(a)) + center
}

private fun Offset.angleDegrees() = atan2(y, x) * 180f / PI.toFloat()

private fun doRepeat(points: List<PointNRound>, reps: Int, center: Offset, mirroring: Boolean): List<PointNRound> =
    if (mirroring) {
        buildList {
            val angles = points.map { (it.o - center).angleDegrees() }
            val distances = points.map { (it.o - center).getDistance() }
            val actualReps = reps * 2
            val sectionAngle = 360f / actualReps
            repeat(actualReps) { rep ->
                points.indices.forEach { index ->
                    val i = if (rep % 2 == 0) index else points.lastIndex - index
                    if (i > 0 || rep % 2 == 0) {
                        val a = (
                            sectionAngle * rep +
                                if (rep % 2 == 0) {
                                    angles[i]
                                } else {
                                    sectionAngle - angles[i] + 2 * angles[0]
                                }
                            ) / 360f * 2 * PI.toFloat()
                        val finalPoint = Offset(cos(a), sin(a)) * distances[i] + center
                        add(PointNRound(finalPoint, points[i].r))
                    }
                }
            }
        }
    } else {
        val np = points.size
        (0 until np * reps).map {
            val point = points[it % np].o.rotateDegrees((it / np) * 360f / reps, center)
            PointNRound(point, points[it % np].r)
        }
    }

private fun customPolygon(
    pnr: List<PointNRound>,
    reps: Int,
    center: Offset = Offset(0.5f, 0.5f),
    mirroring: Boolean = false,
): RoundedPolygon {
    val actualPoints = doRepeat(pnr, reps, center, mirroring)
    return RoundedPolygon(
        vertices = FloatArray(actualPoints.size * 2) { ix ->
            actualPoints[ix / 2].o.let { if (ix % 2 == 0) it.x else it.y }
        },
        perVertexRounding = actualPoints.map { it.r },
        centerX = center.x,
        centerY = center.y,
    )
}

// ── Cached roundings (matching M3) ───────────────────────────────────

private val cr15 = CornerRounding(radius = 0.15f)
private val cr20 = CornerRounding(radius = 0.2f)
private val cr30 = CornerRounding(radius = 0.3f)
private val cr50 = CornerRounding(radius = 0.5f)
private val cr100 = CornerRounding(radius = 1f)

// ── MaterialShapes implementation ────────────────────────────────────

actual object MaterialShapes {

    actual val Circle: RoundedPolygon =
        RoundedPolygon.circle(numVertices = 10).normalized()

    actual val Square: RoundedPolygon =
        RoundedPolygon.rectangle(width = 1f, height = 1f, rounding = cr30).normalized()

    actual val Slanted: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.926f, 0.970f), CornerRounding(0.189f, 0.811f)),
                PointNRound(Offset(-0.021f, 0.967f), CornerRounding(0.187f, 0.057f)),
            ),
            reps = 2,
        ).normalized()

    // 4-vertex polygon: top 2 corners fully rounded, bottom 2 slightly rounded, rotated -135°
    actual val Arch: RoundedPolygon = run {
        val angle = -135f / 360f * 2f * PI.toFloat()
        val cosA = cos(angle)
        val sinA = sin(angle)
        // Inscribed in unit circle. Vertex order: right(0°), bottom(90°), left(180°), top(270°).
        val baseVerts = listOf(
            1f to 0f, // vertex 0
            0f to 1f, // vertex 1
            -1f to 0f, // vertex 2
            0f to -1f, // vertex 3
        )
        val rotatedVerts = FloatArray(8)
        baseVerts.forEachIndexed { i, (x, y) ->
            rotatedVerts[i * 2] = x * cosA - y * sinA
            rotatedVerts[i * 2 + 1] = x * sinA + y * cosA
        }
        RoundedPolygon(
            vertices = rotatedVerts,
            perVertexRounding = listOf(cr100, cr100, cr20, cr20),
        ).normalized()
    }

    actual val Pill: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.961f, 0.039f), CornerRounding(0.426f)),
                PointNRound(Offset(1.001f, 0.428f)),
                PointNRound(Offset(1.000f, 0.609f), CornerRounding(1.000f)),
            ),
            reps = 2,
            mirroring = true,
        ).normalized()

    actual val Diamond: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.500f, 1.096f), CornerRounding(0.151f, 0.524f)),
                PointNRound(Offset(0.040f, 0.500f), CornerRounding(0.159f)),
            ),
            reps = 2,
        ).normalized()

    actual val ClamShell: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.171f, 0.841f), CornerRounding(0.159f)),
                PointNRound(Offset(-0.020f, 0.500f), CornerRounding(0.140f)),
                PointNRound(Offset(0.170f, 0.159f), CornerRounding(0.159f)),
            ),
            reps = 2,
        ).normalized()

    actual val Pentagon: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.500f, -0.009f), CornerRounding(0.172f)),
                PointNRound(Offset(1.030f, 0.365f), CornerRounding(0.164f)),
                PointNRound(Offset(0.828f, 0.970f), CornerRounding(0.169f)),
            ),
            reps = 1,
            mirroring = true,
        ).normalized()

    actual val Gem: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.499f, 1.023f), CornerRounding(0.241f, 0.778f)),
                PointNRound(Offset(-0.005f, 0.792f), CornerRounding(0.208f)),
                PointNRound(Offset(0.073f, 0.258f), CornerRounding(0.228f)),
                PointNRound(Offset(0.433f, -0.000f), CornerRounding(0.491f)),
            ),
            reps = 1,
            mirroring = true,
        ).normalized()

    actual val Sunny: RoundedPolygon =
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.8f,
            rounding = cr15,
        ).normalized()

    actual val VerySunny: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.500f, 1.080f), CornerRounding(0.085f)),
                PointNRound(Offset(0.358f, 0.843f), CornerRounding(0.085f)),
            ),
            reps = 8,
        ).normalized()

    actual val Cookie4Sided: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(1.237f, 1.236f), CornerRounding(0.258f)),
                PointNRound(Offset(0.500f, 0.918f), CornerRounding(0.233f)),
            ),
            reps = 4,
        ).normalized()

    actual val Cookie7Sided: RoundedPolygon =
        RoundedPolygon.star(
            numVerticesPerRadius = 7,
            innerRadius = 0.75f,
            rounding = cr50,
        ).normalized()

    actual val Cookie12Sided: RoundedPolygon =
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.8f,
            rounding = cr50,
        ).normalized()

    actual val Clover4Leaf: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.500f, 0.074f)),
                PointNRound(Offset(0.725f, -0.099f), CornerRounding(0.476f)),
            ),
            reps = 4,
            mirroring = true,
        ).normalized()

    actual val Clover8Leaf: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.500f, 0.036f)),
                PointNRound(Offset(0.758f, -0.101f), CornerRounding(0.209f)),
            ),
            reps = 8,
        ).normalized()

    actual val SoftBurst: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.193f, 0.277f), CornerRounding(0.053f)),
                PointNRound(Offset(0.176f, 0.055f), CornerRounding(0.053f)),
            ),
            reps = 10,
        ).normalized()

    actual val SoftBoom: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.733f, 0.454f)),
                PointNRound(Offset(0.839f, 0.437f), CornerRounding(0.532f)),
                PointNRound(Offset(0.949f, 0.449f), CornerRounding(0.439f, 1.000f)),
                PointNRound(Offset(0.998f, 0.478f), CornerRounding(0.174f)),
            ),
            reps = 16,
            mirroring = true,
        ).normalized()

    actual val Flower: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.370f, 0.187f)),
                PointNRound(Offset(0.416f, 0.049f), CornerRounding(0.381f)),
                PointNRound(Offset(0.479f, 0.001f), CornerRounding(0.095f)),
            ),
            reps = 8,
            mirroring = true,
        ).normalized()

    actual val PuffyDiamond: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.870f, 0.130f), CornerRounding(0.146f)),
                PointNRound(Offset(0.818f, 0.357f)),
                PointNRound(Offset(1.000f, 0.332f), CornerRounding(0.853f)),
            ),
            reps = 4,
            mirroring = true,
        ).normalized()

    actual val Bun: RoundedPolygon =
        customPolygon(
            listOf(
                PointNRound(Offset(0.796f, 0.500f)),
                PointNRound(Offset(0.853f, 0.518f), CornerRounding(1f)),
                PointNRound(Offset(0.992f, 0.631f), CornerRounding(1f)),
                PointNRound(Offset(0.968f, 1.000f), CornerRounding(1f)),
            ),
            reps = 2,
            mirroring = true,
        ).normalized()
}
