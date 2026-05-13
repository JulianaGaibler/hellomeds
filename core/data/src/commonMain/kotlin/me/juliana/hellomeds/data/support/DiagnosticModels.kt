// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.support

/**
 * Structured diagnostic data classes shared between BugReportService (text formatting)
 * and Debug ViewModels (structured UI display).
 *
 * IMPORTANT: These models carry medicationId only — never real medication names.
 * BugReportService resolves IDs to obfuscated aliases ("Medication 1").
 * Debug ViewModels resolve IDs to real names via MedicationDao.
 */

data class DoseOverviewDiagnostic(
    val totalCount: Int,
    val takenCount: Int,
    val pendingCount: Int,
    val skippedCount: Int,
    val overdueCount: Int,
    val doses: List<DoseDiagnostic>,
)

data class DoseDiagnostic(
    val medicationId: Int,
    val dose: Double,
    val strengthUnit: String?,
    val scheduledTime: Long,
    /** MedicationHistory.STATUS_TAKEN/SKIPPED/AUTO_SKIPPED, or "PENDING" for unacted events. */
    val status: String,
    val isOverdue: Boolean,
)

data class UpcomingEventsDiagnostic(
    val next24hCount: Int,
    val next7dCount: Int,
    val nextEventTime: Long?,
    val timezoneId: String,
    val utcOffsetSeconds: Int,
)

data class StockDiagnostic(
    val trackedMedicationCount: Int,
    val lowStockMedications: List<StockMedicationDiagnostic>,
)

data class StockMedicationDiagnostic(
    val medicationId: Int,
    val currentQuantity: Double?,
    val lowStockThreshold: Double?,
    val trackingPrecision: String,
)

data class ReconcilerDiagnostic(
    val healthy: Boolean,
    val healthMessage: String,
    val timezoneId: String,
    val utcOffsetSeconds: Int,
    val details: Map<String, String>,
)
