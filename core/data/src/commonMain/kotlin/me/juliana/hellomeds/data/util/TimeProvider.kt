// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Single source of truth for "now" across the entire app.
 *
 * Background: silent dose duplication / missed reminders were traced to mixing
 * `System.currentTimeMillis()` with `Clock.System.now().toEpochMilliseconds()`
 * in different code paths. With this provider injected via Koin, all callers
 * read the same clock and tests can substitute a fake provider.
 *
 * Use [now] when working with [kotlin.time.Instant] (preferred for new code).
 * Use [nowMillis] for legacy `Long`-based APIs (alarm triggers, Room timestamps).
 */
interface TimeProvider {
    fun now(): Instant
    fun nowMillis(): Long = now().toEpochMilliseconds()
}

class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.System.now()
}
