// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

/**
 * Standardized medication strength units
 * Centralized source of truth for all dosage units in the app
 */
enum class MedicationStrengthUnit(val value: String) {
    MG("mg"),
    MCG("mcg"),
    G("g"),
    ML("mL"),
    IU("IU"),
    MEQ("mEq"),
    PERCENT("%"),
    ;

    companion object {
        /**
         * Get unit from string value (case-insensitive)
         */
        fun fromValue(value: String): MedicationStrengthUnit? {
            return values().firstOrNull {
                it.value.equals(value, ignoreCase = true)
            }
        }

        /**
         * Get all unit values as lowercase strings (for ML Kit OCR matching)
         */
        fun allLowercaseValues(): List<String> {
            return values().map { it.value.lowercase() }
        }

        /**
         * Get all unit values as they should be displayed
         */
        fun allDisplayValues(): List<String> {
            return values().map { it.value }
        }
    }
}
