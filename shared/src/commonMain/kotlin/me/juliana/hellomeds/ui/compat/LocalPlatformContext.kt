// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable

/**
 * Platform context for [PermissionUtils] and [NotificationChannels] — Android `LocalContext.current`
 * (an `android.content.Context`), iOS `Unit`. Return type is `Any` to keep the boundary narrow;
 * callers pass the value straight through to expect/actual utilities that accept `Any`.
 */
@Composable
expect fun platformContext(): Any
