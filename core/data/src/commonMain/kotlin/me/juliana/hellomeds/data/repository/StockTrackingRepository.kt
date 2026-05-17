// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.StockAdjustment
import me.juliana.hellomeds.data.interfaces.DepletionChecker
import me.juliana.hellomeds.data.interfaces.LowStockChecker
import me.juliana.hellomeds.data.interfaces.StockContainerAnchor
import me.juliana.hellomeds.data.model.StockRationale
import me.juliana.hellomeds.data.model.StockStatus
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.service.StockPrediction
import me.juliana.hellomeds.data.service.StockPredictionEngine
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.util.StockThresholdCalculator
import me.juliana.hellomeds.data.util.TransactionRunner
import kotlin.time.Clock
import kotlin.math.roundToInt

class StockTrackingRepository(
    private val medicationDao: MedicationDao,
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val transactionRunner: TransactionRunner,
    private val scheduleDao: ScheduleDao,
    private val lowStockNotifier: LowStockChecker,
    private val predictionEngine: StockPredictionEngine,
    private val medicationHistoryDao: MedicationHistoryDao,
    private val depletionReminderNotifier: DepletionChecker,
    private val clock: Clock = Clock.System,
) : StockContainerAnchor {

    companion object {
        private const val TAG = "StockTrackingRepository"
    }

    fun getMedicationById(medicationId: Int): Flow<Medication?> {
        return medicationDao.getById(medicationId)
    }

    fun getTrackedMedications(): Flow<List<Medication>> {
        return medicationDao.getStockTracked()
    }

    fun getCurrentStock(medicationId: Int): Flow<Double?> {
        return medicationDao.getById(medicationId).map { medication ->
            medication?.let { calculateCurrentStock(it) }
        }
    }

    /**
     * Record dose consumption.
     * EXACT: simple ledger entry.
     * ESTIMATED: no-op (doses don't touch stock, prediction handles it).
     */
    suspend fun recordDoseConsumption(medicationId: Int, historyId: Int, doseQuantity: Double) {
        try {
            val medication = medicationDao.getById(medicationId).first()
            if (medication?.stockTrackingEnabled != true) return

            // ESTIMATED: doses don't decrement stock
            if (medication.trackingPrecision == TrackingPrecision.ESTIMATED) return

            transactionRunner.run {
                stockAdjustmentDao.insert(
                    StockAdjustment(
                        medicationId = medicationId,
                        historyId = historyId,
                        quantityChange = -doseQuantity,
                        adjustmentType = StockAdjustmentType.INTAKE.value,
                    ),
                )
                updateCachedStock(medicationId)
            }

            AppLogger.d(TAG, "Recorded dose consumption: medicationId=$medicationId, dose=$doseQuantity")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to record dose consumption", e)
            throw e
        }
    }

    /**
     * Record a refill.
     * EXACT: positive ledger entry (quantity in doses).
     * ESTIMATED: positive ledger entry (default = 1 container).
     */
    suspend fun recordRefill(medicationId: Int, quantity: Double, notes: String? = null) {
        transactionRunner.run {
            val medication = medicationDao.getById(medicationId).first()
                ?: throw IllegalArgumentException("Medication not found: $medicationId")

            val refillQuantity = if (quantity > 0) {
                quantity
            } else {
                if (medication.trackingPrecision == TrackingPrecision.ESTIMATED) {
                    1.0
                } else {
                    medication.packagingQuantity ?: quantity
                }
            }

            stockAdjustmentDao.insert(
                StockAdjustment(
                    medicationId = medicationId,
                    quantityChange = refillQuantity,
                    adjustmentType = StockAdjustmentType.REFILL.value,
                    notes = notes,
                ),
            )
            updateCachedStock(medicationId)
            medicationDao.updateDepletionAlertSent(medicationId, false)
            AppLogger.d(TAG, "Recorded refill: medicationId=$medicationId, quantity=$refillQuantity")
        }
        try {
            lowStockNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
        }
        try {
            depletionReminderNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
        }
    }

    /**
     * Record a manual correction.
     * Both modes: ledger delta to reach newQuantity.
     */
    suspend fun recordCorrection(medicationId: Int, newQuantity: Double, notes: String? = null) {
        transactionRunner.run {
            val currentStock = stockAdjustmentDao.getCurrentStock(medicationId) ?: 0.0
            val delta = newQuantity - currentStock
            stockAdjustmentDao.insert(
                StockAdjustment(
                    medicationId = medicationId,
                    quantityChange = delta,
                    adjustmentType = StockAdjustmentType.MANUAL_CORRECTION.value,
                    notes = notes,
                ),
            )
            updateCachedStock(medicationId)
            medicationDao.updateDepletionAlertSent(medicationId, false)
            AppLogger.d(TAG, "Recorded correction: medicationId=$medicationId, newQuantity=$newQuantity")
        }
        try {
            lowStockNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
        }
        try {
            depletionReminderNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
        }
    }

    /**
     * Mark a container as depleted (ESTIMATED mode only).
     * Creates a CONTAINER_DEPLETED entry with quantityChange = -1.
     */
    suspend fun recordContainerDepleted(medicationId: Int) {
        transactionRunner.run {
            stockAdjustmentDao.insert(
                StockAdjustment(
                    medicationId = medicationId,
                    quantityChange = -1.0,
                    adjustmentType = StockAdjustmentType.CONTAINER_DEPLETED.value,
                    notes = "Container marked as depleted",
                ),
            )
            updateCachedStock(medicationId)
            medicationDao.updateDepletionAlertSent(medicationId, false)
            // Reset container start date — new container starts now
            val medication = medicationDao.getById(medicationId).first()
            if (medication != null) {
                medicationDao.update(medication.copy(containerStartedAt = clock.now().toEpochMilliseconds()))
            }
            AppLogger.d(TAG, "Recorded container depletion: medicationId=$medicationId")
        }
        try {
            lowStockNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
        }
        try {
            depletionReminderNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
        }
    }

    /**
     * Enable stock tracking for a medication.
     * Both modes: save medication fields + single INITIAL_STOCK ledger entry.
     */
    suspend fun enableStockTracking(
        medication: Medication,
        trackingPrecision: TrackingPrecision,
        initialQuantity: Double,
        lowStockThreshold: Double?,
        packagingQuantity: Double?,
        medicationContainer: MedicationContainer?,
        sealedContainerCount: Int = 0,
        depletionReminderEnabled: Boolean = false,
        containerStartedAt: Long? = null,
    ) {
        transactionRunner.run {
            val totalStock = if (trackingPrecision == TrackingPrecision.EXACT) {
                initialQuantity + (sealedContainerCount * (packagingQuantity ?: 0.0))
            } else {
                // ESTIMATED: initialQuantity is container count
                initialQuantity
            }

            val updated = medication.copy(
                stockTrackingEnabled = true,
                trackingPrecision = trackingPrecision,
                currentStockQuantity = totalStock,
                lowStockThreshold = lowStockThreshold,
                packagingQuantity = packagingQuantity,
                medicationContainer = medicationContainer,
                depletionReminderEnabled = depletionReminderEnabled,
                containerStartedAt = containerStartedAt ?: clock.now().toEpochMilliseconds(),
            )
            medicationDao.update(updated)

            stockAdjustmentDao.insert(
                StockAdjustment(
                    medicationId = medication.id,
                    quantityChange = totalStock,
                    adjustmentType = StockAdjustmentType.INITIAL_STOCK.value,
                    notes = "Initial stock setup",
                ),
            )

            AppLogger.d(
                TAG,
                "Enabled stock tracking: medicationId=${medication.id}, precision=$trackingPrecision, totalStock=$totalStock",
            )
        }
    }

    /**
     * Calculate unified stock status for a medication.
     * Both modes: ledger sum + prediction.
     */
    suspend fun calculateStockStatus(medication: Medication): StockStatus {
        val isEstimated = medication.trackingPrecision == TrackingPrecision.ESTIMATED
        val totalQuantity = stockAdjustmentDao.getCurrentStock(medication.id) ?: 0.0

        // Fetch schedules once — reused for breakdown, prediction, and rationale
        val schedules = scheduleDao.getByMedicationId(medication.id).first()
            .filter { !it.isEffectivelyArchived() }

        val currentContainerRemaining: Double?
        val sealedCount: Int
        val totalDoses: Double?

        if (isEstimated) {
            if (medication.packagingQuantity != null) {
                // Fetch reference timestamps for current container estimation
                val lastDepletion = stockAdjustmentDao.getLatestByTypeForMedication(
                    medication.id,
                    StockAdjustmentType.CONTAINER_DEPLETED.value,
                )
                val lastInitial = stockAdjustmentDao.getLatestByTypeForMedication(
                    medication.id,
                    StockAdjustmentType.INITIAL_STOCK.value,
                )
                val lastRefill = stockAdjustmentDao.getLatestByTypeForMedication(
                    medication.id,
                    StockAdjustmentType.REFILL.value,
                )
                val fallbackTimestamp = maxOf(
                    lastDepletion?.timestamp ?: 0L,
                    lastInitial?.timestamp ?: 0L,
                    lastRefill?.timestamp ?: 0L,
                )

                val breakdown = StockPredictionEngine.estimateContainerBreakdown(
                    totalContainers = totalQuantity,
                    packagingQuantity = medication.packagingQuantity,
                    containerStartedAt = medication.containerStartedAt,
                    fallbackReferenceTimestamp = fallbackTimestamp,
                    schedules = schedules,
                )
                currentContainerRemaining = breakdown.currentContainerRemaining
                sealedCount = breakdown.sealedCount
                totalDoses = breakdown.totalDoses
            } else {
                // ESTIMATED without packagingQuantity: can't predict
                currentContainerRemaining = null
                sealedCount = (totalQuantity - 1).coerceAtLeast(0.0).toInt()
                totalDoses = null
            }
        } else {
            // EXACT: derived from ledger sum
            val (derivedRemaining, derivedSealedCount) = StockPredictionEngine.deriveContainerBreakdown(
                totalQuantity,
                medication.packagingQuantity,
            )
            currentContainerRemaining =
                if (medication.packagingQuantity != null) derivedRemaining else null
            sealedCount = derivedSealedCount
            totalDoses = totalQuantity
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

        val severity = StockThresholdCalculator.getStockSeverity(medication, totalQuantity)

        // Build structured rationale (formatted in UI layer for localization)
        val rationale = if (totalDoses != null && totalDoses > 0) {
            val dailyRate = StockPredictionEngine.dailyConsumption(schedules)
            if (dailyRate > 0) {
                StockRationale(
                    totalDoses = totalDoses.roundToInt(),
                    dosesPerContainer = if (isEstimated) medication.packagingQuantity?.toInt() else null,
                    dailyRate = dailyRate,
                    isEstimated = isEstimated,
                )
            } else {
                null
            }
        } else {
            null
        }

        return StockStatus(
            totalQuantity = totalQuantity,
            sealedContainerCount = sealedCount,
            currentContainerRemaining = currentContainerRemaining,
            daysRemaining = prediction.centerDaysRemaining,
            severity = severity,
            runOutDate = prediction.runOutDate,
            isEstimated = isEstimated,
            earlyRunOutDate = prediction.earlyRunOutDate,
            lateRunOutDate = prediction.lateRunOutDate,
            rationale = rationale,
        )
    }

    /**
     * Get stock adjustment by history record ID.
     */
    suspend fun getAdjustmentByHistoryId(historyId: Int): StockAdjustment? {
        return stockAdjustmentDao.getByHistoryId(historyId)
    }

    /**
     * Update an existing stock adjustment's quantity.
     */
    suspend fun updateAdjustment(adjustmentId: Int, newQuantityChange: Double) {
        transactionRunner.run {
            val adj = stockAdjustmentDao.getById(adjustmentId) ?: return@run

            val updated = adj.copy(quantityChange = newQuantityChange)
            stockAdjustmentDao.update(updated)

            updateCachedStock(adj.medicationId)
            AppLogger.d(TAG, "Updated adjustment: id=$adjustmentId, newQuantityChange=$newQuantityChange")
        }
    }

    /**
     * Reverse stock adjustment when deleting a history record.
     */
    suspend fun reverseStockAdjustment(historyId: Int) {
        transactionRunner.run {
            val medicationId = stockAdjustmentDao.getByHistoryId(historyId)?.medicationId

            stockAdjustmentDao.deleteByHistoryId(historyId)

            if (medicationId != null) {
                updateCachedStock(medicationId)
            }

            AppLogger.d(TAG, "Reversed stock adjustment for history=$historyId")
        }
    }

    /**
     * Get medications with stock below their low stock threshold.
     */
    fun getLowStockMedications(): Flow<List<Medication>> {
        return medicationDao.getActive().map { medications ->
            medications.filter { medication ->
                if (!medication.stockTrackingEnabled || medication.lowStockThreshold == null) {
                    false
                } else {
                    val effectiveStock =
                        StockThresholdCalculator.calculateEffectiveStock(medication, stockAdjustmentDao)
                    effectiveStock <= medication.lowStockThreshold
                }
            }
        }
    }

    /**
     * Calculate current stock for a medication.
     * Both modes: ledger sum.
     */
    suspend fun calculateCurrentStock(medication: Medication): Double {
        return stockAdjustmentDao.getCurrentStock(medication.id) ?: 0.0
    }

    /**
     * Delete all stock tracking records for a medication.
     */
    suspend fun deleteAllTrackingData(medicationId: Int) {
        transactionRunner.run {
            stockAdjustmentDao.deleteByMedicationId(medicationId)
        }
    }

    private suspend fun updateCachedStock(medicationId: Int) {
        val newStock = stockAdjustmentDao.getCurrentStock(medicationId) ?: 0.0
        medicationDao.updateCachedStockQuantity(medicationId, newStock)
    }

    suspend fun updateLowStockThreshold(medicationId: Int, threshold: Double?) {
        val medication = medicationDao.getById(medicationId).first()
            ?: return
        val updated = medication.copy(lowStockThreshold = threshold)
        medicationDao.update(updated)
        medicationDao.updateLowStockAlertSent(medicationId, false)
        try {
            lowStockNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
        }
    }

    suspend fun updateContainerType(medicationId: Int, container: MedicationContainer?) {
        val medication = medicationDao.getById(medicationId).first()
            ?: return
        val updated = medication.copy(medicationContainer = container)
        medicationDao.update(updated)
    }

    suspend fun updatePackagingQuantity(medicationId: Int, newQuantity: Double?) {
        val medication = medicationDao.getById(medicationId).first()
            ?: return
        val updated = medication.copy(packagingQuantity = newQuantity)
        medicationDao.update(updated)
        updateCachedStock(medicationId)
    }

    suspend fun updateDepletionReminderEnabled(medicationId: Int, enabled: Boolean) {
        val medication = medicationDao.getById(medicationId).first()
            ?: return
        val updated = medication.copy(depletionReminderEnabled = enabled)
        medicationDao.update(updated)
        if (!enabled) {
            medicationDao.updateDepletionAlertSent(medicationId, false)
            try {
                depletionReminderNotifier.checkAndNotify(medicationId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
            }
        }
    }

    suspend fun setSealedContainerCount(medicationId: Int, desiredCount: Int) {
        transactionRunner.run {
            val medication = medicationDao.getById(medicationId).first()
                ?: return@run

            val packagingQty = medication.packagingQuantity ?: return@run
            val currentStock = stockAdjustmentDao.getCurrentStock(medicationId) ?: 0.0

            if (medication.trackingPrecision == TrackingPrecision.ESTIMATED) {
                // ESTIMATED: sealed = total - 1 (the one in use)
                val desiredStock = (desiredCount + 1).toDouble()
                val delta = desiredStock - currentStock
                if (delta != 0.0) {
                    stockAdjustmentDao.insert(
                        StockAdjustment(
                            medicationId = medicationId,
                            quantityChange = delta,
                            adjustmentType = if (delta > 0) {
                                StockAdjustmentType.REFILL.value
                            } else {
                                StockAdjustmentType.MANUAL_CORRECTION.value
                            },
                            notes = "Sealed container count adjusted",
                        ),
                    )
                    updateCachedStock(medicationId)
                }
            } else {
                // EXACT: adjust total stock via ledger to match desired sealed count
                val (currentRemaining, _) = StockPredictionEngine.deriveContainerBreakdown(
                    currentStock,
                    packagingQty,
                )
                val desiredStock = currentRemaining + desiredCount * packagingQty
                val delta = desiredStock - currentStock
                if (delta != 0.0) {
                    stockAdjustmentDao.insert(
                        StockAdjustment(
                            medicationId = medicationId,
                            quantityChange = delta,
                            adjustmentType = if (delta > 0) {
                                StockAdjustmentType.REFILL.value
                            } else {
                                StockAdjustmentType.MANUAL_CORRECTION.value
                            },
                            notes = "Sealed container count adjusted",
                        ),
                    )
                    updateCachedStock(medicationId)
                }
            }
        }
        try {
            lowStockNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
        }
        try {
            depletionReminderNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
        }
    }

    /**
     * Adjust the estimated stock level for the current container.
     *
     * Back-calculates [containerStartedAt] from the user's desired remaining dose count,
     * then atomically updates both [packagingQuantity] and [containerStartedAt].
     *
     * ESTIMATED mode only.
     */
    suspend fun adjustEstimatedStockLevel(
        medicationId: Int,
        newPackagingQuantity: Double,
        newDesiredRemaining: Double,
    ) {
        transactionRunner.run {
            val medication = medicationDao.getById(medicationId).first()
                ?: return@run

            val schedules = scheduleDao.getByMedicationId(medicationId).first()
                .filter { !it.isEffectivelyArchived() }
            val dailyRate = StockPredictionEngine.dailyConsumption(schedules)

            val newContainerStartedAt = StockPredictionEngine.backCalculateContainerStartedAt(
                packagingQuantity = newPackagingQuantity,
                desiredRemaining = newDesiredRemaining,
                dailyConsumption = dailyRate,
            )

            val updated = medication.copy(
                packagingQuantity = newPackagingQuantity,
                containerStartedAt = newContainerStartedAt,
            )
            medicationDao.update(updated)
            medicationDao.updateDepletionAlertSent(medicationId, false)
            AppLogger.d(
                TAG,
                "Adjusted estimated stock level: medicationId=$medicationId, " +
                    "packagingQty=$newPackagingQuantity, remaining=$newDesiredRemaining, " +
                    "containerStartedAt=$newContainerStartedAt",
            )
        }
        try {
            lowStockNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Low stock check failed (non-fatal)", e)
        }
        try {
            depletionReminderNotifier.checkAndNotify(medicationId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Depletion check failed (non-fatal)", e)
        }
    }

    /**
     * Get daily consumption for a medication as a reactive Flow.
     * Recomputes when schedules change.
     */
    fun getDailyConsumptionFlow(medicationId: Int): Flow<Double> {
        return scheduleDao.getByMedicationId(medicationId).map { schedules ->
            StockPredictionEngine.dailyConsumption(
                schedules.filter { !it.isEffectivelyArchived() },
            )
        }
    }

    // ── StockContainerAnchor ────────────────────────────────────────────

    override suspend fun snapshotBeforeRateChange(medicationId: Int): Double? {
        val medication = medicationDao.getById(medicationId).first() ?: return null
        if (medication.trackingPrecision != TrackingPrecision.ESTIMATED) return null
        val pkgQty = medication.packagingQuantity ?: return null
        if (medication.containerStartedAt == null) return null

        val totalContainers = stockAdjustmentDao.getCurrentStock(medicationId) ?: return null
        val schedules = scheduleDao.getByMedicationId(medicationId).first()
            .filter { !it.isEffectivelyArchived() }

        val breakdown = StockPredictionEngine.estimateContainerBreakdown(
            totalContainers = totalContainers,
            packagingQuantity = pkgQty,
            containerStartedAt = medication.containerStartedAt,
            fallbackReferenceTimestamp = 0L,
            schedules = schedules,
        )
        return breakdown.currentContainerRemaining
    }

    override suspend fun reanchorAfterRateChange(medicationId: Int, previousRemaining: Double?) {
        if (previousRemaining == null) return
        try {
            val medication = medicationDao.getById(medicationId).first() ?: return
            val pkgQty = medication.packagingQuantity ?: return

            val newSchedules = scheduleDao.getByMedicationId(medicationId).first()
                .filter { !it.isEffectivelyArchived() }
            val newDailyRate = StockPredictionEngine.dailyConsumption(newSchedules)

            val newContainerStartedAt = StockPredictionEngine.backCalculateContainerStartedAt(
                packagingQuantity = pkgQty,
                desiredRemaining = previousRemaining,
                dailyConsumption = newDailyRate,
            )

            medicationDao.update(medication.copy(containerStartedAt = newContainerStartedAt))
            AppLogger.d(
                TAG,
                "Re-anchored containerStartedAt after rate change: medicationId=$medicationId, " +
                    "previousRemaining=$previousRemaining, newDailyRate=$newDailyRate, " +
                    "newContainerStartedAt=$newContainerStartedAt",
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to re-anchor containerStartedAt (non-fatal)", e)
        }
    }
}
