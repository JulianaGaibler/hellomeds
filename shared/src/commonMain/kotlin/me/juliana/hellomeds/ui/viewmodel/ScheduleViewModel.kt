// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.repository.ScheduleRepository

class ScheduleViewModel(
    private val repository: ScheduleRepository,
) : ViewModel() {

    val allSchedules: StateFlow<List<Schedule>> = repository.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val activeSchedules: StateFlow<List<Schedule>> = repository.getActive().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun insertSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.insert(schedule)
        }
    }

    fun updateSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.update(schedule)
        }
    }

    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.delete(schedule)
        }
    }

    fun archiveSchedule(scheduleId: Int) {
        viewModelScope.launch {
            repository.archive(scheduleId)
        }
    }

    fun unarchiveSchedule(scheduleId: Int) {
        viewModelScope.launch {
            repository.unarchive(scheduleId)
        }
    }

    fun getScheduleById(id: Int) = repository.getById(id)

    fun getSchedulesByMedicationId(medicationId: Int) = repository.getByMedicationId(medicationId)
}
