// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.ui.features.medication.EditMedicationDraft
import kotlin.time.Clock

/** Holds the edit draft so the edit screen and its subpage routes share it. */
class EditMedicationViewModel : ViewModel() {
    private var draft: EditMedicationDraft? = null

    fun draftFor(medication: Medication): EditMedicationDraft = draft?.takeIf { it.medication.id == medication.id }
        ?: EditMedicationDraft(
            medication = medication,
            today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        ).also { draft = it }

    fun existingDraft(medicationId: Int): EditMedicationDraft? = draft?.takeIf { it.medication.id == medicationId }

    fun clear() {
        draft = null
    }
}
