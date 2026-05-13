// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-aware Flow collection for Compose.
 *
 * On Android, delegates to [androidx.lifecycle.compose.collectAsStateWithLifecycle], which
 * pauses upstream collection when the host activity goes to STOPPED. This avoids redrawing
 * off-screen UI on every Room emission while the device is locked / app is backgrounded.
 *
 * On iOS, falls back to plain [androidx.compose.runtime.collectAsState] — iOS has no
 * equivalent activity lifecycle and Compose Multiplatform does not yet expose a parity API.
 */
@Composable
expect fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T>

@Composable
expect fun <T> Flow<T>.collectAsStateWithLifecycle(initial: T): State<T>
