// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * iOS fallback: FAB with expandable pill-shaped menu items.
 * Styled to match the M3 Expressive FloatingActionButtonMenu on Android.
 */
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
) {
    Column(
        modifier = modifier.padding(end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEachIndexed { i, item ->
                    Button(
                        onClick = { onItemClick(i) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Icon(item.icon, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        if (visible) {
            FloatingActionButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.semantics {
                    stateDescription = if (expanded) expandedLabel else collapsedLabel
                    contentDescription = toggleMenuLabel
                },
                containerColor = if (expanded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    FloatingActionButtonDefaults.containerColor
                },
                contentColor = if (expanded) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = toggleMenuLabel,
                )
            }
        }
    }
}
