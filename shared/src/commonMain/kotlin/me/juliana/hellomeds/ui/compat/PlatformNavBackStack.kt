// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Platform-specific wrapper for [rememberNavBackStack] which has different
 * signatures in the Android vs CMP Nav3 runtimes.
 */
@Composable
expect fun rememberPlatformNavBackStack(initialRoute: NavKey): NavBackStack<NavKey>
