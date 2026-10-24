// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.service

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.dao.StockAdjustmentDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.StockAdjustment
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.util.TimeProvider
import me.juliana.hellomeds.data.util.TransactionRunner
import kotlin.time.Instant

enum class ClearRange(val keepDays: Int?) {
    OLDER_THAN_24_HOURS(null),
    OLDER_THAN_7(7),
    OLDER_THAN_30(30),
    OLDER_THAN_90(90),
}

/**
 * Removes a medication's old logged doses and folds its old stock ledger entries without changing
 * the current stock. Bumping `resolveFrom` keeps [UnloggedDoseResolver] from filling the gap.
 *
 * Nothing from the last [UnloggedDoseResolver.RESOLVE_AFTER_MS] is cleared, because those doses can
 * still be logged and a second log would deduct stock twice.
 *
 * Folding keeps the ledger sum and the newest anchor timestamp that the tracking mode reads.
 * Adjustments still linked to a kept dose are left alone so later edits to that dose find them.
 */
class MedicationDataClearer(
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val historyDao: MedicationHistoryDao,
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val transactionRunner: TransactionRunner,
    private val resolver: UnloggedDoseResolver,
    private val reconciler: ScheduleReconciler,
    private val timeProvider: TimeProvider,
    private val deviceTimeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    companion object {
        const val CARRIED_FORWARD_NOTE = "Carried forward"

        private val ESTIMATED_ANCHORS = setOf(
            StockAdjustmentType.INITIAL_STOCK,
            StockAdjustmentType.REFILL,
            StockAdjustmentType.CONTAINER_DEPLETED,
        ).map { it.value }

        private val EXACT_ANCHORS = setOf(
            StockAdjustmentType.INITIAL_STOCK,
            StockAdjustmentType.MANUAL_CORRECTION,
            StockAdjustmentType.REFILL,
        ).map { it.value }
    }

    suspend fun clear(medicationId: Int, clearHistory: Boolean, clearStock: Boolean, range: ClearRange) {
        if (!clearHistory && !clearStock) return
        withContext(NonCancellable) {
            // The resolver must not run between the delete and the resolveFrom bump.
            resolver.runExclusive {
                transactionRunner.run {
                    val medication = medicationDao.getByIdSync(medicationId) ?: return@run
                    val cutoff = cutoff(range)
                    if (clearHistory) clearHistory(medication, cutoff)
                    if (clearStock) foldStock(medication, cutoff)
                }
            }
            reconciler.reconcile()
        }
    }

    private fun cutoff(range: ClearRange): Long {
        val now = timeProvider.nowMillis()
        val rangeCutoff = range.keepDays?.let { days ->
            val tz = deviceTimeZone()
            val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date
            today.minus(days, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
        } ?: now
        return minOf(rangeCutoff, now - UnloggedDoseResolver.RESOLVE_AFTER_MS)
    }

    private suspend fun clearHistory(medication: Medication, cutoff: Long) {
        // The depletion reminder counts doses logged since the current container started.
        val historyCutoff = if (isEstimated(medication) && medication.depletionReminderEnabled) {
            minOf(cutoff, latestTimestamp(medication.id, ESTIMATED_ANCHORS) ?: cutoff)
        } else {
            cutoff
        }
        stockAdjustmentDao.detachFromHistoryOlderThan(medication.id, historyCutoff)
        historyDao.deleteForMedicationOlderThan(medication.id, historyCutoff)
        val scheduleIds = scheduleDao.getByMedicationId(medication.id).first().map { it.id }
        if (scheduleIds.isNotEmpty()) scheduleDao.bumpResolveFrom(scheduleIds, historyCutoff)
    }

    private suspend fun foldStock(medication: Medication, cutoff: Long) {
        val candidates = stockAdjustmentDao.getUnlinkedBefore(medication.id, cutoff)
        if (candidates.isEmpty()) return

        val anchorTypes = if (isEstimated(medication)) ESTIMATED_ANCHORS else EXACT_ANCHORS
        val anchor = candidates.lastOrNull { it.adjustmentType in anchorTypes }
        val (upToAnchor, afterAnchor) = if (anchor == null) {
            emptyList<StockAdjustment>() to candidates
        } else {
            candidates.partition { it.timestamp <= anchor.timestamp }
        }

        stockAdjustmentDao.deleteUnlinkedBefore(medication.id, cutoff)
        if (anchor != null) {
            stockAdjustmentDao.insert(
                StockAdjustment(
                    medicationId = medication.id,
                    quantityChange = upToAnchor.sumOf { it.quantityChange },
                    timestamp = anchor.timestamp,
                    adjustmentType = StockAdjustmentType.INITIAL_STOCK.value,
                    notes = CARRIED_FORWARD_NOTE,
                ),
            )
        }
        if (afterAnchor.isNotEmpty()) {
            stockAdjustmentDao.insert(
                StockAdjustment(
                    medicationId = medication.id,
                    quantityChange = afterAnchor.sumOf { it.quantityChange },
                    timestamp = cutoff - 1,
                    adjustmentType = StockAdjustmentType.INTAKE.value,
                    notes = CARRIED_FORWARD_NOTE,
                ),
            )
        }
        medicationDao.updateCachedStockQuantity(medication.id, stockAdjustmentDao.getCurrentStock(medication.id) ?: 0.0)
    }

    private fun isEstimated(medication: Medication) = medication.trackingPrecision == TrackingPrecision.ESTIMATED

    private suspend fun latestTimestamp(medicationId: Int, types: List<String>): Long? =
        types.mapNotNull { stockAdjustmentDao.getLatestByTypeForMedication(medicationId, it)?.timestamp }.maxOrNull()
}
