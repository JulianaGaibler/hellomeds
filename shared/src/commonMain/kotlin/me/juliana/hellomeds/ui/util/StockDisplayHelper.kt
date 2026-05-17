// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.StockStatus
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.util.StockThresholdCalculator
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_containers_generic
import me.juliana.hellomeds.shared.stock_packages_generic
import me.juliana.hellomeds.shared.stock_quantity_with_remainder
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

object StockDisplayHelper {

    @Composable
    fun formatStockQuantity(medication: Medication, currentStock: Double): String {
        return when (medication.trackingPrecision) {
            TrackingPrecision.ESTIMATED -> {
                val count = currentStock.toInt()
                val container = medication.medicationContainer
                if (container != null) {
                    pluralStringResource(container.pluralRes, count, count)
                } else {
                    pluralStringResource(Res.plurals.stock_containers_generic, count, count)
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
                        val packageStr = pluralStringResource(medContainer.pluralRes, packages, packages)
                        if (remainder > 0) {
                            val remainderStr =
                                pluralStringResource(medication.type.dosagePluralRes, remainder, remainder)
                            stringResource(Res.string.stock_quantity_with_remainder, packageStr, remainderStr)
                        } else {
                            packageStr
                        }
                    } else {
                        pluralStringResource(medication.type.dosagePluralRes, stockInt, stockInt)
                    }
                } else {
                    pluralStringResource(medication.type.dosagePluralRes, stockInt, stockInt)
                }
            }

            else -> currentStock.toString()
        }
    }

    @Composable
    fun formatStockQuantity(medication: Medication, stockStatus: StockStatus): String {
        return formatStockQuantity(medication, stockStatus.totalQuantity)
    }

    fun shouldShowLowStockWarning(medication: Medication, currentStock: Double): Boolean {
        val threshold = medication.lowStockThreshold ?: return false
        return currentStock <= threshold
    }

    fun getStockSeverity(medication: Medication, currentStock: Double): String {
        return StockThresholdCalculator.getStockSeverity(medication, currentStock)
    }

    @Composable
    fun formatPackagingUnit(quantity: Int, container: MedicationContainer?): String {
        return if (container != null) {
            pluralStringResource(container.pluralRes, quantity, quantity)
        } else {
            pluralStringResource(Res.plurals.stock_packages_generic, quantity, quantity)
        }
    }

    fun estimateDaysRemaining(currentStock: Double, dailyDoseAverage: Double): Int? {
        if (dailyDoseAverage <= 0 || currentStock <= 0) return null
        return (currentStock / dailyDoseAverage).toInt()
    }
}
