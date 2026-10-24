// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.preferences

/**
 * Persistent cursor state for [me.juliana.hellomeds.data.service.UnloggedDoseResolver].
 * Implemented by [ReliabilityPreferences]; tests substitute an in-memory store.
 */
interface ResolverStateStore {
    /** Everything strictly before this instant has been resolved. Null before the first run. */
    suspend fun getWatermark(): Long?
    suspend fun setWatermark(value: Long)

    /** Device timezone at the last resolver run, used to detect timezone changes. */
    suspend fun getLastSeenTimeZoneId(): String?
    suspend fun setLastSeenTimeZoneId(id: String)

    /** Adds to the counts shown in the "while you were away" banner until the user dismisses it. */
    suspend fun addAwaySummary(skipped: Int, taken: Int)
}
