// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.juliana.hellomeds.designsystem.testing.ScreenshotTestTags
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.menu_content_description
import me.juliana.hellomeds.shared.screen_settings
import me.juliana.hellomeds.shared.support_title
import me.juliana.hellomeds.ui.compat.GroupedOverflowDropdown
import me.juliana.hellomeds.ui.compat.SplitMenuButton
import org.jetbrains.compose.resources.stringResource

/**
 * Configuration for a primary action shown as the leading part of a split button.
 * When provided, the overflow menu renders as a SplitButton instead of an icon button.
 */
data class OverflowMenuPrimaryAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Shared overflow menu with optional split-button primary action and grouped dropdown.
 *
 * - When [primaryAction] is provided: renders a SplitButton (leading = primary action,
 *   trailing = dropdown toggle).
 * - When [primaryAction] is null: renders the standard three-dot icon button.
 *
 * Menu items are organized into visual groups:
 * - Custom items (if [customContent] is provided) form the first group.
 * - Shared items (Support, Settings) form the last group.
 * Groups are separated by a visual gap (Android) or divider (iOS).
 */
@Composable
fun OverflowMenu(
    onNavigateToSettings: () -> Unit,
    onNavigateToSupport: () -> Unit,
    modifier: Modifier = Modifier,
    primaryAction: OverflowMenuPrimaryAction? = null,
    customContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    // Build groups: custom items (optional) + shared (Support, Settings)
    val sharedGroup: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = { dismiss ->
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.support_title)) },
            onClick = {
                dismiss()
                onNavigateToSupport()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.screen_settings)) },
            onClick = {
                dismiss()
                onNavigateToSettings()
            },
            modifier = Modifier.testTag(ScreenshotTestTags.OVERFLOW_MENU_SETTINGS),
        )
    }
    val groups = listOfNotNull(customContent, sharedGroup)

    // Trigger: SplitButton or IconButton
    if (primaryAction != null) {
        SplitMenuButton(
            primaryLabel = primaryAction.label,
            onPrimaryClick = primaryAction.onClick,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = modifier,
        )
    } else {
        IconButton(
            onClick = { expanded = true },
            modifier = modifier.testTag(ScreenshotTestTags.OVERFLOW_MENU_BUTTON),
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.menu_content_description),
            )
        }
    }

    // Dropdown
    GroupedOverflowDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        groups = groups,
    )
}
