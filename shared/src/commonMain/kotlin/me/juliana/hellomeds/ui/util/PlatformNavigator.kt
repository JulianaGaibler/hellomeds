// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

/**
 * Bridge for platform-specific deep-links into system settings panels.
 *
 * Lives in `commonMain` so shared UI can fire intents/URLs without
 * knowing the underlying API. Implementations are wired via Koin per
 * platform — `expect class` rather than an interface so each platform
 * can hold the resources it needs (e.g. Android needs a `Context`).
 */
expect class PlatformNavigator {
    /**
     * Open the system settings panel where the user can grant
     * `SCHEDULE_EXACT_ALARM`. On iOS this permission has no equivalent —
     * implementations route to the app-level settings page (or no-op).
     */
    fun openExactAlarmSettings()
}
