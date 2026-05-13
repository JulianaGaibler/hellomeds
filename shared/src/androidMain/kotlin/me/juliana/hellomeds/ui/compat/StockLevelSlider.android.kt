// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Android: M3 Expressive [VerticalSlider] with reverseDirection (bottom=0, top=max)
 * and a wide track for easy thumb targeting.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun StockLevelSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier,
) {
    val sliderState = rememberSliderState(
        steps = steps,
        valueRange = valueRange,
    )
    sliderState.value = value
    sliderState.onValueChange = onValueChange

    VerticalSlider(
        state = sliderState,
        modifier = modifier.height(280.dp),
        reverseDirection = true,
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.width(144.dp),
                trackCornerSize = 24.dp,
                drawStopIndicator = null,
                drawTick = { _, _ -> },
            )
        },
    )
}
