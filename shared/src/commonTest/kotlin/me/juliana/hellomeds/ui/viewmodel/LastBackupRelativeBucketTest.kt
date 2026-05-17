// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LastBackupRelativeBucketTest {

    private val now: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun bucket(elapsedMs: Long): LastBackupBucket =
        lastBackupRelativeBucket(now.toEpochMilliseconds() - elapsedMs, now)

    @Test
    fun zeroTimestamp_isNever() {
        assertEquals(LastBackupBucket.Never, lastBackupRelativeBucket(0L, now))
    }

    @Test
    fun exactlyNow_isJustNow() {
        // timestamp == now → elapsed == 0 → should be JustNow without div-by-zero
        assertEquals(LastBackupBucket.JustNow, bucket(elapsedMs = 0L))
    }

    @Test
    fun thirtySeconds_isJustNow() {
        assertEquals(LastBackupBucket.JustNow, bucket(elapsedMs = 30.seconds.inWholeMilliseconds))
    }

    @Test
    fun oneMinute_isMinutesAgoOne() {
        assertEquals(LastBackupBucket.MinutesAgo(1), bucket(elapsedMs = 1.minutes.inWholeMilliseconds))
    }

    @Test
    fun fiftyNineMinutes_isMinutesAgo() {
        assertEquals(LastBackupBucket.MinutesAgo(59), bucket(elapsedMs = 59.minutes.inWholeMilliseconds))
    }

    @Test
    fun oneHour_isHoursAgoOne() {
        assertEquals(LastBackupBucket.HoursAgo(1), bucket(elapsedMs = 1.hours.inWholeMilliseconds))
    }

    @Test
    fun twentyThreeHours_isHoursAgo() {
        assertEquals(LastBackupBucket.HoursAgo(23), bucket(elapsedMs = 23.hours.inWholeMilliseconds))
    }

    @Test
    fun oneDay_isDaysAgoOne() {
        assertEquals(LastBackupBucket.DaysAgo(1), bucket(elapsedMs = 1.days.inWholeMilliseconds))
    }

    @Test
    fun twentyNineDays_isDaysAgo() {
        assertEquals(LastBackupBucket.DaysAgo(29), bucket(elapsedMs = 29.days.inWholeMilliseconds))
    }

    @Test
    fun thirtyDays_isLongAgo() {
        assertEquals(LastBackupBucket.LongAgo, bucket(elapsedMs = 30.days.inWholeMilliseconds))
    }

    @Test
    fun negativeElapsed_clockSkew_isJustNow() {
        // Timestamp in the future (clock skew). Must not throw; falls into the smallest bucket.
        val futureTimestamp = now.toEpochMilliseconds() + 5.minutes.inWholeMilliseconds
        assertEquals(LastBackupBucket.JustNow, lastBackupRelativeBucket(futureTimestamp, now))
    }
}
