// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_action_collapse
import me.juliana.hellomeds.shared.accessibility_action_expand
import me.juliana.hellomeds.ui.compat.ListItemShapes
import org.jetbrains.compose.resources.stringResource

/**
 * Helper function to remember a map for tracking expanded state of multiple items by ID.
 * Useful for lists where each item can be independently expanded/collapsed.
 * Uses SnapshotStateMap so that mutations trigger recomposition.
 *
 * @return A state-aware mutable map where keys are item IDs and values are expanded states
 */
@Composable
fun rememberExpandedStateMap(): SnapshotStateMap<Int, Boolean> {
    return remember { mutableStateMapOf() }
}

/**
 * An expandable SmartList item that shows/hides child content when clicked.
 * Displays a chevron icon that rotates when expanded.
 *
 * @param headlineContent The main text content of the item
 * @param expanded Whether the item is currently expanded
 * @param onExpandToggle Callback invoked when the expand button is clicked
 * @param modifier Modifier for styling
 * @param supportingContent Optional supporting text shown below headline
 * @param leadingContent Optional content shown at the start (e.g., icon)
 * @param position Position in the list for proper corner radius
 * @param visible Whether this item is visible (with animation)
 * @param childContent The content to show when expanded (nested items, details, etc.)
 */

@Composable
fun SmartListExpandableItem(
    headlineContent: @Composable () -> Unit,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    shapes: ListItemShapes = smartListSegmentedShapes(index = 0, count = 1),
    visible: Boolean = true,
    childContent: @Composable ColumnScope.() -> Unit,
) {
    // Track if this item has ever transitioned from invisible to visible
    val hasAnimated = remember { mutableStateOf(!visible) }

    // Update animation state when visibility changes
    LaunchedEffect(visible) {
        if (visible && !hasAnimated.value) {
            hasAnimated.value = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (!hasAnimated.value) {
            fadeIn(tween(0)) + expandVertically(tween(0))
        } else {
            fadeIn() + expandVertically()
        },
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            // Main item with expand button
            val itemModifier = modifier
                .clip(shapes.shape)
                .clickable(onClick = onExpandToggle)

            ListItem(
                headlineContent = headlineContent,
                modifier = itemModifier,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) {
                            stringResource(
                                Res.string.accessibility_action_collapse,
                            )
                        } else {
                            stringResource(Res.string.accessibility_action_expand)
                        },
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )

            // Child content (expanded details)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                ) {
                    childContent()
                }
            }
        }
    }
}

/**
 * An expandable section header for grouping multiple SmartList items.
 * Useful for creating collapsible sections like "Today's Alarms", "Tomorrow's Alarms", etc.
 *
 * @param title The section title text
 * @param expanded Whether the section is currently expanded
 * @param onExpandToggle Callback invoked when the section header is clicked
 * @param modifier Modifier for styling
 * @param badge Optional text to show in a badge (e.g., count: "3")
 * @param content The child items to show when expanded
 */
@Composable
fun SmartListExpandableSection(
    title: String,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        // Section header
        Row(
            modifier = Modifier
                .clickable(onClick = onExpandToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) {
                    stringResource(
                        Res.string.accessibility_action_collapse,
                    )
                } else {
                    stringResource(Res.string.accessibility_action_expand)
                },
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        // Section content
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
        ) {
            Column {
                content()
            }
        }
    }
}
