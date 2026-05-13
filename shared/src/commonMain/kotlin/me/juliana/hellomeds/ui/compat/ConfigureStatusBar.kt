// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable

/**
 * Configures status bar icon appearance (light/dark) based on the current theme.
 *
 * On Android, this uses WindowCompat to set light/dark status bar icons.
 * On iOS, this is a no-op (status bar appearance is managed by the system).
 */
@Composable
expect fun ConfigureStatusBar(isDarkTheme: Boolean)
