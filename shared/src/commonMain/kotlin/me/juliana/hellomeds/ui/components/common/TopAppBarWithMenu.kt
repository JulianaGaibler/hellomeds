// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarWithMenu(
    title: String,
    onNavigateToSettings: () -> Unit,
    onNavigateToSupport: () -> Unit,
    modifier: Modifier = Modifier,
    primaryAction: OverflowMenuPrimaryAction? = null,
    menuContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
            )
        },
        actions = {
            OverflowMenu(
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSupport = onNavigateToSupport,
                primaryAction = primaryAction,
                customContent = menuContent,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    )
}
