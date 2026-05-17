// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ExpandableFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<FabMenuItem>,
    onItemClick: (index: Int) -> Unit,
    modifier: Modifier,
    visible: Boolean,
    tooltipText: String,
    expandedLabel: String,
    collapsedLabel: String,
    toggleMenuLabel: String,
    closeMenuLabel: String,
    toggleTestTag: String?,
) {
    FloatingActionButtonMenu(
        modifier = modifier,
        expanded = expanded,
        button = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    if (expanded) TooltipAnchorPosition.Start else TooltipAnchorPosition.Above,
                ),
                tooltip = { PlainTooltip { Text(tooltipText) } },
                state = rememberTooltipState(),
            ) {
                ToggleFloatingActionButton(
                    modifier = Modifier
                        .then(
                            if (toggleTestTag != null) Modifier.testTag(toggleTestTag) else Modifier,
                        )
                        .semantics {
                            stateDescription = if (expanded) expandedLabel else collapsedLabel
                            contentDescription = toggleMenuLabel
                        }
                        .animateFloatingActionButton(
                            visible = visible,
                            alignment = Alignment.BottomEnd,
                        ),
                    checked = expanded,
                    onCheckedChange = { onExpandedChange(!expanded) },
                ) {
                    val imageVector by remember {
                        derivedStateOf {
                            if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                        }
                    }
                    Icon(
                        painter = rememberVectorPainter(imageVector),
                        contentDescription = null,
                        modifier = Modifier.animateIcon({ checkedProgress }),
                    )
                }
            }
        },
    ) {
        items.forEachIndexed { i, item ->
            FloatingActionButtonMenuItem(
                modifier = if (item.testTag != null) Modifier.testTag(item.testTag) else Modifier,
                onClick = { onItemClick(i) },
                icon = { Icon(item.icon, contentDescription = null) },
                text = { Text(text = item.label) },
            )
        }
    }
}
