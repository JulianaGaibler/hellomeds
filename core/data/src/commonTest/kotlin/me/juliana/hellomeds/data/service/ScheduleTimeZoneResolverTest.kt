// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import kotlinx.datetime.TimeZone
import me.juliana.hellomeds.data.createMedication
import me.juliana.hellomeds.data.createSchedule
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleTimeZoneResolverTest {

    @Test
    fun localMode_returnsSystemDefault() {
        val medication = createMedication(timeZoneMode = TimeZoneMode.LOCAL)
        val schedule = createSchedule(originTimeZone = "America/New_York")

        val result = ScheduleTimeZoneResolver.resolve(medication, schedule)

        assertEquals(TimeZone.currentSystemDefault(), result)
    }

    @Test
    fun fixedMode_withOriginTz_returnsOriginTz() {
        val medication = createMedication(timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(originTimeZone = "Asia/Tokyo")

        val result = ScheduleTimeZoneResolver.resolve(medication, schedule)

        assertEquals(TimeZone.of("Asia/Tokyo"), result)
    }

    @Test
    fun fixedMode_withNullOriginTz_returnsSystemDefault() {
        val medication = createMedication(timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(originTimeZone = null)

        val result = ScheduleTimeZoneResolver.resolve(medication, schedule)

        assertEquals(TimeZone.currentSystemDefault(), result)
    }

    @Test
    fun fixedMode_withInvalidOriginTz_returnsSystemDefault() {
        val medication = createMedication(timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(originTimeZone = "Not/A/Timezone")

        val result = ScheduleTimeZoneResolver.resolve(medication, schedule)

        assertEquals(TimeZone.currentSystemDefault(), result)
    }

    @Test
    fun nullMedication_returnsSystemDefault() {
        val schedule = createSchedule(originTimeZone = "America/New_York")

        val result = ScheduleTimeZoneResolver.resolve(null, schedule)

        assertEquals(TimeZone.currentSystemDefault(), result)
    }

    @Test
    fun localMode_ignoresOriginTz() {
        val medication = createMedication(timeZoneMode = TimeZoneMode.LOCAL)
        val schedule = createSchedule(originTimeZone = "Asia/Tokyo")

        val result = ScheduleTimeZoneResolver.resolve(medication, schedule)

        // Should return system default, not Tokyo, because mode is LOCAL
        assertEquals(TimeZone.currentSystemDefault(), result)
    }

    @Test
    fun fixedMode_withAnchorTz_prefersAnchorOverScheduleOrigin() {
        val medication = createMedication(
            timeZoneMode = TimeZoneMode.FIXED,
            anchorTimeZone = "Europe/London",
        )
        val schedule = createSchedule(originTimeZone = "Asia/Tokyo")

        val result = ScheduleTimeZoneResolver.resolve(medication, schedule)

        // Should use medication anchor, not schedule origin
        assertEquals(TimeZone.of("Europe/London"), result)
    }

    @Test
    fun fixedMode_withNullAnchorTz_fallsBackToScheduleOrigin() {
        val medication = createMedication(
            timeZoneMode = TimeZoneMode.FIXED,
            anchorTimeZone = null,
        )
        val schedule = createSchedule(originTimeZone = "Asia/Tokyo")

        val result = ScheduleTimeZoneResolver.resolve(medication, schedule)

        // Should fall back to schedule origin
        assertEquals(TimeZone.of("Asia/Tokyo"), result)
    }
}
