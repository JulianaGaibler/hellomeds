// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable

@Composable
actual fun ConfigureStatusBar(isDarkTheme: Boolean) {
    // No-op on iOS — status bar appearance is managed by the system
}
