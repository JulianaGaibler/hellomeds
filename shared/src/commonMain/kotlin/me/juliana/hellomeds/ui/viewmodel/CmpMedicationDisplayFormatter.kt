// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import kotlinx.coroutines.runBlocking
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.medication_type_strength_format
import me.juliana.hellomeds.shared.schedule_as_needed
import me.juliana.hellomeds.shared.schedule_count_plural
import me.juliana.hellomeds.shared.schedule_every_day
import me.juliana.hellomeds.shared.schedule_every_n_days_plural
import me.juliana.hellomeds.ui.util.displayNameRes
import me.juliana.hellomeds.ui.util.formatDecimal
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

class CmpMedicationDisplayFormatter : MedicationDisplayFormatter {

    override fun asNeededLabel(): String = runBlocking {
        getString(Res.string.schedule_as_needed)
    }

    override fun frequencyText(schedule: Schedule): String = runBlocking {
        when (schedule.frequencyType) {
            FrequencyType.INTERVAL -> {
                if (schedule.frequencyValue == 1) {
                    getString(Res.string.schedule_every_day)
                } else {
                    getPluralString(
                        Res.plurals.schedule_every_n_days_plural,
                        schedule.frequencyValue,
                        schedule.frequencyValue,
                    )
                }
            }

            FrequencyType.DAYS_OF_WEEK -> {
                val days = schedule.daysOfWeek?.split(",") ?: emptyList()
                if (days.size == 7) {
                    getString(Res.string.schedule_every_day)
                } else {
                    days.joinToString(", ") {
                        it.trim().take(3).lowercase().replaceFirstChar { c -> c.uppercase() }
                    }
                }
            }
        }
    }

    override fun scheduleCountText(count: Int): String = runBlocking {
        getPluralString(Res.plurals.schedule_count_plural, count, count)
    }

    override fun typeAndStrength(medication: Medication): String {
        val type = runBlocking { getString(medication.type.displayNameRes) }
        return if (medication.strengthValue != null && medication.strengthUnit != null) {
            val strength = "${formatDecimal(medication.strengthValue!!)}${medication.strengthUnit?.value ?: ""}"
            runBlocking { getString(Res.string.medication_type_strength_format, type, strength) }
        } else {
            type
        }
    }
}
