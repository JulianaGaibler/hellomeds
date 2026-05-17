// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.backup.FakeHistoryDao
import me.juliana.hellomeds.data.backup.FakeMedicationDao
import me.juliana.hellomeds.data.backup.FakeScheduleDao
import me.juliana.hellomeds.data.createHistory
import me.juliana.hellomeds.data.createMedication
import me.juliana.hellomeds.data.createSchedule
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.data.toEpochMillis
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ScheduleProjectorTest {

    private lateinit var projector: ScheduleProjector

    @BeforeTest
    fun setup() {
        // Pure methods under test don't use DAOs — fakes satisfy the constructor.
        projector = ScheduleProjector(
            scheduleDao = FakeScheduleDao(),
            historyDao = FakeHistoryDao(),
            medicationDao = FakeMedicationDao(),
        )
    }

    // --- Interval frequency ---

    @Test
    fun interval_daily_generatesCorrectEventCount() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 7)
        val schedule = createSchedule(
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        assertEquals(7, events.size)
    }

    @Test
    fun interval_everyOtherDay_generatesCorrectSpacing() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 10)
        val schedule = createSchedule(
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 2,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        assertEquals(5, events.size)
        for (i in 1 until events.size) {
            val gapMs = events[i].scheduledTime - events[i - 1].scheduledTime
            val gapDays = gapMs / (24 * 60 * 60 * 1000L)
            assertEquals(2L, gapDays)
        }
    }

    @Test
    fun interval_alignsWhenScheduleStartsBeforeQueryWindow() {
        val scheduleStart = LocalDate(2025, 3, 1)
        val from = LocalDate(2025, 3, 11) // 10 days later
        val to = LocalDate(2025, 3, 20)
        val schedule = createSchedule(
            startDate = scheduleStart.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 3,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        // From March 1 with freq=3: Mar 1, 4, 7, 10, 13, 16, 19
        // In window [Mar 11, Mar 20]: Mar 13, 16, 19
        assertTrue(events.isNotEmpty())
        for (event in events) {
            val eventDate = Instant.fromEpochMilliseconds(event.scheduledTime)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            val daysSinceStart = scheduleStart.daysUntil(eventDate).toLong()
            assertEquals(0L, daysSinceStart % 3, "Event on $eventDate should be aligned to freq=3")
        }
    }

    @Test
    fun interval_respectsScheduleStartDateWithinWindow() {
        val from = LocalDate(2025, 3, 1)
        val scheduleStart = LocalDate(2025, 3, 4)
        val to = LocalDate(2025, 3, 7)
        val schedule = createSchedule(
            startDate = scheduleStart.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        assertEquals(4, events.size)
        val firstEventDate =
            Instant.fromEpochMilliseconds(events.first().scheduledTime)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
        assertEquals(scheduleStart, firstEventDate)
    }

    @Test
    fun interval_respectsScheduleEndDate() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 10)
        val scheduleEnd = LocalDate(2025, 3, 5)
        val schedule = createSchedule(
            startDate = from.toEpochMillis(),
            endDate = scheduleEnd.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        assertEquals(5, events.size)
    }

    @Test
    fun interval_endDateBeforeWindow_returnsEmpty() {
        val from = LocalDate(2025, 3, 10)
        val to = LocalDate(2025, 3, 20)
        val schedule = createSchedule(
            startDate = LocalDate(2025, 3, 1).toEpochMillis(),
            endDate = LocalDate(2025, 3, 5).toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        assertTrue(events.isEmpty())
    }

    @Test
    fun interval_startDateAfterWindow_returnsEmpty() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 5)
        val schedule = createSchedule(
            startDate = LocalDate(2025, 3, 10).toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        assertTrue(events.isEmpty())
    }

    // --- Days-of-week frequency ---

    @Test
    fun daysOfWeek_generatesEventsOnSpecifiedDays() {
        val from = LocalDate(2025, 3, 3) // Monday
        val to = LocalDate(2025, 3, 16) // Sunday (2 full weeks)
        val schedule = createSchedule(
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.DAYS_OF_WEEK,
            frequencyValue = 0,
            daysOfWeek = "MONDAY,WEDNESDAY,FRIDAY",
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        assertEquals(6, events.size)
        events.forEach { event ->
            val day = Instant.fromEpochMilliseconds(event.scheduledTime)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek
            assertTrue(
                day in listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                "Expected MWF, got $day",
            )
        }
    }

    @Test
    fun daysOfWeek_withSingleDay() {
        val from = LocalDate(2025, 3, 3) // Monday
        val to = LocalDate(2025, 3, 16) // Sunday
        val schedule = createSchedule(
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.DAYS_OF_WEEK,
            frequencyValue = 0,
            daysOfWeek = "MONDAY",
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        assertEquals(2, events.size)
    }

    @Test
    fun daysOfWeek_nullDaysOfWeek_returnsEmpty() {
        val from = LocalDate(2025, 3, 3)
        val to = LocalDate(2025, 3, 16)
        val schedule = createSchedule(
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.DAYS_OF_WEEK,
            frequencyValue = 0,
            daysOfWeek = null,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        assertTrue(events.isEmpty())
    }

    @Test
    fun daysOfWeek_respectsStartDateAndEndDate() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 31)
        val schedule = createSchedule(
            startDate = LocalDate(2025, 3, 10).toEpochMillis(),
            endDate = LocalDate(2025, 3, 20).toEpochMillis(),
            frequencyType = FrequencyType.DAYS_OF_WEEK,
            frequencyValue = 0,
            daysOfWeek = "MONDAY,FRIDAY",
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        events.forEach { event ->
            val date = Instant.fromEpochMilliseconds(event.scheduledTime)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            assertFalse(date < LocalDate(2025, 3, 10), "Event $date should not be before startDate")
            assertFalse(date > LocalDate(2025, 3, 20), "Event $date should not be after endDate")
        }
    }

    @Test
    fun daysOfWeek_allSevenDays_matchesDailyInterval() {
        val from = LocalDate(2025, 3, 3)
        val to = LocalDate(2025, 3, 9) // 7 days
        val allDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY"
        val dowSchedule = createSchedule(
            id = 1,
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.DAYS_OF_WEEK,
            frequencyValue = 0,
            daysOfWeek = allDays,
            timeOfDay = "08:00",
        )
        val intervalSchedule = createSchedule(
            id = 2,
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val dowEvents = projector.generateEventsForSchedule(dowSchedule, from, to)
        val intervalEvents = projector.generateEventsForSchedule(intervalSchedule, from, to)

        assertEquals(intervalEvents.size, dowEvents.size)
        dowEvents.zip(intervalEvents).forEach { (dow, interval) ->
            assertEquals(dow.scheduledTime, interval.scheduledTime)
        }
    }

    // --- History matching ---

    @Test
    fun marksTakenEventsAsNonPending() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 1)
        val schedule = createSchedule(
            id = 1,
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val eventTime = from.toEpochMillis("08:00")
        val history = listOf(
            createHistory(
                scheduleId = 1,
                scheduledTime = eventTime,
                status = MedicationHistory.STATUS_TAKEN,
            ),
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            from.toEpochMillis(),
            to.toEpochMillis("23:59"),
            history,
        )

        assertEquals(1, events.size)
        assertFalse(events[0].isPending)
        assertTrue(events[0].isTaken)
    }

    @Test
    fun leavesEventsWithoutHistoryAsPending() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 1)
        val schedule = createSchedule(
            id = 1,
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            from.toEpochMillis(),
            to.toEpochMillis("23:59"),
            emptyList(),
        )

        assertEquals(1, events.size)
        assertTrue(events[0].isPending)
        assertNull(events[0].historyRecord)
    }

    @Test
    fun compositeKeyPreventsCrossScheduleContamination() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 1)
        val schedule1 = createSchedule(id = 1, medicationId = 1, startDate = from.toEpochMillis(), timeOfDay = "08:00")
        val schedule2 = createSchedule(id = 2, medicationId = 2, startDate = from.toEpochMillis(), timeOfDay = "08:00")

        val eventTime = from.toEpochMillis("08:00")
        val history = listOf(
            createHistory(scheduleId = 1, scheduledTime = eventTime, status = MedicationHistory.STATUS_TAKEN),
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule1, schedule2),
            from.toEpochMillis(),
            to.toEpochMillis("23:59"),
            history,
        )

        assertEquals(2, events.size)
        val event1 = events.first { it.scheduleId == 1 }
        val event2 = events.first { it.scheduleId == 2 }
        assertFalse(event1.isPending)
        assertTrue(event2.isPending)
    }

    @Test
    fun historyWithNullScheduleIdExcludedFromIndex() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 1)
        val schedule = createSchedule(id = 1, startDate = from.toEpochMillis(), timeOfDay = "08:00")

        val eventTime = from.toEpochMillis("08:00")
        val history = listOf(
            createHistory(scheduleId = null, scheduledTime = eventTime, status = MedicationHistory.STATUS_TAKEN),
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            from.toEpochMillis(),
            to.toEpochMillis("23:59"),
            history,
        )

        assertEquals(1, events.size)
        assertTrue(events[0].isPending, "Event should remain pending when history has null scheduleId")
    }

    @Test
    fun filtersArchivedSchedules() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 3)
        val active = createSchedule(id = 1, startDate = from.toEpochMillis(), isArchived = false, timeOfDay = "08:00")
        val archived = createSchedule(id = 2, startDate = from.toEpochMillis(), isArchived = true, timeOfDay = "08:00")

        val events = projector.projectEventsWithHistory(
            listOf(active, archived),
            from.toEpochMillis(),
            to.toEpochMillis("23:59"),
            emptyList(),
        )

        assertTrue(events.all { it.scheduleId == 1 })
    }

    // --- Edge cases ---

    @Test
    fun eventsOutsideTimeRangeAreFiltered() {
        val from = LocalDate(2025, 3, 1)
        val schedule = createSchedule(id = 1, startDate = from.toEpochMillis(), timeOfDay = "08:00")

        val narrowStart = LocalDate(2025, 3, 2).toEpochMillis()
        val narrowEnd = LocalDate(2025, 3, 3).toEpochMillis("23:59")

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            narrowStart,
            narrowEnd,
            emptyList(),
        )

        assertEquals(2, events.size)
    }

    @Test
    fun eventsAreSortedByScheduledTime() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 3)
        val morning = createSchedule(id = 1, startDate = from.toEpochMillis(), timeOfDay = "08:00")
        val evening = createSchedule(id = 2, startDate = from.toEpochMillis(), timeOfDay = "20:00")

        val events = projector.projectEventsWithHistory(
            listOf(evening, morning),
            from.toEpochMillis(),
            to.toEpochMillis("23:59"),
            emptyList(),
        )

        for (i in 1 until events.size) {
            assertTrue(
                events[i].scheduledTime >= events[i - 1].scheduledTime,
                "Events should be sorted ascending",
            )
        }
    }

    @Test
    fun emptyScheduleListReturnsEmpty() {
        val events = projector.projectEventsWithHistory(
            emptyList(),
            LocalDate(2025, 3, 1).toEpochMillis(),
            LocalDate(2025, 3, 5).toEpochMillis("23:59"),
            emptyList(),
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun dstSpringForwardDoesNotLoseEvents() {
        // In America/New_York, DST spring-forward in 2025 is March 9 at 2:00 AM
        // A schedule at 02:30 should still produce an event (mapped to 03:30 EDT)
        val zone = TimeZone.of("America/New_York")
        val dstDay = LocalDate(2025, 3, 9)
        val startMillis = dstDay.atStartOfDayIn(zone).toEpochMilliseconds()

        val schedule = createSchedule(
            startDate = startMillis,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "02:30",
        )

        val events = projector.generateEventsForSchedule(schedule, dstDay, dstDay)

        assertEquals(1, events.size, "Event should still be generated during DST spring-forward")
    }

    // --- Timezone mode tests ---

    @Test
    fun fixedMode_eventTimeUsesOriginTimezone() {
        // Schedule at 21:00 in America/New_York
        val nyTz = TimeZone.of("America/New_York")
        val date = LocalDate(2025, 6, 15) // Summer (EDT = UTC-4)
        val startMillis = date.atStartOfDayIn(nyTz).toEpochMilliseconds()

        val schedule = createSchedule(
            startDate = startMillis,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "21:00",
            originTimeZone = "America/New_York",
        )

        // Generate using the origin timezone explicitly (simulating FIXED mode)
        val events = projector.generateEventsForSchedule(schedule, date, date, nyTz)

        assertEquals(1, events.size)

        // The epoch millis should correspond to 21:00 EDT (UTC-4) = 01:00 UTC next day
        val expectedMillis = kotlinx.datetime.LocalDateTime(date, LocalTime(21, 0))
            .toInstant(nyTz)
            .toEpochMilliseconds()
        assertEquals(expectedMillis, events[0].scheduledTime)
    }

    @Test
    fun fixedMode_projectEventsWithHistory_usesOriginTimezone() {
        val nyTz = TimeZone.of("America/New_York")
        val date = LocalDate(2025, 6, 15)
        val startMillis = date.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val endMillis = date.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(nyTz).toEpochMilliseconds()

        val medication = createMedication(id = 1, timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = startMillis,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "21:00",
            originTimeZone = "America/New_York",
        )

        val events = projector.projectEventsWithHistory(
            schedules = listOf(schedule),
            startTime = startMillis,
            endTime = endMillis,
            history = emptyList(),
            medications = mapOf(1 to medication),
        )

        assertEquals(1, events.size)
        val expectedMillis = kotlinx.datetime.LocalDateTime(date, LocalTime(21, 0))
            .toInstant(nyTz)
            .toEpochMilliseconds()
        assertEquals(expectedMillis, events[0].scheduledTime)
    }

    @Test
    fun localMode_projectEventsWithHistory_usesSystemDefault() {
        val systemTz = TimeZone.currentSystemDefault()
        val date = LocalDate(2025, 6, 15)
        val startMillis = date.atStartOfDayIn(systemTz).toEpochMilliseconds()
        val endMillis = date.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(systemTz).toEpochMilliseconds()

        val medication = createMedication(id = 1, timeZoneMode = TimeZoneMode.LOCAL)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = startMillis,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "09:00",
            originTimeZone = "America/New_York", // Should be ignored in LOCAL mode
        )

        val events = projector.projectEventsWithHistory(
            schedules = listOf(schedule),
            startTime = startMillis,
            endTime = endMillis,
            history = emptyList(),
            medications = mapOf(1 to medication),
        )

        assertEquals(1, events.size)
        // Should use system default, not New York
        val expectedMillis = kotlinx.datetime.LocalDateTime(date, LocalTime(9, 0))
            .toInstant(systemTz)
            .toEpochMilliseconds()
        assertEquals(expectedMillis, events[0].scheduledTime)
    }

    @Test
    fun fixedMode_cycleMask_usesOriginTimezone() {
        val nyTz = TimeZone.of("America/New_York")
        val anchorDate = LocalDate(2025, 6, 1)
        val testDate = LocalDate(2025, 6, 15) // Day 14 in cycle (0-based)
        val startMillis = testDate.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val endMillis = testDate.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(nyTz).toEpochMilliseconds()

        // 21-day active + 7-day break cycle. Day 14 (0-based) is in active period.
        val medication = createMedication(
            id = 1,
            timeZoneMode = TimeZoneMode.FIXED,
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 21,
            cycleDaysBreak = 7,
            cycleHasPlacebos = false,
            cycleStartDate = anchorDate,
        )

        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = anchorDate.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "21:00",
            originTimeZone = "America/New_York",
        )

        val events = projector.projectEventsWithHistory(
            schedules = listOf(schedule),
            startTime = startMillis,
            endTime = endMillis,
            history = emptyList(),
            medications = mapOf(1 to medication),
        )

        // Day 14 is in active period (< 21), event should pass through cycle mask
        assertEquals(1, events.size)
        assertFalse(events[0].isPlacebo)
    }

    @Test
    fun mixedSchedules_eachUsesCorrectTimezone() {
        val nyTz = TimeZone.of("America/New_York")
        val systemTz = TimeZone.currentSystemDefault()
        val date = LocalDate(2025, 6, 15)
        val startMillis = date.atStartOfDayIn(systemTz).toEpochMilliseconds()
        // Use a wide window to capture events from both timezones
        val endMillis = date.plus(2, DateTimeUnit.DAY)
            .atStartOfDayIn(systemTz).toEpochMilliseconds()

        val fixedMed = createMedication(id = 1, name = "Fixed Med", timeZoneMode = TimeZoneMode.FIXED)
        val localMed = createMedication(id = 2, name = "Local Med", timeZoneMode = TimeZoneMode.LOCAL)

        val fixedSchedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = date.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "21:00",
            originTimeZone = "America/New_York",
        )
        val localSchedule = createSchedule(
            id = 2,
            medicationId = 2,
            startDate = date.atStartOfDayIn(systemTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "21:00",
        )

        val events = projector.projectEventsWithHistory(
            schedules = listOf(fixedSchedule, localSchedule),
            startTime = startMillis,
            endTime = endMillis,
            history = emptyList(),
            medications = mapOf(1 to fixedMed, 2 to localMed),
        )

        val fixedEvents = events.filter { it.medicationId == 1 }
        val localEvents = events.filter { it.medicationId == 2 }

        assertTrue(fixedEvents.isNotEmpty(), "FIXED schedule should produce events")
        assertTrue(localEvents.isNotEmpty(), "LOCAL schedule should produce events")

        // FIXED: 21:00 in NY timezone
        val expectedFixedMillis = kotlinx.datetime.LocalDateTime(date, LocalTime(21, 0))
            .toInstant(nyTz)
            .toEpochMilliseconds()
        assertEquals(expectedFixedMillis, fixedEvents[0].scheduledTime)

        // LOCAL: 21:00 in system timezone
        val expectedLocalMillis = kotlinx.datetime.LocalDateTime(date, LocalTime(21, 0))
            .toInstant(systemTz)
            .toEpochMilliseconds()
        assertEquals(expectedLocalMillis, localEvents[0].scheduledTime)
    }

    @Test
    fun fixedMode_nullOriginTimeZone_fallsBackToSystemDefault() {
        val systemTz = TimeZone.currentSystemDefault()
        val date = LocalDate(2025, 6, 15)
        val startMillis = date.atStartOfDayIn(systemTz).toEpochMilliseconds()
        val endMillis = date.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(systemTz).toEpochMilliseconds()

        val medication = createMedication(id = 1, timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = startMillis,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "09:00",
            originTimeZone = null, // No origin TZ — should fall back to system default
        )

        val events = projector.projectEventsWithHistory(
            schedules = listOf(schedule),
            startTime = startMillis,
            endTime = endMillis,
            history = emptyList(),
            medications = mapOf(1 to medication),
        )

        assertEquals(1, events.size)
        val expectedMillis = kotlinx.datetime.LocalDateTime(date, LocalTime(9, 0))
            .toInstant(systemTz)
            .toEpochMilliseconds()
        assertEquals(expectedMillis, events[0].scheduledTime)
    }

    @Test
    fun fixedMode_crossDateBoundary_bufferCapturesEvent() {
        // FIXED at 23:00 Asia/Tokyo (UTC+9). 23:00 JST = 14:00 UTC same day.
        // Query window: June 15 00:00 UTC to June 16 00:00 UTC.
        // The event at 14:00 UTC is INSIDE the window → must appear.
        val tokyoTz = TimeZone.of("Asia/Tokyo")
        val utcTz = TimeZone.of("UTC")
        val date = LocalDate(2025, 6, 15)

        val medication = createMedication(id = 1, timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = date.atStartOfDayIn(tokyoTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "23:00",
            originTimeZone = "Asia/Tokyo",
        )

        val queryStart = date.atStartOfDayIn(utcTz).toEpochMilliseconds()
        val queryEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(utcTz).toEpochMilliseconds()

        val events = projector.projectEventsWithHistory(
            schedules = listOf(schedule),
            startTime = queryStart,
            endTime = queryEnd,
            history = emptyList(),
            medications = mapOf(1 to medication),
        )

        // 23:00 JST on June 15 = 14:00 UTC on June 15 → inside [00:00 UTC, 00:00 UTC+1d)
        assertEquals(1, events.size, "Buffer should capture cross-TZ event")
        assertScheduledAt(events[0].scheduledTime, date, LocalTime(23, 0), tokyoTz)
    }

    @Test
    fun fixedMode_historyKeyStability() {
        // Project the same FIXED schedule from two different "observer" windows.
        // The scheduledTime (epoch millis) must be identical — this is the history key.
        val nyTz = TimeZone.of("America/New_York")
        val tokyoTz = TimeZone.of("Asia/Tokyo")
        val date = LocalDate(2025, 6, 15)

        val medication = createMedication(id = 1, timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = date.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "21:00",
            originTimeZone = "America/New_York",
        )

        // Window 1: queried from NY perspective
        val nyStart = date.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val nyEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(nyTz).toEpochMilliseconds()
        val eventsFromNY = projector.projectEventsWithHistory(
            listOf(schedule),
            nyStart,
            nyEnd,
            emptyList(),
            mapOf(1 to medication),
        )

        // Window 2: queried from Tokyo perspective (wide enough to capture)
        val tokyoStart = date.atStartOfDayIn(tokyoTz).toEpochMilliseconds()
        val tokyoEnd = date.plus(2, DateTimeUnit.DAY).atStartOfDayIn(tokyoTz).toEpochMilliseconds()
        val eventsFromTokyo = projector.projectEventsWithHistory(
            listOf(schedule),
            tokyoStart,
            tokyoEnd,
            emptyList(),
            mapOf(1 to medication),
        )

        assertTrue(eventsFromNY.isNotEmpty(), "NY window should contain event")
        assertTrue(eventsFromTokyo.isNotEmpty(), "Tokyo window should contain event")

        // The epoch millis for the same date's event must be identical regardless of observer TZ
        val nyEventForDate = eventsFromNY.first { e ->
            Instant.fromEpochMilliseconds(e.scheduledTime).toLocalDateTime(nyTz).date == date
        }
        val tokyoEventForDate = eventsFromTokyo.first { e ->
            Instant.fromEpochMilliseconds(e.scheduledTime).toLocalDateTime(nyTz).date == date
        }
        assertEquals(
            nyEventForDate.scheduledTime,
            tokyoEventForDate.scheduledTime,
            "History key (scheduledTime) must be stable across observer timezones",
        )
    }

    @Test
    fun fixedMode_dstSpringForward() {
        // March 9 2025: 2:00 AM EST → 3:00 AM EDT (spring forward)
        // A FIXED schedule at 02:30 should produce an event mapped to 03:30 EDT
        val nyTz = TimeZone.of("America/New_York")
        val dstDay = LocalDate(2025, 3, 9)

        createMedication(id = 1, timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = dstDay.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "02:30",
            originTimeZone = "America/New_York",
        )

        val events = projector.generateEventsForSchedule(schedule, dstDay, dstDay, nyTz)

        assertEquals(1, events.size, "Event should be generated during DST spring-forward")
        // 02:30 doesn't exist → kotlinx.datetime maps to 03:30 EDT (UTC-4) = 07:30 UTC
        val expectedUtcHour = 7 // 03:30 EDT = 07:30 UTC
        val actualUtc = Instant.fromEpochMilliseconds(events[0].scheduledTime)
            .toLocalDateTime(TimeZone.of("UTC"))
        assertEquals(expectedUtcHour, actualUtc.hour, "Should be mapped to post-transition time in UTC")
        assertEquals(30, actualUtc.minute)
    }

    @Test
    fun fixedMode_dstFallBack() {
        // November 2 2025: 2:00 AM EDT → 1:00 AM EST (fall back)
        // A FIXED schedule at 01:30 — the "first" 01:30 is EDT (UTC-4)
        val nyTz = TimeZone.of("America/New_York")
        val dstDay = LocalDate(2025, 11, 2)

        createMedication(id = 1, timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = dstDay.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "01:30",
            originTimeZone = "America/New_York",
        )

        val events = projector.generateEventsForSchedule(schedule, dstDay, dstDay, nyTz)

        assertEquals(1, events.size, "Event should be generated during DST fall-back")
        // First 01:30 is EDT (UTC-4) → 05:30 UTC
        val actualUtc = Instant.fromEpochMilliseconds(events[0].scheduledTime)
            .toLocalDateTime(TimeZone.of("UTC"))
        assertEquals(5, actualUtc.hour, "Should use first occurrence (EDT = UTC-4), 01:30 + 4 = 05:30 UTC")
        assertEquals(30, actualUtc.minute)
    }

    @Test
    fun fixedMode_cycleMask_breakDay_suppressedCorrectly() {
        val nyTz = TimeZone.of("America/New_York")
        val anchorDate = LocalDate(2025, 6, 1)
        // Day 22 (0-based) → in 21/7 cycle, this is break day 1 (first break day)
        val breakDate = LocalDate(2025, 6, 23) // 22 days after anchor
        val startMillis = breakDate.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val endMillis = breakDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(nyTz).toEpochMilliseconds()

        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = anchorDate.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "09:00",
            originTimeZone = "America/New_York",
        )

        // Case 1: no placebos → event suppressed
        val medNoPlacebo = createMedication(
            id = 1,
            timeZoneMode = TimeZoneMode.FIXED,
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 21,
            cycleDaysBreak = 7,
            cycleHasPlacebos = false,
            cycleStartDate = anchorDate,
        )
        val eventsNoPlacebo = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            emptyList(),
            mapOf(1 to medNoPlacebo),
        )
        assertEquals(0, eventsNoPlacebo.size, "Break day without placebos should suppress event")

        // Case 2: with placebos → event marked isPlacebo
        val medWithPlacebo = createMedication(
            id = 1,
            timeZoneMode = TimeZoneMode.FIXED,
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 21,
            cycleDaysBreak = 7,
            cycleHasPlacebos = true,
            cycleStartDate = anchorDate,
        )
        val eventsWithPlacebo = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            emptyList(),
            mapOf(1 to medWithPlacebo),
        )
        assertEquals(1, eventsWithPlacebo.size, "Break day with placebos should produce event")
        assertTrue(eventsWithPlacebo[0].isPlacebo, "Event on break day should be marked as placebo")
    }

    @Test
    fun fixedMode_daysOfWeek_usesOriginTimezone() {
        val nyTz = TimeZone.of("America/New_York")
        // June 16 2025 is a Monday in both NY and UTC
        val monday = LocalDate(2025, 6, 16)
        val startMillis = monday.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val endMillis = monday.plus(7, DateTimeUnit.DAY).atStartOfDayIn(nyTz).toEpochMilliseconds()

        val medication = createMedication(id = 1, timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = monday.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.DAYS_OF_WEEK,
            frequencyValue = 0,
            daysOfWeek = "MONDAY,WEDNESDAY,FRIDAY",
            timeOfDay = "09:00",
            originTimeZone = "America/New_York",
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            emptyList(),
            mapOf(1 to medication),
        )

        assertEquals(3, events.size, "Should produce Mon/Wed/Fri events")
        // Verify each event is at 09:00 NY time
        events.forEach { event ->
            assertScheduledAt(
                event.scheduledTime,
                Instant.fromEpochMilliseconds(event.scheduledTime).toLocalDateTime(nyTz).date,
                LocalTime(9, 0),
                nyTz,
            )
        }
        // Verify correct days
        val eventDays = events.map {
            Instant.fromEpochMilliseconds(it.scheduledTime).toLocalDateTime(nyTz).dayOfWeek
        }
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), eventDays)
    }

    @Test
    fun fixedMode_multiDay_correctEventCount() {
        val nyTz = TimeZone.of("America/New_York")
        val startDate = LocalDate(2025, 6, 15)
        val startMillis = startDate.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val endMillis = startDate.plus(7, DateTimeUnit.DAY).atStartOfDayIn(nyTz).toEpochMilliseconds()

        val medication = createMedication(id = 1, timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = startMillis,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "21:00",
            originTimeZone = "America/New_York",
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            emptyList(),
            mapOf(1 to medication),
        )

        assertEquals(7, events.size, "Daily FIXED schedule over 7 days should produce 7 events")
        // Each event should be exactly 24h apart
        for (i in 1 until events.size) {
            val diff = events[i].scheduledTime - events[i - 1].scheduledTime
            // Allow for DST — most days are 24h = 86400000ms
            assertTrue(diff in 82800000..90000000, "Events should be ~24h apart (got ${diff}ms)")
        }
    }

    @Test
    fun fixedMode_multiDay_yearBoundary() {
        val nyTz = TimeZone.of("America/New_York")
        val dec30 = LocalDate(2025, 12, 30)
        val jan2 = LocalDate(2026, 1, 2)
        val startMillis = dec30.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val endMillis = jan2.plus(1, DateTimeUnit.DAY).atStartOfDayIn(nyTz).toEpochMilliseconds()

        val medication = createMedication(id = 1, timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = dec30.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "09:00",
            originTimeZone = "America/New_York",
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            emptyList(),
            mapOf(1 to medication),
        )

        // Dec 30, 31, Jan 1, 2 = 4 days
        assertEquals(4, events.size, "Should produce events across year boundary")
        val dates = events.map {
            Instant.fromEpochMilliseconds(it.scheduledTime).toLocalDateTime(nyTz).date
        }
        assertEquals(
            listOf(
                LocalDate(2025, 12, 30),
                LocalDate(2025, 12, 31),
                LocalDate(2026, 1, 1),
                LocalDate(2026, 1, 2),
            ),
            dates,
        )
    }

    @Test
    fun fixedMode_withHistory_historyOverridesCycleMask() {
        val nyTz = TimeZone.of("America/New_York")
        val anchorDate = LocalDate(2025, 6, 1)
        // Day 22 (0-based) → break day in 21/7 cycle
        val breakDate = LocalDate(2025, 6, 23)
        val startMillis = breakDate.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val endMillis = breakDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(nyTz).toEpochMilliseconds()

        val medication = createMedication(
            id = 1,
            timeZoneMode = TimeZoneMode.FIXED,
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 21,
            cycleDaysBreak = 7,
            cycleHasPlacebos = false,
            cycleStartDate = anchorDate,
        )
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = anchorDate.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "09:00",
            originTimeZone = "America/New_York",
        )

        // Create a history record for this event (user already took it)
        val eventTime = kotlinx.datetime.LocalDateTime(breakDate, LocalTime(9, 0))
            .toInstant(nyTz).toEpochMilliseconds()
        val history = listOf(
            createHistory(
                medicationId = 1,
                scheduleId = 1,
                scheduledTime = eventTime,
                takenTime = eventTime + 60000,
                status = "TAKEN",
            ),
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            history,
            mapOf(1 to medication),
        )

        // Despite being a break day with no placebos, history overrides the mask
        assertEquals(1, events.size, "History should override cycle mask suppression")
        assertTrue(events[0].isTaken, "Event should show as taken")
    }

    // --- DST fall-back LOCAL mode (double-dose prevention) ---

    @Test
    fun localMode_dstFallBack_producesExactlyOneEvent() {
        // Consequence of failure: DOUBLE DOSE. If fall-back produces two events at the
        // ambiguous 01:30, the user gets two notifications and may take a double dose.
        val nyTz = TimeZone.of("America/New_York")
        val dstDay = LocalDate(2025, 11, 2) // Fall-back: 2:00 AM EDT → 1:00 AM EST

        val schedule = createSchedule(
            startDate = dstDay.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "01:30",
        )

        val events = projector.generateEventsForSchedule(schedule, dstDay, dstDay, nyTz)

        assertEquals(1, events.size, "Fall-back must produce exactly ONE event, not two")
        // kotlinx.datetime resolves ambiguous time to the earlier offset (EDT = UTC-4)
        // 01:30 EDT = 05:30 UTC
        val actualUtc = Instant.fromEpochMilliseconds(events[0].scheduledTime)
            .toLocalDateTime(TimeZone.of("UTC"))
        assertEquals(5, actualUtc.hour, "Should resolve to earlier offset (EDT): 01:30 EDT = 05:30 UTC")
        assertEquals(30, actualUtc.minute)
    }

    @Test
    fun localMode_dstFallBack_nonAmbiguousTime_unaffected() {
        // Consequence of failure: MISSED DOSE. A non-ambiguous time during fall-back day
        // is lost or duplicated.
        val nyTz = TimeZone.of("America/New_York")
        val dstDay = LocalDate(2025, 11, 2)

        val schedule = createSchedule(
            startDate = dstDay.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "10:00",
        )

        val events = projector.generateEventsForSchedule(schedule, dstDay, dstDay, nyTz)

        assertEquals(1, events.size)
        // 10:00 EST (post-transition) = UTC-5 → 15:00 UTC
        val actualUtc = Instant.fromEpochMilliseconds(events[0].scheduledTime)
            .toLocalDateTime(TimeZone.of("UTC"))
        assertEquals(15, actualUtc.hour, "10:00 EST = 15:00 UTC")
    }

    // --- Multi-day DST spanning (missed dose prevention) ---

    @Test
    fun localMode_multiDayProjection_spanningDstSpringForward_exactlySevenEvents() {
        // Consequence of failure: MISSED DOSE. Week-long projection across spring-forward
        // produces 6 instead of 7 events.
        val nyTz = TimeZone.of("America/New_York")
        val from = LocalDate(2025, 3, 7)
        val to = LocalDate(2025, 3, 13) // DST spring-forward on March 9

        val schedule = createSchedule(
            startDate = from.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to, nyTz)

        assertEquals(7, events.size, "Must produce exactly 7 events across DST spring-forward")
        val dates = events.map {
            Instant.fromEpochMilliseconds(it.scheduledTime).toLocalDateTime(nyTz).date
        }
        assertEquals(
            (0..6).map { from.plus(it, DateTimeUnit.DAY) },
            dates,
            "Each calendar day Mar 7-13 must have one event",
        )
    }

    @Test
    fun localMode_multiDayProjection_spanningDstFallBack_exactlySevenEvents() {
        // Consequence of failure: DOUBLE DOSE. Week-long projection across fall-back
        // produces 8 instead of 7 events.
        val nyTz = TimeZone.of("America/New_York")
        val from = LocalDate(2025, 10, 31)
        val to = LocalDate(2025, 11, 6) // DST fall-back on Nov 2

        val schedule = createSchedule(
            startDate = from.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to, nyTz)

        assertEquals(7, events.size, "Must produce exactly 7 events across DST fall-back")
    }

    @Test
    fun localMode_multiDayProjection_springForward_viaProjectEventsWithHistory() {
        // Consequence of failure: MISSED DOSE. The epoch-millis filter in
        // projectEventsWithHistory clips events near DST transition.
        val nyTz = TimeZone.of("America/New_York")
        val from = LocalDate(2025, 3, 7)
        val to = LocalDate(2025, 3, 13)
        val startMillis = from.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val endMillis = to.plus(1, DateTimeUnit.DAY).atStartOfDayIn(nyTz).toEpochMilliseconds()

        val medication = createMedication(id = 1, timeZoneMode = TimeZoneMode.LOCAL)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = startMillis,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            emptyList(),
            mapOf(1 to medication),
        )

        assertEquals(7, events.size, "projectEventsWithHistory must not clip DST-boundary events")
        assertTrue(events.all { it.isPending })
    }

    // --- Cycle boundary at DST ---

    @Test
    fun cycleBoundaryAtDstTransition_activeToBreak() {
        // Consequence of failure: MISSED DOSE (active event suppressed on DST day)
        // or UNWANTED DOSE (break event fires on DST day).
        val nyTz = TimeZone.of("America/New_York")
        // Anchor Feb 16. 21 active days = Feb 16..Mar 8. Day 21 (0-based) = Mar 9 = DST spring-forward = first break day.
        val anchorDate = LocalDate(2025, 2, 16)
        val dstDay = LocalDate(2025, 3, 9)
        val startMillis = dstDay.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val endMillis = dstDay.plus(1, DateTimeUnit.DAY).atStartOfDayIn(nyTz).toEpochMilliseconds()

        val medication = createMedication(
            id = 1,
            timeZoneMode = TimeZoneMode.FIXED,
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 21,
            cycleDaysBreak = 7,
            cycleHasPlacebos = false,
            cycleStartDate = anchorDate,
        )
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = anchorDate.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "09:00",
            originTimeZone = "America/New_York",
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            emptyList(),
            mapOf(1 to medication),
        )

        assertEquals(0, events.size, "DST day that falls on break day should suppress event")
    }

    // --- Multiple schedules for same medication ---

    @Test
    fun multipleSchedules_sameMedication_differentTimes_independentEvents() {
        // Consequence of failure: MISSED DOSE. Morning and evening schedules interfere,
        // producing 1 instead of 2 events per day.
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 3)
        val morning = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = from.toEpochMillis(),
            timeOfDay = "08:00",
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
        )
        val evening = createSchedule(
            id = 2,
            medicationId = 1,
            startDate = from.toEpochMillis(),
            timeOfDay = "20:00",
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
        )

        val events = projector.projectEventsWithHistory(
            listOf(morning, evening),
            from.toEpochMillis(),
            to.toEpochMillis("23:59"),
            emptyList(),
        )

        assertEquals(6, events.size, "3 days x 2 schedules = 6 events")
        assertEquals(3, events.count { it.scheduleId == 1 }, "Morning schedule should have 3 events")
        assertEquals(3, events.count { it.scheduleId == 2 }, "Evening schedule should have 3 events")
        assertTrue(events.all { it.medicationId == 1 }, "All events belong to same medication")
    }

    @Test
    fun multipleSchedules_sameMedication_differentFrequencies() {
        // Consequence of failure: MISSED DOSE. Daily + Mon/Wed/Fri schedules on same
        // medication drop events.
        val monday = LocalDate(2025, 3, 3) // Monday
        val sunday = LocalDate(2025, 3, 9) // Sunday
        val daily = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = monday.toEpochMillis(),
            timeOfDay = "08:00",
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
        )
        val mwf = createSchedule(
            id = 2,
            medicationId = 1,
            startDate = monday.toEpochMillis(),
            timeOfDay = "20:00",
            frequencyType = FrequencyType.DAYS_OF_WEEK,
            frequencyValue = 0,
            daysOfWeek = "MONDAY,WEDNESDAY,FRIDAY",
        )

        val events = projector.projectEventsWithHistory(
            listOf(daily, mwf),
            monday.toEpochMillis(),
            sunday.toEpochMillis("23:59"),
            emptyList(),
        )

        val dailyEvents = events.filter { it.scheduleId == 1 }
        val mwfEvents = events.filter { it.scheduleId == 2 }
        assertEquals(7, dailyEvents.size, "Daily schedule: 7 events")
        assertEquals(3, mwfEvents.size, "MWF schedule: 3 events")
        assertEquals(10, events.size, "Total: 10 events")
    }

    // --- Large interval across DST ---

    @Test
    fun largeIntervalFrequency_acrossDst_alignmentStable() {
        // Consequence of failure: Biweekly medication drifts by a day at DST,
        // misaligning doses.
        val nyTz = TimeZone.of("America/New_York")
        val startDate = LocalDate(2025, 2, 23)
        val endDate = LocalDate(2025, 4, 6)

        val schedule = createSchedule(
            startDate = startDate.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 14,
            timeOfDay = "08:00",
        )

        val events = projector.generateEventsForSchedule(schedule, startDate, endDate, nyTz)

        // Feb 23, Mar 9 (DST!), Mar 23, Apr 6
        assertEquals(4, events.size, "Biweekly over ~6 weeks = 4 events")
        val dates = events.map {
            Instant.fromEpochMilliseconds(it.scheduledTime).toLocalDateTime(nyTz).date
        }
        assertEquals(
            listOf(
                LocalDate(2025, 2, 23),
                LocalDate(2025, 3, 9), // DST spring-forward day
                LocalDate(2025, 3, 23),
                LocalDate(2025, 4, 6),
            ),
            dates,
            "14 calendar-day intervals must be maintained across DST",
        )
        // Verify all are at 08:00 local
        events.forEach { event ->
            val localTime = Instant.fromEpochMilliseconds(event.scheduledTime)
                .toLocalDateTime(nyTz).time
            assertEquals(LocalTime(8, 0), localTime, "Event should be at 08:00 local time")
        }
    }

    // --- Edge cases ---

    @Test
    fun midnightSchedule_producesOneEventPerDay() {
        // Consequence of failure: DOUBLE DOSE from duplicate events at day boundaries.
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 3)
        val schedule = createSchedule(
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "00:00",
        )

        val events = projector.generateEventsForSchedule(schedule, from, to)

        assertEquals(3, events.size, "00:00 schedule must produce exactly 1 event per day")
    }

    @Test
    fun zeroBreakCycle_allDaysActive_neverSuppressed() {
        // Consequence of failure: MISSED DOSE. Continuous medication (28/0 cycle, e.g.,
        // continuous birth control) has events suppressed.
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 28)
        val startMillis = from.toEpochMillis()
        val endMillis = to.toEpochMillis("23:59")

        val medication = createMedication(
            id = 1,
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 28,
            cycleDaysBreak = 0,
            cycleHasPlacebos = false,
            cycleStartDate = from,
        )
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = startMillis,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "09:00",
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            emptyList(),
            mapOf(1 to medication),
        )

        assertEquals(28, events.size, "28/0 cycle: all 28 days must produce events")
        assertTrue(events.none { it.isPlacebo }, "No events should be marked as placebo")
    }

    // --- Travel / timezone change safety ---

    @Test
    fun fixedMode_travelScenario_eventTimeStaysAbsolute() {
        // Consequence of failure: User sets med for 08:00 NY, flies to London.
        // FIXED mode should fire at 13:00 London time (08:00 NY = 13:00 BST).
        // If it fires at 08:00 London instead, dose interval is 5 hours early.
        val nyTz = TimeZone.of("America/New_York")
        val londonTz = TimeZone.of("Europe/London")
        val date = LocalDate(2025, 6, 15) // Summer: EDT (UTC-4), BST (UTC+1)

        val medication = createMedication(id = 1, timeZoneMode = TimeZoneMode.FIXED)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = date.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
            originTimeZone = "America/New_York",
        )

        // Query window from NY perspective
        val nyStart = date.atStartOfDayIn(nyTz).toEpochMilliseconds()
        val nyEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(nyTz).toEpochMilliseconds()
        val eventsFromNY = projector.projectEventsWithHistory(
            listOf(schedule),
            nyStart,
            nyEnd,
            emptyList(),
            mapOf(1 to medication),
        )

        // Query window from London perspective (wide enough)
        val londonStart = date.atStartOfDayIn(londonTz).toEpochMilliseconds()
        val londonEnd = date.plus(2, DateTimeUnit.DAY).atStartOfDayIn(londonTz).toEpochMilliseconds()
        val eventsFromLondon = projector.projectEventsWithHistory(
            listOf(schedule),
            londonStart,
            londonEnd,
            emptyList(),
            mapOf(1 to medication),
        )

        assertTrue(eventsFromNY.isNotEmpty(), "NY window should contain event")
        assertTrue(eventsFromLondon.isNotEmpty(), "London window should contain event")

        // The scheduled time (epoch millis) must be identical — FIXED mode is absolute
        val nyEvent = eventsFromNY.first {
            Instant.fromEpochMilliseconds(it.scheduledTime).toLocalDateTime(nyTz).date == date
        }
        val londonEvent = eventsFromLondon.first {
            Instant.fromEpochMilliseconds(it.scheduledTime).toLocalDateTime(nyTz).date == date
        }
        assertEquals(
            nyEvent.scheduledTime,
            londonEvent.scheduledTime,
            "FIXED mode: epoch millis must be identical regardless of observer timezone",
        )

        // Verify it's at 08:00 NY = 13:00 London (BST, UTC+1)
        val londonLocal = Instant.fromEpochMilliseconds(nyEvent.scheduledTime)
            .toLocalDateTime(londonTz)
        assertEquals(13, londonLocal.hour, "08:00 EDT (UTC-4) should be 13:00 BST (UTC+1)")
    }

    @Test
    fun localMode_travelScenario_eventTimeShiftsToNewTimezone() {
        // Consequence of failure: User travels from NY to London. LOCAL mode should fire
        // at 08:00 London time (wall-clock). If it still fires at NY time, the dose is
        // at 3 AM local, waking the user.
        val nyTz = TimeZone.of("America/New_York")
        val londonTz = TimeZone.of("Europe/London")
        val date = LocalDate(2025, 6, 15) // Summer

        val schedule = createSchedule(
            startDate = date.atStartOfDayIn(nyTz).toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )

        // Generate events as if in NY
        val eventsInNY = projector.generateEventsForSchedule(schedule, date, date, nyTz)
        // Generate events as if in London (simulating system TZ change)
        val eventsInLondon = projector.generateEventsForSchedule(schedule, date, date, londonTz)

        assertEquals(1, eventsInNY.size)
        assertEquals(1, eventsInLondon.size)

        // The epoch millis should be DIFFERENT — LOCAL mode adapts to wall-clock
        val nyMillis = eventsInNY[0].scheduledTime
        val londonMillis = eventsInLondon[0].scheduledTime
        assertTrue(
            nyMillis != londonMillis,
            "LOCAL mode: epoch millis must differ when timezone changes (wall-clock adaptation)",
        )

        // Both should be at 08:00 in their respective timezones
        val nyLocal = Instant.fromEpochMilliseconds(nyMillis).toLocalDateTime(nyTz)
        val londonLocal = Instant.fromEpochMilliseconds(londonMillis).toLocalDateTime(londonTz)
        assertEquals(8, nyLocal.hour, "Should be 08:00 in NY")
        assertEquals(8, londonLocal.hour, "Should be 08:00 in London")
    }

    // --- Data integrity ---

    @Test
    fun compositeKey_threeSchedulesSameTime_correctHistoryBinding() {
        // Consequence of failure: History for med A matched to med B's event. Med B shows
        // as taken when it was not. MISSED DOSE of B.
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 1)
        val schedule1 = createSchedule(id = 1, medicationId = 1, startDate = from.toEpochMillis(), timeOfDay = "08:00")
        val schedule2 = createSchedule(id = 2, medicationId = 2, startDate = from.toEpochMillis(), timeOfDay = "08:00")
        val schedule3 = createSchedule(id = 3, medicationId = 3, startDate = from.toEpochMillis(), timeOfDay = "08:00")

        val eventTime = from.toEpochMillis("08:00")
        val history = listOf(
            createHistory(
                medicationId = 2,
                scheduleId = 2,
                scheduledTime = eventTime,
                status = MedicationHistory.STATUS_TAKEN,
            ),
        )

        val events = projector.projectEventsWithHistory(
            listOf(schedule1, schedule2, schedule3),
            from.toEpochMillis(),
            to.toEpochMillis("23:59"),
            history,
        )

        assertEquals(3, events.size)
        val event1 = events.first { it.medicationId == 1 }
        val event2 = events.first { it.medicationId == 2 }
        val event3 = events.first { it.medicationId == 3 }
        assertTrue(event1.isPending, "Med 1 should remain pending")
        assertFalse(event2.isPending, "Med 2 should be taken")
        assertTrue(event2.isTaken, "Med 2 should be marked taken")
        assertTrue(event3.isPending, "Med 3 should remain pending")
    }

    @Test
    fun projectEventsWithHistory_deterministic_sameInputsSameOutput() {
        // Consequence of failure: Non-deterministic projection causes tracking UI and
        // notification system to disagree on event state.
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 7)
        val startMillis = from.toEpochMillis()
        val endMillis = to.toEpochMillis("23:59")

        val medication = createMedication(
            id = 1,
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 5,
            cycleDaysBreak = 2,
            cycleStartDate = from,
        )
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = startMillis,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )
        val eventTime = from.toEpochMillis("08:00")
        val history = listOf(
            createHistory(
                medicationId = 1,
                scheduleId = 1,
                scheduledTime = eventTime,
                status = MedicationHistory.STATUS_TAKEN,
            ),
        )

        val result1 = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            history,
            mapOf(1 to medication),
        )
        val result2 = projector.projectEventsWithHistory(
            listOf(schedule),
            startMillis,
            endMillis,
            history,
            mapOf(1 to medication),
        )

        assertEquals(result1.size, result2.size, "Same inputs must produce same count")
        result1.zip(result2).forEach { (a, b) ->
            assertEquals(a.scheduledTime, b.scheduledTime, "Scheduled times must be identical")
            assertEquals(a.isPending, b.isPending, "Pending state must be identical")
            assertEquals(a.isPlacebo, b.isPlacebo, "Placebo state must be identical")
        }
    }

    // --- Auto-skip safety ---

    @Test
    fun shouldAutoSkip_currentTimeBetweenOccurrences_returnsFalse() = runTest {
        // Consequence of failure: Pending event at 08:00 today is auto-skipped at 20:00
        // today. User LOSES the chance to log the dose.
        val today = LocalDate(2025, 3, 15)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = today.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )
        val medication = createMedication(id = 1)

        val scheduleDao = FakeScheduleDao().apply { seed(schedule) }
        val medicationDao = FakeMedicationDao().apply { seed(medication) }
        val seededProjector = ScheduleProjector(scheduleDao, FakeHistoryDao(), medicationDao)

        val eventTime = today.toEpochMillis("08:00")
        val currentTime = today.toEpochMillis("20:00") // Same day, 12h later

        val result = seededProjector.shouldAutoSkip(
            scheduleId = 1,
            scheduledTime = eventTime,
            currentTime = currentTime,
        )

        // Next occurrence is tomorrow at 08:00 which is > 20:00 today
        assertFalse(result, "Should NOT auto-skip when next occurrence hasn't passed yet")
    }

    @Test
    fun shouldAutoSkip_currentTimePastNextOccurrence_returnsTrue() = runTest {
        // Consequence of failure: Stale events from 2 days ago linger as pending forever,
        // cluttering the tracking UI and causing notification noise.
        val march1 = LocalDate(2025, 3, 1)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = march1.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
        )
        val medication = createMedication(id = 1)

        val scheduleDao = FakeScheduleDao().apply { seed(schedule) }
        val medicationDao = FakeMedicationDao().apply { seed(medication) }
        val seededProjector = ScheduleProjector(scheduleDao, FakeHistoryDao(), medicationDao)

        val eventTime = march1.toEpochMillis("08:00")
        // Current time is March 3 at 09:00 — next occurrence (March 2 08:00) has passed
        val currentTime = LocalDate(2025, 3, 3).toEpochMillis("09:00")

        val result = seededProjector.shouldAutoSkip(
            scheduleId = 1,
            scheduledTime = eventTime,
            currentTime = currentTime,
        )

        assertTrue(result, "Should auto-skip when next occurrence has already passed")
    }

    @Test
    fun shouldAutoSkip_archivedSchedule_returnsFalse() = runTest {
        // Consequence of failure: Archived schedules generate phantom AUTO_SKIPPED
        // history records.
        val today = LocalDate(2025, 3, 15)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = today.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "08:00",
            isArchived = true,
        )

        val scheduleDao = FakeScheduleDao().apply { seed(schedule) }
        val seededProjector = ScheduleProjector(scheduleDao, FakeHistoryDao(), FakeMedicationDao())

        val result = seededProjector.shouldAutoSkip(
            scheduleId = 1,
            scheduledTime = today.toEpochMillis("08:00"),
            currentTime = LocalDate(2025, 3, 20).toEpochMillis("09:00"),
        )

        assertFalse(result, "Archived schedule should never auto-skip")
    }

    // --- Progressive next-event lookup (Tier 1 regression: 48h window) ---

    @Test
    fun findNextPendingEvent_eventEightyDaysOut_isFoundByProgressiveWidening() = runTest {
        // Quarterly medication: first dose is 80 days out, no prior history.
        // The previous 48h hardcoded window returned null here and the
        // AlarmReconciler then cancelled the alarm — silent missed dose for
        // any user whose next dose drifts past 48h between safety-net runs.
        val referenceDate = LocalDate(2026, 1, 1)
        val referenceMs = referenceDate.atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val firstDoseDate = referenceDate.plus(80, DateTimeUnit.DAY)

        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = firstDoseDate.atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 90,
            timeOfDay = "08:00",
        )
        val medication = createMedication(id = 1)

        val scheduleDao = FakeScheduleDao().apply { seed(schedule) }
        val medicationDao = FakeMedicationDao().apply { seed(medication) }
        val projector = ScheduleProjector(scheduleDao, FakeHistoryDao(), medicationDao)

        val result = projector.findNextPendingEvent(fromTime = referenceMs)

        assertNotNull(result, "Quarterly dose 80 days out must be discoverable")
        assertEquals(1, result.scheduleId)
        assertTrue(result.isPending, "Returned event must still be pending")

        val expected = kotlinx.datetime.LocalDateTime(firstDoseDate, LocalTime(8, 0))
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        assertEquals(expected, result.scheduledTime)

        // Sanity: event 100 days out is past the 90d cap and must return null.
        // Without this, the test would silently pass if the cap were lifted
        // entirely — pinning the bound makes the contract explicit.
        val farScheduleStart = referenceDate.plus(100, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val farOnly = createSchedule(
            id = 2,
            medicationId = 2,
            startDate = farScheduleStart,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 90,
            timeOfDay = "08:00",
        )
        val farProjector = ScheduleProjector(
            FakeScheduleDao().apply { seed(farOnly) },
            FakeHistoryDao(),
            FakeMedicationDao().apply { seed(createMedication(id = 2)) },
        )
        assertNull(
            farProjector.findNextPendingEvent(fromTime = referenceMs),
            "Event 100 days out must not be returned — the 90d cap is the bound",
        )
    }

    // --- Malformed-input defenses (Tier 2 regression) ---

    @Test
    fun projectEvents_intervalWithFrequencyValueZero_dropsScheduleWithoutCrashing() = runTest {
        // Validation should reject this at write-time, but a bad row can land via
        // backup import / manual DB edit / partial migration. Pre-guard, the
        // modulo alignment threw ArithmeticException and the advance loop went
        // infinite. The schedule must be dropped silently; valid siblings must
        // continue projecting.
        val from = LocalDate(2026, 1, 1)
        val bad = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 0,
            timeOfDay = "08:00",
        )
        val valid = createSchedule(
            id = 2,
            medicationId = 2,
            startDate = from.toEpochMillis(),
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "09:00",
        )
        val scheduleDao = FakeScheduleDao().apply {
            seed(bad)
            seed(valid)
        }
        val medicationDao = FakeMedicationDao().apply {
            seed(createMedication(id = 1))
            seed(createMedication(id = 2))
        }
        val projector = ScheduleProjector(scheduleDao, FakeHistoryDao(), medicationDao)

        // runTest's default timeout fires if the loop goes infinite.
        val events = projector.projectEvents(
            from.toEpochMillis(),
            from.plus(3, DateTimeUnit.DAY).toEpochMillis(),
        )

        assertTrue(events.none { it.scheduleId == 1 }, "frequencyValue=0 schedule must be dropped")
        assertEquals(
            3,
            events.count { it.scheduleId == 2 },
            "Valid sibling schedule must keep projecting — bad schedule must not poison the whole call",
        )
    }

    @Test
    fun projectEvents_daysOfWeekWithMalformedToken_skipsInvalidAndKeepsValidDays() = runTest {
        // 2026-03-02 is Monday. Window covers Mon, Wed, next Mon.
        val mondayMar2 = LocalDate(2026, 3, 2)
        val endExclusive = mondayMar2.plus(8, DateTimeUnit.DAY)
        val schedule = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = mondayMar2.toEpochMillis(),
            frequencyType = FrequencyType.DAYS_OF_WEEK,
            frequencyValue = 0,
            daysOfWeek = "MONDAY,INVALID_DAY,WEDNESDAY",
            timeOfDay = "08:00",
        )
        val scheduleDao = FakeScheduleDao().apply { seed(schedule) }
        val medicationDao = FakeMedicationDao().apply { seed(createMedication(id = 1)) }
        val projector = ScheduleProjector(scheduleDao, FakeHistoryDao(), medicationDao)

        val events = projector.projectEvents(
            mondayMar2.toEpochMillis(),
            endExclusive.toEpochMillis(),
        )

        val days = events.map { event ->
            Instant.fromEpochMilliseconds(event.scheduledTime)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date.dayOfWeek
        }
        assertEquals(
            3,
            events.size,
            "Mon Mar 2, Wed Mar 4, Mon Mar 9 = 3 events; INVALID_DAY silently dropped",
        )
        assertTrue(
            days.all { it == DayOfWeek.MONDAY || it == DayOfWeek.WEDNESDAY },
            "All events must be Mon or Wed — INVALID_DAY must not crash, fall back, or contaminate",
        )
    }

    // --- Test helper ---

    private fun assertScheduledAt(actualMillis: Long, date: LocalDate, time: LocalTime, tz: TimeZone) {
        val expected = kotlinx.datetime.LocalDateTime(date, time).toInstant(tz).toEpochMilliseconds()
        assertEquals(expected, actualMillis, "Expected ${date}T$time in ${tz.id}")
    }
}
