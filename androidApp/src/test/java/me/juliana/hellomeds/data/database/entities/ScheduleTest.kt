// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.entities

import me.juliana.hellomeds.createSchedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ScheduleTest {

    private fun todayStartMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun daysFromNow(days: Long): Long =
        LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `active schedule is not effectively archived`() {
        val schedule = createSchedule(isArchived = false, endDate = null)
        assertFalse(schedule.isEffectivelyArchived())
    }

    @Test
    fun `manually archived is effectively archived`() {
        val schedule = createSchedule(isArchived = true)
        assertTrue(schedule.isEffectivelyArchived())
    }

    @Test
    fun `date-archived is effectively archived`() {
        val schedule = createSchedule(isArchived = false, endDate = daysFromNow(-1))
        assertTrue(schedule.isEffectivelyArchived())
    }

    @Test
    fun `endDate today is not date-archived`() {
        // endDate = today's start-of-day is NOT < today's start-of-day
        val schedule = createSchedule(isArchived = false, endDate = todayStartMillis())
        assertFalse(schedule.isDateArchived())
    }

    @Test
    fun `endDate in future is not archived`() {
        val schedule = createSchedule(isArchived = false, endDate = daysFromNow(1))
        assertFalse(schedule.isEffectivelyArchived())
    }

    @Test
    fun `null endDate is not date-archived`() {
        val schedule = createSchedule(isArchived = false, endDate = null)
        assertFalse(schedule.isDateArchived())
    }

    @Test
    fun `isManuallyArchived true only for flag`() {
        val schedule = createSchedule(isArchived = true)
        assertTrue(schedule.isManuallyArchived())
    }

    @Test
    fun `isManuallyArchived false when only date-archived`() {
        val schedule = createSchedule(isArchived = false, endDate = daysFromNow(-1))
        assertFalse(schedule.isManuallyArchived())
    }
}
