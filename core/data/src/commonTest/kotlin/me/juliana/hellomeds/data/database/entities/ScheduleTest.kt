// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.entities

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.createSchedule
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class ScheduleTest {

    // Uses kotlinx.datetime (KMP-safe) instead of java.time.
    // Note: Schedule.isDateArchived() calls Clock.System.now() internally,
    // so test helpers must use the real current date. Clock injection (QUALITY-5)
    // can make this fully deterministic later.
    private val tz = TimeZone.currentSystemDefault()
    private val today = Clock.System.now().toLocalDateTime(tz).date
    private val todayStartMillis = today.atStartOfDayIn(tz).toEpochMilliseconds()

    private fun daysFromToday(days: Int): Long =
        today.plus(days, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()

    @Test
    fun activeSchedule_isNotEffectivelyArchived() {
        val schedule = createSchedule(isArchived = false, endDate = null)
        assertFalse(schedule.isEffectivelyArchived())
    }

    @Test
    fun manuallyArchived_isEffectivelyArchived() {
        val schedule = createSchedule(isArchived = true)
        assertTrue(schedule.isEffectivelyArchived())
    }

    @Test
    fun dateArchived_isEffectivelyArchived() {
        val schedule = createSchedule(isArchived = false, endDate = daysFromToday(-1))
        assertTrue(schedule.isEffectivelyArchived())
    }

    @Test
    fun endDateToday_isNotDateArchived() {
        // endDate = today's start-of-day is NOT < today's start-of-day
        val schedule = createSchedule(isArchived = false, endDate = todayStartMillis)
        assertFalse(schedule.isDateArchived())
    }

    @Test
    fun endDateInFuture_isNotArchived() {
        val schedule = createSchedule(isArchived = false, endDate = daysFromToday(1))
        assertFalse(schedule.isEffectivelyArchived())
    }

    @Test
    fun nullEndDate_isNotDateArchived() {
        val schedule = createSchedule(isArchived = false, endDate = null)
        assertFalse(schedule.isDateArchived())
    }

    @Test
    fun isManuallyArchived_trueOnlyForFlag() {
        val schedule = createSchedule(isArchived = true)
        assertTrue(schedule.isManuallyArchived())
    }

    @Test
    fun isManuallyArchived_falseWhenOnlyDateArchived() {
        val schedule = createSchedule(isArchived = false, endDate = daysFromToday(-1))
        assertFalse(schedule.isManuallyArchived())
    }
}
