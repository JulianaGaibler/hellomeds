// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * Converts a [RoundedPolygon] to a Compose [Path].
 *
 * On Android, uses `RoundedPolygon.toPath().asComposePath()`.
 * On iOS, uses the cubic points API to manually build the path.
 */
expect fun RoundedPolygon.toComposePath(): Path

/**
 * Converts a [Morph] at the given [progress] to a Compose [Path].
 *
 * On Android, uses `Morph.toPath(progress).asComposePath()`.
 * On iOS, uses the cubic points API to manually build the path.
 */
expect fun Morph.toComposePath(progress: Float): Path
