// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackupImportParsingTest {

    @Test
    fun parseMedicationType_handlesValidValues() {
        assertEquals(MedicationType.TABLET, parseMedicationType("TABLET"))
        assertEquals(MedicationType.CAPSULE, parseMedicationType("CAPSULE"))
        assertEquals(MedicationType.LIQUID, parseMedicationType("LIQUID"))
    }

    @Test
    fun parseMedicationType_returnsNullForUnknownValues() {
        assertNull(parseMedicationType("UNKNOWN_TYPE"))
        assertNull(parseMedicationType(""))
        assertNull(parseMedicationType("tablet")) // case-sensitive
    }

    @Test
    fun parseFrequencyType_handlesValidValues() {
        assertEquals(FrequencyType.INTERVAL, parseFrequencyType("INTERVAL"))
        assertEquals(FrequencyType.DAYS_OF_WEEK, parseFrequencyType("DAYS_OF_WEEK"))
    }

    @Test
    fun parseFrequencyType_returnsNullForUnknownValues() {
        assertNull(parseFrequencyType("WEEKLY"))
        assertNull(parseFrequencyType(""))
    }

    @Test
    fun parseTrackingPrecision_handlesValidValues() {
        assertEquals(TrackingPrecision.EXACT, parseTrackingPrecision("EXACT"))
        assertEquals(TrackingPrecision.ESTIMATED, parseTrackingPrecision("ESTIMATED"))
    }

    @Test
    fun parseMedicationContainer_handlesValidValues() {
        assertEquals(MedicationContainer.BOTTLE, parseMedicationContainer("BOTTLE"))
        assertEquals(MedicationContainer.BLISTER_PACK, parseMedicationContainer("BLISTER_PACK"))
    }

    @Test
    fun parseMedicationStrengthUnit_handlesValidValues() {
        assertEquals(MedicationStrengthUnit.MG, parseMedicationStrengthUnit("MG"))
        assertEquals(MedicationStrengthUnit.ML, parseMedicationStrengthUnit("ML"))
    }

    @Test
    fun parseIsoToMillis_handlesValidIsoTimestamps() {
        val millis = parseIsoToMillis("2026-01-15T10:00:00Z")
        assertNotNull(millis)
    }

    @Test
    fun parseIsoToMillis_returnsNullForInvalidStrings() {
        assertNull(parseIsoToMillis("not-a-date"))
        assertNull(parseIsoToMillis(""))
        assertNull(parseIsoToMillis(null))
    }

    @Test
    fun parseDateToMillis_handlesValidIsoDates() {
        val millis = parseDateToMillis("2026-01-15")
        assertNotNull(millis)
    }

    @Test
    fun parseDateToMillis_returnsNullForInvalidDates() {
        assertNull(parseDateToMillis("not-a-date"))
        assertNull(parseDateToMillis("2026-13-45"))
    }
}
