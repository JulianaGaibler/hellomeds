// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.interfaces

/**
 * Platform-agnostic interface for depletion reminder notification checking.
 * Android: DepletionReminderNotifier (Android notification system)
 * iOS: iOS UNNotification-based implementation
 */
interface DepletionChecker {
    suspend fun checkAndNotify(medicationId: Int)
}
