// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import kotlinx.coroutines.runBlocking
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.util.StockThresholdCalculator
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_containers_generic
import me.juliana.hellomeds.shared.stock_quantity_with_remainder
import me.juliana.hellomeds.ui.util.dosagePluralRes
import me.juliana.hellomeds.ui.util.pluralRes
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

class CmpStockDisplayFormatter : StockDisplayFormatter {

    override fun formatStockQuantity(medication: Medication, currentStock: Double): String = runBlocking {
        when (medication.trackingPrecision) {
            TrackingPrecision.ESTIMATED -> {
                val count = currentStock.toInt()
                val container = medication.medicationContainer
                if (container != null) {
                    getPluralString(container.pluralRes, count, count)
                } else {
                    getPluralString(Res.plurals.stock_containers_generic, count, count)
                }
            }

            TrackingPrecision.EXACT -> {
                val stockInt = currentStock.toInt()
                val packagingQuantity = medication.packagingQuantity
                val medContainer = medication.medicationContainer
                if (packagingQuantity != null && medContainer != null) {
                    val packagingQty = packagingQuantity.toInt()
                    if (packagingQty > 0) {
                        val packages = stockInt / packagingQty
                        val remainder = stockInt % packagingQty
                        val packageStr = getPluralString(medContainer.pluralRes, packages, packages)
                        if (remainder > 0) {
                            val remainderStr = getPluralString(medication.type.dosagePluralRes, remainder, remainder)
                            getString(Res.string.stock_quantity_with_remainder, packageStr, remainderStr)
                        } else {
                            packageStr
                        }
                    } else {
                        getPluralString(medication.type.dosagePluralRes, stockInt, stockInt)
                    }
                } else {
                    getPluralString(medication.type.dosagePluralRes, stockInt, stockInt)
                }
            }

            else -> currentStock.toString()
        }
    }

    override fun shouldShowLowStockWarning(medication: Medication, currentStock: Double): Boolean {
        val threshold = medication.lowStockThreshold ?: return false
        return currentStock <= threshold
    }

    override fun getStockSeverity(medication: Medication, currentStock: Double): String {
        return StockThresholdCalculator.getStockSeverity(medication, currentStock)
    }
}
