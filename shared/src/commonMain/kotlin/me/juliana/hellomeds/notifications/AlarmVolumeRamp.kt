// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlin.math.pow

/**
 * Fade-in curve for the alarm sound, shared by Android and iOS.
 *
 * Loudness is perceived logarithmically, so a linear amplitude ramp sounds almost
 * full volume after a couple of seconds. This ramp rises by equal decibel steps
 * instead, from about -26 dB to full volume, which sounds like a steady climb.
 */
object AlarmVolumeRamp {
    const val START_VOLUME: Float = 0.05f
    const val TARGET_VOLUME: Float = 1.0f
    const val DURATION_MS: Long = 30_000L
    const val TICK_MS: Long = 100L

    /** Returns the player volume scalar after [elapsedMs] of ramping. */
    fun volumeAt(elapsedMs: Long): Float {
        val fraction = (elapsedMs.toFloat() / DURATION_MS).coerceIn(0f, 1f)
        return START_VOLUME * (TARGET_VOLUME / START_VOLUME).pow(fraction)
    }
}
