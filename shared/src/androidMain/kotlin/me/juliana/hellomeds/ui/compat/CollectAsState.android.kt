// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle as androidxCollectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
actual fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> = androidxCollectAsStateWithLifecycle()

@Composable
actual fun <T> Flow<T>.collectAsStateWithLifecycle(initial: T): State<T> =
    androidxCollectAsStateWithLifecycle(initialValue = initial)
