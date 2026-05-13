// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule

/**
 * Abstracts platform-specific string formatting for medication display items.
 *
 * On Android, this wraps Context + string resources (R.string, R.plurals).
 * On iOS, this would use NSLocalizedString or similar.
 *
 * This interface allows MedicationViewModel to live in commonMain while
 * keeping full-fidelity localized formatting on each platform.
 */
interface MedicationDisplayFormatter {

    /**
     * Returns a localized "as needed" label (e.g., "As needed").
     */
    fun asNeededLabel(): String

    /**
     * Returns a localized frequency text for a single schedule.
     * e.g., "Every day", "Every 2 days", "Mon, Wed, Fri"
     */
    fun frequencyText(schedule: Schedule): String

    /**
     * Returns a localized plural schedule count.
     * e.g., "2 schedules", "3 schedules"
     */
    fun scheduleCountText(count: Int): String

    /**
     * Formats medication type with optional strength for display.
     * e.g., "Capsule, 100mg", "Liquid, 5ml"
     */
    fun typeAndStrength(medication: Medication): String
}
