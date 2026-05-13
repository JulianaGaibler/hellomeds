// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import io.mockk.mockk
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.createHistory
import me.juliana.hellomeds.createSchedule
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleProjectorTest {

    private lateinit var projector: ScheduleProjector

    @Before
    fun setup() {
        // ScheduleProjector requires DAO params but pure methods don't use them
        projector = ScheduleProjector(
            scheduleDao = mockk(),
            historyDao = mockk(),
            medicationDao = mockk(),
        )
    }

    // --- Interval frequency ---

    @Test
    fun `interval daily generates correct event count`() {
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
    fun `interval every-other-day generates correct spacing`() {
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
        // Check 2-day gaps
        for (i in 1 until events.size) {
            val gapMs = events[i].scheduledTime - events[i - 1].scheduledTime
            val gapDays = gapMs / (24 * 60 * 60 * 1000L)
            assertEquals(2L, gapDays)
        }
    }

    @Test
    fun `interval aligns when schedule starts before query window`() {
        // Schedule started 10 days ago with freq=3
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
            val eventDate = kotlin.time.Instant.fromEpochMilliseconds(event.scheduledTime)
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            val daysSinceStart = scheduleStart.daysUntil(eventDate).toLong()
            assertEquals("Event on $eventDate should be aligned to freq=3", 0L, daysSinceStart % 3)
        }
    }

    @Test
    fun `interval respects schedule startDate within window`() {
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

        // Events should only be from Mar 4 to Mar 7
        assertEquals(4, events.size)
        val firstEventDate =
            kotlin.time.Instant.fromEpochMilliseconds(events.first().scheduledTime)
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
        assertEquals(scheduleStart, firstEventDate)
    }

    @Test
    fun `interval respects schedule endDate`() {
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

        // Events should only be Mar 1 to Mar 5
        assertEquals(5, events.size)
    }

    @Test
    fun `interval with endDate before window returns empty`() {
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
    fun `interval with startDate after window returns empty`() {
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
    fun `days of week generates events on specified days`() {
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
            val day = kotlin.time.Instant.fromEpochMilliseconds(event.scheduledTime)
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.dayOfWeek
            assertTrue(
                "Expected MWF, got $day",
                day in listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            )
        }
    }

    @Test
    fun `days of week with single day`() {
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
    fun `days of week with null daysOfWeek returns empty`() {
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
    fun `days of week respects startDate and endDate`() {
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
            val date = kotlin.time.Instant.fromEpochMilliseconds(event.scheduledTime)
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            assertFalse("Event $date should not be before startDate", date < LocalDate(2025, 3, 10))
            assertFalse("Event $date should not be after endDate", date > LocalDate(2025, 3, 20))
        }
    }

    @Test
    fun `days of week all seven days matches daily interval`() {
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
    fun `marks taken events as non-pending`() {
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
    fun `leaves events without history as pending`() {
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
    fun `composite key prevents cross-schedule contamination`() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 1)
        val schedule1 = createSchedule(
            id = 1,
            medicationId = 1,
            startDate = from.toEpochMillis(),
            timeOfDay = "08:00",
        )
        val schedule2 = createSchedule(
            id = 2,
            medicationId = 2,
            startDate = from.toEpochMillis(),
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
    fun `history with null scheduleId excluded from index`() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 1)
        val schedule = createSchedule(id = 1, startDate = from.toEpochMillis(), timeOfDay = "08:00")

        val eventTime = from.toEpochMillis("08:00")
        val history = listOf(
            createHistory(
                scheduleId = null,
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
        assertTrue("Event should remain pending when history has null scheduleId", events[0].isPending)
    }

    @Test
    fun `filters archived schedules`() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 3)
        val active = createSchedule(
            id = 1,
            startDate = from.toEpochMillis(),
            isArchived = false,
            timeOfDay = "08:00",
        )
        val archived = createSchedule(
            id = 2,
            startDate = from.toEpochMillis(),
            isArchived = true,
            timeOfDay = "08:00",
        )

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
    fun `events outside time range are filtered`() {
        val from = LocalDate(2025, 3, 1)
        val to = LocalDate(2025, 3, 5)
        val schedule = createSchedule(id = 1, startDate = from.toEpochMillis(), timeOfDay = "08:00")

        // Use a narrow time range that only includes Mar 2-3
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
    fun `events are sorted by scheduledTime`() {
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
                "Events should be sorted ascending",
                events[i].scheduledTime >= events[i - 1].scheduledTime,
            )
        }
    }

    @Test
    fun `empty schedule list returns empty`() {
        val events = projector.projectEventsWithHistory(
            emptyList(),
            LocalDate(2025, 3, 1).toEpochMillis(),
            LocalDate(2025, 3, 5).toEpochMillis("23:59"),
            emptyList(),
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `DST spring-forward does not lose events`() {
        // In America/New_York, DST spring-forward in 2025 is March 9 at 2:00 AM
        // A schedule at 02:30 should still produce an event (mapped to 03:30 EDT)
        val zone = kotlinx.datetime.TimeZone.of("America/New_York")
        val dstDay = LocalDate(2025, 3, 9)
        val startMillis = dstDay.atStartOfDayIn(zone).toEpochMilliseconds()

        val schedule = createSchedule(
            startDate = startMillis,
            frequencyType = FrequencyType.INTERVAL,
            frequencyValue = 1,
            timeOfDay = "02:30",
        )

        val events = projector.generateEventsForSchedule(schedule, dstDay, dstDay)

        assertEquals("Event should still be generated during DST spring-forward", 1, events.size)
    }
}
