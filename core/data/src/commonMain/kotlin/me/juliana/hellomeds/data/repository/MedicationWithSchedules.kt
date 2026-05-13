// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.repository

import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule

data class MedicationWithSchedules(
    val medication: Medication,
    val schedules: List<Schedule>,
)
