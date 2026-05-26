// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.stock.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import kotlin.math.ceil

/**
 * Picks the right preview for a medication. Container types that read as a grid of pills get the
 * bubble preview (with optional user-defined manual layout); everything else falls back to a fill
 * indicator.
 */
@Composable
fun StockPreviewSelector(medication: Medication, currentStock: Double, modifier: Modifier = Modifier) {
    val usesBubblePreview = medication.medicationContainer in BUBBLE_CONTAINERS
    val isEstimated = medication.trackingPrecision == TrackingPrecision.ESTIMATED

    if (usesBubblePreview) {
        val packagingQty = medication.packagingQuantity?.toInt() ?: 0

        val remainingInCurrentPackage = if (packagingQty > 0) {
            if (isEstimated) {
                // currentStock is container count (fractional means a partial container).
                val fractional = currentStock - currentStock.toInt()
                val fromFraction = (fractional * packagingQty).toInt()
                if (fractional == 0.0 && currentStock > 0) packagingQty else fromFraction
            } else {
                // currentStock is total doses across all packs.
                val remainder = (currentStock.toInt() % packagingQty)
                if (remainder == 0 && currentStock > 0) packagingQty else remainder
            }
        } else {
            0
        }

        val layoutOverride: GridLayout? = medication.bubbleManualLayout
            ?.let { BubbleLayoutCodec.decode(it) }
            ?.takeIf { it.isValidFor(packagingQty) }
            ?.let { manual ->
                val rows = ceil(packagingQty.toDouble() / manual.columns).toInt()
                GridLayout(rows = rows, columns = manual.columns, spacerIndices = manual.spacerIndices)
            }

        BubbleStockPreview(
            totalQuantity = packagingQty,
            remainingQuantity = remainingInCurrentPackage,
            isEstimated = isEstimated,
            layoutOverride = layoutOverride,
            flowDirection = medication.bubbleFlowDirection,
            modifier = modifier,
        )
    } else {
        FillStockPreview(
            medication = medication,
            currentStock = currentStock,
            modifier = modifier,
        )
    }
}

/** Container types that render as a grid of pill bubbles. Settings UI shows the Layout row only for these. */
val BUBBLE_CONTAINERS: Set<MedicationContainer> = setOf(
    MedicationContainer.BLISTER_PACK,
    MedicationContainer.PACKAGE,
    MedicationContainer.BOTTLE,
)
