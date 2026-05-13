// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal providing permission warning state to all screens.
 *
 * Provided once at the app root (AdaptiveMainScreen) via [rememberPermissionWarnings].
 * Screens consume with `LocalPermissionWarnings.current` — zero observer overhead.
 *
 * Uses compositionLocalOf (not static) because the state updates on each app resume,
 * and we want targeted recomposition of reading screens rather than full-tree invalidation.
 */
val LocalPermissionWarnings = compositionLocalOf { PermissionWarningState() }

/**
 * Checks all permission states and re-checks on app resume (lifecycle-aware).
 *
 * On Android: Uses Lifecycle.ON_RESUME to detect settings changes.
 * On iOS: Uses UIApplicationDidBecomeActiveNotification.
 *
 * Call once at the app root and provide via [LocalPermissionWarnings].
 */
@Composable
expect fun rememberPermissionWarnings(): PermissionWarningState
