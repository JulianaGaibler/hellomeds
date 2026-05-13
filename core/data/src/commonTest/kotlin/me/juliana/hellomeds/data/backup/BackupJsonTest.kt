// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import me.juliana.hellomeds.data.backup.model.BackupData
import me.juliana.hellomeds.data.backup.model.BackupHistory
import me.juliana.hellomeds.data.backup.model.BackupImportanceLabel
import me.juliana.hellomeds.data.backup.model.BackupMedication
import me.juliana.hellomeds.data.backup.model.BackupSchedule
import me.juliana.hellomeds.data.backup.model.BackupStockAdjustment
import me.juliana.hellomeds.data.backup.model.BackupStockSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackupJsonTest {

    @Test
    fun serializeAndDeserializeRoundTripPreservesAllFields() {
        val backup = BackupData(
            version = 1,
            appVersion = "1.0.0",
            exportedAt = "2026-03-22T14:30:00Z",
            importanceLabels = listOf(
                BackupImportanceLabel(
                    name = "Critical",
                    shouldRemind = true,
                    isCritical = true,
                    hasFollowUps = true,
                    followUpCount = 3,
                    followUpIntervalMinutes = 20,
                    criticalAfterFollowUp = 2,
                ),
            ),
            medications = listOf(
                BackupMedication(
                    name = "Lisinopril",
                    displayName = "BP Med",
                    type = "TABLET",
                    importanceLabel = "Critical",
                    strengthValue = 10.0,
                    strengthUnit = "MG",
                    stock = BackupStockSettings(
                        trackingPrecision = "EXACT",
                        currentStockQuantity = 45.0,
                        lowStockThreshold = 10.0,
                    ),
                    schedules = listOf(
                        BackupSchedule(
                            id = 1,
                            dose = 1.0,
                            startDate = "2026-01-15",
                            timeOfDay = "08:00",
                            frequencyType = "INTERVAL",
                            frequencyValue = 1,
                        ),
                    ),
                    history = listOf(
                        BackupHistory(
                            scheduledTime = "2026-01-15T08:00:00Z",
                            takenTime = "2026-01-15T08:05:00Z",
                            scheduledDose = 1.0,
                            actualDose = 1.0,
                            status = "TAKEN",
                        ),
                    ),
                    stockAdjustments = listOf(
                        BackupStockAdjustment(
                            quantityChange = -1.0,
                            timestamp = "2026-01-15T08:05:00Z",
                            adjustmentType = "INTAKE",
                        ),
                    ),
                ),
            ),
        )

        val json = backupJson.encodeToString(BackupData.serializer(), backup)
        val deserialized = backupJson.decodeFromString(BackupData.serializer(), json)

        assertEquals(backup.version, deserialized.version)
        assertEquals(backup.appVersion, deserialized.appVersion)
        assertEquals(1, deserialized.medications.size)

        val med = deserialized.medications[0]
        assertEquals("Lisinopril", med.name)
        assertEquals("BP Med", med.displayName)
        assertEquals("TABLET", med.type)
        assertEquals(10.0, med.strengthValue!!)
        assertEquals("MG", med.strengthUnit)
        assertNotNull(med.stock)
        assertEquals(45.0, med.stock!!.currentStockQuantity!!)
        assertEquals(1, med.schedules.size)
        assertEquals(1, med.history.size)
        assertEquals("TAKEN", med.history[0].status)
        assertEquals(1, med.stockAdjustments.size)

        val label = deserialized.importanceLabels[0]
        assertEquals("Critical", label.name)
        assertTrue(label.isCritical)
        assertEquals(2, label.criticalAfterFollowUp)
    }

    @Test
    fun deserializeWithUnknownFieldsSucceeds() {
        val json = """
            {
                "version": 1,
                "appVersion": "2.0.0",
                "exportedAt": "2026-03-22T14:30:00Z",
                "unknownField": "should be ignored",
                "importanceLabels": [],
                "medications": [
                    {
                        "name": "Test",
                        "importanceLabel": "Silent",
                        "futureField": 42
                    }
                ]
            }
        """.trimIndent()

        val backup = backupJson.decodeFromString(BackupData.serializer(), json)
        assertEquals(1, backup.medications.size)
        assertEquals("Test", backup.medications[0].name)
    }

    @Test
    fun deserializeWithMissingOptionalFieldsUsesDefaults() {
        val json = """
            {
                "version": 1,
                "medications": [
                    {
                        "name": "Minimal",
                        "importanceLabel": "Silent"
                    }
                ]
            }
        """.trimIndent()

        val backup = backupJson.decodeFromString(BackupData.serializer(), json)
        val med = backup.medications[0]
        assertEquals("TABLET", med.type)
        assertEquals("CAPSULE_PILL", med.foregroundShape)
        assertEquals("CIRCLE", med.backgroundShape)
        assertEquals(false, med.isArchived)
        assertEquals(0, med.displayOrder)
        assertTrue(med.schedules.isEmpty())
        assertTrue(med.history.isEmpty())
        assertTrue(med.stockAdjustments.isEmpty())
    }

    @Test
    fun deserializeEmptyMedicationsList() {
        val json = """{"version": 1, "medications": []}"""
        val backup = backupJson.decodeFromString(BackupData.serializer(), json)
        assertTrue(backup.medications.isEmpty())
    }
}
