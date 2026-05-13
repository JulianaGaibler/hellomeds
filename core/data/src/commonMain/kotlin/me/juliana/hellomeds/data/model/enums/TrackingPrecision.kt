// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

enum class TrackingPrecision(val value: String) {
    EXACT("EXACT"),
    ESTIMATED("ESTIMATED"),
    ;

    companion object {
        fun fromValue(value: String?): TrackingPrecision? {
            return entries.find { it.value.equals(value, ignoreCase = true) }
        }
    }
}
