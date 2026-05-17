// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centered empty-state block: optional illustration, optional title, optional description,
 * and an optional trailing action slot (e.g. a CTA button).
 *
 * Pass [contentDescription] when there's no visible [title] but the empty state still needs
 * an announcement for screen readers — e.g. inside a bottom sheet where the surrounding
 * context already names the screen.
 *
 * Outer padding is left to the caller — wrap in whatever container the host screen
 * already uses.
 */
@Composable
fun EmptyState(
    title: String? = null,
    modifier: Modifier = Modifier,
    description: String? = null,
    illustration: Painter? = null,
    illustrationSize: Dp = 200.dp,
    contentDescription: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val rootModifier = if (contentDescription != null) {
        modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription }
    } else {
        modifier.fillMaxWidth()
    }
    Column(
        modifier = rootModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val hasContentBelowIllustration = title != null || description != null || action != null
        if (illustration != null) {
            Image(
                painter = illustration,
                contentDescription = null,
                modifier = Modifier.size(illustrationSize),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.outlineVariant),
            )
            if (hasContentBelowIllustration) {
                Spacer(Modifier.height(32.dp))
            }
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (description != null) {
            if (title != null) Spacer(Modifier.height(16.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            if (title != null || description != null) Spacer(Modifier.height(24.dp))
            action()
        }
    }
}
