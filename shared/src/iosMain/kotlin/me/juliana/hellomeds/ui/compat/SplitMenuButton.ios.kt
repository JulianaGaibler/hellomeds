// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_collapsed
import me.juliana.hellomeds.shared.accessibility_expanded
import me.juliana.hellomeds.shared.menu_content_description
import org.jetbrains.compose.resources.stringResource

/**
 * iOS fallback: Row with a leading tonal button and trailing tonal icon button.
 * Uses manual rounded corner shapes to approximate the M3 SplitButton look.
 */
@Composable
actual fun SplitMenuButton(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val leadingShape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    val trailingShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 50.dp, bottomEnd = 50.dp)

    val expandedLabel = stringResource(Res.string.accessibility_expanded)
    val collapsedLabel = stringResource(Res.string.accessibility_collapsed)
    val menuLabel = stringResource(Res.string.menu_content_description)

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceBright,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val iconColors = IconButtonDefaults.iconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceBright,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

    Row(modifier = modifier) {
        Button(
            onClick = onPrimaryClick,
            shape = leadingShape,
            colors = buttonColors,
        ) {
            Text(primaryLabel)
        }

        FilledIconButton(
            onClick = { onExpandedChange(!expanded) },
            shape = trailingShape,
            colors = iconColors,
            modifier = Modifier.semantics {
                stateDescription = if (expanded) expandedLabel else collapsedLabel
                contentDescription = menuLabel
            },
        ) {
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                label = "Trailing Icon Rotation",
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation },
                contentDescription = null,
            )
        }
    }
}
