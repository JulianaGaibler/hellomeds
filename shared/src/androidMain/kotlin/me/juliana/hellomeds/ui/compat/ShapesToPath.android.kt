// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

actual fun RoundedPolygon.toComposePath(): Path = toPath().asComposePath()

actual fun Morph.toComposePath(progress: Float): Path = toPath(progress = progress).asComposePath()
