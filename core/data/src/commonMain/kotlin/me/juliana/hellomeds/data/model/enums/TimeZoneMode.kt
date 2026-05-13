// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

/**
 * Controls how a medication's schedule times are interpreted across timezone changes.
 */
enum class TimeZoneMode {
    /** "9 PM wherever I am" — notifications follow local wall-clock time (default). */
    LOCAL,

    /** "9 PM in my home timezone" — maintains absolute timing across timezone changes. */
    FIXED,
}
