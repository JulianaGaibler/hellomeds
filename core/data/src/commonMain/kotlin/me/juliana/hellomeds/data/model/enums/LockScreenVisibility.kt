// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

/**
 * Defines how notifications are displayed on the lock screen.
 */
enum class LockScreenVisibility {
    /**
     * Show notifications on lock screen with full medication names and details
     */
    SHOW_WITH_NAMES,

    /**
     * Show notifications on lock screen but hide sensitive details (medication names)
     */
    SHOW_WITHOUT_NAMES,

    /**
     * Don't show notifications on lock screen at all
     */
    HIDE,
}
