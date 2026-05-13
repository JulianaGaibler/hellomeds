// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

/**
 * Enum representing different types of medication containers/packaging.
 *
 * Used in stock tracking to track how many containers (bottles, boxes, tubes, etc.)
 * a medication is packaged in, separate from the medication unit itself (tablets, ml, etc.).
 *
 * Example: "You have 15 tablets in 2 bottles remaining"
 * - "tablets" comes from MedicationType
 * - "bottles" comes from MedicationContainer
 *
 * @property value The string representation stored in the database (lowercase)
 */
enum class MedicationContainer(val value: String) {
    PACKAGE(value = "package"),
    BOTTLE(value = "bottle"),
    DISPENSER(value = "dispenser"),
    BLISTER_PACK(value = "blister_pack"),
    TUBE(value = "tube"),
    VIAL(value = "vial"),
    INHALER(value = "inhaler"),
    PEN(value = "pen"),
    AMPOULE(value = "ampoule"),
    CANISTER(value = "canister"),
    JAR(value = "jar"),
    ;

    companion object {
        /**
         * Convert a string value to a MedicationContainer enum (case-insensitive).
         *
         * @param value The string value to convert
         * @return The matching MedicationContainer, or null if no match found
         */
        fun fromValue(value: String): MedicationContainer? {
            return entries.find { it.value.equals(value, ignoreCase = true) }
        }

        /**
         * Get all container type values as lowercase strings.
         *
         * @return List of all container type values
         */
        fun allValues(): List<String> {
            return entries.map { it.value }
        }
    }
}
