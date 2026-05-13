// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable

/**
 * Returns a platform context usable with [PermissionUtils] and [NotificationChannels].
 *
 * On Android this returns `LocalContext.current` (an `android.content.Context`).
 * On iOS this returns `Unit`.
 *
 * The return type is `Any` to keep the boundary narrow; platform callers should
 * pass the value straight through to expect/actual utilities that accept `Any`.
 */
@Composable
expect fun platformContext(): Any
