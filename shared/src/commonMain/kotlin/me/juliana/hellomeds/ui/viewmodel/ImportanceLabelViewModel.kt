// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.repository.ImportanceLabelRepository

class ImportanceLabelViewModel(
    private val repository: ImportanceLabelRepository,
) : ViewModel() {

    val allLabels: StateFlow<List<ImportanceLabel>> = repository.allLabels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun insertLabel(label: ImportanceLabel) {
        viewModelScope.launch {
            repository.insert(label)
        }
    }

    fun updateLabel(label: ImportanceLabel) {
        viewModelScope.launch {
            repository.update(label)
        }
    }

    fun deleteLabel(label: ImportanceLabel, defaultLabelId: Int) {
        viewModelScope.launch {
            repository.deleteWithArchivedReassignment(label, defaultLabelId)
        }
    }

    fun getLabelById(id: Int) = repository.getById(id)

    suspend fun canDeleteLabel(labelId: Int): Boolean {
        return repository.canDeleteLabel(labelId)
    }

    suspend fun getActiveMedicationsUsingLabel(labelId: Int): List<Medication> {
        return repository.getActiveMedicationsUsingLabel(labelId).first()
    }

    fun resetLabelToDefault(label: ImportanceLabel) {
        viewModelScope.launch {
            repository.resetToDefault(label)
        }
    }
}
