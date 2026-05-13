// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.data.support.BugReportService
import me.juliana.hellomeds.data.support.PlatformDiagnosticsProvider

class SupportViewModel(
    private val bugReportService: BugReportService,
    private val diagnosticsProvider: PlatformDiagnosticsProvider,
    private val reconciler: ScheduleReconciler,
) : ViewModel() {

    private val _reportText = MutableStateFlow<String?>(null)
    val reportText: StateFlow<String?> = _reportText

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    fun generateReport() {
        viewModelScope.launch {
            _isGenerating.value = true
            _reportText.value = try {
                bugReportService.generateReport(diagnosticsProvider, reconciler)
            } catch (e: Exception) {
                "Failed to generate report: ${e.message}"
            }
            _isGenerating.value = false
        }
    }

    fun clearReport() {
        _reportText.value = null
    }
}
