// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.validation

import me.juliana.hellomeds.data.database.entities.Medication

/**
 * Validation utilities for medication data
 */
object MedicationValidation {
    const val MAX_NAME_LENGTH = 200
    const val MAX_DISPLAY_NAME_LENGTH = 200

    /**
     * Gets the effective display name for a medication.
     * Returns display name if set, otherwise returns the medication name.
     */
    fun getEffectiveDisplayName(medication: Medication): String {
        return medication.displayName?.takeIf { it.isNotBlank() } ?: medication.name
    }

    /**
     * Checks if medication has a custom display name set
     */
    fun hasCustomDisplayName(medication: Medication): Boolean {
        return !medication.displayName.isNullOrBlank()
    }

    /**
     * Validates medication name
     * @return error message if invalid, null if valid
     */
    fun validateMedicationName(name: String): String? {
        return when {
            name.isBlank() -> "Medication name is required"
            name.length > MAX_NAME_LENGTH -> "Medication name must be $MAX_NAME_LENGTH characters or less"
            else -> null
        }
    }

    /**
     * Validates display name (optional field)
     * @return error message if invalid, null if valid
     */
    fun validateDisplayName(displayName: String): String? {
        return when {
            displayName.length > MAX_DISPLAY_NAME_LENGTH -> "Display name must be $MAX_DISPLAY_NAME_LENGTH characters or less"
            else -> null
        }
    }

    /**
     * Validates strength value when strength is enabled
     * @return error message if invalid, null if valid
     */
    fun validateStrengthValue(strengthValue: String, strengthEnabled: Boolean): String? {
        if (!strengthEnabled) return null

        return when {
            strengthValue.isBlank() -> "Strength value is required"
            strengthValue.toDoubleOrNull() == null -> "Strength value must be a valid number"
            strengthValue.toDouble() <= 0 -> "Strength value must be greater than 0"
            else -> null
        }
    }

    /**
     * Checks if medication data is valid for saving
     */
    fun isMedicationValid(name: String, strengthValue: String, strengthEnabled: Boolean): Boolean {
        return validateMedicationName(name) == null &&
            validateStrengthValue(strengthValue, strengthEnabled) == null
    }
}
