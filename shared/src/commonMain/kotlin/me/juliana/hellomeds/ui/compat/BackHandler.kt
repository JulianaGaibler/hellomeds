// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable

/**
 * Cross-platform BackHandler.
 *
 * On Android this delegates to [androidx.activity.compose.BackHandler].
 * On iOS this is a no-op (iOS uses its own navigation patterns).
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
