// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule

/**
 * Platform-specific string formatting for medication display items.
 *
 * Lets MedicationViewModel live in commonMain while each platform plugs in its own localized
 * resources (Android `R.string` / `R.plurals`, iOS `NSLocalizedString`).
 */
interface MedicationDisplayFormatter {

    fun asNeededLabel(): String

    /** e.g., "Every day", "Every 2 days", "Mon, Wed, Fri" */
    fun frequencyText(schedule: Schedule): String

    /** e.g., "2 schedules", "3 schedules" */
    fun scheduleCountText(count: Int): String

    /** e.g., "Capsule, 100mg", "Liquid, 5ml" */
    fun typeAndStrength(medication: Medication): String
}
