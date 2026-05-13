// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.compat

import androidx.compose.runtime.Composable

/**
 * Force dark theme wrapper for onboarding screens.
 *
 * On Android, uses Material You dynamic dark colors on Android 12+ for personalized theming
 * based on the user's wallpaper, and forces light status bar icons.
 *
 * On iOS, uses a standard Material 3 dark color scheme.
 */
@Composable
expect fun DynamicDarkOnboardingTheme(content: @Composable () -> Unit)
