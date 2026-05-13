// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

enum class MedicationType(val value: String) {
    CAPSULE(value = "capsule"),
    TABLET(value = "tablet"),
    LIQUID(value = "liquid"),
    TOPICAL(value = "topical"),
    CREAM(value = "cream"),
    DEVICE(value = "device"),
    DROPS(value = "drops"),
    FOAM(value = "foam"),
    GEL(value = "gel"),
    INHALER(value = "inhaler"),
    INJECTION(value = "injection"),
    LOTION(value = "lotion"),
    OINTMENT(value = "ointment"),
    PATCH(value = "patch"),
    POWDER(value = "powder"),
    SPRAY(value = "spray"),
    SUPPOSITORY(value = "suppository"),
    ;

    companion object {
        fun fromValue(value: String): MedicationType? {
            return entries.find { it.value.equals(value, ignoreCase = true) }
        }

        /**
         * Get all type values as lowercase strings (for ML Kit detection)
         */
        fun allValues(): List<String> {
            return entries.map { it.value }
        }
    }
}
