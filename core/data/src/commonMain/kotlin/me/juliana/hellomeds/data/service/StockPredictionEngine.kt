// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.StockDataPoint
import me.juliana.hellomeds.data.model.StockEvent
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.getCycleDay
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.util.currentTimeMillis
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Single source of truth for all stock depletion predictions.
 *
 * Runs a container-aware step simulation that models:
 * - Per-dose depletion using schedule dose quantities
 * - Container transitions: when active container depletes, switches to next sealed container
 *
 * For EXACT medications, totalDoses = ledger sum.
 * For ESTIMATED medications, callers use [estimateContainerBreakdown] to compute totalDoses,
 * accounting for partial consumption of the current open container.
 *
 * Both the graph (StockLine points) and text predictions (StockStatus dates) consume the
 * same [StockPrediction] output, so they can never diverge.
 */
class StockPredictionEngine(
    private val clock: Clock = Clock.System,
) {

    private val TAG = "StockPrediction"
    private val dayMs = 24.0 * 60 * 60 * 1000

    // Uncertainty band constants
    private val minSpreadDays = 3.0 // Band never tighter than ±1.5 days
    private val maxSpreadDays = 21.0 // Band never wider than ±10.5 days
    private val spreadGrowthPerDay = 0.3 // Band widens by this many days per projected day

    /**
     * Generate a full stock prediction for a medication.
     *
     * @param medication The medication to predict for
     * @param totalDoses Total remaining doses (EXACT: ledger sum, ESTIMATED: containers × packagingQuantity)
     * @param schedules Active (non-archived) schedules for this medication
     * @return Prediction with graph points and date estimates, or empty prediction if insufficient data
     */
    fun predict(medication: Medication, totalDoses: Double, schedules: List<Schedule>): StockPrediction {
        if (totalDoses <= 0 || schedules.isEmpty()) return StockPrediction.EMPTY

        val dpd = dosesPerDay(schedules)
        if (dpd <= 0) return StockPrediction.EMPTY

        val dailyConsumption = dailyConsumption(schedules)
        if (dailyConsumption <= 0) return StockPrediction.EMPTY
        val doseQuantity = dailyConsumption / dpd

        if (doseQuantity <= 0) return StockPrediction.EMPTY

        val doseIntervalMs = (24.0 * 60 * 60 * 1000 / dpd).toLong()

        AppLogger.d(
            TAG,
            "predict: totalDoses=$totalDoses, dpd=$dpd, dailyConsumption=$dailyConsumption, " +
                "doseQuantity=$doseQuantity, doseIntervalMs=${doseIntervalMs}ms (${doseIntervalMs / 3600000.0}h), " +
                "packagingQty=${medication.packagingQuantity}, precision=${medication.trackingPrecision}",
        )

        val containerBuckets = buildVirtualContainers(totalDoses, medication.packagingQuantity)
        val consumesStockOn = buildConsumptionPredicate(medication, schedules)

        val centerResult = simulateDepletion(
            totalStock = totalDoses,
            doseQuantity = doseQuantity,
            doseIntervalMs = doseIntervalMs,
            containerBuckets = containerBuckets,
            container = medication.medicationContainer,
            consumesStockOn = consumesStockOn,
            markEmpty = true,
        )

        val now = clock.now().toEpochMilliseconds()
        val centerRunOutMs = centerResult.runOutTimestamp
        val simulatedDaysRemaining = if (centerRunOutMs != null) {
            ((centerRunOutMs - now) / (24.0 * 60 * 60 * 1000)).toInt()
        } else {
            null
        }

        val simulatedRunOutDate = centerRunOutMs?.let { msToLocalDate(it) }

        // Fallback: if simulation capped at 730 days without finding depletion, compute mathematically
        // For cyclic meds without placebos, scale by cycle ratio (more calendar days pass per dose)
        val cycleRatio = if (medication.cycleType == CycleType.CYCLIC && !medication.cycleHasPlacebos) {
            val active = medication.cycleDaysActive ?: 0
            val total = active + (medication.cycleDaysBreak ?: 0)
            if (active > 0) total.toDouble() / active else 1.0
        } else {
            1.0
        }
        val centerDaysRemaining = simulatedDaysRemaining
            ?: kotlin.math.ceil(totalDoses / dailyConsumption * cycleRatio).toInt()

        val runOutDate = simulatedRunOutDate
            ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                .plus(centerDaysRemaining, DateTimeUnit.DAY)

        // Compute uncertainty bounds for ESTIMATED mode
        val isEstimated = medication.trackingPrecision ==
            me.juliana.hellomeds.data.model.enums.TrackingPrecision.ESTIMATED
        if (isEstimated && centerResult.points.isNotEmpty() && dailyConsumption > 0) {
            val bounds = computeUncertaintyBounds(
                centerPoints = centerResult.points,
                dailyConsumption = dailyConsumption,
                maxStock = totalDoses,
                now = now,
            )
            val earlyRunOutDate = bounds.earlyRunOutMs?.let { msToLocalDate(it) }
            val lateRunOutDate = bounds.lateRunOutMs?.let { msToLocalDate(it) }

            return StockPrediction(
                centerDaysRemaining = centerDaysRemaining,
                runOutDate = runOutDate,
                centerPoints = centerResult.points,
                earlyRunOutDate = earlyRunOutDate,
                lateRunOutDate = lateRunOutDate,
                lowerBoundPoints = bounds.lowerPoints,
                upperBoundPoints = bounds.upperPoints,
            )
        }

        return StockPrediction(
            centerDaysRemaining = centerDaysRemaining,
            runOutDate = runOutDate,
            centerPoints = centerResult.points,
        )
    }

    /**
     * Compute uncertainty bounds from center prediction points.
     * The band widens as we project further into the future.
     */
    /**
     * Compute uncertainty bounds as two independent depletion lines:
     * - Lower (pessimistic): depletes faster → runs out sooner
     * - Upper (optimistic): depletes slower → runs out later
     *
     * The rate difference grows with projection distance so the band widens over time.
     * Min band width ~3 days, max ~21 days (3 weeks).
     */
    private fun computeUncertaintyBounds(
        centerPoints: List<StockDataPoint>,
        dailyConsumption: Double,
        maxStock: Double,
        now: Long,
    ): UncertaintyBounds {
        if (centerPoints.isEmpty() || dailyConsumption <= 0) {
            return UncertaintyBounds(emptyList(), emptyList(), null, null)
        }

        val lowerPoints = mutableListOf<StockDataPoint>()
        val upperPoints = mutableListOf<StockDataPoint>()
        var earlyRunOutMs: Long? = null
        var lateRunOutMs: Long? = null

        // Start both bounds at the same stock level as center
        val startStock = maxStock
        var lowerRemaining = startStock
        var upperRemaining = startStock

        // Run independent depletion lines day by day
        val tz = TimeZone.currentSystemDefault()
        val startDate = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date
        var dayOffset = 0L
        val maxDays = 730L // 2-year cap, matching center prediction horizon

        while (dayOffset < maxDays && (upperRemaining > 0 || lowerRemaining > 0)) {
            dayOffset++
            val date = startDate.plus(dayOffset.toInt(), DateTimeUnit.DAY)
            val time =
                kotlinx.datetime.LocalDateTime(date, LocalTime(12, 0)).toInstant(tz).toEpochMilliseconds()

            // Spread grows with projection distance
            val spreadDays = (minSpreadDays + dayOffset * spreadGrowthPerDay)
                .coerceIn(minSpreadDays, maxSpreadDays)
            // Convert spread in days to a rate adjustment factor
            // spreadDays means the band is spreadDays wide at this point,
            // so the fast line is (spreadDays/2) days ahead and slow line is behind
            val rateFactor = spreadDays / (2.0 * (maxStock / dailyConsumption))

            val lowerRate = dailyConsumption * (1.0 + rateFactor) // faster depletion
            val upperRate = dailyConsumption * (1.0 - rateFactor).coerceAtLeast(0.1) // slower depletion

            lowerRemaining = (lowerRemaining - lowerRate).coerceAtLeast(0.0)
            upperRemaining = (upperRemaining - upperRate).coerceAtLeast(0.0)

            lowerPoints.add(StockDataPoint(time, lowerRemaining, isFuture = true))
            upperPoints.add(StockDataPoint(time, upperRemaining, isFuture = true))

            if (earlyRunOutMs == null && lowerRemaining <= 0.0) {
                earlyRunOutMs = time
            }
            if (lateRunOutMs == null && upperRemaining <= 0.0) {
                lateRunOutMs = time
            }

            if (lowerRemaining <= 0.0 && upperRemaining <= 0.0) break
        }

        return UncertaintyBounds(lowerPoints, upperPoints, earlyRunOutMs, lateRunOutMs)
    }

    private data class UncertaintyBounds(
        val lowerPoints: List<StockDataPoint>,
        val upperPoints: List<StockDataPoint>,
        val earlyRunOutMs: Long?,
        val lateRunOutMs: Long?,
    )

    /**
     * Derive virtual container buckets from total stock and packaging quantity.
     *
     * For discrete medications, containers are a display concept — not stored in the DB.
     * This function computes what the physical container layout looks like from just two numbers.
     */
    private fun buildVirtualContainers(totalStock: Double, packagingQty: Double?): List<ContainerBucket> {
        if (packagingQty == null || packagingQty <= 0.0) {
            return listOf(ContainerBucket(totalStock, 0))
        }
        val buckets = mutableListOf<ContainerBucket>()
        var remaining = totalStock
        val currentContent = if (remaining > 0 && remaining % packagingQty == 0.0) {
            packagingQty
        } else {
            remaining % packagingQty
        }
        if (currentContent > 0) {
            buckets.add(ContainerBucket(currentContent, 0))
            remaining -= currentContent
        }
        var seq = 1
        while (remaining >= packagingQty) {
            buckets.add(ContainerBucket(packagingQty, seq++))
            remaining -= packagingQty
        }
        return buckets
    }

    /**
     * Build a predicate that determines whether stock is consumed on a given date.
     * Encapsulates schedule frequency and cycle masking so the simulation stays generic.
     */
    private fun buildConsumptionPredicate(medication: Medication, schedules: List<Schedule>): (LocalDate) -> Boolean {
        val isCyclicNoPlacebo = medication.cycleType == CycleType.CYCLIC &&
            !medication.cycleHasPlacebos
        return { date: LocalDate ->
            val scheduled = isDoseDay(date, schedules)
            if (!scheduled) {
                false
            } else if (isCyclicNoPlacebo) {
                getCycleDay(medication, date)?.isActive != false
            } else {
                true
            }
        }
    }

    /**
     * Simulate stock depletion across container buckets, emitting data points and
     * container transition markers.
     */
    private fun simulateDepletion(
        totalStock: Double,
        doseQuantity: Double,
        doseIntervalMs: Long,
        containerBuckets: List<ContainerBucket>,
        container: MedicationContainer?,
        consumesStockOn: (LocalDate) -> Boolean = { true },
        markEmpty: Boolean = false,
    ): SimulationResult {
        val points = mutableListOf<StockDataPoint>()
        val now = clock.now().toEpochMilliseconds()

        val containerQueue = ArrayDeque(containerBuckets)

        if (containerQueue.isEmpty()) return SimulationResult(emptyList(), null)

        var currentBucket = containerQueue.removeFirst()
        var remainingInCurrent = currentBucket.remaining
        var totalRemaining = totalStock
        var runOutTimestamp: Long? = null

        // Use LocalDate stepping to avoid DST issues (adding 24h in millis
        // can land on the same calendar day during spring-forward transitions)
        val tz = TimeZone.currentSystemDefault()
        val startDate = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date
        val exactStepsPerDay = dayMs / doseIntervalMs
        val stepsPerDay = exactStepsPerDay.toInt().coerceAtLeast(1)
        // Deduct the full dose quantity per step — the consumesStockOn predicate
        // ensures deductions only happen on actual dose days.
        val deductionPerStep = doseQuantity
        var dayOffset = 0L

        AppLogger.d(
            TAG,
            "simulateDepletion: totalStock=$totalStock, doseQty=$doseQuantity, " +
                "intervalMs=$doseIntervalMs, buckets=${containerBuckets.size}, stepsPerDay=$stepsPerDay",
        )

        outer@ while (totalRemaining > 0 && dayOffset < 730) {
            dayOffset++
            val date = startDate.plus(dayOffset.toInt(), DateTimeUnit.DAY)
            val time =
                kotlinx.datetime.LocalDateTime(date, LocalTime(12, 0)).toInstant(tz).toEpochMilliseconds()

            val consumesToday = consumesStockOn(date)

            // Only deduct stock on days when medication is actually consumed
            if (consumesToday) {
                for (step in 0 until stepsPerDay) {
                    remainingInCurrent -= deductionPerStep
                    totalRemaining -= deductionPerStep

                    // Container transition: current depleted, switch to next
                    if (remainingInCurrent <= 0 && containerQueue.isNotEmpty()) {
                        val next = containerQueue.removeFirst()
                        remainingInCurrent += next.remaining
                        currentBucket = next

                        points.add(
                            StockDataPoint(
                                timestamp = time,
                                value = totalRemaining.coerceAtLeast(0.0),
                                isFuture = true,
                                event = StockEvent.ContainerSwitch(container),
                            ),
                        )
                        continue // continue inner loop
                    }

                    if (totalRemaining <= 0) {
                        totalRemaining = 0.0
                        runOutTimestamp = time
                    }
                }

                // Emit a chart point only on consumption days
                points.add(
                    StockDataPoint(
                        timestamp = time,
                        value = totalRemaining,
                        isFuture = true,
                        event = if (markEmpty && totalRemaining <= 0) {
                            StockEvent.Adjustment("PREDICTED_EMPTY", "Predicted to run out")
                        } else {
                            null
                        },
                    ),
                )
            }

            if (totalRemaining <= 0) break@outer
        }

        if (totalRemaining > 0) runOutTimestamp = null

        return SimulationResult(points, runOutTimestamp)
    }

    private data class ContainerBucket(val remaining: Double, val sequence: Int)

    private data class SimulationResult(
        val points: List<StockDataPoint>,
        val runOutTimestamp: Long?,
    )

    private fun msToLocalDate(ms: Long): LocalDate {
        return Instant.fromEpochMilliseconds(ms)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    }

    companion object {
        /**
         * Derive the virtual container breakdown from total stock and packaging quantity.
         *
         * Returns (currentContainerRemaining, sealedContainerCount).
         * Handles the modulo-zero case: if totalStock is an exact multiple of packagingQuantity,
         * the current container is full (not empty).
         */
        fun deriveContainerBreakdown(totalStock: Double, packagingQuantity: Double?): Pair<Double, Int> {
            if (packagingQuantity == null || packagingQuantity <= 0 || totalStock <= 0) {
                return totalStock to 0
            }
            val currentContent = if (totalStock % packagingQuantity == 0.0) {
                packagingQuantity
            } else {
                totalStock % packagingQuantity
            }
            val sealedCount = ((totalStock - currentContent) / packagingQuantity).toInt()
            return currentContent to sealedCount
        }

        /**
         * Calculate doses per day from active schedules (count of doses, ignoring dose quantity).
         */
        fun dosesPerDay(schedules: List<Schedule>): Double {
            return schedules.sumOf { schedule ->
                when (schedule.frequencyType) {
                    FrequencyType.INTERVAL -> 1.0 / schedule.frequencyValue
                    FrequencyType.DAYS_OF_WEEK -> (schedule.daysOfWeek?.split(",")?.size ?: 0) / 7.0
                }
            }
        }

        /**
         * Calculate total daily consumption in units (dose quantity × frequency per day).
         * Accounts for varying dose sizes across schedules.
         */
        fun dailyConsumption(schedules: List<Schedule>): Double {
            return schedules.sumOf { schedule ->
                val scheduleDpd = when (schedule.frequencyType) {
                    FrequencyType.INTERVAL -> 1.0 / schedule.frequencyValue
                    FrequencyType.DAYS_OF_WEEK -> (schedule.daysOfWeek?.split(",")?.size ?: 0) / 7.0
                }
                schedule.dose * scheduleDpd
            }
        }

        /**
         * Returns true if any schedule has a dose on the given date.
         * Uses the same interval alignment logic as [ScheduleProjector].
         */
        fun isDoseDay(date: LocalDate, schedules: List<Schedule>): Boolean {
            val tz = TimeZone.currentSystemDefault()
            return schedules.any { schedule ->
                when (schedule.frequencyType) {
                    FrequencyType.INTERVAL -> {
                        val startDate = Instant.fromEpochMilliseconds(schedule.startDate)
                            .toLocalDateTime(tz).date
                        val daysSince = startDate.daysUntil(date)
                        daysSince >= 0 && daysSince % schedule.frequencyValue == 0
                    }
                    FrequencyType.DAYS_OF_WEEK -> {
                        val days = schedule.daysOfWeek?.split(",")?.map { DayOfWeek.valueOf(it.trim()) }
                        days?.contains(date.dayOfWeek) == true
                    }
                }
            }
        }

        /**
         * Estimate the dose breakdown for an ESTIMATED-precision medication.
         *
         * Unlike [deriveContainerBreakdown] (which works on exact dose counts),
         * this function estimates how many doses remain in the current open container
         * based on elapsed time and daily consumption rate, then computes total remaining doses.
         *
         * @param totalContainers Ledger sum (container count) from StockAdjustmentDao
         * @param packagingQuantity Doses per container
         * @param containerStartedAt Timestamp (millis) when the current container was opened, or null
         * @param fallbackReferenceTimestamp Max of latest CONTAINER_DEPLETED/INITIAL_STOCK/REFILL timestamps
         * @param schedules Active (non-archived) schedules for daily rate calculation
         * @param nowMillis Current time in millis (injectable for testing)
         */
        fun estimateContainerBreakdown(
            totalContainers: Double,
            packagingQuantity: Double,
            containerStartedAt: Long?,
            fallbackReferenceTimestamp: Long,
            schedules: List<Schedule>,
            nowMillis: Long = currentTimeMillis(),
        ): EstimatedDoseBreakdown {
            if (totalContainers <= 0) {
                return EstimatedDoseBreakdown(
                    currentContainerRemaining = 0.0,
                    sealedCount = 0,
                    totalDoses = 0.0,
                )
            }

            val sealedCount = (totalContainers - 1).coerceAtLeast(0.0).toInt()

            // containerStartedAt is the primary reference; fallback only used when it's null.
            // After back-calculation (rate changes), containerStartedAt can be negative —
            // that's valid (the virtual start is before epoch). Only "no reference at all" means full.
            val referenceTimestamp = containerStartedAt
                ?: fallbackReferenceTimestamp.takeIf { it > 0L }
            val currentContainerRemaining = if (referenceTimestamp != null) {
                val daysSince = (nowMillis - referenceTimestamp) / (24.0 * 60 * 60 * 1000)
                val dailyRate = dailyConsumption(schedules)
                val estimatedConsumed = daysSince * dailyRate
                (packagingQuantity - estimatedConsumed).coerceIn(0.0, packagingQuantity)
            } else {
                packagingQuantity // assume full when no reference timestamp
            }

            val totalDoses = sealedCount * packagingQuantity + currentContainerRemaining
            return EstimatedDoseBreakdown(currentContainerRemaining, sealedCount, totalDoses)
        }

        /**
         * Back-calculate the [containerStartedAt] timestamp that would produce the
         * desired remaining dose count in the current open container.
         *
         * Used when the user manually adjusts their ESTIMATED stock level.
         *
         * @param packagingQuantity Total doses a full container holds
         * @param desiredRemaining How many doses the user says remain
         * @param dailyConsumption Daily consumption rate (from active schedules)
         * @param nowMillis Current time in millis (injectable for testing)
         * @return Timestamp (millis) to set as [containerStartedAt]
         */
        fun backCalculateContainerStartedAt(
            packagingQuantity: Double,
            desiredRemaining: Double,
            dailyConsumption: Double,
            nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
        ): Long {
            // Full container — avoid floating-point drift
            if (packagingQuantity == desiredRemaining) return nowMillis
            // No consumption rate — can't back-calculate, assume just opened
            if (dailyConsumption <= 0.0) return nowMillis
            val daysConsumed = (packagingQuantity - desiredRemaining) / dailyConsumption
            return nowMillis - (daysConsumed * DAY_MS).toLong()
        }

        private const val DAY_MS = 86_400_000.0
    }
}

/**
 * Breakdown of estimated dose counts for an ESTIMATED-precision medication.
 */
data class EstimatedDoseBreakdown(
    val currentContainerRemaining: Double,
    val sealedCount: Int,
    val totalDoses: Double,
)

/**
 * Result of stock prediction — consumed by both graph (points) and text (dates).
 */
data class StockPrediction(
    val centerDaysRemaining: Int?,
    val runOutDate: LocalDate?,
    val centerPoints: List<StockDataPoint>,
    val earlyRunOutDate: LocalDate? = null,
    val lateRunOutDate: LocalDate? = null,
    val lowerBoundPoints: List<StockDataPoint> = emptyList(),
    val upperBoundPoints: List<StockDataPoint> = emptyList(),
) {
    companion object {
        val EMPTY = StockPrediction(
            centerDaysRemaining = null,
            runOutDate = null,
            centerPoints = emptyList(),
        )
    }
}
