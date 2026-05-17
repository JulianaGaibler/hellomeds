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

/** Expanded-state per item ID; SnapshotStateMap so mutations trigger recomposition. */
@Composable
fun rememberExpandedStateMap(): SnapshotStateMap<Int, Boolean> {
    return remember { mutableStateMapOf() }
}

/** Expandable SmartList item with a rotating chevron and child content revealed on click. */
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
    // Suppress the enter animation on the very first frame this item appears.
    val hasAnimated = remember { mutableStateOf(!visible) }

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

/** Collapsible section header for grouping SmartList items (e.g., "Today's Alarms"). */
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
