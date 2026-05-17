// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3.entries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import me.juliana.hellomeds.ui.features.settings.ImportanceLabelBottomSheet
import me.juliana.hellomeds.ui.features.settings.ImportanceLabelsScreen
import me.juliana.hellomeds.ui.viewmodel.ImportanceLabelViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Entry point for the Importance Labels screen.
 * Manages custom importance labels for medications.
 */
@Composable
fun ImportanceLabelsScreenEntry(onNavigateBack: () -> Unit) {
    val labelViewModel: ImportanceLabelViewModel = koinViewModel()
    val labels by labelViewModel.allLabels.collectAsStateWithLifecycle()

    var showBottomSheet by remember { mutableStateOf(false) }
    var editingLabel by remember { mutableStateOf<ImportanceLabel?>(null) }

    ImportanceLabelsScreen(
        labels = labels,
        onNavigateBack = onNavigateBack,
        onAddLabel = {
            editingLabel = null
            showBottomSheet = true
        },
        onEditLabel = { label ->
            editingLabel = label
            showBottomSheet = true
        },
    )

    if (showBottomSheet) {
        ImportanceLabelBottomSheet(
            label = editingLabel,
            onDismiss = { showBottomSheet = false },
            onSave = { label ->
                if (label.id == 0) {
                    labelViewModel.insertLabel(label)
                } else {
                    labelViewModel.updateLabel(label)
                }
                showBottomSheet = false
            },
            onDelete = if (editingLabel != null) {
                { label ->
                    // Use first label (id = 1) as default for archived medication reassignment
                    labelViewModel.deleteLabel(label, defaultLabelId = 1)
                    showBottomSheet = false
                }
            } else {
                null
            },
            onCheckDeletion = if (editingLabel != null) {
                { labelId ->
                    labelViewModel.getActiveMedicationsUsingLabel(labelId)
                }
            } else {
                null
            },
            onResetToDefault = if (editingLabel?.isDefault == true) {
                { label ->
                    labelViewModel.resetLabelToDefault(label)
                }
            } else {
                null
            },
        )
    }
}
