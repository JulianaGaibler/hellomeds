// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication.steps

import androidx.compose.runtime.Composable
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.ui.components.medication.ImportanceLabelSelector

/**
 * Step 6: Select importance label
 */
@Composable
internal fun ImportanceLabelStep(labels: List<ImportanceLabel>, selectedLabelId: Int?, onLabelSelected: (Int) -> Unit) {
    ImportanceLabelSelector(
        labels = labels,
        selectedLabelId = selectedLabelId,
        onLabelSelected = onLabelSelected,
        showHeader = true,
        showFooter = true,
    )
}
