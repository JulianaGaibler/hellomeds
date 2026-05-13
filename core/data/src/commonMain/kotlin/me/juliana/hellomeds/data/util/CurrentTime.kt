// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import kotlin.time.Clock

/**
 * KMP-compatible replacement for System.currentTimeMillis().
 * Uses kotlin.time.Clock.System which works on all platforms.
 */
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
