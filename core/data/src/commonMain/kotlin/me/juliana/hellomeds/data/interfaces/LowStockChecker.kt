// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.interfaces

/**
 * Platform-agnostic interface for low stock notification checking.
 * Android: LowStockNotifier (Android notification system)
 * iOS: iOS UNNotification-based implementation
 */
interface LowStockChecker {
    suspend fun checkAndNotify(medicationId: Int)
}
