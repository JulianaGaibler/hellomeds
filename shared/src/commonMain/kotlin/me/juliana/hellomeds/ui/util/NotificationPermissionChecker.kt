// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable

/**
 * Reactively checks whether notification permission is currently granted.
 *
 * On Android: Uses NotificationManagerCompat.areNotificationsEnabled().
 * On iOS: Uses UNUserNotificationCenter and re-checks on app resume.
 *
 * This is useful for detecting permission revocation after the user
 * has left the app and changed settings.
 */
@Composable
expect fun isNotificationPermissionGranted(): Boolean
