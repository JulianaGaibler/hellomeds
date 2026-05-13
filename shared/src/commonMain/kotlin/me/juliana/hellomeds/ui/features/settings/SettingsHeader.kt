// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared horizontal inset for text rendered directly in a settings-style LazyColumn
 * (outside SmartList items). Keeps headings + body paragraphs visually consistent across
 * Settings, subpages, and backup screens. Update here to change everywhere.
 */
val SettingsContentHorizontalPadding: Dp = 8.dp

/**
 * Apply the shared settings-style horizontal padding to free-standing text
 * (intros, descriptions, inline warnings). SmartList items don't need this — they have
 * their own ListItem padding.
 */
fun Modifier.settingsContentPadding(): Modifier = this.padding(horizontal = SettingsContentHorizontalPadding)

/**
 * Section header used across the Settings screen and its subpages.
 * Larger, bolder than the default list header to give clear visual grouping.
 */
@Composable
fun SettingsHeader(text: String, isFirst: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .settingsContentPadding()
            .padding(
                top = if (isFirst) 8.dp else 48.dp,
                bottom = 12.dp,
            )
            .semantics { heading() },
    )
}
