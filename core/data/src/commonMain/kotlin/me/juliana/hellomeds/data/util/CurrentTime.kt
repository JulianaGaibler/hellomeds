// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * KMP-compatible replacement for System.currentTimeMillis().
 * Uses kotlin.time.Clock.System which works on all platforms.
 */
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Epoch millis of local midnight (device timezone) on the day containing [epochMillis]. */
fun startOfDayMillis(epochMillis: Long = currentTimeMillis(), tz: TimeZone = TimeZone.currentSystemDefault()): Long =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()
