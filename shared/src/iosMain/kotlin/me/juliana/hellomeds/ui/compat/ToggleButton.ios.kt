// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * iOS fallback for [ToggleButton].
 *
 * Styled to match M3 Expressive connected toggle buttons with animated transitions:
 * - Checked: fully rounded shape, secondary / onSecondary colors
 * - Unchecked: connected shape (leading/middle/trailing), secondaryContainer / onSecondaryContainer
 * - Callers can override colors via [ToggleButtonColors]
 */
@Composable
actual fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shapes: ButtonShapes?,
    colors: ToggleButtonColors?,
    content: @Composable RowScope.() -> Unit,
) {
    val uncheckedShape = shapes?.shape ?: MaterialTheme.shapes.extraLarge
    val checkedShape = shapes?.checkedShape ?: shapes?.shape ?: MaterialTheme.shapes.extraLarge

    val targetContainerColor = if (checked) {
        colors?.checkedContainerColor?.takeUnless { it == Color.Unspecified }
            ?: MaterialTheme.colorScheme.secondary
    } else {
        colors?.containerColor?.takeUnless { it == Color.Unspecified }
            ?: MaterialTheme.colorScheme.secondaryContainer
    }
    val targetContentColor = if (checked) {
        colors?.checkedContentColor?.takeUnless { it == Color.Unspecified }
            ?: MaterialTheme.colorScheme.onSecondary
    } else {
        colors?.contentColor?.takeUnless { it == Color.Unspecified }
            ?: MaterialTheme.colorScheme.onSecondaryContainer
    }

    val animatedContainerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(200),
        label = "toggleContainer",
    )
    val animatedContentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(200),
        label = "toggleContent",
    )

    // Animate shape via scale — brief squeeze on toggle

    val shape: Shape = if (checked) checkedShape else uncheckedShape

    Button(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedContainerColor,
            contentColor = animatedContentColor,
        ),
        content = content,
    )
}
