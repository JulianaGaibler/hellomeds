// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import me.juliana.hellomeds.data.backup.FakeHistoryDao
import me.juliana.hellomeds.data.backup.FakeMedicationDao
import me.juliana.hellomeds.data.backup.FakeScheduleDao
import me.juliana.hellomeds.data.createHistory
import me.juliana.hellomeds.data.createMedication
import me.juliana.hellomeds.data.createSchedule
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.toEpochMillis
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleProjectorDiagnosticTest {

    private lateinit var scheduleDao: FakeScheduleDao
    private lateinit var medicationDao: FakeMedicationDao
    private lateinit var historyDao: FakeHistoryDao
    private lateinit var projector: ScheduleProjector

    @BeforeTest
    fun setup() {
        scheduleDao = FakeScheduleDao()
        medicationDao = FakeMedicationDao()
        historyDao = FakeHistoryDao()
        projector = ScheduleProjector(scheduleDao, historyDao, medicationDao)
    }

    // --- getDoseOverview ---

    @Test
    fun getDoseOverview_emptyDay_returnsAllZeros() = runTest {
        // No schedules seeded
        val date = LocalDate(2025, 6, 15)
        val start = date.toEpochMillis("00:00")
        val end = date.toEpochMillis("23:59")
        val now = date.toEpochMillis("12:00")

        val overview = projector.getDoseOverview(start, end, now)

        assertEquals(0, overview.totalCount)
        assertEquals(0, overview.takenCount)
        assertEquals(0, overview.pendingCount)
        assertEquals(0, overview.skippedCount)
        assertEquals(0, overview.overdueCount)
        assertTrue(overview.doses.isEmpty())
    }

    @Test
    fun getDoseOverview_allOverdue_pendingDosesBeforeNowAreOverdue() = runTest {
        val date = LocalDate(2025, 6, 15)
        medicationDao.seed(createMedication(id = 1))
        scheduleDao.seed(
            createSchedule(id = 1, medicationId = 1, timeOfDay = "08:00", startDate = date.toEpochMillis()),
            createSchedule(id = 2, medicationId = 1, timeOfDay = "12:00", startDate = date.toEpochMillis()),
        )

        val start = date.toEpochMillis("00:00")
        val end = date.toEpochMillis("23:59")
        // nowMs is way past all events — everything pending is overdue
        val now = date.toEpochMillis("23:00")

        val overview = projector.getDoseOverview(start, end, now)

        assertEquals(2, overview.totalCount)
        assertEquals(2, overview.pendingCount)
        assertEquals(2, overview.overdueCount)
        assertTrue(overview.doses.all { it.isOverdue })
    }

    @Test
    fun getDoseOverview_mixedStatuses_countsCorrectly() = runTest {
        val date = LocalDate(2025, 6, 15)
        medicationDao.seed(
            createMedication(id = 1, name = "Med A"),
            createMedication(id = 2, name = "Med B"),
            createMedication(id = 3, name = "Med C"),
            createMedication(id = 4, name = "Med D"),
        )
        scheduleDao.seed(
            createSchedule(id = 1, medicationId = 1, timeOfDay = "08:00", startDate = date.toEpochMillis()),
            createSchedule(id = 2, medicationId = 2, timeOfDay = "09:00", startDate = date.toEpochMillis()),
            createSchedule(id = 3, medicationId = 3, timeOfDay = "10:00", startDate = date.toEpochMillis()),
            createSchedule(id = 4, medicationId = 4, timeOfDay = "11:00", startDate = date.toEpochMillis()),
        )

        val time08 = date.toEpochMillis("08:00")
        val time09 = date.toEpochMillis("09:00")
        val time10 = date.toEpochMillis("10:00")

        // Med 1: taken, Med 2: skipped, Med 3: auto-skipped, Med 4: pending
        historyDao.insert(
            createHistory(
                medicationId = 1,
                scheduleId = 1,
                scheduledTime = time08,
                status = MedicationHistory.STATUS_TAKEN,
            ),
        )
        historyDao.insert(
            createHistory(
                medicationId = 2,
                scheduleId = 2,
                scheduledTime = time09,
                status = MedicationHistory.STATUS_SKIPPED,
            ),
        )
        historyDao.insert(
            createHistory(
                medicationId = 3,
                scheduleId = 3,
                scheduledTime = time10,
                status = MedicationHistory.STATUS_AUTO_SKIPPED,
            ),
        )

        val start = date.toEpochMillis("00:00")
        val end = date.toEpochMillis("23:59")
        val now = date.toEpochMillis("12:00")

        val overview = projector.getDoseOverview(start, end, now)

        assertEquals(4, overview.totalCount)
        assertEquals(1, overview.takenCount)
        assertEquals(2, overview.skippedCount) // skipped + auto_skipped
        assertEquals(1, overview.pendingCount)
    }

    @Test
    fun getDoseOverview_deletedMedication_doesNotCrash() = runTest {
        // Schedule references medication id=99 which doesn't exist in medicationDao
        val date = LocalDate(2025, 6, 15)
        scheduleDao.seed(
            createSchedule(id = 1, medicationId = 99, timeOfDay = "08:00", startDate = date.toEpochMillis()),
        )
        // medicationDao is empty — getByIdSync(99) returns null

        val start = date.toEpochMillis("00:00")
        val end = date.toEpochMillis("23:59")
        val now = date.toEpochMillis("12:00")

        val overview = projector.getDoseOverview(start, end, now)

        assertEquals(1, overview.totalCount)
        assertNull(overview.doses.first().strengthUnit)
    }

    // --- getUpcomingEventsDiagnostic ---

    @Test
    fun getUpcomingEventsDiagnostic_noSchedules_returnsZerosAndNullNextEvent() = runTest {
        val now = LocalDate(2025, 6, 15).toEpochMillis("12:00")

        val upcoming = projector.getUpcomingEventsDiagnostic(now)

        assertEquals(0, upcoming.next24hCount)
        assertEquals(0, upcoming.next7dCount)
        assertNull(upcoming.nextEventTime)
        assertTrue(upcoming.timezoneId.isNotEmpty())
    }

    @Test
    fun getUpcomingEventsDiagnostic_7dCountIncludesAll24hEvents() = runTest {
        val today = LocalDate(2025, 6, 15)
        medicationDao.seed(createMedication(id = 1))
        scheduleDao.seed(
            createSchedule(id = 1, medicationId = 1, timeOfDay = "18:00", startDate = today.toEpochMillis()),
        )

        // now is morning — 18:00 today is within 24h AND within 7d
        val now = today.toEpochMillis("06:00")

        val upcoming = projector.getUpcomingEventsDiagnostic(now)

        assertTrue(upcoming.next24hCount >= 1)
        assertTrue(upcoming.next7dCount >= upcoming.next24hCount)
    }
}
