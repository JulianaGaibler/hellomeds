// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.stock.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.TrackingPrecision

/**
 * Smart preview selector that automatically chooses the appropriate visualization
 * based on tracking precision and container type.
 *
 * Selection Logic:
 * - Bubble preview: Blister Pack, Package, or Bottle (both exact and estimated)
 * - Fill preview: Everything else (other container types)
 *
 * @param medication The medication with stock tracking configuration
 * @param currentStock Current stock quantity or percentage
 * @param modifier Modifier for the preview component
 */
@Composable
fun StockPreviewSelector(medication: Medication, currentStock: Double, modifier: Modifier = Modifier) {
    val bubbleContainers = listOf(
        MedicationContainer.BLISTER_PACK,
        MedicationContainer.PACKAGE,
        MedicationContainer.BOTTLE,
    )
    val usesBubblePreview = medication.medicationContainer in bubbleContainers
    val isEstimated = medication.trackingPrecision == TrackingPrecision.ESTIMATED

    if (usesBubblePreview) {
        val packagingQty = medication.packagingQuantity?.toInt() ?: 0

        val remainingInCurrentPackage = if (packagingQty > 0) {
            if (isEstimated) {
                // Estimated: currentStock is container count (e.g. 4.3 blisters)
                // Show one pack with fractional fill
                val fractional = currentStock - currentStock.toInt()
                val fromFraction = (fractional * packagingQty).toInt()
                // Whole number → full pack; fractional → partial pack
                if (fractional == 0.0 && currentStock > 0) packagingQty else fromFraction
            } else {
                // Exact: currentStock is total pills across all packs
                val remainder = (currentStock.toInt() % packagingQty)
                if (remainder == 0 && currentStock > 0) packagingQty else remainder
            }
        } else {
            0
        }

        BubbleStockPreview(
            totalQuantity = packagingQty,
            remainingQuantity = remainingInCurrentPackage,
            isEstimated = isEstimated,
            modifier = modifier,
        )
    } else {
        // Fill visualization for all other cases
        FillStockPreview(
            medication = medication,
            currentStock = currentStock,
            modifier = modifier,
        )
    }
}
