// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.StockStatus
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.repository.StockTrackingRepository
import me.juliana.hellomeds.ui.components.graph.models.StockLine
import me.juliana.hellomeds.ui.util.StockGraphBuilder

/**
 * ViewModel for the Stock Tracking Dashboard screen.
 * Manages medication inventory, low stock warnings, and stock adjustments.
 */
class StockTrackingViewModel(
    private val repository: StockTrackingRepository,
    private val stockDisplayFormatter: StockDisplayFormatter,
    private val stockGraphBuilder: StockGraphBuilder,
) : ViewModel() {

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private val _stockStatuses = MutableStateFlow<Map<Int, StockStatus>>(emptyMap())
    val stockStatuses: StateFlow<Map<Int, StockStatus>> = _stockStatuses.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getTrackedMedications().collect { medications ->
                val results = medications
                    .filter { it.stockTrackingEnabled }
                    .map { med ->
                        async(Dispatchers.Default) {
                            try {
                                med.id to repository.calculateStockStatus(med)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .toMap()
                _stockStatuses.value = results
                if (!_hasLoaded.value) _hasLoaded.value = true
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val trackedMedications: StateFlow<List<MedicationStockDisplay>> =
        repository.getTrackedMedications()
            .flatMapLatest { medications ->
                if (medications.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val stockFlows = medications.map { medication ->
                        repository.getCurrentStock(medication.id).map { stock ->
                            medication to (stock ?: 0.0)
                        }
                    }
                    combine(stockFlows) { pairs ->
                        pairs.map { (medication, currentStock) ->
                            MedicationStockDisplay(
                                medication = medication,
                                currentStock = currentStock,
                                formattedStock = stockDisplayFormatter.formatStockQuantity(
                                    medication,
                                    currentStock,
                                ),
                                isLowStock = stockDisplayFormatter.shouldShowLowStockWarning(
                                    medication,
                                    currentStock,
                                ),
                                severity = stockDisplayFormatter.getStockSeverity(medication, currentStock),
                            )
                        }.sortedByDescending { it.isLowStock }
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    val lowStockMedications: StateFlow<List<MedicationStockDisplay>> =
        trackedMedications
            .map { it.filter { display -> display.isLowStock } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    val lowStockCount: StateFlow<Int> =
        lowStockMedications
            .map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0,
            )

    fun recordRefill(medicationId: Int, quantity: Double, notes: String? = null) {
        viewModelScope.launch {
            repository.recordRefill(medicationId, quantity, notes)
        }
    }

    fun recordCorrection(medicationId: Int, newQuantity: Double, notes: String? = null) {
        viewModelScope.launch {
            repository.recordCorrection(medicationId, newQuantity, notes)
        }
    }

    fun recordContainerDepleted(medicationId: Int) {
        viewModelScope.launch {
            repository.recordContainerDepleted(medicationId)
        }
    }

    fun getMedicationById(medicationId: Int): Flow<Medication?> {
        return repository.getMedicationById(medicationId)
    }

    fun getCurrentStock(medicationId: Int): Flow<Double?> {
        return repository.getCurrentStock(medicationId)
    }

    fun getStockGraphLine(medication: Medication): Flow<StockLine> {
        return stockGraphBuilder.getStockGraphLine(medication)
    }

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val allStockGraphLines: StateFlow<List<StockLine>> =
        repository.getTrackedMedications().flatMapLatest { medications ->
            if (medications.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(medications.map { stockGraphBuilder.getStockGraphLine(it) }) { it.toList() }
            }
        }
            .flowOn(Dispatchers.Default)
            .debounce(100)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    suspend fun calculateStockStatus(medication: Medication): StockStatus {
        return withContext(Dispatchers.Default) {
            repository.calculateStockStatus(medication)
        }
    }

    suspend fun deleteAllTrackingData(medicationId: Int) {
        repository.deleteAllTrackingData(medicationId)
    }

    fun updateLowStockThreshold(medicationId: Int, threshold: Double?) {
        viewModelScope.launch { repository.updateLowStockThreshold(medicationId, threshold) }
    }

    fun updateContainerType(medicationId: Int, container: MedicationContainer?) {
        viewModelScope.launch { repository.updateContainerType(medicationId, container) }
    }

    fun updatePackagingQuantity(medicationId: Int, newQuantity: Double?) {
        viewModelScope.launch { repository.updatePackagingQuantity(medicationId, newQuantity) }
    }

    fun updateDepletionReminderEnabled(medicationId: Int, enabled: Boolean) {
        viewModelScope.launch { repository.updateDepletionReminderEnabled(medicationId, enabled) }
    }

    fun setSealedContainerCount(medicationId: Int, count: Int) {
        viewModelScope.launch {
            repository.setSealedContainerCount(medicationId, count)
        }
    }

    fun adjustEstimatedStockLevel(medicationId: Int, newPackagingQuantity: Double, newDesiredRemaining: Double) {
        viewModelScope.launch {
            repository.adjustEstimatedStockLevel(medicationId, newPackagingQuantity, newDesiredRemaining)
        }
    }

    fun getDailyConsumptionFlow(medicationId: Int): Flow<Double> {
        return repository.getDailyConsumptionFlow(medicationId)
    }
}

data class MedicationStockDisplay(
    val medication: Medication,
    val currentStock: Double,
    val formattedStock: String,
    val isLowStock: Boolean,
    val severity: String,
)
