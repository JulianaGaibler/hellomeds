// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import kotlinx.datetime.LocalDate
import me.juliana.hellomeds.data.createMedication
import me.juliana.hellomeds.data.createSchedule
import me.juliana.hellomeds.data.toEpochMillis
import me.juliana.hellomeds.data.model.StockEvent
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class StockPredictionEngineTest {

    private lateinit var engine: StockPredictionEngine

    @BeforeTest
    fun setup() {
        engine = StockPredictionEngine()
    }

    // ================================================================
    // deriveContainerBreakdown — pure companion function
    // ================================================================

    @Test
    fun deriveContainerBreakdown_partialContainer() {
        val (remaining, sealed) = StockPredictionEngine.deriveContainerBreakdown(116.0, 10.0)
        assertEquals(6.0, remaining)
        assertEquals(11, sealed)
    }

    @Test
    fun deriveContainerBreakdown_moduloZero_fullContainerNotEmpty() {
        val (remaining, sealed) = StockPredictionEngine.deriveContainerBreakdown(60.0, 30.0)
        assertEquals(30.0, remaining)
        assertEquals(1, sealed)
    }

    @Test
    fun deriveContainerBreakdown_singleFullContainer() {
        val (remaining, sealed) = StockPredictionEngine.deriveContainerBreakdown(10.0, 10.0)
        assertEquals(10.0, remaining)
        assertEquals(0, sealed)
    }

    @Test
    fun deriveContainerBreakdown_noPackaging() {
        val (remaining, sealed) = StockPredictionEngine.deriveContainerBreakdown(50.0, null)
        assertEquals(50.0, remaining)
        assertEquals(0, sealed)
    }

    @Test
    fun deriveContainerBreakdown_zeroPackaging() {
        val (remaining, sealed) = StockPredictionEngine.deriveContainerBreakdown(50.0, 0.0)
        assertEquals(50.0, remaining)
        assertEquals(0, sealed)
    }

    @Test
    fun deriveContainerBreakdown_negativePackaging() {
        val (remaining, sealed) = StockPredictionEngine.deriveContainerBreakdown(50.0, -5.0)
        assertEquals(50.0, remaining)
        assertEquals(0, sealed)
    }

    @Test
    fun deriveContainerBreakdown_zeroStock() {
        val (remaining, sealed) = StockPredictionEngine.deriveContainerBreakdown(0.0, 10.0)
        assertEquals(0.0, remaining)
        assertEquals(0, sealed)
    }

    @Test
    fun deriveContainerBreakdown_smallPartial() {
        val (remaining, sealed) = StockPredictionEngine.deriveContainerBreakdown(3.0, 10.0)
        assertEquals(3.0, remaining)
        assertEquals(0, sealed)
    }

    // ================================================================
    // estimateContainerBreakdown — pure companion function
    // ================================================================

    @Test
    fun estimateContainerBreakdown_partiallyConsumedContainer() {
        // 2 containers, 40 doses/container, 3 doses/day, container opened 12 days ago
        // currentRemaining = 40 - (12 * 3) = 4, totalDoses = 1 * 40 + 4 = 44
        val dayMs = 24L * 60 * 60 * 1000
        val startTime = dayMs // non-zero reference point
        val now = startTime + 12 * dayMs // 12 days later
        val schedules = listOf(
            createSchedule(id = 1, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
            createSchedule(id = 2, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
            createSchedule(id = 3, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val breakdown = StockPredictionEngine.estimateContainerBreakdown(
            totalContainers = 2.0,
            packagingQuantity = 40.0,
            containerStartedAt = startTime,
            fallbackReferenceTimestamp = startTime,
            schedules = schedules,
            nowMillis = now,
        )

        assertEquals(1, breakdown.sealedCount)
        assertEquals(4.0, breakdown.currentContainerRemaining)
        assertEquals(44.0, breakdown.totalDoses)
    }

    @Test
    fun estimateContainerBreakdown_freshContainer_equalsFullCapacity() {
        // Container just opened — no consumption yet, should equal full capacity
        val now = 1000L
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val breakdown = StockPredictionEngine.estimateContainerBreakdown(
            totalContainers = 2.0,
            packagingQuantity = 40.0,
            containerStartedAt = now, // just opened
            fallbackReferenceTimestamp = 0L,
            schedules = schedules,
            nowMillis = now,
        )

        assertEquals(1, breakdown.sealedCount)
        assertEquals(40.0, breakdown.currentContainerRemaining)
        assertEquals(80.0, breakdown.totalDoses) // same as old totalContainers * packagingQuantity
    }

    // ================================================================
    // dosesPerDay — pure companion function
    // ================================================================

    @Test
    fun dosesPerDay_dailyInterval() {
        val schedules =
            listOf(createSchedule(frequencyType = FrequencyType.INTERVAL, frequencyValue = 1))
        assertEquals(1.0, StockPredictionEngine.dosesPerDay(schedules))
    }

    @Test
    fun dosesPerDay_everyTwoDays() {
        val schedules =
            listOf(createSchedule(frequencyType = FrequencyType.INTERVAL, frequencyValue = 2))
        assertEquals(0.5, StockPredictionEngine.dosesPerDay(schedules))
    }

    @Test
    fun dosesPerDay_twoDailySchedules() {
        val schedules = listOf(
            createSchedule(id = 1, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
            createSchedule(id = 2, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        assertEquals(2.0, StockPredictionEngine.dosesPerDay(schedules))
    }

    @Test
    fun dosesPerDay_daysOfWeek() {
        val schedules = listOf(
            createSchedule(
                frequencyType = FrequencyType.DAYS_OF_WEEK,
                daysOfWeek = "MONDAY,WEDNESDAY,FRIDAY",
            ),
        )
        assertEquals(3.0 / 7.0, StockPredictionEngine.dosesPerDay(schedules))
    }

    @Test
    fun dosesPerDay_emptySchedules() {
        assertEquals(0.0, StockPredictionEngine.dosesPerDay(emptyList()))
    }

    // ================================================================
    // dailyConsumption — pure companion function
    // ================================================================

    @Test
    fun dailyConsumption_twoPillsDaily() {
        val schedules = listOf(
            createSchedule(dose = 2.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        assertEquals(2.0, StockPredictionEngine.dailyConsumption(schedules))
    }

    @Test
    fun dailyConsumption_onePillEveryTwoDays() {
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 2),
        )
        assertEquals(0.5, StockPredictionEngine.dailyConsumption(schedules))
    }

    @Test
    fun dailyConsumption_mixedSchedules() {
        val schedules = listOf(
            createSchedule(
                id = 1,
                dose = 2.0,
                frequencyType = FrequencyType.INTERVAL,
                frequencyValue = 1,
            ),
            createSchedule(id = 2, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 2),
        )
        assertEquals(2.5, StockPredictionEngine.dailyConsumption(schedules))
    }

    // ================================================================
    // predict() — EXACT mode
    // ================================================================

    @Test
    fun predict_exact_withPackaging_hasContainerSwitches() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.EXACT,
            packagingQuantity = 10.0,
        )
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
            createSchedule(id = 2, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val prediction = engine.predict(
            medication = medication,
            totalDoses = 116.0,
            schedules = schedules,
        )

        assertTrue(
            prediction.centerDaysRemaining!! in 57..58,
            "Expected ~58 days, got ${prediction.centerDaysRemaining}",
        )
        assertNotNull(prediction.runOutDate)

        val switchEvents = prediction.centerPoints.filter { it.event is StockEvent.ContainerSwitch }
        assertTrue(switchEvents.isNotEmpty(), "Expected container switches, got ${switchEvents.size}")
        assertEquals(11, switchEvents.size)
    }

    @Test
    fun predict_exact_withoutPackaging_noSwitches() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.EXACT,
            packagingQuantity = null,
        )
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
            createSchedule(id = 2, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val prediction = engine.predict(
            medication = medication,
            totalDoses = 50.0,
            schedules = schedules,
        )

        assertTrue(
            prediction.centerDaysRemaining!! in 24..25,
            "Expected ~25 days, got ${prediction.centerDaysRemaining}",
        )

        val switchEvents = prediction.centerPoints.filter { it.event is StockEvent.ContainerSwitch }
        assertTrue(switchEvents.isEmpty(), "Expected no switches")
    }

    @Test
    fun predict_exact_moduloZero_correctSwitchTiming() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.EXACT,
            packagingQuantity = 30.0,
        )
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val prediction = engine.predict(
            medication = medication,
            totalDoses = 60.0,
            schedules = schedules,
        )

        assertTrue(
            prediction.centerDaysRemaining!! in 59..60,
            "Expected ~60 days, got ${prediction.centerDaysRemaining}",
        )

        val switchEvents = prediction.centerPoints.filter { it.event is StockEvent.ContainerSwitch }
        assertEquals(1, switchEvents.size)
    }

    @Test
    fun predict_zeroStock_returnsEmpty() {
        val medication = createMedication(trackingPrecision = TrackingPrecision.EXACT)
        val schedules = listOf(createSchedule())

        val prediction = engine.predict(
            medication = medication,
            totalDoses = 0.0,
            schedules = schedules,
        )

        assertEquals(StockPrediction.EMPTY, prediction)
    }

    @Test
    fun predict_noSchedules_returnsEmpty() {
        val medication = createMedication(trackingPrecision = TrackingPrecision.EXACT)

        val prediction = engine.predict(
            medication = medication,
            totalDoses = 100.0,
            schedules = emptyList(),
        )

        assertEquals(StockPrediction.EMPTY, prediction)
    }

    @Test
    fun predict_exact_singleWeekday_correctDays() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.EXACT,
            packagingQuantity = null,
        )
        val schedules = listOf(
            createSchedule(
                dose = 1.0,
                frequencyType = FrequencyType.DAYS_OF_WEEK,
                daysOfWeek = "MONDAY",
            ),
        )
        // 100 pills, 1 dose per week -> 100 weeks -> 700 days
        val prediction = engine.predict(
            medication = medication,
            totalDoses = 100.0,
            schedules = schedules,
        )

        // Simulation steps day-by-day with fractional consumption (1/7 per day).
        // Result varies slightly depending on which day of week "today" is.
        assertTrue(
            prediction.centerDaysRemaining!! in 690..705,
            "Expected ~700 days, got ${prediction.centerDaysRemaining}",
        )
        assertNotNull(prediction.runOutDate)
    }

    // ================================================================
    // predict() — ESTIMATED mode
    // ================================================================

    @Test
    fun predict_estimated_withPackaging_correctDays() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.ESTIMATED,
            packagingQuantity = 30.0,
        )
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val prediction = engine.predict(
            medication = medication,
            totalDoses = 60.0,
            schedules = schedules,
        )

        assertTrue(
            prediction.centerDaysRemaining!! in 59..60,
            "Expected ~60 days, got ${prediction.centerDaysRemaining}",
        )
        assertNotNull(prediction.runOutDate)
    }

    @Test
    fun predict_estimated_withoutPackaging_returnsEmpty() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.ESTIMATED,
            packagingQuantity = null,
        )
        val schedules = listOf(createSchedule())

        val prediction = engine.predict(
            medication = medication,
            totalDoses = 0.0,
            schedules = schedules,
        )

        assertEquals(StockPrediction.EMPTY, prediction)
    }

    // ================================================================
    // predict() — ESTIMATED mode: uncertainty bounds
    // ================================================================

    @Test
    fun predict_estimated_uncertaintyBounds_nonEmpty() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.ESTIMATED,
            packagingQuantity = 30.0,
        )
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val prediction = engine.predict(
            medication = medication,
            totalDoses = 60.0,
            schedules = schedules,
        )

        assertTrue(prediction.lowerBoundPoints.isNotEmpty(), "Lower bound should have data points")
        assertTrue(prediction.upperBoundPoints.isNotEmpty(), "Upper bound should have data points")
    }

    @Test
    fun predict_estimated_uncertaintyBounds_dateOrdering() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.ESTIMATED,
            packagingQuantity = 30.0,
        )
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val prediction = engine.predict(
            medication = medication,
            totalDoses = 60.0,
            schedules = schedules,
        )

        assertNotNull(prediction.earlyRunOutDate, "ESTIMATED should have earlyRunOutDate")
        assertNotNull(prediction.runOutDate, "Should have center runOutDate")
        assertNotNull(prediction.lateRunOutDate, "ESTIMATED should have lateRunOutDate")

        // Early <= center <= late
        assertTrue(
            prediction.earlyRunOutDate!! <= prediction.runOutDate!!,
            "Early run-out should be before or at center",
        )
        assertTrue(
            prediction.runOutDate!! <= prediction.lateRunOutDate!!,
            "Center run-out should be before or at late",
        )
    }

    @Test
    fun predict_estimated_weeklySchedule_fractionalConsumption() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.ESTIMATED,
            packagingQuantity = 10.0,
        )
        val schedules = listOf(
            createSchedule(
                dose = 1.0,
                frequencyType = FrequencyType.DAYS_OF_WEEK,
                daysOfWeek = "MONDAY",
            ),
        )
        // 10 pills, 1 dose per week -> 10 weeks -> ~70 days
        val prediction = engine.predict(
            medication = medication,
            totalDoses = 10.0,
            schedules = schedules,
        )

        assertNotNull(prediction.centerDaysRemaining)
        assertTrue(
            prediction.centerDaysRemaining!! in 63..77,
            "Expected ~70 days for weekly schedule, got ${prediction.centerDaysRemaining}",
        )
    }

    @Test
    fun predict_estimated_noBoundsWhenEmpty() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.ESTIMATED,
            packagingQuantity = null,
        )
        val schedules = listOf(createSchedule())

        val prediction = engine.predict(
            medication = medication,
            totalDoses = 0.0,
            schedules = schedules,
        )

        assertTrue(prediction.lowerBoundPoints.isEmpty())
        assertTrue(prediction.upperBoundPoints.isEmpty())
        assertNull(prediction.earlyRunOutDate)
        assertNull(prediction.lateRunOutDate)
    }

    @Test
    fun predict_exact_weeklySchedule_largeDuration() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.EXACT,
            packagingQuantity = null,
        )
        val schedules = listOf(
            createSchedule(
                dose = 1.0,
                frequencyType = FrequencyType.DAYS_OF_WEEK,
                daysOfWeek = "MONDAY",
            ),
        )
        // 52 pills, 1 dose per week -> 52 weeks -> ~364 days
        val prediction = engine.predict(
            medication = medication,
            totalDoses = 52.0,
            schedules = schedules,
        )

        assertNotNull(prediction.centerDaysRemaining)
        assertTrue(
            prediction.centerDaysRemaining!! in 357..371,
            "Expected ~364 days, got ${prediction.centerDaysRemaining}",
        )
    }

    // ================================================================
    // backCalculateContainerStartedAt — companion function
    // ================================================================

    @Test
    fun backCalculate_standardCase() {
        val now = 1_000_000_000_000L
        val result = StockPredictionEngine.backCalculateContainerStartedAt(
            packagingQuantity = 30.0,
            desiredRemaining = 20.0,
            dailyConsumption = 2.0,
            nowMillis = now,
        )
        // Consumed 10 doses at 2/day = 5 days ago
        val expectedMs = now - (5 * 86_400_000L)
        assertEquals(expectedMs, result)
    }

    @Test
    fun backCalculate_fullContainer_returnsNow() {
        val now = 1_000_000_000_000L
        val result = StockPredictionEngine.backCalculateContainerStartedAt(
            packagingQuantity = 60.0,
            desiredRemaining = 60.0,
            dailyConsumption = 2.0,
            nowMillis = now,
        )
        assertEquals(now, result)
    }

    @Test
    fun backCalculate_emptyContainer() {
        val now = 1_000_000_000_000L
        val result = StockPredictionEngine.backCalculateContainerStartedAt(
            packagingQuantity = 30.0,
            desiredRemaining = 0.0,
            dailyConsumption = 2.0,
            nowMillis = now,
        )
        // Consumed 30 doses at 2/day = 15 days ago
        val expectedMs = now - (15 * 86_400_000L)
        assertEquals(expectedMs, result)
    }

    @Test
    fun backCalculate_zeroDailyConsumption_returnsNow() {
        val now = 1_000_000_000_000L
        val result = StockPredictionEngine.backCalculateContainerStartedAt(
            packagingQuantity = 30.0,
            desiredRemaining = 15.0,
            dailyConsumption = 0.0,
            nowMillis = now,
        )
        assertEquals(now, result)
    }

    // ================================================================
    // Rate-change round-trip: estimate → backCalculate → estimate
    // ================================================================

    @Test
    fun rateChangeRoundTrip_rateIncrease_preservesRemaining() {
        val dayMs = 24L * 60 * 60 * 1000
        val pkgQty = 40.0
        val containerStartedAt = dayMs // non-zero reference
        val now = containerStartedAt + 10 * dayMs // 10 days later

        // Step 1: estimate remaining with old rate
        val oldSchedules = listOf(
            createSchedule(id = 1, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
            createSchedule(id = 2, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
            createSchedule(id = 3, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val oldBreakdown = StockPredictionEngine.estimateContainerBreakdown(
            totalContainers = 1.0,
            packagingQuantity = pkgQty,
            containerStartedAt = containerStartedAt,
            fallbackReferenceTimestamp = 0L,
            schedules = oldSchedules,
            nowMillis = now,
        )
        assertEquals(10.0, oldBreakdown.currentContainerRemaining)

        // Step 2: back-calculate new containerStartedAt for doubled rate (6/day)
        val newRate = 6.0
        val newContainerStartedAt = StockPredictionEngine.backCalculateContainerStartedAt(
            packagingQuantity = pkgQty,
            desiredRemaining = oldBreakdown.currentContainerRemaining,
            dailyConsumption = newRate,
            nowMillis = now,
        )

        // Step 3: re-estimate with new rate → should still be ~10
        val newSchedules = List(6) {
            createSchedule(id = it + 1, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1)
        }
        val newBreakdown = StockPredictionEngine.estimateContainerBreakdown(
            totalContainers = 1.0,
            packagingQuantity = pkgQty,
            containerStartedAt = newContainerStartedAt,
            fallbackReferenceTimestamp = 0L,
            schedules = newSchedules,
            nowMillis = now,
        )
        assertEquals(10.0, newBreakdown.currentContainerRemaining, 0.001)
    }

    @Test
    fun rateChangeRoundTrip_rateDecrease_preservesRemaining() {
        val dayMs = 24L * 60 * 60 * 1000
        val pkgQty = 40.0
        val containerStartedAt = dayMs
        val now = containerStartedAt + 10 * dayMs

        // Old rate 3/day → remaining = 10
        val oldSchedules = listOf(
            createSchedule(id = 1, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
            createSchedule(id = 2, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
            createSchedule(id = 3, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val oldBreakdown = StockPredictionEngine.estimateContainerBreakdown(
            totalContainers = 1.0,
            packagingQuantity = pkgQty,
            containerStartedAt = containerStartedAt,
            fallbackReferenceTimestamp = 0L,
            schedules = oldSchedules,
            nowMillis = now,
        )
        assertEquals(10.0, oldBreakdown.currentContainerRemaining)

        // Rate drops to 1/day
        val newContainerStartedAt = StockPredictionEngine.backCalculateContainerStartedAt(
            packagingQuantity = pkgQty,
            desiredRemaining = oldBreakdown.currentContainerRemaining,
            dailyConsumption = 1.0,
            nowMillis = now,
        )

        val newSchedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val newBreakdown = StockPredictionEngine.estimateContainerBreakdown(
            totalContainers = 1.0,
            packagingQuantity = pkgQty,
            containerStartedAt = newContainerStartedAt,
            fallbackReferenceTimestamp = 0L,
            schedules = newSchedules,
            nowMillis = now,
        )
        assertEquals(10.0, newBreakdown.currentContainerRemaining, 0.001)
    }

    @Test
    fun rateChangeRoundTrip_emptyContainer_staysEmpty() {
        val dayMs = 24L * 60 * 60 * 1000
        val pkgQty = 30.0
        val containerStartedAt = dayMs
        val now = containerStartedAt + 15 * dayMs // 15 days, 2/day → consumed 30 → remaining = 0

        val oldSchedules = listOf(
            createSchedule(id = 1, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
            createSchedule(id = 2, dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val oldBreakdown = StockPredictionEngine.estimateContainerBreakdown(
            totalContainers = 1.0,
            packagingQuantity = pkgQty,
            containerStartedAt = containerStartedAt,
            fallbackReferenceTimestamp = 0L,
            schedules = oldSchedules,
            nowMillis = now,
        )
        assertEquals(0.0, oldBreakdown.currentContainerRemaining)

        // Rate changes to 1/day — container should NOT resurrect doses
        val newContainerStartedAt = StockPredictionEngine.backCalculateContainerStartedAt(
            packagingQuantity = pkgQty,
            desiredRemaining = 0.0,
            dailyConsumption = 1.0,
            nowMillis = now,
        )

        val newSchedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val newBreakdown = StockPredictionEngine.estimateContainerBreakdown(
            totalContainers = 1.0,
            packagingQuantity = pkgQty,
            containerStartedAt = newContainerStartedAt,
            fallbackReferenceTimestamp = 0L,
            schedules = newSchedules,
            nowMillis = now,
        )
        assertEquals(0.0, newBreakdown.currentContainerRemaining, 0.001)
    }

    // ================================================================
    // isDoseDay — companion function
    // ================================================================

    @Test
    fun isDoseDay_dailyInterval_alwaysTrue() {
        val schedules = listOf(
            createSchedule(frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )
        val date = LocalDate(2026, 4, 10) // any date
        assertTrue(StockPredictionEngine.isDoseDay(date, schedules))
    }

    @Test
    fun isDoseDay_everyTwoDays_alternates() {
        val schedules = listOf(
            createSchedule(
                frequencyType = FrequencyType.INTERVAL,
                frequencyValue = 2,
                startDate = LocalDate(2026, 1, 1)
                    .toEpochMillis(),
            ),
        )
        // Day 0 (Jan 1) = dose day, Day 1 (Jan 2) = no dose, Day 2 (Jan 3) = dose day
        assertTrue(StockPredictionEngine.isDoseDay(LocalDate(2026, 1, 1), schedules))
        assertFalse(StockPredictionEngine.isDoseDay(LocalDate(2026, 1, 2), schedules))
        assertTrue(StockPredictionEngine.isDoseDay(LocalDate(2026, 1, 3), schedules))
    }

    @Test
    fun isDoseDay_daysOfWeek_onlyScheduledDays() {
        val schedules = listOf(
            createSchedule(
                frequencyType = FrequencyType.DAYS_OF_WEEK,
                daysOfWeek = "MONDAY,WEDNESDAY,FRIDAY",
            ),
        )
        // 2026-04-06 = Monday, 2026-04-07 = Tuesday, 2026-04-08 = Wednesday
        assertTrue(StockPredictionEngine.isDoseDay(LocalDate(2026, 4, 6), schedules))
        assertFalse(StockPredictionEngine.isDoseDay(LocalDate(2026, 4, 7), schedules))
        assertTrue(StockPredictionEngine.isDoseDay(LocalDate(2026, 4, 8), schedules))
    }

    // ================================================================
    // predict() — cyclic medications
    // ================================================================

    @Test
    fun predict_cyclic_noPlacebos_stockLastsLonger() {
        // 21 active / 7 break, no placebos → stock should last ~33% longer.
        // Pin "now" to mid-day Jan 14 2026 UTC = cycle day 13 of the
        // 21-active/7-break cycle starting Jan 1 2026 → deep inside the active
        // phase, so the 21-dose prediction is deterministically 28 days in
        // any system timezone (±12h jitter cannot push us off active phase).
        val engine = StockPredictionEngine(
            clock = FixedClock(Instant.parse("2026-01-14T12:00:00Z")),
        )
        val cyclicMed = createMedication(
            trackingPrecision = TrackingPrecision.EXACT,
            packagingQuantity = null,
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 21,
            cycleDaysBreak = 7,
            cycleHasPlacebos = false,
            cycleStartDate = LocalDate(2026, 1, 1),
        )
        val nonCyclicMed = createMedication(
            trackingPrecision = TrackingPrecision.EXACT,
            packagingQuantity = null,
        )
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )

        val cyclicPrediction = engine.predict(cyclicMed, totalDoses = 21.0, schedules = schedules)
        val nonCyclicPrediction = engine.predict(nonCyclicMed, totalDoses = 21.0, schedules = schedules)

        // Non-cyclic: 21 doses / 1 per day = 21 days
        // Cyclic: 21 active days + 7 break days = 28 days
        assertTrue(
            cyclicPrediction.centerDaysRemaining!! > nonCyclicPrediction.centerDaysRemaining!!,
            "Cyclic med should last longer: cyclic=${cyclicPrediction.centerDaysRemaining}, " +
                "nonCyclic=${nonCyclicPrediction.centerDaysRemaining}",
        )
        assertTrue(
            cyclicPrediction.centerDaysRemaining!! in 26..30,
            "Expected ~28 days for cyclic 21/7, got ${cyclicPrediction.centerDaysRemaining}",
        )
    }

    @Test
    fun predict_cyclic_withPlacebos_sameAsNonCyclic() {
        // 21 active / 7 placebo → pill consumed every day, same as non-cyclic.
        // Pin the clock for the same reason as the no-placebos test above —
        // engine output is identical across calendar dates with placebos, but
        // pinning makes the *test* date-independent end-to-end.
        val engine = StockPredictionEngine(
            clock = FixedClock(Instant.parse("2026-01-14T12:00:00Z")),
        )
        val cyclicMed = createMedication(
            trackingPrecision = TrackingPrecision.EXACT,
            packagingQuantity = null,
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 21,
            cycleDaysBreak = 7,
            cycleHasPlacebos = true,
            cycleStartDate = LocalDate(2026, 1, 1),
        )
        val nonCyclicMed = createMedication(
            trackingPrecision = TrackingPrecision.EXACT,
            packagingQuantity = null,
        )
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )

        val cyclicPrediction = engine.predict(cyclicMed, totalDoses = 28.0, schedules = schedules)
        val nonCyclicPrediction = engine.predict(nonCyclicMed, totalDoses = 28.0, schedules = schedules)

        // Both should take ~28 days since placebos consume stock
        assertEquals(
            nonCyclicPrediction.centerDaysRemaining!!,
            cyclicPrediction.centerDaysRemaining!!,
            "Placebo cycle should match non-cyclic duration",
        )
    }

    @Test
    fun predict_cyclic_noPlacebos_noMarkersOnBreakDays() {
        val medication = createMedication(
            trackingPrecision = TrackingPrecision.EXACT,
            packagingQuantity = null,
            cycleType = CycleType.CYCLIC,
            cycleDaysActive = 5,
            cycleDaysBreak = 2,
            cycleHasPlacebos = false,
            cycleStartDate = LocalDate(2026, 1, 1),
        )
        val schedules = listOf(
            createSchedule(dose = 1.0, frequencyType = FrequencyType.INTERVAL, frequencyValue = 1),
        )

        val prediction = engine.predict(medication, totalDoses = 10.0, schedules = schedules)

        // With 5 active / 2 break, 10 doses takes 2 full active periods + break gaps
        // Should have exactly 10 data points (one per active dose day), not 14 (every day)
        val futurePoints = prediction.centerPoints.filter { it.isFuture }
        assertTrue(
            futurePoints.size <= 12,
            "Expected roughly 10 data points (only dose days), got ${futurePoints.size}",
        )
    }
}

/** Test helper for pinning `Clock` to a fixed instant. */
private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
