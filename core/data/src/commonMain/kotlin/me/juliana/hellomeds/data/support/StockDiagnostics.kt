// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.support

import kotlinx.coroutines.flow.first
import me.juliana.hellomeds.data.dao.MedicationDao

suspend fun getStockDiagnostic(medicationDao: MedicationDao): StockDiagnostic {
    val tracked = medicationDao.getStockTracked().first()
    val lowStock = tracked.filter { med ->
        val qty = med.currentStockQuantity ?: return@filter false
        val threshold = med.lowStockThreshold ?: return@filter false
        qty <= threshold
    }
    return StockDiagnostic(
        trackedMedicationCount = tracked.size,
        lowStockMedications = lowStock.map { med ->
            StockMedicationDiagnostic(
                medicationId = med.id,
                currentQuantity = med.currentStockQuantity,
                lowStockThreshold = med.lowStockThreshold,
                trackingPrecision = med.trackingPrecision?.name ?: "UNKNOWN",
            )
        },
    )
}
