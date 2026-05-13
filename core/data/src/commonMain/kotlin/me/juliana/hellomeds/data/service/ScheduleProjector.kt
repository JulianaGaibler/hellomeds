// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.getCycleDay
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Generates projected medication events on-the-fly from schedule definitions.
 * Combines with MedicationHistory to determine which events have been acted upon.
 *
 * Replaces the old ScheduleGenerator + VirtualEventService + EventPersistenceService chain.
 * Future events are NEVER stored in the database.
 *
 * Timezone-aware: each schedule's events are projected using the timezone resolved by
 * [ScheduleTimeZoneResolver] — either system default (LOCAL mode) or the schedule's
 * origin timezone (FIXED mode).
 */
class ScheduleProjector(
    private val scheduleDao: ScheduleDao,
    private val historyDao: MedicationHistoryDao,
    private val medicationDao: MedicationDao,
) {

    companion object {
        /** Maximum lookback for catch-up processing when an alarm fires late. */
        const val MAX_CATCH_UP_LOOKBACK_MS = 4 * 60 * 60 * 1000L // 4 hours

        // Progressive lookup windows for [findNextPendingEvent]. The first
        // window covers daily/twice-daily (the common case) at the same cost
        // as the previous hardcoded 48h. Subsequent steps are tried only when
        // no pending event was found in the prior window. 90d is the realistic
        // upper bound for prescribed dosing intervals (quarterly meds).
        private val NEXT_EVENT_LOOKUP_WINDOWS_MS = longArrayOf(
            48L * 60 * 60 * 1000L,
            7L * 24 * 60 * 60 * 1000L,
            30L * 24 * 60 * 60 * 1000L,
            90L * 24 * 60 * 60 * 1000L,
        )
    }

    /**
     * Project all events for active schedules within a time range.
     * Each event is annotated with its history record (if acted upon).
     * Cycle masking is applied automatically using medications from the database.
     */
    suspend fun projectEvents(startTime: Long, endTime: Long): List<ProjectedEvent> {
        val schedules = scheduleDao.getActive().first()
        return projectEventsForSchedules(schedules, startTime, endTime)
    }

    /**
     * Project events for specific schedules within a time range.
     * Fetches medications for cycle masking automatically.
     */
    suspend fun projectEventsForSchedules(
        schedules: List<Schedule>,
        startTime: Long,
        endTime: Long,
    ): List<ProjectedEvent> {
        val history = historyDao.getInTimeRangeSuspend(startTime, endTime)
        val medications = medicationDao.getAllSuspend().associateBy { it.id }
        return projectEventsWithHistory(schedules, startTime, endTime, history, medications)
    }

    /**
     * Pure projection: schedules + history + time range -> list of ProjectedEvent.
     * This is the core algorithm with no side effects.
     *
     * Timezone is resolved per-schedule: FIXED-mode schedules use their origin timezone,
     * LOCAL-mode schedules use the system default. A +-1 day buffer on the date range
     * ensures cross-timezone date boundary events are captured; the epoch millis filter
     * trims any spurious events.
     *
     * @param medications Optional map for cycle masking. When provided, events for cyclic
     *   medications are filtered/transformed based on cycle position. Events with history
     *   records are never suppressed (history overrides the mask).
     */
    fun projectEventsWithHistory(
        schedules: List<Schedule>,
        startTime: Long,
        endTime: Long,
        history: List<MedicationHistory>,
        medications: Map<Int, Medication> = emptyMap(),
    ): List<ProjectedEvent> {
        // Index history by (scheduleId, scheduledTime) for fast lookup
        val historyIndex = history
            .filter { it.scheduleId != null && it.scheduledTime != null }
            .associateBy { "${it.medicationId}_${it.scheduleId}_${it.scheduledTime}" }

        val events = mutableListOf<ProjectedEvent>()

        // Cache resolved TimeZone objects to avoid redundant IANA ID parsing
        val tzCache = mutableMapOf<String, TimeZone>()

        for (schedule in schedules) {
            if (schedule.isEffectivelyArchived()) continue

            val medication = medications[schedule.medicationId]
            val tz = resolveAndCache(medication, schedule, tzCache)

            // Convert time range to local dates in the schedule's timezone,
            // with +-1 day buffer to handle cross-timezone date boundaries
            val startDate = Instant.fromEpochMilliseconds(startTime)
                .toLocalDateTime(tz)
                .date
                .plus(-1, DateTimeUnit.DAY)
            val endDate = Instant.fromEpochMilliseconds(endTime)
                .toLocalDateTime(tz)
                .date
                .plus(1, DateTimeUnit.DAY)

            val scheduleEvents = generateEventsForSchedule(schedule, startDate, endDate, tz)

            for (event in scheduleEvents) {
                // Filter to exact time range (trims the buffer)
                if (event.scheduledTime < startTime || event.scheduledTime >= endTime) continue

                // Check if there's a history record for this event
                val key = "${event.medicationId}_${event.scheduleId}_${event.scheduledTime}"
                val historyRecord = historyIndex[key]

                events.add(event.copy(historyRecord = historyRecord))
            }
        }

        // Apply cycle mask: suppress or mark events as placebo based on medication cycle config
        val masked = events.flatMap { event ->
            val medication = medications[event.medicationId]
            if (medication != null) {
                val schedule = schedules.find { it.id == event.scheduleId }
                val tz = if (schedule != null) {
                    resolveAndCache(medication, schedule, tzCache)
                } else {
                    TimeZone.currentSystemDefault()
                }
                applyCycleMask(event, medication, tz)
            } else {
                listOf(event)
            }
        }

        return masked.sortedBy { it.scheduledTime }
    }

    /**
     * Resolve the timezone for a schedule and cache the result.
     */
    private fun resolveAndCache(
        medication: Medication?,
        schedule: Schedule,
        cache: MutableMap<String, TimeZone>,
    ): TimeZone {
        val tz = ScheduleTimeZoneResolver.resolve(medication, schedule)
        val id = tz.id
        return cache.getOrPut(id) { tz }
    }

    /**
     * Apply the cycle mask to a single event.
     *
     * Rules:
     * - Non-cyclic medications: pass through unchanged
     * - Events with history records: ALWAYS pass through (history overrides the mask)
     * - Active period: pass through unchanged
     * - Break period + placebos: mark as placebo
     * - Break period + no placebos: suppress (return empty)
     */
    fun applyCycleMask(
        event: ProjectedEvent,
        medication: Medication,
        tz: TimeZone = TimeZone.currentSystemDefault(),
    ): List<ProjectedEvent> {
        if (medication.cycleType != CycleType.CYCLIC) return listOf(event)

        // History overrides the mask — never suppress acted-upon events
        if (event.historyRecord != null) return listOf(event)

        val eventDate = Instant.fromEpochMilliseconds(event.scheduledTime)
            .toLocalDateTime(tz)
            .date

        val info = getCycleDay(medication, eventDate) ?: return listOf(event)

        return when {
            info.isActive -> listOf(event)
            medication.cycleHasPlacebos -> listOf(event.copy(isPlacebo = true))
            else -> emptyList()
        }
    }

    /**
     * Generate raw projected events for a single schedule over a date range.
     * Does NOT check history — returns all possible events.
     *
     * @param tz The timezone to use for converting time-of-day to epoch millis.
     */
    fun generateEventsForSchedule(
        schedule: Schedule,
        fromDate: LocalDate,
        toDate: LocalDate,
        tz: TimeZone = TimeZone.currentSystemDefault(),
    ): List<ProjectedEvent> {
        val timeOfDay = LocalTime.parse(schedule.timeOfDay)

        val scheduleStart = Instant.fromEpochMilliseconds(schedule.startDate)
            .toLocalDateTime(tz)
            .date

        val scheduleEnd = schedule.endDate?.let {
            Instant.fromEpochMilliseconds(it)
                .toLocalDateTime(tz)
                .date
        }

        return when (schedule.frequencyType) {
            FrequencyType.INTERVAL -> generateIntervalEvents(
                schedule,
                scheduleStart,
                scheduleEnd,
                fromDate,
                toDate,
                timeOfDay,
                tz,
            )

            FrequencyType.DAYS_OF_WEEK -> generateDaysOfWeekEvents(
                schedule,
                scheduleStart,
                scheduleEnd,
                fromDate,
                toDate,
                timeOfDay,
                tz,
            )
        }
    }

    /**
     * Find the next pending event across all active schedules from a given time.
     * Used for alarm scheduling — finds the soonest event that hasn't been acted upon.
     *
     * Walks progressively wider windows so weekly/biweekly/monthly schedules still
     * produce a wakeup. Returns as soon as a pending event is found, so the common
     * (daily/twice-daily) case pays only the cost of the first 48h projection.
     */
    suspend fun findNextPendingEvent(fromTime: Long = Clock.System.now().toEpochMilliseconds()): ProjectedEvent? {
        for (windowMs in NEXT_EVENT_LOOKUP_WINDOWS_MS) {
            val events = projectEvents(fromTime, fromTime + windowMs)
            events.firstOrNull { it.isPending }?.let { return it }
        }
        return null
    }

    /**
     * Get all pending events at a specific time (1-minute tolerance).
     * Used for notification building — groups all meds at the same time slot.
     */
    suspend fun getPendingEventsAtTime(scheduledTime: Long): List<ProjectedEvent> {
        val tolerance = 60_000L // 1 minute
        val events = projectEvents(scheduledTime - tolerance, scheduledTime + tolerance)
        return events.filter { it.isPending && kotlin.math.abs(it.scheduledTime - scheduledTime) < tolerance }
    }

    /**
     * Return all pending events in [startTime, endTime).
     * Used by the watermark model to catch up on missed events.
     */
    suspend fun getPendingEventsSince(startTime: Long, endTime: Long): List<ProjectedEvent> {
        return projectEvents(startTime, endTime).filter { it.isPending }
    }

    /**
     * Check if a specific event (identified by schedule + time) should be auto-skipped.
     * An event should be auto-skipped if the next scheduled occurrence has already passed.
     */
    suspend fun shouldAutoSkip(
        scheduleId: Int,
        scheduledTime: Long,
        currentTime: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean {
        val schedule = scheduleDao.getById(scheduleId).first() ?: return false
        if (schedule.isEffectivelyArchived()) return false

        val medication = medicationDao.getByIdSync(schedule.medicationId)
        val tz = ScheduleTimeZoneResolver.resolve(medication, schedule)

        val eventDate = Instant.fromEpochMilliseconds(scheduledTime)
            .toLocalDateTime(tz)
            .date

        val nextEvents = generateEventsForSchedule(
            schedule,
            fromDate = eventDate.plus(1, DateTimeUnit.DAY),
            toDate = eventDate.plus(31, DateTimeUnit.DAY),
            tz = tz,
        )

        val nextOccurrence = nextEvents.firstOrNull()
        return nextOccurrence != null && nextOccurrence.scheduledTime < currentTime
    }

    private fun generateIntervalEvents(
        schedule: Schedule,
        scheduleStart: LocalDate,
        scheduleEnd: LocalDate?,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeOfDay: LocalTime,
        tz: TimeZone,
    ): List<ProjectedEvent> {
        // Defense in depth: validation should reject this at write-time, but
        // frequencyValue <= 0 here would throw ArithmeticException on the
        // modulo alignment below or produce an infinite loop (currentDate.plus(0)
        // never advances). Drop the schedule rather than propagate the crash —
        // a bad row can surface via backup import, manual DB edit, or partial migration.
        if (schedule.frequencyValue <= 0) return emptyList()

        val events = mutableListOf<ProjectedEvent>()

        var currentDate = maxOf(scheduleStart, fromDate)

        // Align to schedule interval if started before fromDate
        if (scheduleStart < fromDate) {
            val daysSinceStart = scheduleStart.daysUntil(fromDate).toLong()
            val remainder = daysSinceStart % schedule.frequencyValue
            if (remainder > 0) {
                currentDate = fromDate.plus((schedule.frequencyValue - remainder).toInt(), DateTimeUnit.DAY)
            }
        }

        while (currentDate <= toDate &&
            (scheduleEnd == null || currentDate <= scheduleEnd)
        ) {
            val scheduledTimestamp = LocalDateTime(currentDate, timeOfDay)
                .toInstant(tz)
                .toEpochMilliseconds()

            events.add(
                ProjectedEvent(
                    scheduleId = schedule.id,
                    medicationId = schedule.medicationId,
                    scheduledTime = scheduledTimestamp,
                    dose = schedule.dose,
                ),
            )

            currentDate = currentDate.plus(schedule.frequencyValue, DateTimeUnit.DAY)
        }

        return events
    }

    private fun generateDaysOfWeekEvents(
        schedule: Schedule,
        scheduleStart: LocalDate,
        scheduleEnd: LocalDate?,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeOfDay: LocalTime,
        tz: TimeZone,
    ): List<ProjectedEvent> {
        val events = mutableListOf<ProjectedEvent>()

        val daysOfWeek = schedule.daysOfWeek
            ?.split(",")
            ?.mapNotNull { token -> runCatching { DayOfWeek.valueOf(token.trim()) }.getOrNull() }
            ?.toSet()
            ?: return events

        var currentDate = maxOf(scheduleStart, fromDate)

        while (currentDate <= toDate &&
            (scheduleEnd == null || currentDate <= scheduleEnd)
        ) {
            if (daysOfWeek.contains(currentDate.dayOfWeek)) {
                val scheduledTimestamp = LocalDateTime(currentDate, timeOfDay)
                    .toInstant(tz)
                    .toEpochMilliseconds()

                events.add(
                    ProjectedEvent(
                        scheduleId = schedule.id,
                        medicationId = schedule.medicationId,
                        scheduledTime = scheduledTimestamp,
                        dose = schedule.dose,
                    ),
                )
            }

            currentDate = currentDate.plus(1, DateTimeUnit.DAY)
        }

        return events
    }

    // --- Diagnostic Methods ---

    /**
     * Generate a dose overview for a given day.
     * Used by both BugReportService and debug ViewModels.
     * Returns medicationId only — callers resolve names as needed.
     */
    suspend fun getDoseOverview(
        dayStartMs: Long,
        dayEndMs: Long,
        nowMs: Long,
    ): me.juliana.hellomeds.data.support.DoseOverviewDiagnostic {
        val events = projectEvents(dayStartMs, dayEndMs).sortedBy { it.scheduledTime }

        val doses = events.map { event ->
            val med = medicationDao.getByIdSync(event.medicationId)
            me.juliana.hellomeds.data.support.DoseDiagnostic(
                medicationId = event.medicationId,
                dose = event.dose,
                strengthUnit = med?.strengthUnit?.value,
                scheduledTime = event.scheduledTime,
                status = event.historyRecord?.status ?: "PENDING",
                isOverdue = event.isPending && event.scheduledTime < nowMs,
            )
        }

        return me.juliana.hellomeds.data.support.DoseOverviewDiagnostic(
            totalCount = doses.size,
            takenCount = doses.count { it.status == me.juliana.hellomeds.data.database.entities.MedicationHistory.STATUS_TAKEN },
            pendingCount = doses.count { it.status == "PENDING" },
            skippedCount = doses.count {
                it.status == me.juliana.hellomeds.data.database.entities.MedicationHistory.STATUS_SKIPPED ||
                    it.status == me.juliana.hellomeds.data.database.entities.MedicationHistory.STATUS_AUTO_SKIPPED
            },
            overdueCount = doses.count { it.isOverdue },
            doses = doses,
        )
    }

    /**
     * Count upcoming pending events in 24h and 7d windows, with timezone info.
     */
    suspend fun getUpcomingEventsDiagnostic(nowMs: Long): me.juliana.hellomeds.data.support.UpcomingEventsDiagnostic {
        val next24h = projectEvents(nowMs, nowMs + 24 * 3600_000L).count { it.isPending }
        val next7d = projectEvents(nowMs, nowMs + 7 * 24 * 3600_000L).count { it.isPending }
        val nextEvent = findNextPendingEvent(nowMs)
        val tz = TimeZone.currentSystemDefault()
        val nowInstant = Instant.fromEpochMilliseconds(nowMs)
        val localDt = nowInstant.toLocalDateTime(tz)
        val offsetSeconds = (
            (
                localDt.toInstant(
                    kotlinx.datetime.UtcOffset.ZERO,
                ).toEpochMilliseconds() - nowMs
                ) / 1000
            ).toInt()
        return me.juliana.hellomeds.data.support.UpcomingEventsDiagnostic(
            next24hCount = next24h,
            next7dCount = next7d,
            nextEventTime = nextEvent?.scheduledTime,
            timezoneId = tz.id,
            utcOffsetSeconds = offsetSeconds,
        )
    }
}
