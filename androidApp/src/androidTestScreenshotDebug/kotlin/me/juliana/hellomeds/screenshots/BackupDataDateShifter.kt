// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.screenshots

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import me.juliana.hellomeds.data.backup.model.BackupData
import me.juliana.hellomeds.data.backup.model.BackupHistory
import me.juliana.hellomeds.data.backup.model.BackupMedication
import me.juliana.hellomeds.data.backup.model.BackupSchedule
import me.juliana.hellomeds.data.backup.model.BackupStockAdjustment
import me.juliana.hellomeds.data.backup.model.BackupStockSettings
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Rebases every datetime/date field in a [BackupData] so the curated "today"
 * lines up with the device's actual today.
 *
 * The curated demo data is anchored on [DEMO_ANCHOR_DATE]. When this shifter
 * runs on a device whose clock reads `now`, every timestamp is offset by
 * `now - anchorAt10am`. ISO-date fields (start dates etc.) are offset in whole
 * days; ISO-instant fields keep sub-second precision.
 */
object BackupDataDateShifter {
    val DEMO_ANCHOR_DATE: LocalDate = LocalDate(2026, 4, 20)

    fun shift(data: BackupData, now: Instant = Clock.System.now()): BackupData {
        val tz = TimeZone.currentSystemDefault()
        val anchorInstant = DEMO_ANCHOR_DATE.atStartOfDayIn(tz) + 10.hours
        val delta = now - anchorInstant
        val dayDelta = delta.inWholeDays
        return data.copy(
            exportedAt = shiftInstant(data.exportedAt, delta) ?: data.exportedAt,
            medications = data.medications.map { shiftMedication(it, delta, dayDelta, tz) },
        )
    }

    private val Int.hours get() = (this * 60L * 60L * 1000L).milliseconds

    private fun shiftMedication(
        med: BackupMedication,
        delta: Duration,
        dayDelta: Long,
        tz: TimeZone,
    ): BackupMedication = med.copy(
        createdAt = shiftInstant(med.createdAt, delta),
        cycleStartDate = shiftLocalDate(med.cycleStartDate, dayDelta),
        stock = med.stock?.let { shiftStock(it, delta) },
        schedules = med.schedules.map { shiftSchedule(it, delta, dayDelta) },
        history = med.history.map { shiftHistory(it, delta) },
        stockAdjustments = med.stockAdjustments.map { shiftStockAdjustment(it, delta) },
    )

    private fun shiftSchedule(schedule: BackupSchedule, delta: Duration, dayDelta: Long): BackupSchedule =
        schedule.copy(
            startDate = shiftLocalDate(schedule.startDate, dayDelta) ?: schedule.startDate,
            endDate = shiftLocalDate(schedule.endDate, dayDelta),
            createdAt = shiftInstant(schedule.createdAt, delta),
        )

    private fun shiftHistory(history: BackupHistory, delta: Duration): BackupHistory = history.copy(
        scheduledTime = shiftInstant(history.scheduledTime, delta),
        takenTime = shiftInstant(history.takenTime, delta),
        createdAt = shiftInstant(history.createdAt, delta),
    )

    private fun shiftStock(stock: BackupStockSettings, delta: Duration): BackupStockSettings =
        stock.copy(containerStartedAt = shiftInstant(stock.containerStartedAt, delta))

    private fun shiftStockAdjustment(adj: BackupStockAdjustment, delta: Duration): BackupStockAdjustment =
        adj.copy(timestamp = shiftInstant(adj.timestamp, delta))

    private fun shiftInstant(iso: String?, delta: Duration): String? {
        if (iso.isNullOrBlank()) return iso
        return try {
            (Instant.parse(iso) + delta).toString()
        } catch (_: Exception) {
            iso
        }
    }

    private fun shiftLocalDate(iso: String?, dayDelta: Long): String? {
        if (iso.isNullOrBlank()) return iso
        return try {
            LocalDate.parse(iso).plus(dayDelta, DateTimeUnit.DAY).toString()
        } catch (_: Exception) {
            iso
        }
    }
}
