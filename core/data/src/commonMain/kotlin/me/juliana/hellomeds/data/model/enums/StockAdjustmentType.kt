// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

enum class StockAdjustmentType(val value: String) {
    INTAKE("INTAKE"),
    REFILL("REFILL"),
    MANUAL_CORRECTION("MANUAL_CORRECTION"),
    INITIAL_STOCK("INITIAL_STOCK"),
    CONTAINER_DEPLETED("CONTAINER_DEPLETED"),
    ;

    companion object {
        fun fromValue(value: String?): StockAdjustmentType? {
            return entries.find { it.value.equals(value, ignoreCase = true) }
        }
    }
}
