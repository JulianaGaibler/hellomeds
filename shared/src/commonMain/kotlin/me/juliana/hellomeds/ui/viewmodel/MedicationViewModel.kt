// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.repository.MedicationRepository
import me.juliana.hellomeds.data.repository.ScheduleRepository
import me.juliana.hellomeds.data.repository.StockTrackingRepository
import me.juliana.hellomeds.domain.validation.MedicationValidation
import me.juliana.hellomeds.ui.theme.MedicationColor

/**
 * UI model for displaying medication in grid/list
 */
data class MedicationDisplayItem(
    val id: Int,
    val name: String,
    val typeAndStrength: String,
    val scheduleSummary: String,
    val foregroundShape: MedicationForegroundShape,
    val backgroundShape: MedicationBackgroundShape,
    val color1: MedicationColor?,
)

class MedicationViewModel(
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val stockTrackingRepository: StockTrackingRepository,
    private val medicationDao: MedicationDao,
    private val displayFormatter: MedicationDisplayFormatter,
) : ViewModel() {

    // Loading state - true once the first DB emission arrives
    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    // One-shot error events for UI (Snackbar)
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    // Debounced reorder: emits the latest reorder request after 500ms of no new events
    private val _reorderRequests =
        MutableSharedFlow<List<MedicationDisplayItem>>(extraBufferCapacity = 1)

    val hasCriticalMedications: StateFlow<Boolean> = medicationDao
        .hasActiveMedicationsWithCriticalLabel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val activeMedications: StateFlow<List<MedicationDisplayItem>> =
        createDisplayItemsFlow(medicationRepository.getActive()).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val archivedMedications: StateFlow<List<MedicationDisplayItem>> =
        createDisplayItemsFlow(medicationRepository.getArchived()).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    init {
        // Process debounced reorder requests
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _reorderRequests
                .debounce(500)
                .collect { reorderedItems ->
                    performReorder(reorderedItems)
                }
        }

        // Mark as loaded once the first DB emission arrives
        viewModelScope.launch {
            medicationRepository.getActive().first()
            _hasLoaded.value = true
        }
    }

    private fun createDisplayItemsFlow(medicationsFlow: Flow<List<Medication>>): Flow<List<MedicationDisplayItem>> {
        return combine(
            medicationsFlow,
            scheduleRepository.getActive(),
        ) { medications, schedules ->
            // Group schedules by medication ID
            val schedulesByMedId = schedules.groupBy { it.medicationId }

            // Map each medication to a display item
            medications.map { medication ->
                val medicationSchedules = schedulesByMedId[medication.id] ?: emptyList()
                medication.toDisplayItem(medicationSchedules)
            }
        }.flowOn(Dispatchers.Default)
    }

    fun insertMedication(medication: Medication, onInserted: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = medicationRepository.insert(medication)
            onInserted(id)
        }
    }

    fun getMedicationById(id: Int): Flow<Medication?> {
        return medicationRepository.getById(id)
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            medicationRepository.delete(medication)
        }
    }

    fun archiveMedication(medicationId: Int) {
        viewModelScope.launch {
            medicationRepository.archive(medicationId)
        }
    }

    fun unarchiveMedication(medicationId: Int) {
        viewModelScope.launch {
            medicationRepository.unarchive(medicationId)
        }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch {
            medicationRepository.update(medication)
        }
    }

    fun updateMedicationNotes(medicationId: Int, notes: String) {
        viewModelScope.launch {
            val medication = medicationRepository.getById(medicationId).first()
            medication?.let {
                val updated = it.copy(notes = notes.trim().ifEmpty { null })
                medicationRepository.update(updated)
            }
        }
    }

    fun updateMedicationLabel(medicationId: Int, importanceLabelId: Int) {
        viewModelScope.launch {
            val medication = medicationRepository.getById(medicationId).first()
            medication?.let {
                val updated = it.copy(importanceLabelId = importanceLabelId)
                medicationRepository.update(updated)
            }
        }
    }

    fun enableStockTracking(
        medication: Medication,
        trackingPrecision: TrackingPrecision,
        initialQuantity: Double,
        lowStockThreshold: Double?,
        packagingQuantity: Double?,
        medicationContainer: MedicationContainer?,
        sealedContainerCount: Int = 0,
        depletionReminderEnabled: Boolean = false,
        containerStartedAt: Long? = null,
    ) {
        viewModelScope.launch {
            stockTrackingRepository.enableStockTracking(
                medication = medication,
                trackingPrecision = trackingPrecision,
                initialQuantity = initialQuantity,
                lowStockThreshold = lowStockThreshold,
                packagingQuantity = packagingQuantity,
                medicationContainer = medicationContainer,
                sealedContainerCount = sealedContainerCount,
                depletionReminderEnabled = depletionReminderEnabled,
                containerStartedAt = containerStartedAt,
            )
        }
    }

    fun updateCycleConfig(
        medicationId: Int,
        cycleType: CycleType,
        daysActive: Int?,
        daysBreak: Int?,
        hasPlacebos: Boolean,
        startDate: kotlinx.datetime.LocalDate?,
    ) {
        viewModelScope.launch {
            val medication = medicationRepository.getById(medicationId).first() ?: return@launch
            val updated = medication.copy(
                cycleType = cycleType,
                cycleDaysActive = daysActive,
                cycleDaysBreak = daysBreak,
                cycleHasPlacebos = hasPlacebos,
                cycleStartDate = startDate,
            )
            medicationRepository.update(updated)
        }
    }

    fun updateTimeZoneMode(medicationId: Int, timeZoneMode: TimeZoneMode) {
        viewModelScope.launch {
            val medication = medicationRepository.getById(medicationId).first() ?: return@launch
            val updated = medication.copy(timeZoneMode = timeZoneMode)
            medicationRepository.update(updated)
        }
    }

    fun startNewPack(medicationId: Int) {
        viewModelScope.launch {
            val medication = medicationRepository.getById(medicationId).first() ?: return@launch
            val today = kotlin.time.Clock.System.now()
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            val updated = medication.copy(cycleStartDate = today)
            medicationRepository.update(updated)
        }
    }

    fun setCycleDay(medicationId: Int, dayInCycle: Int) {
        viewModelScope.launch {
            val medication = medicationRepository.getById(medicationId).first() ?: return@launch
            val today = kotlin.time.Clock.System.now()
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            val updated = medication.copy(
                cycleStartDate = today.minus(dayInCycle - 1, DateTimeUnit.DAY),
            )
            medicationRepository.update(updated)
        }
    }

    fun reorderMedications(reorderedMedications: List<MedicationDisplayItem>) {
        _reorderRequests.tryEmit(reorderedMedications)
    }

    private suspend fun performReorder(reorderedMedications: List<MedicationDisplayItem>) {
        val currentMedications = medicationRepository.getActive().first()
        val medicationMap = currentMedications.associateBy { it.id }
        val reorderedFullMedications = reorderedMedications.mapNotNull { displayItem ->
            medicationMap[displayItem.id]
        }
        medicationRepository.reorderMedications(reorderedFullMedications)
    }

    private fun Medication.toDisplayItem(schedules: List<Schedule>): MedicationDisplayItem {
        val activeSchedules = schedules.filter { !it.isEffectivelyArchived() }

        val scheduleSummary = when (activeSchedules.size) {
            0 -> displayFormatter.asNeededLabel()
            1 -> displayFormatter.frequencyText(activeSchedules.first())
            else -> displayFormatter.scheduleCountText(activeSchedules.size)
        }

        return MedicationDisplayItem(
            id = id,
            name = MedicationValidation.getEffectiveDisplayName(this),
            typeAndStrength = displayFormatter.typeAndStrength(this),
            scheduleSummary = scheduleSummary,
            foregroundShape = parseEnum(foregroundShape, MedicationForegroundShape.CAPSULE_PILL),
            backgroundShape = parseEnum(backgroundShape, MedicationBackgroundShape.CIRCLE),
            color1 = shapeColor?.let { MedicationColor.fromName(it) },
        )
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String?, default: T): T {
        if (value == null) return default
        return try {
            enumValueOf<T>(value.uppercase().replace(" ", "_"))
        } catch (e: IllegalArgumentException) {
            default
        }
    }
}
