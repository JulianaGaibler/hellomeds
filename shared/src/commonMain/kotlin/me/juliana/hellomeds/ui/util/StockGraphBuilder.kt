// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.StockDataPoint
import me.juliana.hellomeds.data.model.StockEvent
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.service.StockPrediction
import me.juliana.hellomeds.data.service.StockPredictionEngine
import me.juliana.hellomeds.ui.components.graph.models.StockLine
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Builds [StockLine] graph data from repository data.
 * Separated from StockTrackingRepository to keep the data layer free of Compose dependencies.
 */
class StockGraphBuilder(
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val scheduleDao: ScheduleDao,
    private val predictionEngine: StockPredictionEngine,
    private val clock: Clock = Clock.System,
) {

    /**
     * Build a StockLine for the graph from stock adjustment history + future prediction.
     */
    fun getStockGraphLine(medication: Medication): Flow<StockLine> {
        return stockAdjustmentDao.getByMedicationIdAsc(medication.id).map { adjustments ->
            val isEstimated = medication.trackingPrecision == TrackingPrecision.ESTIMATED

            // Compute running cumulative sum and build data points
            var cumulativeSum = 0.0
            var maxValue = 0.0
            var minValue = 0.0
            val dataPoints = adjustments.map { adj ->
                cumulativeSum += adj.quantityChange
                val value = cumulativeSum
                if (value > maxValue) maxValue = value
                if (value < minValue) minValue = value

                val event: StockEvent? = when (val adjType = StockAdjustmentType.fromValue(adj.adjustmentType)) {
                    StockAdjustmentType.INTAKE -> StockEvent.DoseTaken(adj.quantityChange)
                    StockAdjustmentType.REFILL -> StockEvent.Refill(adj.quantityChange)
                    StockAdjustmentType.MANUAL_CORRECTION -> StockEvent.Adjustment(
                        adjType.value,
                        adj.notes,
                        adj.quantityChange,
                    )

                    StockAdjustmentType.INITIAL_STOCK -> StockEvent.Adjustment(
                        adjType.value,
                        adj.notes,
                        adj.quantityChange,
                    )

                    StockAdjustmentType.CONTAINER_DEPLETED -> StockEvent.ContainerSwitch(
                        medication.medicationContainer,
                    )

                    else -> null
                }

                StockDataPoint(
                    timestamp = adj.timestamp,
                    value = value,
                    isFuture = false,
                    event = event,
                )
            }

            // ESTIMATED mode: convert historical container-count values to dose-equivalent
            // so they share the same y-axis unit as the prediction (which is in doses).
            val packagingQuantity = medication.packagingQuantity
            val convertedPoints = if (isEstimated && packagingQuantity != null) {
                maxValue = 0.0
                minValue = 0.0
                dataPoints.map { point ->
                    val doseValue = point.value * packagingQuantity
                    if (doseValue > maxValue) maxValue = doseValue
                    if (doseValue < minValue) minValue = doseValue
                    point.copy(value = doseValue)
                }
            } else {
                dataPoints
            }

            // Align all timestamps to noon of their calendar day
            val dayAlignedPoints = convertedPoints.map { it.copy(timestamp = alignToDay(it.timestamp)) }

            // Generate future prediction via unified engine
            // ESTIMATED without packagingQuantity: skip prediction (container count alone is meaningless)
            val currentStock = cumulativeSum.coerceAtLeast(0.0)

            // Fetch schedules once — reused for both dose breakdown and prediction
            val schedules = scheduleDao.getByMedicationId(medication.id).first()
                .filter { !it.isEffectivelyArchived() }

            val totalDoses = if (isEstimated && packagingQuantity != null) {
                // Derive fallback reference timestamp from already-loaded adjustments
                val fallbackTimestamp = adjustments.lastOrNull {
                    val type = StockAdjustmentType.fromValue(it.adjustmentType)
                    type == StockAdjustmentType.CONTAINER_DEPLETED ||
                        type == StockAdjustmentType.INITIAL_STOCK ||
                        type == StockAdjustmentType.REFILL
                }?.timestamp ?: 0L

                val breakdown = StockPredictionEngine.estimateContainerBreakdown(
                    totalContainers = currentStock,
                    packagingQuantity = packagingQuantity,
                    containerStartedAt = medication.containerStartedAt,
                    fallbackReferenceTimestamp = fallbackTimestamp,
                    schedules = schedules,
                )
                breakdown.totalDoses
            } else if (isEstimated) {
                null
            } else {
                currentStock
            }

            // Insert a "now" bridge point for ESTIMATED mode with dose conversion.
            // Historical points show full-container dose equivalents; the "now" point
            // steps down to the actual estimated remaining doses (accounting for
            // partial consumption of the open container), bridging to the prediction.
            val nowBridgePoint = if (isEstimated && packagingQuantity != null && totalDoses != null) {
                StockDataPoint(
                    timestamp = alignToDay(clock.now().toEpochMilliseconds()),
                    value = totalDoses,
                    isFuture = false,
                )
            } else {
                null
            }

            val prediction = if (totalDoses != null) {
                predictionEngine.predict(
                    medication = medication,
                    totalDoses = totalDoses,
                    schedules = schedules,
                )
            } else {
                StockPrediction.EMPTY
            }

            val alignedCenter =
                prediction.centerPoints.map { it.copy(timestamp = alignToDay(it.timestamp)) }
            val alignedLower =
                prediction.lowerBoundPoints.map { it.copy(timestamp = alignToDay(it.timestamp)) }
            val alignedUpper =
                prediction.upperBoundPoints.map { it.copy(timestamp = alignToDay(it.timestamp)) }

            val bridgeList = listOfNotNull(nowBridgePoint)
            val allPoints = dayAlignedPoints + bridgeList + alignedCenter
            val futureMax = alignedCenter.maxOfOrNull { it.value } ?: 0.0
            val yMin = if (minValue < 0) minValue * 1.1 else 0.0
            val yMax = maxOf(maxValue, futureMax, medication.packagingQuantity ?: 0.0) * 1.1
            StockLine(
                medicationId = medication.id,
                medicationName = medication.displayName ?: medication.name,
                dataPoints = allPoints,
                medicationType = medication.type,
                isEstimatedTracking = isEstimated,
                color = Color.Unspecified,
                yAxisRange = yMin to if (yMax > yMin) yMax else yMin + 1.0,
                lowerBoundPoints = alignedLower,
                upperBoundPoints = alignedUpper,
            )
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Round a timestamp to noon of its calendar day for day-level graph alignment.
     */
    private fun alignToDay(timestamp: Long): Long {
        val tz = TimeZone.currentSystemDefault()
        val date = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(tz)
            .date
        return kotlinx.datetime.LocalDateTime(date, LocalTime(12, 0))
            .toInstant(tz)
            .toEpochMilliseconds()
    }
}
