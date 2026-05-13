// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.validation

import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MedicationValidationTest {

    private fun createMed(name: String = "Ibuprofen", displayName: String? = null): Medication = Medication(
        id = 1,
        name = name,
        type = MedicationType.TABLET,
        shape = "",
        importanceLabelId = 1,
        foregroundShape = "CIRCLE",
        backgroundShape = "CIRCLE",
    ).copy(displayName = displayName)

    // --- validateMedicationName ---

    @Test
    fun validName_returnsNull() {
        assertNull(MedicationValidation.validateMedicationName("Ibuprofen"))
    }

    @Test
    fun blankName_returnsError() {
        assertNotNull(MedicationValidation.validateMedicationName(""))
    }

    @Test
    fun whitespaceOnlyName_returnsError() {
        assertNotNull(MedicationValidation.validateMedicationName("   "))
    }

    @Test
    fun nameWithLeadingTrailingSpaces_isValid() {
        // "  Tylenol  " is not blank, so it should pass validation
        assertNull(MedicationValidation.validateMedicationName("  Tylenol  "))
    }

    @Test
    fun nameAtMaxLength_returnsNull() {
        val name = "a".repeat(MedicationValidation.MAX_NAME_LENGTH)
        assertNull(MedicationValidation.validateMedicationName(name))
    }

    @Test
    fun nameOverMaxLength_returnsError() {
        val name = "a".repeat(MedicationValidation.MAX_NAME_LENGTH + 1)
        assertNotNull(MedicationValidation.validateMedicationName(name))
    }

    // --- validateDisplayName ---

    @Test
    fun validDisplayName_returnsNull() {
        assertNull(MedicationValidation.validateDisplayName("My Ibuprofen"))
    }

    @Test
    fun displayNameOverMaxLength_returnsError() {
        val name = "a".repeat(MedicationValidation.MAX_DISPLAY_NAME_LENGTH + 1)
        assertNotNull(MedicationValidation.validateDisplayName(name))
    }

    // --- validateStrengthValue ---

    @Test
    fun strengthDisabled_returnsNull() {
        assertNull(MedicationValidation.validateStrengthValue("", strengthEnabled = false))
    }

    @Test
    fun strengthEnabled_blankValue_returnsError() {
        assertNotNull(MedicationValidation.validateStrengthValue("", strengthEnabled = true))
    }

    @Test
    fun strengthEnabled_nonNumeric_returnsError() {
        assertNotNull(MedicationValidation.validateStrengthValue("abc", strengthEnabled = true))
    }

    @Test
    fun strengthEnabled_zero_returnsError() {
        assertNotNull(MedicationValidation.validateStrengthValue("0", strengthEnabled = true))
    }

    @Test
    fun strengthEnabled_negative_returnsError() {
        assertNotNull(MedicationValidation.validateStrengthValue("-5", strengthEnabled = true))
    }

    @Test
    fun strengthEnabled_validValue_returnsNull() {
        assertNull(MedicationValidation.validateStrengthValue("500", strengthEnabled = true))
    }

    @Test
    fun strengthEnabled_decimalValue_returnsNull() {
        assertNull(MedicationValidation.validateStrengthValue("0.5", strengthEnabled = true))
    }

    // --- isMedicationValid ---

    @Test
    fun validNameAndStrength_returnsTrue() {
        assertTrue(MedicationValidation.isMedicationValid("Ibuprofen", "400", strengthEnabled = true))
    }

    @Test
    fun invalidName_returnsFalse() {
        assertFalse(MedicationValidation.isMedicationValid("", "400", strengthEnabled = true))
    }

    @Test
    fun invalidStrength_returnsFalse() {
        assertFalse(MedicationValidation.isMedicationValid("Ibuprofen", "abc", strengthEnabled = true))
    }

    // --- getEffectiveDisplayName / hasCustomDisplayName ---

    @Test
    fun noDisplayName_usesName() {
        val med = createMed(name = "Ibuprofen", displayName = null)
        assertEquals("Ibuprofen", MedicationValidation.getEffectiveDisplayName(med))
        assertFalse(MedicationValidation.hasCustomDisplayName(med))
    }

    @Test
    fun blankDisplayName_usesName() {
        val med = createMed(name = "Ibuprofen", displayName = "  ")
        assertEquals("Ibuprofen", MedicationValidation.getEffectiveDisplayName(med))
        assertFalse(MedicationValidation.hasCustomDisplayName(med))
    }

    @Test
    fun validDisplayName_usesDisplayName() {
        val med = createMed(name = "Ibuprofen", displayName = "My Ibu")
        assertEquals("My Ibu", MedicationValidation.getEffectiveDisplayName(med))
        assertTrue(MedicationValidation.hasCustomDisplayName(med))
    }
}
