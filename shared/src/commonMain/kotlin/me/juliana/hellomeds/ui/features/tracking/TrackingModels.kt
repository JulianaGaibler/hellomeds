// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.tracking

import kotlinx.datetime.LocalTime
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.ProjectedEventWithMedication
import me.juliana.hellomeds.ui.components.medication.LogStatus

data class ScheduledMedicationLog(
    val eventWithMedication: ProjectedEventWithMedication,
    val included: Boolean,
    val status: LogStatus,
    val dose: Double,
    val time: LocalTime,
    val isExpanded: Boolean,
)

data class AsNeededMedicationLog(
    val medication: Medication,
    val isTaken: Boolean,
    val dose: Double,
    val time: LocalTime,
)
