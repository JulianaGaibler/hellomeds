// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import kotlinx.datetime.LocalDate
import me.juliana.hellomeds.ui.test.testMedication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditMedicationDraftTest {

    @Test
    fun applyTo_keepsStockAndAlertFieldsWrittenAfterEditingStarted() {
        val opened = testMedication()
        val draft = EditMedicationDraft(opened, today = LocalDate(2025, 6, 15))
        draft.name = "Renamed"

        val latest = opened.copy(currentStockQuantity = 12.0, lowStockAlertSent = true)
        val saved = draft.applyTo(latest)

        assertEquals("Renamed", saved.name)
        assertEquals(12.0, saved.currentStockQuantity)
        assertTrue(saved.lowStockAlertSent)
    }
}
