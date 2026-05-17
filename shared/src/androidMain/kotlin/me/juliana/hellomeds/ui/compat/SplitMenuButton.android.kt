// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import me.juliana.hellomeds.designsystem.testing.ScreenshotTestTags
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_collapsed
import me.juliana.hellomeds.shared.accessibility_expanded
import me.juliana.hellomeds.shared.menu_content_description
import org.jetbrains.compose.resources.stringResource

/**
 * On Android, delegates to the real M3 Expressive [SplitButtonLayout].
 * Uses tonal variant at default size.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun SplitMenuButton(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val expandedLabel = stringResource(Res.string.accessibility_expanded)
    val collapsedLabel = stringResource(Res.string.accessibility_collapsed)
    val menuLabel = stringResource(Res.string.menu_content_description)

    val colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceBright,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onPrimaryClick,
                colors = colors,
            ) {
                Text(primaryLabel)
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                colors = colors,
                modifier = Modifier
                    .testTag(ScreenshotTestTags.OVERFLOW_MENU_BUTTON)
                    .semantics {
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
                    modifier = Modifier
                        .size(SplitButtonDefaults.TrailingIconSize)
                        .graphicsLayer { rotationZ = rotation },
                    contentDescription = null,
                )
            }
        },
        modifier = modifier,
    )
}
