// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.graph.util

import androidx.compose.runtime.Composable
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.zoom_level_day
import me.juliana.hellomeds.shared.zoom_level_month
import me.juliana.hellomeds.shared.zoom_level_quarter
import me.juliana.hellomeds.shared.zoom_level_week
import me.juliana.hellomeds.shared.zoom_level_year
import me.juliana.hellomeds.ui.components.graph.models.ZoomLevel
import org.jetbrains.compose.resources.stringResource

@Composable
fun ZoomLevel.label(): String = stringResource(
    when (this) {
        ZoomLevel.DAY -> Res.string.zoom_level_day
        ZoomLevel.WEEK -> Res.string.zoom_level_week
        ZoomLevel.MONTH -> Res.string.zoom_level_month
        ZoomLevel.QUARTER -> Res.string.zoom_level_quarter
        ZoomLevel.YEAR -> Res.string.zoom_level_year
    },
)
