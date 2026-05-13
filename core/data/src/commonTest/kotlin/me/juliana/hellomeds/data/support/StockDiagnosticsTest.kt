// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.support

import kotlinx.coroutines.test.runTest
import me.juliana.hellomeds.data.backup.FakeMedicationDao
import me.juliana.hellomeds.data.createMedication
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StockDiagnosticsTest {

    @Test
    fun noTrackedMedications_returnsEmptyResult() = runTest {
        val dao = FakeMedicationDao()
        dao.seed(createMedication(id = 1, stockTrackingEnabled = false))

        val result = getStockDiagnostic(dao)

        assertEquals(0, result.trackedMedicationCount)
        assertTrue(result.lowStockMedications.isEmpty())
    }

    @Test
    fun nullQuantity_notFlaggedAsLowStock() = runTest {
        val dao = FakeMedicationDao()
        dao.seed(
            createMedication(
                id = 1,
                stockTrackingEnabled = true,
                trackingPrecision = TrackingPrecision.EXACT,
                currentStockQuantity = null, // no quantity recorded yet
                lowStockThreshold = 10.0,
            ),
        )

        val result = getStockDiagnostic(dao)

        assertEquals(1, result.trackedMedicationCount)
        assertTrue(result.lowStockMedications.isEmpty())
    }

    @Test
    fun nullThreshold_notFlaggedAsLowStock() = runTest {
        val dao = FakeMedicationDao()
        dao.seed(
            createMedication(
                id = 1,
                stockTrackingEnabled = true,
                trackingPrecision = TrackingPrecision.EXACT,
                currentStockQuantity = 5.0,
                lowStockThreshold = null, // no threshold configured
            ),
        )

        val result = getStockDiagnostic(dao)

        assertEquals(1, result.trackedMedicationCount)
        assertTrue(result.lowStockMedications.isEmpty())
    }

    @Test
    fun exactlyAtThreshold_isFlaggedAsLowStock() = runTest {
        val dao = FakeMedicationDao()
        dao.seed(
            createMedication(
                id = 1,
                stockTrackingEnabled = true,
                trackingPrecision = TrackingPrecision.EXACT,
                currentStockQuantity = 10.0,
                lowStockThreshold = 10.0, // qty == threshold → low stock
            ),
        )

        val result = getStockDiagnostic(dao)

        assertEquals(1, result.lowStockMedications.size)
        assertEquals(10.0, result.lowStockMedications.first().currentQuantity)
    }

    @Test
    fun nullTrackingPrecision_fallsBackToUnknown() = runTest {
        val dao = FakeMedicationDao()
        dao.seed(
            createMedication(
                id = 1,
                stockTrackingEnabled = true,
                trackingPrecision = null, // data inconsistency
                currentStockQuantity = 2.0,
                lowStockThreshold = 5.0,
            ),
        )

        val result = getStockDiagnostic(dao)

        assertEquals(1, result.lowStockMedications.size)
        assertEquals("UNKNOWN", result.lowStockMedications.first().trackingPrecision)
    }
}
