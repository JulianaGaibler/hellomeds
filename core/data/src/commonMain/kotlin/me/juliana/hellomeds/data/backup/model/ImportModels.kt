// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup.model

import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication

sealed class ImportWarning {
    data object NewerVersion : ImportWarning()
    data class UnknownMedicationType(val type: String) : ImportWarning()
    data class UnknownStrengthUnit(val unit: String) : ImportWarning()
    data class UnknownFrequencyType(val scheduleIndex: Int, val type: String) : ImportWarning()
    data class UnknownTrackingPrecision(val precision: String) : ImportWarning()
    data class UnknownContainerType(val container: String) : ImportWarning()
    data class LabelNotFound(val labelName: String) : ImportWarning()
}

enum class ImportDecision {
    IMPORT_AS_NEW,
    REPLACE,
    SKIP,
}

data class ImportAnalysis(
    val medications: List<MedicationImportInfo>,
    val labels: List<LabelImportInfo>,
    val warnings: List<ImportWarning>,
    val errors: List<String>,
    val hasHistory: Boolean,
    val hasStockAdjustments: Boolean,
)

data class MedicationImportInfo(
    val index: Int,
    val backupMedication: BackupMedication,
    val duplicateOf: Medication?,
    val validationWarnings: List<ImportWarning>,
    val hasErrors: Boolean,
    val errorMessage: String? = null,
)

data class LabelImportInfo(
    val backupLabel: BackupImportanceLabel,
    val existingMatch: ImportanceLabel?,
)

data class ImportResult(
    val medicationsImported: Int,
    val medicationsReplaced: Int,
    val medicationsSkipped: Int,
    val schedulesImported: Int,
    val labelsCreated: Int,
    val historyImported: Int,
    val stockAdjustmentsImported: Int,
)
