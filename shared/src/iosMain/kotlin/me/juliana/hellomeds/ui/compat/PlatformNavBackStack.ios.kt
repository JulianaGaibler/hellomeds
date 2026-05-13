// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

@Composable
actual fun rememberPlatformNavBackStack(initialRoute: NavKey): NavBackStack<NavKey> {
    // On iOS, create the back stack manually without the saved state infrastructure
    // that is Android-specific in the CMP Nav3 alpha runtime.
    return remember {
        NavBackStack(mutableStateListOf(initialRoute))
    }
}
